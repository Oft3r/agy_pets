package com.droid.agypets.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetBehaviorEngineTest {

  @Test
  fun initialState_isWalking() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )

    assertEquals(PetState.WALK, engine.currentState)
    assertEquals(Direction.RIGHT, engine.direction)
    assertEquals(720f, engine.y, 0.01f)
  }

  @Test
  fun walkReachesRightEdge_reversesDirection() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    engine.x = 930f // Near right edge (1000 - 80 = 920 is max)

    engine.tick(randomVal = 0.99) // update tick

    assertEquals(Direction.LEFT, engine.direction)
    assertTrue(engine.vx < 0)
    assertTrue(engine.x <= 920f)
  }

  @Test
  fun walkReachesLeftEdge_reversesDirection() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    engine.x = -10f
    engine.direction = Direction.LEFT
    engine.vx = -2f

    engine.tick(randomVal = 0.99)

    assertEquals(Direction.RIGHT, engine.direction)
    assertTrue(engine.vx > 0)
    assertTrue(engine.x >= 0f)
  }

  @Test
  fun releaseInAir_withZeroGravity_entersFloat() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f,
      zeroGravity = true
    )
    engine.onStartDrag(100f, 200f)
    assertEquals(PetState.DRAGGING, engine.currentState)

    engine.onRelease(tossVx = 1f, tossVy = -2f)
    assertEquals(PetState.FLOAT, engine.currentState)
  }

  @Test
  fun releaseInAir_withoutZeroGravity_entersFall() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f,
      zeroGravity = false
    )
    engine.onStartDrag(100f, 200f)
    engine.onRelease(tossVx = 0f, tossVy = 0f)

    assertEquals(PetState.FALL, engine.currentState)
  }

  @Test
  fun fallReachesGround_landsSuccessfully() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f,
      zeroGravity = false
    )
    engine.y = 710f
    engine.vy = 20f
    engine.currentState = PetState.FALL

    engine.tick(randomVal = 0.99)

    assertEquals(720f, engine.y, 0.01f)
    assertEquals(PetState.IDLE, engine.currentState)
  }

  @Test
  fun touchPoke_whileSleeping_wakesUp() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    engine.currentState = PetState.SLEEP

    engine.onPoke()

    // When poked while sleeping, the mascot wakes up with surprise ALERT hop
    assertEquals(PetState.ALERT, engine.currentState)
  }

  @Test
  fun quickTapAfterStartDrag_doesNotFreezeInDragging() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    // Simulate ACTION_DOWN calling onStartDrag followed by ACTION_UP quick tap calling onPoke
    engine.onStartDrag(460f, 720f)
    engine.onPoke()

    // Must not remain frozen in DRAGGING state
    assertTrue(engine.currentState != PetState.DRAGGING)
    assertEquals(PetState.ALERT, engine.currentState)

    // After the brief alert hop completes, it resumes normal walking
    repeat(25) { engine.tick(randomVal = 0.5) }
    assertEquals(PetState.WALK, engine.currentState)
    assertTrue(Math.abs(engine.vx) >= 1.5f)
  }

  @Test
  fun pokeWhileAgentWorking_resumesWorkingAfterAlert() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    engine.setAgentWorking(true)
    assertEquals(PetState.WORKING, engine.currentState)

    // User clicks mascot to open usage capsule while Antigravity is working
    engine.onStartDrag(460f, 720f)
    engine.onPoke()

    assertEquals(PetState.ALERT, engine.currentState)

    // After brief alert hop, mascot must resume WORKING and moving horizontally
    val startX = engine.x
    repeat(30) { engine.tick(randomVal = 0.5) }
    assertEquals(PetState.WORKING, engine.currentState)
    assertTrue(engine.x != startX)
  }

  @Test
  fun releaseWithZeroVelocity_restoresWalkingSpeedAndAgentState() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f,
      zeroGravity = false
    )
    engine.setAgentWorking(true)
    engine.onStartDrag(400f, 720f)
    engine.onRelease(tossVx = 0f, tossVy = 0f)

    assertEquals(PetState.WORKING, engine.currentState)
    assertTrue(Math.abs(engine.vx) >= 1.5f)
  }

  @Test
  fun setAgentWorking_transitionsStateCorrectly() {
    val engine = PetBehaviorEngine(
      screenWidth = 1000,
      screenHeight = 800,
      petWidth = 80,
      groundY = 720f
    )
    assertEquals(PetState.WALK, engine.currentState)

    engine.setAgentWorking(true)
    assertEquals(PetState.WORKING, engine.currentState)

    // When the agent finishes, the pet enters a victory CELEBRATE dance before walking
    engine.setAgentWorking(false)
    assertEquals(PetState.CELEBRATE, engine.currentState)

    // After celebrate ticks elapse, it returns to WALK
    engine.celebrateTicksLeft = 1
    engine.tick(randomVal = 0.5)
    assertEquals(PetState.WALK, engine.currentState)
  }

  @Test
  fun antigravityQuotaGroups_parseIsoAndSeparateGroupsCorrectly() {
    val nowMs = com.droid.agypets.data.AgyUsageRepository.parseIso8601ToMillis("2026-11-04T22:37:00Z")
    val targetMs = com.droid.agypets.data.AgyUsageRepository.parseIso8601ToMillis("2026-11-05T02:11:18Z")
    assertTrue(targetMs > nowMs)

    val countdown = com.droid.agypets.data.AgyUsageRepository.formatCountdownFromMillis(targetMs, nowMs)
    assertEquals("3h 34m", countdown)

    val groups = com.droid.agypets.data.AgyUsageRepository.defaultAntigravityGroups(nowMs)
    assertEquals(3, groups.size)
    assertEquals("Gemini Models", groups[0].displayName)
    assertEquals("Gemini 4 Argon", groups[1].displayName)
    assertEquals("Claude and GPT models", groups[2].displayName)
  }

  @Test
  fun petPreferences_storesAndRetrievesBootSettings() {
    val prefs = com.droid.agypets.data.PetSettings(
      startOnBoot = true,
      autoShowWithAntigravity = true
    )
    assertTrue(prefs.startOnBoot)
    assertTrue(prefs.autoShowWithAntigravity)
  }
}
