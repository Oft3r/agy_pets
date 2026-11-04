package com.droid.agypets.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.droid.agypets.data.AgyUsageData
import java.util.Locale

class AgyStatusPillView(
  context: Context,
  private val onPillClicked: () -> Unit
) : LinearLayout(context) {

  private val tvContent: TextView

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER
    val density = context.resources.displayMetrics.density
    val padH = (10 * density).toInt()
    val padV = (3 * density).toInt()
    setPadding(padH, padV, padH, padV)

    val bg = GradientDrawable().apply {
      setColor(Color.parseColor("#E60B0F19"))
      cornerRadius = 12 * density
      setStroke((1.2f * density).toInt(), Color.parseColor("#38BDF8"))
    }
    background = bg
    elevation = 8 * density

    tvContent = TextView(context).apply {
      text = "⚡ Argon 89% (21h 52m) | 63k"
      setTextColor(Color.parseColor("#38BDF8"))
      textSize = 10f
      setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    addView(tvContent)

    setOnClickListener {
      onPillClicked()
    }
  }

  fun updateData(data: AgyUsageData) {
    val icon = if (data.isAgentRunning) "⚡" else "💤"
    val kCtx = (data.contextTokens / 1000.0)
    val grp = data.activeGroupShortName.ifEmpty { "AGY" }
    val usedPct = data.activePrimaryUsedPercent
    val countdown = data.activePrimaryResetCountdown.ifEmpty { "—" }

    // English format: ⚡ Argon 89% (21h 52m) | 63k
    tvContent.text = String.format(
      Locale.US,
      "%s %s %d%% (%s) | %.0fk",
      icon,
      grp,
      usedPct,
      countdown,
      kCtx
    )

    val col = when {
      usedPct < 60 -> Color.parseColor("#4ADE80") // Green
      usedPct < 85 -> Color.parseColor("#FBBF24") // Amber
      else -> Color.parseColor("#F87171") // Red/Coral
    }
    tvContent.setTextColor(col)
  }
}
