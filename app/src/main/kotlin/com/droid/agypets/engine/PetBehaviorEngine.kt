package com.droid.agypets.engine

enum class PetState {
  WALK,
  IDLE,
  SIT,
  SLEEP,
  FLOAT,
  FALL,
  DRAGGING,
  WORKING
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

  fun tick(randomVal: Double = Math.random()) {
    stateTicks++

    when (currentState) {
      PetState.WALK -> {
        x += vx * speedMultiplier
        if (stateTicks % 12 == 0) {
          animFrame = (animFrame + 1) % 4
        }

        // Check horizontal bounds
        if (x >= maxX) {
          x = maxX
          direction = Direction.LEFT
          vx = -Math.abs(vx)
        } else if (x <= minX) {
          x = minX
          direction = Direction.RIGHT
          vx = Math.abs(vx)
        }

        // Autonomous state transitions (Shimeji AI)
        if (stateTicks > 180 && randomVal < 0.02) {
          if (randomVal < 0.01) {
            currentState = PetState.IDLE
          } else {
            currentState = PetState.SIT
          }
          stateTicks = 0
        }
      }

      PetState.WORKING -> {
        // Fast, energetic pacing while generating code or running tools
        x += (vx * 0.6f) * speedMultiplier
        if (stateTicks % 6 == 0) {
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
        if (stateTicks > 120 && randomVal < 0.03) {
          if (randomVal < 0.01) {
            currentState = PetState.SLEEP
          } else {
            currentState = PetState.WALK
            if (randomVal < 0.02) {
              direction = if (direction == Direction.RIGHT) Direction.LEFT else Direction.RIGHT
              vx = if (direction == Direction.RIGHT) 2f else -2f
            }
          }
          stateTicks = 0
        }
      }

      PetState.SIT -> {
        if (stateTicks > 180 && randomVal < 0.02) {
          if (randomVal < 0.01) {
            currentState = PetState.SLEEP
          } else {
            currentState = PetState.WALK
          }
          stateTicks = 0
        }
      }

      PetState.SLEEP -> {
        if (stateTicks > 300 && randomVal < 0.01) {
          currentState = PetState.IDLE
          stateTicks = 0
        }
      }

      PetState.FLOAT -> {
        x += vx * 0.8f
        y += vy
        vy += 0.15f // subtle gravity drift

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

        // Top ceiling bounce
        if (y < 40f) {
          y = 40f
          vy = 1.0f
        }

        // Land on ground
        if (y >= groundY) {
          y = groundY
          vy = 0f
          currentState = PetState.WALK
          stateTicks = 0
        }
      }

      PetState.FALL -> {
        y += vy
        vy += 0.8f // normal gravity acceleration

        if (y >= groundY) {
          y = groundY
          vy = 0f
          currentState = PetState.IDLE
          stateTicks = 0
        }
      }

      PetState.DRAGGING -> {
        // Position controlled directly by touch
      }
    }
  }

  fun setAgentWorking(working: Boolean) {
    if (currentState == PetState.DRAGGING) return
    if (working) {
      if (currentState != PetState.WORKING) {
        currentState = PetState.WORKING
        stateTicks = 0
      }
    } else {
      if (currentState == PetState.WORKING) {
        currentState = PetState.WALK
        stateTicks = 0
      }
    }
  }

  fun onStartDrag(startX: Float, startY: Float) {
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
    if (currentState == PetState.DRAGGING) {
      vx = tossVx.coerceIn(-25f, 25f)
      vy = tossVy.coerceIn(-25f, 25f)

      if (zeroGravity) {
        currentState = PetState.FLOAT
        if (Math.abs(vy) < 1f && y < groundY - 50) {
          vy = -3f
        }
      } else {
        currentState = if (y < groundY - 10) PetState.FALL else PetState.WALK
      }
      stateTicks = 0
    }
  }

  fun onPoke() {
    if (currentState == PetState.DRAGGING) return

    if (currentState == PetState.SLEEP) {
      currentState = PetState.IDLE
    } else if (zeroGravity) {
      currentState = PetState.FLOAT
      vy = -12f
      vx = (Math.random() * 8 - 4).toFloat()
    } else {
      currentState = PetState.WALK
      vx = -vx
      direction = if (direction == Direction.RIGHT) Direction.LEFT else Direction.RIGHT
    }
    stateTicks = 0
  }
}
