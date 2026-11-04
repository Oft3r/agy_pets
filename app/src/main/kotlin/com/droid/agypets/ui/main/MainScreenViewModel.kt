package com.droid.agypets.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.agypets.data.AgyUsageData
import com.droid.agypets.data.AgyUsageRepository
import com.droid.agypets.data.PetSettings
import com.droid.agypets.data.PetSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
  data class Success(val settings: PetSettings) : MainScreenUiState
}

class MainScreenViewModel(
  private val repository: PetSettingsRepository,
  private val usageRepository: AgyUsageRepository? = null,
  sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

  val uiState: StateFlow<MainScreenUiState> =
    repository.settingsFlow
      .map<PetSettings, MainScreenUiState> { MainScreenUiState.Success(it) }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(
        viewModelScope,
        sharingStarted,
        MainScreenUiState.Loading
      )

  val usageState: StateFlow<AgyUsageData> =
    usageRepository?.usageState
      ?: kotlinx.coroutines.flow.MutableStateFlow(AgyUsageData())

  init {
    refreshUsage()
  }

  fun refreshUsage() {
    viewModelScope.launch {
      usageRepository?.refresh()
    }
  }

  fun togglePet(enable: Boolean) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(isPetEnabled = enable))
  }

  fun updateScale(scale: Float) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(petScale = scale))
  }

  fun updateSpeed(speed: Float) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(speedMultiplier = speed))
  }

  fun toggleZeroGravity(enabled: Boolean) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(zeroGravity = enabled))
  }

  fun toggleUsageBadge(show: Boolean) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(showUsageBadge = show))
  }

  fun toggleStartOnBoot(enable: Boolean) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(startOnBoot = enable))
  }

  fun toggleAutoShowWithAntigravity(enable: Boolean) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(autoShowWithAntigravity = enable))
  }

  fun updateContextLimit(limit: Long) {
    val current = repository.currentSettings
    repository.updateSettings(current.copy(contextWindowLimitTokens = limit))
    refreshUsage()
  }

  fun updateOverlayPermission(granted: Boolean) {
    repository.setOverlayPermission(granted)
  }

  fun updateStoragePermission(granted: Boolean) {
    repository.setStoragePermission(granted)
  }

  fun updateUsageStatsPermission(granted: Boolean) {
    repository.setUsageStatsPermission(granted)
  }
}
