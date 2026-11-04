package com.droid.agypets.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import org.json.JSONObject
import java.io.File

/**
 * Detects whether the Antigravity Android app (com.google.android.apps.antigravity) currently
 * has a visible window on screen.
 *
 * Strategy (stateful, incremental UsageEvents tracking):
 *  - Every activity of the Antigravity package is tracked individually by class name.
 *  - ACTIVITY_RESUMED / ACTIVITY_PAUSED -> activity is visible (paused == visible but unfocused
 *    in GoogleBook desktop multi-window mode).
 *  - ACTIVITY_STOPPED / ACTIVITY_DESTROYED -> activity no longer visible (minimized, closed,
 *    or force-stopped).
 *  - The app counts as "open" while at least one of its activities is visible.
 *
 * Secondary signal: the sync_usage.py heartbeat in usage_limits.json ("lsAlive"). The Antigravity
 * LanguageServer port is a tunnel opened by the Antigravity app itself, so when the app is closed
 * the tunnel disappears and the heartbeat reports lsAlive=false.
 */
object AntigravityDetector {

  const val ANTIGRAVITY_PACKAGE = "com.google.android.apps.antigravity"

  private const val USAGE_LIMITS_PATH =
    "/storage/emulated/10/Documents/Projects/.gemini/antigravity/usage_limits.json"
  private const val HEARTBEAT_FRESH_MS = 30_000L
  private const val INITIAL_LOOKBACK_MS = 24L * 3600 * 1000

  // Hidden/Q+ event type constants (values are stable across releases)
  private const val EVT_ACTIVITY_RESUMED = 1
  private const val EVT_ACTIVITY_PAUSED = 2
  private const val EVT_ACTIVITY_STOPPED = 23
  private const val EVT_ACTIVITY_DESTROYED = 24
  private const val EVT_DEVICE_SHUTDOWN = 26
  private const val EVT_DEVICE_STARTUP = 27

  private val visibleActivities = mutableSetOf<String>()
  private var lastQueryEnd = 0L

  /** Result of a detection pass, exposed for logging/diagnostics. */
  data class Detection(
    val isOpen: Boolean,
    val usageStatsVisible: Boolean?,
    val lsAlive: Boolean?
  )

  fun hasUsageAccessPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
      )
    } else {
      @Suppress("DEPRECATION")
      appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
      )
    }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  /**
   * Returns true if Antigravity has at least one visible activity, false if not,
   * or null if Usage Access is not granted.
   */
  @Synchronized
  fun isAntigravityVisible(context: Context): Boolean? {
    if (!hasUsageAccessPermission(context)) return null
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      ?: return null

    val now = System.currentTimeMillis()
    val begin = if (lastQueryEnd == 0L) {
      visibleActivities.clear()
      now - INITIAL_LOOKBACK_MS
    } else {
      // Small overlap so events that are flushed late are not missed
      lastQueryEnd - 2_000L
    }

    return try {
      val events = usm.queryEvents(begin, now)
      val e = UsageEvents.Event()
      while (events.hasNextEvent()) {
        events.getNextEvent(e)
        when (e.eventType) {
          EVT_DEVICE_SHUTDOWN, EVT_DEVICE_STARTUP -> visibleActivities.clear()
        }
        if (e.packageName != ANTIGRAVITY_PACKAGE) continue
        val key = e.className ?: "default"
        when (e.eventType) {
          EVT_ACTIVITY_RESUMED, EVT_ACTIVITY_PAUSED -> visibleActivities.add(key)
          EVT_ACTIVITY_STOPPED, EVT_ACTIVITY_DESTROYED -> visibleActivities.remove(key)
        }
      }
      lastQueryEnd = now
      visibleActivities.isNotEmpty()
    } catch (ex: Exception) {
      null
    }
  }

  /**
   * Reads the sync_usage.py heartbeat. Returns true/false only if the heartbeat is fresh,
   * otherwise null (unknown).
   */
  fun readLsAliveHeartbeat(): Boolean? {
    return try {
      val f = File(USAGE_LIMITS_PATH)
      if (!f.exists()) return null
      val json = JSONObject(f.readText())
      if (!json.has("lsAlive")) return null
      val updated = json.optLong("updatedAtEpochMs", 0L)
      if (System.currentTimeMillis() - updated > HEARTBEAT_FRESH_MS) return null
      json.optBoolean("lsAlive", false)
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Final decision:
   *  - A fresh heartbeat saying the LanguageServer is gone always means "closed".
   *  - Otherwise UsageStats visibility decides.
   *  - Without Usage Access, fall back to the heartbeat (unknown -> closed).
   */
  fun detect(context: Context): Detection {
    val visible = isAntigravityVisible(context)
    val lsAlive = readLsAliveHeartbeat()
    val isOpen = when {
      lsAlive == false -> false
      visible != null -> visible
      else -> lsAlive == true
    }
    return Detection(isOpen, visible, lsAlive)
  }

  /** Forces a full rescan on the next query (e.g., after the service restarts). */
  @Synchronized
  fun reset() {
    lastQueryEnd = 0L
    visibleActivities.clear()
  }
}
