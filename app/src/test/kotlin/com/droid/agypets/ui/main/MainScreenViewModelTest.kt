package com.droid.agypets.ui.main

import com.droid.agypets.data.DefaultPetSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_initiallyEmitsSettings() = runTest(testDispatcher) {
    val repo = DefaultPetSettingsRepository(null)
    val viewModel = MainScreenViewModel(repository = repo, sharingStarted = SharingStarted.Eagerly)
    testScheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is MainScreenUiState.Success)
    val success = state as MainScreenUiState.Success
    assertEquals(2.0f, success.settings.petScale)
    assertTrue(success.settings.zeroGravity)
    assertTrue(success.settings.showUsageBadge)
    assertEquals(200_000L, success.settings.contextWindowLimitTokens)
  }

  @Test
  fun togglePet_updatesEnabledState() = runTest(testDispatcher) {
    val repo = DefaultPetSettingsRepository(null)
    val viewModel = MainScreenViewModel(repository = repo, sharingStarted = SharingStarted.Eagerly)
    testScheduler.advanceUntilIdle()

    viewModel.togglePet(true)
    testScheduler.advanceUntilIdle()

    var success = viewModel.uiState.value as MainScreenUiState.Success
    assertTrue(success.settings.isPetEnabled)

    viewModel.togglePet(false)
    testScheduler.advanceUntilIdle()

    success = viewModel.uiState.value as MainScreenUiState.Success
    assertFalse(success.settings.isPetEnabled)
  }

  @Test
  fun updateScaleAndSpeed_updatesSettings() = runTest(testDispatcher) {
    val repo = DefaultPetSettingsRepository(null)
    val viewModel = MainScreenViewModel(repository = repo, sharingStarted = SharingStarted.Eagerly)
    testScheduler.advanceUntilIdle()

    viewModel.updateScale(3.0f)
    viewModel.updateSpeed(1.8f)
    testScheduler.advanceUntilIdle()

    val success = viewModel.uiState.value as MainScreenUiState.Success
    assertEquals(3.0f, success.settings.petScale)
    assertEquals(1.8f, success.settings.speedMultiplier)
  }

  @Test
  fun toggleUsageBadgeAndContextLimit_updatesSettings() = runTest(testDispatcher) {
    val repo = DefaultPetSettingsRepository(null)
    val viewModel = MainScreenViewModel(repository = repo, sharingStarted = SharingStarted.Eagerly)
    testScheduler.advanceUntilIdle()

    viewModel.toggleUsageBadge(false)
    viewModel.updateContextLimit(1_000_000L)
    testScheduler.advanceUntilIdle()

    val success = viewModel.uiState.value as MainScreenUiState.Success
    assertFalse(success.settings.showUsageBadge)
    assertEquals(1_000_000L, success.settings.contextWindowLimitTokens)
  }
}
