package com.droid.agypets.data

data class QuotaBucketItem(
  val bucketId: String,
  val displayName: String,
  val window: String, // "5h" or "weekly"
  val remainingFraction: Float,
  val remainingPercent: Int,
  val usedPercent: Int,
  val resetTimeIso: String,
  val resetCountdown: String
)

data class QuotaGroupItem(
  val displayName: String,
  val description: String,
  val buckets: List<QuotaBucketItem>
)

data class ModelQuotaItem(
  val name: String,
  val modelId: String = "",
  val usedPercent: Int = 0,
  val remainingPercent: Int = 100,
  val resetCountdown: String = "",
  val resetTimeIso: String = ""
) {
  // Backward compatibility alias
  val percent: Int get() = usedPercent
}

data class SessionSummaryItem(
  val id: String,
  val title: String,
  val stepCount: Int,
  val isRunning: Boolean,
  val lastModified: String
)

data class AgyUsageData(
  val activeSessionId: String = "",
  val activeSessionTitle: String = "AntiGravity Ready",
  val isAgentRunning: Boolean = false,
  val activeSessionSteps: Int = 0,

  // Active Antigravity Model & Plan
  val planName: String = "Pro",
  val activeModelLabel: String = "Gemini 4 Argon (Medium)",
  val activeGroupName: String = "Gemini 4 Argon",

  // Primary Active Model/Group Summary (shown on Floating Pill)
  val activeGroupShortName: String = "Argon",
  val activePrimaryUsedPercent: Int = 89,
  val activePrimaryRemainingPercent: Int = 11,
  val activePrimaryWindowLabel: String = "Semanal",
  val activePrimaryResetCountdown: String = "21h 52m",

  // Antigravity Model Groups (Gemini Models, Claude and GPT models, Gemini 4 Argon)
  val quotaGroups: List<QuotaGroupItem> = emptyList(),

  // Legacy / Summary 5h & Weekly fields (mapped to active group or Gemini group)
  val sessionQuotaPercent: Int = 15,
  val sessionResetTimeMillis: Long = 0L,
  val sessionResetCountdown: String = "3h 33m",
  val sessionResetDateStr: String = "",

  val weeklyQuotaPercent: Int = 10,
  val weeklyResetTimeMillis: Long = 0L,
  val weeklyResetCountdown: String = "6d 2h",
  val weeklyResetDateStr: String = "",

  // Context Window (Tokens)
  val contextTokens: Long = 0L,
  val contextLimit: Long = 200_000L,
  val contextPercent: Int = 0,
  val inputTokens: Long = 0L,
  val outputTokens: Long = 0L,
  val cacheTokens: Long = 0L,

  // Individual Antigravity models breakdown
  val modelQuotas: List<ModelQuotaItem> = emptyList(),

  // Totals & Sessions
  val totalStepsAll: Int = 0,
  val recentSessions: List<SessionSummaryItem> = emptyList(),
  val cloudStatusOk: Boolean = true,
  val cloudStatusText: String = "Google Cloud & Gemini: Operational",
  val hasStorageAccess: Boolean = false,
  val isLiveConnected: Boolean = false,
  val lastUpdatedMillis: Long = System.currentTimeMillis()
)
