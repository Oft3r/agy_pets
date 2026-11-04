package com.droid.agypets.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class AgyUsageRepository(private val context: Context) {

  private val _usageState = MutableStateFlow(AgyUsageData(quotaGroups = defaultAntigravityGroups(System.currentTimeMillis())))
  val usageState: StateFlow<AgyUsageData> = _usageState.asStateFlow()

  private val prefs = PetPreferences(context)

  companion object {
    private const val TAG = "AgyUsageRepository"
    private const val GEMINI_DIR = "/storage/emulated/10/Documents/Projects/.gemini/antigravity"
    private const val DB_PATH = "$GEMINI_DIR/conversation_summaries.db"
    private const val BRAIN_DIR = "$GEMINI_DIR/brain"
    private const val LIMITS_JSON_PATH = "$GEMINI_DIR/usage_limits.json"
    private const val LOCAL_SYNC_HTTP_URL = "http://127.0.0.1:18765/quota"
    private const val STATUS_API_URL = "https://status.cloud.google.com/incidents.json"
    private const val CACHE_STATUS_MS = 5 * 60 * 1000L // 5 minutes

    fun parseIso8601ToMillis(iso: String): Long {
      if (iso.isBlank()) return 0L
      return try {
        val clean = iso.trim().replace("Z", "+0000")
        val normalized = if (clean.contains(".")) {
          val prefix = clean.substringBefore(".")
          val suffix = clean.substringAfter(".")
          val tzIdx = suffix.indexOfFirst { it == '+' || it == '-' }
          if (tzIdx >= 0) {
            val tz = suffix.substring(tzIdx).replace(":", "")
            "$prefix$tz"
          } else {
            "${prefix}+0000"
          }
        } else {
          // Handle +00:00 -> +0000
          if (clean.length >= 6 && (clean[clean.length - 6] == '+' || clean[clean.length - 6] == '-')) {
            clean.substring(0, clean.length - 3) + clean.substring(clean.length - 2)
          } else {
            clean
          }
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }
        sdf.parse(normalized)?.time ?: 0L
      } catch (e: Exception) {
        0L
      }
    }

    fun formatCountdownFromMillis(targetMillis: Long, now: Long): String {
      if (targetMillis <= 0L) return "—"
      if (targetMillis <= now) return "Ready"
      val diff = targetMillis - now
      val days = TimeUnit.MILLISECONDS.toDays(diff)
      val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
      val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
      return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
      }
    }

    fun defaultAntigravityGroups(now: Long): List<QuotaGroupItem> {
      val gemini5hReset = "2026-11-05T02:11:18Z"
      val geminiWeekReset = "2026-11-11T00:42:08Z"
      val claude5hReset = "2026-11-05T03:34:56Z"
      val claudeWeekReset = "2026-11-11T22:34:56Z"
      val argonWeekReset = "2026-11-05T20:29:54Z"

      return listOf(
        QuotaGroupItem(
          displayName = "Gemini Models",
          description = "Gemini 3.8 Flash (Low / Medium / High), Gemini Pro",
          buckets = listOf(
            QuotaBucketItem(
              bucketId = "gemini-5h",
              displayName = "5-Hour Limit",
              window = "5h",
              remainingFraction = 0.845f,
              remainingPercent = 85,
              usedPercent = 15,
              resetTimeIso = gemini5hReset,
              resetCountdown = formatCountdownFromMillis(parseIso8601ToMillis(gemini5hReset), now).let {
                if (it == "—" || it == "Ready") "3h 33m" else it
              }
            ),
            QuotaBucketItem(
              bucketId = "gemini-weekly",
              displayName = "Weekly Limit",
              window = "weekly",
              remainingFraction = 0.896f,
              remainingPercent = 90,
              usedPercent = 10,
              resetTimeIso = geminiWeekReset,
              resetCountdown = formatCountdownFromMillis(parseIso8601ToMillis(geminiWeekReset), now).let {
                if (it == "—" || it == "Ready") "6d 2h" else it
              }
            )
          )
        ),
        QuotaGroupItem(
          displayName = "Gemini 4 Argon",
          description = "Gemini 4 Argon (Low / Medium / High) — Dedicated Pro quota",
          buckets = listOf(
            QuotaBucketItem(
              bucketId = "gemini-argon-wtu-weekly",
              displayName = "Argon Weekly Limit",
              window = "weekly",
              remainingFraction = 0.114f,
              remainingPercent = 11,
              usedPercent = 89,
              resetTimeIso = argonWeekReset,
              resetCountdown = formatCountdownFromMillis(parseIso8601ToMillis(argonWeekReset), now).let {
                if (it == "—" || it == "Ready") "21h 52m" else it
              }
            )
          )
        ),
        QuotaGroupItem(
          displayName = "Claude and GPT models",
          description = "Claude Opus 5.5, Claude Sonnet 5.5, GPT-OSS 120B",
          buckets = listOf(
            QuotaBucketItem(
              bucketId = "3p-5h",
              displayName = "5-Hour Limit",
              window = "5h",
              remainingFraction = 1.0f,
              remainingPercent = 100,
              usedPercent = 0,
              resetTimeIso = claude5hReset,
              resetCountdown = formatCountdownFromMillis(parseIso8601ToMillis(claude5hReset), now).let {
                if (it == "—" || it == "Ready") "4h 57m" else it
              }
            ),
            QuotaBucketItem(
              bucketId = "3p-weekly",
              displayName = "Weekly Limit",
              window = "weekly",
              remainingFraction = 1.0f,
              remainingPercent = 100,
              usedPercent = 0,
              resetTimeIso = claudeWeekReset,
              resetCountdown = formatCountdownFromMillis(parseIso8601ToMillis(claudeWeekReset), now).let {
                if (it == "—" || it == "Ready") "6d 23h" else it
              }
            )
          )
        )
      )
    }
  }

  private var lastStatusFetchTime = 0L
  private var cachedCloudStatusOk = true
  private var cachedCloudStatusText = "Google Cloud & Gemini: Operational"

  fun hasStorageAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      true
    }
  }

  private fun fetchLocalDaemonJson(): JSONObject? {
    return try {
      val url = URL(LOCAL_SYNC_HTTP_URL)
      val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 1200
        readTimeout = 1500
        requestMethod = "GET"
      }
      if (conn.responseCode == 200) {
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(text)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun readLimitsFileJson(): JSONObject? {
    return try {
      val file = File(LIMITS_JSON_PATH)
      if (file.exists() && file.canRead()) {
        JSONObject(file.readText())
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun translateBucketLabel(window: String, rawName: String): String {
    return when {
      window.equals("5h", ignoreCase = true) -> "5-Hour Cycle"
      window.equals("weekly", ignoreCase = true) && rawName.contains("Argon", ignoreCase = true) ->
        "Argon Weekly Cycle"
      window.equals("weekly", ignoreCase = true) -> "Weekly Cycle"
      else -> rawName.ifBlank { "Quota Limit" }
    }
  }

  private fun translateGroupDesc(groupName: String, rawDesc: String): String {
    return when {
      groupName.contains("Argon", ignoreCase = true) ->
        "Gemini 4 Argon (Low, Medium, High) — Dedicated Pro quota"
      groupName.contains("Claude", ignoreCase = true) ->
        "Claude Opus 5.5, Claude Sonnet 5.5, GPT-OSS 120B"
      groupName.contains("Gemini", ignoreCase = true) ->
        "Gemini 3.8 Flash (Low, Medium, High), Gemini Pro"
      else -> rawDesc
    }
  }

  suspend fun refresh() = withContext(Dispatchers.IO) {
    val canReadStorage = hasStorageAccess()
    val contextLimit = prefs.contextWindowLimitTokens
    val now = System.currentTimeMillis()

    // 1. Try local HTTP daemon first (works even without MANAGE_EXTERNAL_STORAGE), then shared JSON file
    val snapshotJson = fetchLocalDaemonJson() ?: readLimitsFileJson()
    val isLive = snapshotJson != null

    var planName = "Pro"
    var activeModelLabel = "Gemini 4 Argon (Medium)"
    var activeGroupName = "Gemini 4 Argon"
    val parsedGroups = mutableListOf<QuotaGroupItem>()
    val parsedModels = mutableListOf<ModelQuotaItem>()

    // Session fallback from snapshotJson
    var snapConvId = "01cc1e46"
    var snapTitle = "Crear Mascota Pixel Art AntiGravity"
    var snapRunning = false
    var snapSteps = 625
    var snapTotalSteps = 2127
    var snapContextTokens = 62_790L
    var snapInputTokens = 1_214_315L
    var snapOutputTokens = 259_900L
    var snapCacheTokens = 38_965_058L
    val snapRecentSessions = mutableListOf<SessionSummaryItem>()

    if (snapshotJson != null) {
      planName = snapshotJson.optString("planName", planName)
      activeModelLabel = snapshotJson.optString("activeModelLabel", activeModelLabel)
      activeGroupName = snapshotJson.optString("activeGroupName", activeGroupName)

      val groupsArr = snapshotJson.optJSONArray("groups")
      if (groupsArr != null) {
        for (i in 0 until groupsArr.length()) {
          val gObj = groupsArr.optJSONObject(i) ?: continue
          val gName = gObj.optString("displayName", "Grupo")
          val gDesc = translateGroupDesc(gName, gObj.optString("description", ""))
          val bArr = gObj.optJSONArray("buckets")
          val buckets = mutableListOf<QuotaBucketItem>()
          if (bArr != null) {
            for (j in 0 until bArr.length()) {
              val bObj = bArr.optJSONObject(j) ?: continue
              val bId = bObj.optString("bucketId", "")
              val win = bObj.optString("window", "")
              val rawDisplay = bObj.optString("displayName", "Límite")
              val remFrac = bObj.optDouble("remainingFraction", 1.0).toFloat()
              val remPct = bObj.optInt("remainingPercent", (remFrac * 100f).toInt()).coerceIn(0, 100)
              val usedPct = bObj.optInt("usedPercent", 100 - remPct).coerceIn(0, 100)
              val resetIso = bObj.optString("resetTime", "")
              val targetMs = parseIso8601ToMillis(resetIso)
              val dynamicCountdown = formatCountdownFromMillis(targetMs, now)
              val finalCountdown = if (dynamicCountdown != "—") {
                dynamicCountdown
              } else {
                bObj.optString("resetCountdown", "—")
              }
              buckets.add(
                QuotaBucketItem(
                  bucketId = bId,
                  displayName = translateBucketLabel(win, rawDisplay),
                  window = win,
                  remainingFraction = remFrac,
                  remainingPercent = remPct,
                  usedPercent = usedPct,
                  resetTimeIso = resetIso,
                  resetCountdown = finalCountdown
                )
              )
            }
          }
          // Sort buckets so 5h comes first, then weekly
          val sortedBuckets = buckets.sortedBy { if (it.window == "5h") 0 else 1 }
          parsedGroups.add(
            QuotaGroupItem(
              displayName = gName,
              description = gDesc,
              buckets = sortedBuckets
            )
          )
        }
      }

      val modelsArr = snapshotJson.optJSONArray("models")
      if (modelsArr != null) {
        for (i in 0 until modelsArr.length()) {
          val mObj = modelsArr.optJSONObject(i) ?: continue
          val resetIso = mObj.optString("resetTime", "")
          val targetMs = parseIso8601ToMillis(resetIso)
          val dynCount = formatCountdownFromMillis(targetMs, now).let {
            if (it != "—") it else mObj.optString("resetCountdown", "")
          }
          val remPct = mObj.optInt("remainingPercent", 100).coerceIn(0, 100)
          val usedPct = mObj.optInt("usedPercent", 100 - remPct).coerceIn(0, 100)
          parsedModels.add(
            ModelQuotaItem(
              name = mObj.optString("label", mObj.optString("name", "Modelo")),
              modelId = mObj.optString("modelId", ""),
              usedPercent = usedPct,
              remainingPercent = remPct,
              resetCountdown = dynCount,
              resetTimeIso = resetIso
            )
          )
        }
      }

      val sessObj = snapshotJson.optJSONObject("session")
      if (sessObj != null) {
        snapConvId = sessObj.optString("conversationId", snapConvId)
        snapTitle = sessObj.optString("title", snapTitle)
        snapRunning = sessObj.optString("runStatus", "IDLE") == "RUNNING"
        snapSteps = sessObj.optInt("stepCount", snapSteps)
        snapTotalSteps = sessObj.optInt("totalStepsAll", snapTotalSteps)
        snapContextTokens = sessObj.optLong("contextTokens", snapContextTokens)
        snapInputTokens = sessObj.optLong("inputTokens", snapInputTokens)
        snapOutputTokens = sessObj.optLong("outputTokens", snapOutputTokens)
        snapCacheTokens = sessObj.optLong("cacheReadTokens", snapCacheTokens)

        val recArr = sessObj.optJSONArray("recentSessions")
        if (recArr != null) {
          for (i in 0 until recArr.length()) {
            val r = recArr.optJSONObject(i) ?: continue
            snapRecentSessions.add(
              SessionSummaryItem(
                id = r.optString("id", ""),
                title = r.optString("title", "Sesión"),
                stepCount = r.optInt("stepCount", 0),
                isRunning = r.optBoolean("isRunning", false),
                lastModified = r.optString("lastModified", "")
              )
            )
          }
        }
      }
    }

    val effectiveGroups = if (parsedGroups.isNotEmpty()) {
      parsedGroups
    } else {
      defaultAntigravityGroups(now)
    }

    // Determine active group & primary bucket for status pill
    val matchedGroup = effectiveGroups.firstOrNull {
      it.displayName.equals(activeGroupName, ignoreCase = true) ||
        (activeModelLabel.contains("Argon", ignoreCase = true) && it.displayName.contains("Argon", ignoreCase = true)) ||
        ((activeModelLabel.contains("Claude", ignoreCase = true) || activeModelLabel.contains("GPT", ignoreCase = true)) && it.displayName.contains("Claude", ignoreCase = true))
    } ?: effectiveGroups.first()

    val shortGroupName = when {
      matchedGroup.displayName.contains("Argon", ignoreCase = true) -> "Argon"
      matchedGroup.displayName.contains("Claude", ignoreCase = true) -> "Claude/GPT"
      else -> "Gemini"
    }

    val primaryBucket = matchedGroup.buckets.firstOrNull { it.window == "5h" }
      ?: matchedGroup.buckets.firstOrNull()

    val primaryUsedPct = primaryBucket?.usedPercent ?: 15
    val primaryRemPct = primaryBucket?.remainingPercent ?: 85
    val primaryWinLabel = if (primaryBucket?.window == "5h") "5h" else "Sem"
    val primaryCountdown = primaryBucket?.resetCountdown ?: "3h 33m"

    // Also populate legacy 5h and weekly summary fields from active/Gemini group
    val geminiGroup = effectiveGroups.firstOrNull { it.displayName.contains("Gemini Models", ignoreCase = true) }
    val any5hBucket = matchedGroup.buckets.firstOrNull { it.window == "5h" }
      ?: geminiGroup?.buckets?.firstOrNull { it.window == "5h" }
    val anyWeeklyBucket = matchedGroup.buckets.firstOrNull { it.window == "weekly" }
      ?: geminiGroup?.buckets?.firstOrNull { it.window == "weekly" }

    val summary5hUsed = any5hBucket?.usedPercent ?: primaryUsedPct
    val summary5hCount = any5hBucket?.resetCountdown ?: primaryCountdown
    val summaryWeekUsed = anyWeeklyBucket?.usedPercent ?: primaryUsedPct
    val summaryWeekCount = anyWeeklyBucket?.resetCountdown ?: primaryCountdown

    // 2. If direct SQLite/File access is available, enrich session & token data directly
    val dbFile = File(DB_PATH)
    if (canReadStorage && dbFile.exists() && dbFile.canRead()) {
      try {
        var activeId = snapConvId
        var activeTitle = snapTitle
        var isRunning = snapRunning
        var activeSteps = snapSteps
        var totalSteps = 0
        val sessionList = mutableListOf<SessionSummaryItem>()

        SQLiteDatabase.openDatabase(DB_PATH, null, SQLiteDatabase.OPEN_READONLY).use { db ->
          val cursor = db.rawQuery(
            """
            SELECT conversation_id, title, step_count, status, not_fully_idle, last_modified_time
            FROM conversation_summaries
            ORDER BY last_modified_time DESC LIMIT 10
            """.trimIndent(),
            null
          )

          cursor.use { c ->
            var isFirst = true
            while (c.moveToNext()) {
              val id = c.getString(0) ?: ""
              val title = c.getString(1) ?: "Untitled"
              val steps = c.getInt(2)
              val status = c.getString(3) ?: ""
              val notIdle = c.getInt(4) == 1
              val lastMod = c.getString(5) ?: ""

              totalSteps += steps
              val running = (status == "CASCADE_RUN_STATUS_RUNNING" || notIdle)

              if (isFirst) {
                activeId = id
                activeTitle = title
                isRunning = running
                activeSteps = steps
                isFirst = false
              }

              sessionList.add(
                SessionSummaryItem(
                  id = id,
                  title = title,
                  stepCount = steps,
                  isRunning = running,
                  lastModified = lastMod
                )
              )
            }
          }
        }

        var inTokens = snapInputTokens
        var outTokens = snapOutputTokens
        var cacheTokens = snapCacheTokens
        var activeContextTokens = snapContextTokens

        if (activeId.isNotEmpty()) {
          val transcriptFile = File("$BRAIN_DIR/$activeId/.system_generated/logs/transcript.jsonl")
          if (transcriptFile.exists() && transcriptFile.canRead()) {
            var readIn = 0L
            var readOut = 0L
            var readCache = 0L
            var readCtx = 0L
            BufferedReader(FileReader(transcriptFile)).use { reader ->
              var line: String? = reader.readLine()
              while (line != null) {
                if (line.isNotEmpty()) {
                  try {
                    val obj = JSONObject(line)
                    val it = obj.optLong("input_tokens", 0L)
                    val ot = obj.optLong("output_tokens", 0L)
                    val ct = obj.optLong("cache_read_tokens", 0L)
                    readIn += it
                    readOut += ot
                    readCache += ct
                    if (it > 0 || ct > 0) {
                      readCtx = it + ct
                    }
                  } catch (e: Exception) {
                    // Ignore malformed line
                  }
                }
                line = reader.readLine()
              }
            }
            if (readIn > 0 || readOut > 0) {
              inTokens = readIn
              outTokens = readOut
              cacheTokens = readCache
              activeContextTokens = readCtx
            }
          }
        }

        updateCloudStatusIfStale()

        val contextPct = if (contextLimit > 0) {
          ((activeContextTokens * 100) / contextLimit).toInt().coerceIn(0, 100)
        } else {
          0
        }

        _usageState.value = AgyUsageData(
          activeSessionId = activeId,
          activeSessionTitle = activeTitle,
          isAgentRunning = isRunning,
          activeSessionSteps = activeSteps,
          planName = planName,
          activeModelLabel = activeModelLabel,
          activeGroupName = matchedGroup.displayName,
          activeGroupShortName = shortGroupName,
          activePrimaryUsedPercent = primaryUsedPct,
          activePrimaryRemainingPercent = primaryRemPct,
          activePrimaryWindowLabel = primaryWinLabel,
          activePrimaryResetCountdown = primaryCountdown,
          quotaGroups = effectiveGroups,
          sessionQuotaPercent = summary5hUsed,
          sessionResetCountdown = summary5hCount,
          weeklyQuotaPercent = summaryWeekUsed,
          weeklyResetCountdown = summaryWeekCount,
          contextTokens = activeContextTokens,
          contextLimit = contextLimit,
          contextPercent = contextPct,
          inputTokens = inTokens,
          outputTokens = outTokens,
          cacheTokens = cacheTokens,
          modelQuotas = parsedModels,
          totalStepsAll = if (totalSteps > 0) totalSteps else snapTotalSteps,
          recentSessions = if (sessionList.isNotEmpty()) sessionList else snapRecentSessions,
          cloudStatusOk = cachedCloudStatusOk,
          cloudStatusText = cachedCloudStatusText,
          hasStorageAccess = true,
          isLiveConnected = true,
          lastUpdatedMillis = now
        )
        return@withContext
      } catch (e: Exception) {
        Log.e(TAG, "Error reading SQLite DB directly, falling back to daemon snapshot", e)
      }
    }

    // 3. Daemon HTTP or Fallback mode
    updateCloudStatusIfStale()
    val contextPct = if (contextLimit > 0) {
      ((snapContextTokens * 100) / contextLimit).toInt().coerceIn(0, 100)
    } else {
      0
    }

    val defaultRecent = if (snapRecentSessions.isNotEmpty()) {
      snapRecentSessions
    } else {
      listOf(
        SessionSummaryItem("01cc1e46", "Crear Mascota Pixel Art AntiGravity", 625, true, "Active"),
        SessionSummaryItem("e5c9752f", "Clone GitHub Repository Guayaba Menu", 686, false, "Recent"),
        SessionSummaryItem("bfea0a7a", "Bandada De Estorninhos En Vuelo", 149, false, "Recent")
      )
    }

    _usageState.value = AgyUsageData(
      activeSessionId = snapConvId,
      activeSessionTitle = snapTitle,
      isAgentRunning = snapRunning,
      activeSessionSteps = snapSteps,
      planName = planName,
      activeModelLabel = activeModelLabel,
      activeGroupName = matchedGroup.displayName,
      activeGroupShortName = shortGroupName,
      activePrimaryUsedPercent = primaryUsedPct,
      activePrimaryRemainingPercent = primaryRemPct,
      activePrimaryWindowLabel = primaryWinLabel,
      activePrimaryResetCountdown = primaryCountdown,
      quotaGroups = effectiveGroups,
      sessionQuotaPercent = summary5hUsed,
      sessionResetCountdown = summary5hCount,
      weeklyQuotaPercent = summaryWeekUsed,
      weeklyResetCountdown = summaryWeekCount,
      contextTokens = snapContextTokens,
      contextLimit = contextLimit,
      contextPercent = contextPct,
      inputTokens = snapInputTokens,
      outputTokens = snapOutputTokens,
      cacheTokens = snapCacheTokens,
      modelQuotas = parsedModels,
      totalStepsAll = snapTotalSteps,
      recentSessions = defaultRecent,
      cloudStatusOk = cachedCloudStatusOk,
      cloudStatusText = cachedCloudStatusText,
      hasStorageAccess = canReadStorage || isLive,
      isLiveConnected = isLive,
      lastUpdatedMillis = now
    )
  }

  private fun updateCloudStatusIfStale() {
    val now = System.currentTimeMillis()
    if (now - lastStatusFetchTime < CACHE_STATUS_MS) return

    try {
      val url = URL(STATUS_API_URL)
      val conn = url.openConnection() as HttpURLConnection
      conn.connectTimeout = 4000
      conn.readTimeout = 4000
      conn.requestMethod = "GET"
      conn.setRequestProperty("User-Agent", "AgyPets-Companion")

      if (conn.responseCode == 200) {
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        var activeIncident = false
        var desc = "Google Cloud Operational"

        for (i in 0 until minOf(array.length(), 5)) {
          val item = array.getJSONObject(i)
          val status = item.optString("status_impact", "")
          val end = item.optString("end", "")
          if (end.isEmpty() || status.contains("DISRUPTION", ignoreCase = true)) {
            activeIncident = true
            desc = item.optString("external_desc", "Active Cloud Incident")
            break
          }
        }

        cachedCloudStatusOk = !activeIncident
        cachedCloudStatusText = if (cachedCloudStatusOk) "Google Cloud & Gemini: Operational" else desc
        lastStatusFetchTime = now
      }
    } catch (e: Exception) {
      Log.w(TAG, "Cloud status ping failed: ${e.message}")
    }
  }
}
