package com.droid.agypets.engine

enum class PetState {
  WALK,
  IDLE,
  SIT,
  SLEEP,
  FLOAT,
  FALL,
  DRAGGING,
  WORKING,
  CELEBRATE,
  ALERT
}

enum class Direction {
  LEFT,
  RIGHT
}

data class PetBehaviorEngine(
  var screenWidth: Int,
  var screenHeight: Int,
  var petWidth: Int,
  var groundY: Float,
  var speedMultiplier: Float = 1.0f,
  var zeroGravity: Boolean = true,
  var x: Float = 0f,
  var y: Float = groundY,
  var vx: Float = 2.0f,
  var vy: Float = 0f,
  var direction: Direction = Direction.RIGHT,
  var currentState: PetState = PetState.WALK,
  var stateTicks: Int = 0,
  var animFrame: Int = 0
) {

  private val minX: Float get() = 0f
  private val maxX: Float get() = (screenWidth - petWidth).coerceAtLeast(0).toFloat()

  // Dynamic behavior pacing & inactivity tracking
  var isAgentActive: Boolean = false
  var inactivityTicks: Long = 0L
  var celebrateTicksLeft: Int = 0
  var alertTicksLeft: Int = 0

  private var currentWalkTargetTicks: Int = 360 // ~12 seconds
  private var currentIdleTargetTicks: Int = 210 // ~7 seconds
  private var currentSitTargetTicks: Int = 300  // ~10 seconds

  private fun ensureWalkingVelocity() {
    if (Math.abs(vx) < 1.5f) {
      vx = if (direction == Direction.RIGHT) 2.0f else -2.0f
    } else {
      vx = if (vx > 0f) 2.0f else -2.0f
      direction = if (vx > 0f) Direction.RIGHT else Direction.LEFT
    }
  }

  fun tick(randomVal: Double = Math.random()) {
    stateTicks++
    if (!isAgentActive && currentState != PetState.DRAGGING) {
      inactivityTicks++
    } else {
      inactivityTicks = 0L
    }

    when (currentState) {
      PetState.ALERT -> {
        // Brief surprise hop / exclamation reaction before working or walking
        alertTicksLeft--
        if (alertTicksLeft <= 0) {
          stateTicks = 0
          ensureWalkingVelocity()
          currentState = if (isAgentActive) PetState.WORKING else PetState.WALK
        }
      }

      PetState.CELEBRATE -> {
        // Victory dance / celebration when AI agent completes a response!
        celebrateTicksLeft--
        if (stateTicks % 6 == 0) {
          animFrame = (animFrame + 1) % 4
        }
        if (celebrateTicksLeft <= 0) {
          stateTicks = 0
          ensureWalkingVelocity()
          currentState = if (isAgentActive) PetState.WORKING else PetState.WALK
        }
      }

      PetState.WALK -> {
        if (isAgentActive) {
          ensureWalkingVelocity()
          currentState = PetState.WORKING
          stateTicks = 0
        }
        if (Math.abs(vx) < 1.5f) {
          ensureWalkingVelocity()
        }
        x += vx * speedMultiplier
        if (stateTicks % 10 == 0) {
          animFrame = (animFrame + 1) % 4
        }

        // Horizontal bounce on screen edges
        if (x >= maxX) {
          x = maxX
          direction = Direction.LEFT
          vx = -Math.abs(vx)
        } else if (x <= minX) {
          x = minX
          direction = Direction.RIGHT
          vx = Math.abs(vx)
        }

        // Natural pause: after walking a while, stop and observe (IDLE)
        if (!isAgentActive && stateTicks >= currentWalkTargetTicks) {
          currentState = PetState.IDLE
          stateTicks = 0
          currentIdleTargetTicks = 180 + (randomVal * 120).toInt() // 6 - 10s
        }
      }

      PetState.WORKING -> {
        if (Math.abs(vx) < 1.5f) {
          ensureWalkingVelocity()
        }
        // Fast, energetic pacing with focused concentration
        x += (vx * 0.75f) * speedMultiplier
        if (stateTicks % 5 == 0) {
          animFrame = (animFrame + 1) % 4
        }

        if (x >= maxX) {
          x = maxX
          direction = Direction.LEFT
          vx = -Math.abs(vx)
        } else if (x <= minX) {
          x = minX
          direction = Direction.RIGHT
          vx = Math.abs(vx)
        }
      }

      PetState.IDLE -> {
        // Standing still, looking around curiously
        if (stateTicks >= currentIdleTargetTicks) {
          stateTicks = 0
          if (isAgentActive) {
            ensureWalkingVelocity()
            currentState = PetState.WORKING
          } else if (inactivityTicks >= 900) { // >30s of system inactivity -> sit down
            currentState = PetState.SIT
            currentSitTargetTicks = 300 + (randomVal * 150).toInt() // 10 - 15s
          } else {
            // 50% chance to turn around and walk, 50% chance to sit
            if (randomVal < 0.5) {
              currentState = PetState.SIT
              currentSitTargetTicks = 240 + (randomVal * 150).toInt()
            } else {
              currentState = PetState.WALK
              direction = if (randomVal < 0.75) {
                if (direction == Direction.RIGHT) Direction.LEFT else Direction.RIGHT
              } else direction
              vx = if (direction == Direction.RIGHT) 2f else -2f
              currentWalkTargetTicks = 300 + (randomVal * 200).toInt()
            }
          }
        }
      }

      PetState.SIT -> {
        // Sitting relaxed on the floor
        if (stateTicks >= currentSitTargetTicks) {
          stateTicks = 0
          if (isAgentActive) {
            ensureWalkingVelocity()
            currentState = PetState.WORKING
          } else if (inactivityTicks >= 1350) {
            // If the computer has been quiet for > 45 seconds total, fall asleep!
            currentState = PetState.SLEEP
          } else {
            ensureWalkingVelocity()
            currentState = PetState.WALK
            currentWalkTargetTicks = 300 + (randomVal * 180).toInt()
          }
        }
      }

      PetState.SLEEP -> {
        // Deep restorative sleep with floating pixel-art Zzz!
        // Naturally wakes up after a peaceful 40-50s nap, or immediately on touch/agent event
        if (stateTicks >= 1350) {
          currentState = PetState.IDLE
          stateTicks = 0
          inactivityTicks = 0L
          currentIdleTargetTicks = 150
        }
      }

      PetState.FLOAT -> {
        x += vx * 0.85f
        y += vy
        vy += 0.15f // subtle zero-gravity drift

        // Horizontal bounce
        if (x <= minX) {
          x = minX
          vx = Math.abs(vx)
          direction = Direction.RIGHT
        } else if (x >= maxX) {
          x = maxX
          vx = -Math.abs(vx)
          direction = Direction.LEFT
        }

        // Ceiling bounce
        if (y < 40f) {
          y = 40f
          vy = 1.0f
        }

        // Land on ground
        if (y >= groundY) {
          y = groundY
          vy = 0f
          ensureWalkingVelocity()
          currentState = if (isAgentActive) PetState.WORKING else PetState.WALK
          stateTicks = 0
          currentWalkTargetTicks = 300
        }
      }

      PetState.FALL -> {
        y += vy
        vy += 0.8f // standard gravity acceleration

        if (y >= groundY) {
          y = groundY
          vy = 0f
          stateTicks = 0
          if (isAgentActive) {
            ensureWalkingVelocity()
            currentState = PetState.WORKING
          } else {
            currentState = PetState.IDLE
            currentIdleTargetTicks = 120
          }
        }
      }

      PetState.DRAGGING -> {
        // Position controlled directly by touch
      }
    }
  }

  fun setAgentWorking(working: Boolean) {
    if (currentState == PetState.DRAGGING) {
      isAgentActive = working
      return
    }

    if (working) {
      inactivityTicks = 0L
      if (!isAgentActive) {
        isAgentActive = true
        // If waking up from sleep, sitting or idle: alert hop first!
        if (currentState == PetState.SLEEP || currentState == PetState.SIT || currentState == PetState.IDLE) {
          currentState = PetState.ALERT
          alertTicksLeft = 24 // ~0.8s surprise hop
          stateTicks = 0
        } else if (currentState != PetState.FLOAT && currentState != PetState.FALL && currentState != PetState.ALERT) {
          ensureWalkingVelocity()
          currentState = PetState.WORKING
          stateTicks = 0
        }
      } else if (currentState == PetState.WALK || currentState == PetState.IDLE || currentState == PetState.SIT || currentState == PetState.SLEEP) {
        ensureWalkingVelocity()
        currentState = PetState.WORKING
        stateTicks = 0
      }
    } else {
      if (isAgentActive) {
        isAgentActive = false
        // Just finished an AI task: celebratory victory dance!
        if (currentState == PetState.WORKING || currentState == PetState.ALERT) {
          currentState = PetState.CELEBRATE
          celebrateTicksLeft = 90 // 3 seconds of victory bounce + sparkles
          stateTicks = 0
        } else if (currentState != PetState.FLOAT && currentState != PetState.FALL) {
          ensureWalkingVelocity()
          currentState = PetState.WALK
          stateTicks = 0
        }
      }
    }
  }

  fun onStartDrag(startX: Float, startY: Float) {
    inactivityTicks = 0L
    currentState = PetState.DRAGGING
    x = startX
    y = startY
    stateTicks = 0
  }

  fun onDragMove(newX: Float, newY: Float) {
    if (currentState == PetState.DRAGGING) {
      x = newX.coerceIn(minX, maxX)
      y = newY.coerceIn(40f, groundY + 100f)
    }
  }

  fun onRelease(tossVx: Float, tossVy: Float) {
    inactivityTicks = 0L
    if (currentState == PetState.DRAGGING) {
      vx = tossVx.coerceIn(-25f, 25f)
      vy = tossVy.coerceIn(-25f, 25f)

      if (y < groundY - 10 || (zeroGravity && vy < -2f)) {
        if (zeroGravity) {
          currentState = PetState.FLOAT
          if (Math.abs(vy) < 1f && y < groundY - 50) {
            vy = -3f
          }
        } else {
          currentState = PetState.FALL
        }
      } else {
        y = groundY
        vy = 0f
        ensureWalkingVelocity()
        currentState = if (isAgentActive) PetState.WORKING else PetState.WALK
      }
      stateTicks = 0
    }
  }

  fun onPoke() {
    inactivityTicks = 0L
    if (y >= groundY - 10f) {
      y = groundY
      vy = 0f
    }
    direction = if (direction == Direction.RIGHT) Direction.LEFT else Direction.RIGHT
    ensureWalkingVelocity()
    currentState = PetState.ALERT
    alertTicksLeft = 18
    stateTicks = 0
  }
}
