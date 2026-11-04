package com.droid.agypets.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PetSettings(
  val isPetEnabled: Boolean = false,
  val petScale: Float = 2.0f,
  val speedMultiplier: Float = 1.2f,
  val zeroGravity: Boolean = true,
  val hasOverlayPermission: Boolean = false,
  val hasStoragePermission: Boolean = false,
  val hasUsageStatsPermission: Boolean = false,
  val showUsageBadge: Boolean = true,
  val contextWindowLimitTokens: Long = 200_000L,
  val liveTrackingEnabled: Boolean = true,
  val startOnBoot: Boolean = true,
  val autoShowWithAntigravity: Boolean = true
)

interface PetSettingsRepository {
  val settingsFlow: Flow<PetSettings>
  val currentSettings: PetSettings
  fun updateSettings(settings: PetSettings)
  fun setOverlayPermission(granted: Boolean)
  fun setStoragePermission(granted: Boolean)
  fun setUsageStatsPermission(granted: Boolean)
}

class DefaultPetSettingsRepository(
  private val prefs: PetPreferences? = null
) : PetSettingsRepository {

  private val _state = MutableStateFlow(
    PetSettings(
      isPetEnabled = prefs?.isPetEnabled ?: false,
      petScale = prefs?.petScale ?: 2.0f,
      speedMultiplier = prefs?.speedMultiplier ?: 1.2f,
      zeroGravity = prefs?.zeroGravity ?: true,
      hasOverlayPermission = false,
      hasStoragePermission = false,
      hasUsageStatsPermission = false,
      showUsageBadge = prefs?.showUsageBadge ?: true,
      contextWindowLimitTokens = prefs?.contextWindowLimitTokens ?: 200_000L,
      liveTrackingEnabled = prefs?.liveTrackingEnabled ?: true,
      startOnBoot = prefs?.startOnBoot ?: true,
      autoShowWithAntigravity = prefs?.autoShowWithAntigravity ?: true
    )
  )

  override val settingsFlow: Flow<PetSettings> = _state.asStateFlow()
  override val currentSettings: PetSettings get() = _state.value

  override fun updateSettings(settings: PetSettings) {
    _state.value = settings
    prefs?.let {
      it.isPetEnabled = settings.isPetEnabled
      it.petScale = settings.petScale
      it.speedMultiplier = settings.speedMultiplier
      it.zeroGravity = settings.zeroGravity
      it.showUsageBadge = settings.showUsageBadge
      it.contextWindowLimitTokens = settings.contextWindowLimitTokens
      it.liveTrackingEnabled = settings.liveTrackingEnabled
      it.startOnBoot = settings.startOnBoot
      it.autoShowWithAntigravity = settings.autoShowWithAntigravity
    }
  }

  override fun setOverlayPermission(granted: Boolean) {
    _state.value = _state.value.copy(hasOverlayPermission = granted)
  }

  override fun setStoragePermission(granted: Boolean) {
    _state.value = _state.value.copy(hasStoragePermission = granted)
  }

  override fun setUsageStatsPermission(granted: Boolean) {
    _state.value = _state.value.copy(hasUsageStatsPermission = granted)
  }
}
