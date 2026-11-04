package com.droid.agypets.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.droid.agypets.data.AgyUsageData
import com.droid.agypets.data.QuotaGroupItem
import java.util.Locale

class AgyUsageHudView(
  context: Context,
  private val onRefreshClicked: () -> Unit,
  private val onCloseClicked: () -> Unit
) : LinearLayout(context) {

  private val tvStatusBadge: TextView
  private val tvActiveModelBadge: TextView
  private val tvSessionTitle: TextView
  private val groupsContainer: LinearLayout

  // Context Window Views
  private val tvContextLabel: TextView
  private val tvContextTokens: TextView
  private val pbContextBar: ProgressBar
  private val tvTokenStats: TextView

  private val tvCloudStatus: TextView
  private val tvStorageStatus: TextView

  init {
    orientation = VERTICAL
    val density = context.resources.displayMetrics.density
    val pad = (14 * density).toInt()
    setPadding(pad, pad, pad, pad)

    // Dark glassmorphism background
    val bg = GradientDrawable().apply {
      setColor(Color.parseColor("#F5080C1C"))
      cornerRadius = 16 * density
      setStroke((1.5f * density).toInt(), Color.parseColor("#38BDF8"))
    }
    background = bg
    elevation = 20 * density

    // Header Row: Title + Close Button
    val headerLayout = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val tvHeader = TextView(context).apply {
      text = "✦ AntiGravity Quotas by Group"
      setTextColor(Color.parseColor("#38BDF8"))
      textSize = 14f
      setTypeface(typeface, Typeface.BOLD)
      layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
    }

    val btnClose = TextView(context).apply {
      text = " ✕ "
      setTextColor(Color.parseColor("#94A3B8"))
      textSize = 15f
      setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
      setOnClickListener { onCloseClicked() }
    }

    headerLayout.addView(tvHeader)
    headerLayout.addView(btnClose)
    addView(headerLayout)

    // Status Badge & Active Model
    tvStatusBadge = TextView(context).apply {
      text = "⚡ Agent Working"
      setTextColor(Color.parseColor("#4ADE80"))
      textSize = 12f
      setTypeface(typeface, Typeface.BOLD)
      setPadding(0, (4 * density).toInt(), 0, (2 * density).toInt())
    }
    addView(tvStatusBadge)

    tvActiveModelBadge = TextView(context).apply {
      text = "Selected Model: Gemini 4 Argon (Medium) • Pro Plan"
      setTextColor(Color.parseColor("#A78BFA"))
      textSize = 11f
      setTypeface(typeface, Typeface.BOLD)
      setPadding(0, 0, 0, (2 * density).toInt())
    }
    addView(tvActiveModelBadge)

    tvSessionTitle = TextView(context).apply {
      text = "Create Pixel Art Mascot AntiGravity"
      setTextColor(Color.parseColor("#CBD5E1"))
      textSize = 11f
      setPadding(0, 0, 0, (6 * density).toInt())
    }
    addView(tvSessionTitle)

    // Scrollable body for Model Groups + Context Window
    val scrollView = ScrollView(context).apply {
      isVerticalScrollBarEnabled = true
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (270 * density).toInt())
    }

    val scrollContent = LinearLayout(context).apply {
      orientation = VERTICAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    // Dynamic Container for Antigravity Quota Groups
    groupsContainer = LinearLayout(context).apply {
      orientation = VERTICAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    scrollContent.addView(groupsContainer)

    // ==============================================================
    // CONTEXT WINDOW (TOKENS)
    // ==============================================================
    val ctxRow = LinearLayout(context).apply {
      orientation = HORIZONTAL
      setPadding(0, (6 * density).toInt(), 0, 0)
    }
    tvContextLabel = TextView(context).apply {
      text = "Context Window (Active Session):"
      setTextColor(Color.parseColor("#94A3B8"))
      textSize = 11f
      layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
    }
    tvContextTokens = TextView(context).apply {
      text = "62.8k / 200k (31%)"
      setTextColor(Color.parseColor("#38BDF8"))
      textSize = 11f
      setTypeface(typeface, Typeface.BOLD)
    }
    ctxRow.addView(tvContextLabel)
    ctxRow.addView(tvContextTokens)
    scrollContent.addView(ctxRow)

    pbContextBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 100
      progress = 31
      isIndeterminate = false
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (6 * density).toInt()).apply {
        topMargin = (3 * density).toInt()
        bottomMargin = (6 * density).toInt()
      }
    }
    scrollContent.addView(pbContextBar)

    // Tokens Breakdown Card
    tvTokenStats = TextView(context).apply {
      text = "📥 In: 1214k  |  📤 Out: 259k  |  ⚡ Cache: 39.0M"
      setTextColor(Color.parseColor("#CBD5E1"))
      textSize = 10f
      val statBg = GradientDrawable().apply {
        setColor(Color.parseColor("#251E293B"))
        cornerRadius = 6 * density
      }
      background = statBg
      setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
    }
    scrollContent.addView(tvTokenStats)

    // Cloud Status & Sync Status
    tvCloudStatus = TextView(context).apply {
      text = "Google Cloud & Gemini: Operational 🟢"
      setTextColor(Color.parseColor("#4ADE80"))
      textSize = 10f
      setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
    }
    scrollContent.addView(tvCloudStatus)

    tvStorageStatus = TextView(context).apply {
      text = "Sync: Connected to Antigravity LanguageServer ✅"
      setTextColor(Color.parseColor("#4ADE80"))
      textSize = 9f
      setPadding(0, 0, 0, (4 * density).toInt())
    }
    scrollContent.addView(tvStorageStatus)

    scrollView.addView(scrollContent)
    addView(scrollView)

    // Bottom Action Row: Refresh Button
    val btnRefresh = Button(context).apply {
      text = "Refresh Quotas 🔄"
      setTextColor(Color.WHITE)
      textSize = 11f
      val btnBg = GradientDrawable().apply {
        setColor(Color.parseColor("#0284C7"))
        cornerRadius = 8 * density
      }
      background = btnBg
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (36 * density).toInt()).apply {
        topMargin = (6 * density).toInt()
      }
      setOnClickListener { onRefreshClicked() }
    }
    addView(btnRefresh)
  }

  private fun renderGroups(groups: List<QuotaGroupItem>, activeGroupName: String) {
    groupsContainer.removeAllViews()
    val density = context.resources.displayMetrics.density

    for (group in groups) {
      val isActiveGroup = group.displayName.equals(activeGroupName, ignoreCase = true)
      val cardLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        val pad = (8 * density).toInt()
        setPadding(pad, pad, pad, pad)
        val cardBg = GradientDrawable().apply {
          setColor(if (isActiveGroup) Color.parseColor("#181E3B") else Color.parseColor("#10152B"))
          cornerRadius = 8 * density
          setStroke(
            (1 * density).toInt(),
            if (isActiveGroup) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B")
          )
        }
        background = cardBg
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
          bottomMargin = (6 * density).toInt()
        }
      }

      // Group Title Row
      val titleRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }
      val tvGroupName = TextView(context).apply {
        val prefix = if (isActiveGroup) "★ " else "◆ "
        text = "$prefix${group.displayName}"
        setTextColor(if (isActiveGroup) Color.parseColor("#38BDF8") else Color.parseColor("#F1F5F9"))
        textSize = 11.5f
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
      }
      titleRow.addView(tvGroupName)

      if (isActiveGroup) {
        val tvActiveTag = TextView(context).apply {
          text = "IN USE"
          setTextColor(Color.parseColor("#4ADE80"))
          textSize = 9f
          setTypeface(typeface, Typeface.BOLD)
        }
        titleRow.addView(tvActiveTag)
      }
      cardLayout.addView(titleRow)

      if (group.description.isNotBlank()) {
        val tvDesc = TextView(context).apply {
          text = group.description
          setTextColor(Color.parseColor("#94A3B8"))
          textSize = 9.5f
          setPadding(0, 0, 0, (4 * density).toInt())
        }
        cardLayout.addView(tvDesc)
      }

      // Render each bucket (5h and/or weekly) inside this group
      for (bucket in group.buckets) {
        val bRow = LinearLayout(context).apply {
          orientation = HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
        }
        val winIcon = if (bucket.window == "5h") "⏱️" else "📅"
        val tvBucketLabel = TextView(context).apply {
          text = "$winIcon ${bucket.displayName}: ${bucket.usedPercent}% used (${bucket.remainingPercent}% left)"
          setTextColor(Color.parseColor("#E2E8F0"))
          textSize = 10f
          setTypeface(typeface, Typeface.BOLD)
          layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusCol = when {
          bucket.usedPercent < 60 -> Color.parseColor("#4ADE80")
          bucket.usedPercent < 85 -> Color.parseColor("#FBBF24")
          else -> Color.parseColor("#F87171")
        }
        val tvReset = TextView(context).apply {
          text = "↻ ${bucket.resetCountdown}"
          setTextColor(statusCol)
          textSize = 10f
          setTypeface(typeface, Typeface.BOLD)
        }
        bRow.addView(tvBucketLabel)
        bRow.addView(tvReset)
        cardLayout.addView(bRow)

        val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
          max = 100
          progress = bucket.usedPercent
          isIndeterminate = false
          layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (5 * density).toInt()).apply {
            topMargin = (2 * density).toInt()
            bottomMargin = (4 * density).toInt()
          }
        }
        cardLayout.addView(pb)
      }

      groupsContainer.addView(cardLayout)
    }
  }

  fun updateData(data: AgyUsageData) {
    if (data.isAgentRunning) {
      tvStatusBadge.text = "⚡ AntiGravity Agent Working (Step #${data.activeSessionSteps})"
      tvStatusBadge.setTextColor(Color.parseColor("#4ADE80"))
    } else {
      tvStatusBadge.text = "💤 AntiGravity Agent Idle (${data.activeSessionSteps} steps)"
      tvStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
    }

    tvActiveModelBadge.text = "Model: ${data.activeModelLabel} • ${data.planName} Plan"
    tvSessionTitle.text = data.activeSessionTitle

    renderGroups(data.quotaGroups, data.activeGroupName)

    // Context Window (Tokens)
    val ctxPct = data.contextPercent
    val kCtx = (data.contextTokens / 1000.0)
    val kLim = (data.contextLimit / 1000.0)
    tvContextTokens.text = String.format(Locale.US, "%.1fk / %.0fk (%d%%)", kCtx, kLim, ctxPct)

    pbContextBar.progress = ctxPct
    when {
      ctxPct < 60 -> tvContextTokens.setTextColor(Color.parseColor("#38BDF8"))
      ctxPct < 85 -> tvContextTokens.setTextColor(Color.parseColor("#FBBF24"))
      else -> tvContextTokens.setTextColor(Color.parseColor("#F87171"))
    }

    // Tokens Breakdown
    val inK = (data.inputTokens / 1000.0)
    val outK = (data.outputTokens / 1000.0)
    val cacheM = (data.cacheTokens / 1_000_000.0)
    tvTokenStats.text = String.format(
      Locale.US,
      "📥 In: %.1fk  |  📤 Out: %.1fk  |  ⚡ Cache: %.1fM",
      inK,
      outK,
      cacheM
    )

    // Cloud Status
    tvCloudStatus.text = data.cloudStatusText
    tvCloudStatus.setTextColor(
      if (data.cloudStatusOk) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
    )

    // Sync Status
    if (data.isLiveConnected) {
      tvStorageStatus.text = "Live Sync: Connected to LanguageServer ✅"
      tvStorageStatus.setTextColor(Color.parseColor("#4ADE80"))
    } else if (data.hasStorageAccess) {
      tvStorageStatus.text = "File Sync: Connected to .gemini directory ✅"
      tvStorageStatus.setTextColor(Color.parseColor("#4ADE80"))
    } else {
      tvStorageStatus.text = "Local Demo Mode (Grant storage access or run sync daemon) ⚠️"
      tvStorageStatus.setTextColor(Color.parseColor("#FBBF24"))
    }
  }
}
