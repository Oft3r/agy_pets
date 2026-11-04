package com.droid.agypets.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.droid.agypets.data.PetSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.droid.agypets.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreenContent(
        settings = PetSettings(
          isPetEnabled = true,
          petScale = 2.0f,
          speedMultiplier = 1.2f,
          zeroGravity = true,
          hasOverlayPermission = true
        ),
        onTogglePet = {},
        onResetPosition = {},
        onScaleChange = {},
        onSpeedChange = {},
        onZeroGravityToggle = {},
        onRequestOverlayPermission = {}
      )
    }
  }

  @Test
  fun appTitle_exists() {
    composeTestRule.onNodeWithText("✦ AntiGravity Pets").assertExists()
  }

  @Test
  fun petStatus_exists() {
    composeTestRule.onNodeWithText("Mascota en Pantalla").assertExists()
  }
}
