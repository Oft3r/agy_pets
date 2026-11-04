package com.droid.agypets.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.droid.agypets.engine.Direction
import com.droid.agypets.engine.PetState

class PixelPetView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val paint = Paint().apply {
    isAntiAlias = false
    isDither = false
    style = Paint.Style.FILL
  }

  var currentState: PetState = PetState.WALK
  var currentDirection: Direction = Direction.RIGHT
  var animFrame: Int = 0
  var tickCount: Long = 0L
  var contextUsagePercent: Int = 0

  data class AgyPixel(val x: Int, val y: Int, val color: Int)

  private val officialPixels = OFFICIAL_PIXELS


  private val cDarkEye = Color.parseColor("#060919")
  private val cWhite = Color.parseColor("#FFFFFF")
  private val cBlush = Color.parseColor("#F472B6")
  private val cCyanGlow = Color.parseColor("#00E5FF")
  private val cVioletGlow = Color.parseColor("#818CF8")
  private val cShadow = Color.parseColor("#40000000")
  private val cOutline = Color.parseColor("#0A0D24")

  private val pixelRect = RectF()

  fun updateAnimation(
    state: PetState,
    direction: Direction,
    frame: Int,
    ticks: Long,
    quotaPercent: Int = contextUsagePercent
  ) {
    this.currentState = state
    this.currentDirection = direction
    this.animFrame = frame
    this.tickCount = ticks
    this.contextUsagePercent = quotaPercent
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0 || h <= 0) return

    val pixelSize = (w / 20f).coerceAtMost(h / 18f)

    canvas.save()

    // Flip horizontally when facing LEFT
    if (currentDirection == Direction.LEFT) {
      canvas.scale(-1f, 1f, w / 2f, h / 2f)
    }

    val ox = (w - 20 * pixelSize) / 2f
    val oy = (h - 16 * pixelSize) / 2f

    fun drawPx(x: Int, y: Int, color: Int) {
      paint.color = color
      pixelRect.set(
        ox + x * pixelSize,
        oy + y * pixelSize,
        ox + (x + 1) * pixelSize,
        oy + (y + 1) * pixelSize
      )
      canvas.drawRect(pixelRect, paint)
    }

    // Vertical bobbing
    val bob = when (currentState) {
      PetState.WALK -> if (animFrame % 2 == 1) -1 else 0
      PetState.WORKING -> if (animFrame % 2 == 1) -1 else 0
      PetState.IDLE -> if ((tickCount % 60) > 30) -1 else 0
      PetState.FLOAT, PetState.DRAGGING -> if ((tickCount % 20) > 10) -1 else 0
      else -> 0
    }

    // Shadow on base under the legs of the 'A'
    if (currentState != PetState.DRAGGING && currentState != PetState.FLOAT) {
      paint.color = cShadow
      canvas.drawOval(
        ox + 3 * pixelSize,
        oy + 14 * pixelSize,
        ox + 17 * pixelSize,
        oy + 16 * pixelSize,
        paint
      )
    }

    // Working aura: when AntiGravity is actively running code
    if (currentState == PetState.WORKING) {
      val pulse = (tickCount % 16) < 8
      val auraCol = if (pulse) cCyanGlow else cVioletGlow
      drawPx(9, 1 + bob, auraCol)
      drawPx(10, 1 + bob, auraCol)
      if ((tickCount % 12) < 6) {
        drawPx(5, 5 + bob, cCyanGlow)
        drawPx(14, 5 + bob, cVioletGlow)
      }
    }

    // 1. Draw subtle dark outline for crisp contrast
    for (px in officialPixels) {
      drawPx(px.x - 1, px.y + bob, cOutline)
      drawPx(px.x + 1, px.y + bob, cOutline)
      drawPx(px.x, px.y - 1 + bob, cOutline)
      drawPx(px.x, px.y + 1 + bob, cOutline)
    }

    // 2. Draw all 50 Official AntiGravity Pixels
    for (px in officialPixels) {
      drawPx(px.x, px.y + bob, px.color)
    }

    // 3. Cute Expressive Eyes on the upper block
    val eyeLeftX = 8
    val eyeRightX = 11
    val eyeY = 5 + bob

    when (currentState) {
      PetState.WORKING -> {
        // Highly focused scanning eyes (AntiGravity agent coding)
        val scan = (tickCount % 30) < 15
        val shiftX = if (scan) 0 else 1
        drawPx(eyeLeftX + shiftX - 1, eyeY, cDarkEye)
        drawPx(eyeLeftX + shiftX, eyeY, cCyanGlow)
        drawPx(eyeRightX + shiftX - 1, eyeY, cDarkEye)
        drawPx(eyeRightX + shiftX, eyeY, cCyanGlow)
        drawPx(7, 6 + bob, cBlush)
        drawPx(12, 6 + bob, cBlush)
      }
      PetState.SLEEP -> {
        drawPx(eyeLeftX, eyeY, cDarkEye)
        drawPx(eyeLeftX - 1, eyeY, cDarkEye)
        drawPx(eyeRightX, eyeY, cDarkEye)
        drawPx(eyeRightX + 1, eyeY, cDarkEye)
        drawPx(7, 6 + bob, cBlush)
        drawPx(12, 6 + bob, cBlush)
      }
      PetState.SIT -> {
        drawPx(eyeLeftX - 1, eyeY, cDarkEye)
        drawPx(eyeLeftX, eyeY - 1, cDarkEye)
        drawPx(eyeRightX, eyeY - 1, cDarkEye)
        drawPx(eyeRightX + 1, eyeY, cDarkEye)
        drawPx(9, 6 + bob, cDarkEye)
        drawPx(10, 6 + bob, cDarkEye)
        drawPx(7, 6 + bob, cBlush)
        drawPx(12, 6 + bob, cBlush)
      }
      PetState.DRAGGING, PetState.FLOAT -> {
        drawPx(eyeLeftX, eyeY, cDarkEye)
        drawPx(eyeLeftX, eyeY + 1, cDarkEye)
        drawPx(eyeLeftX, eyeY, cWhite)

        drawPx(eyeRightX, eyeY, cDarkEye)
        drawPx(eyeRightX, eyeY + 1, cDarkEye)
        drawPx(eyeRightX, eyeY, cWhite)

        drawPx(9, 6 + bob, cDarkEye)
        drawPx(10, 6 + bob, cDarkEye)
      }
      else -> {
        val blink = (tickCount % 90) > 85
        if (blink) {
          drawPx(eyeLeftX, eyeY, cDarkEye)
          drawPx(eyeRightX, eyeY, cDarkEye)
        } else {
          drawPx(eyeLeftX, eyeY, cDarkEye)
          drawPx(eyeLeftX, eyeY - 1, cWhite)
          drawPx(eyeRightX, eyeY, cDarkEye)
          drawPx(eyeRightX, eyeY - 1, cWhite)
        }
        drawPx(7, 6 + bob, cBlush)
        drawPx(12, 6 + bob, cBlush)
      }
    }

    // High Quota Sweat Bead (like Clawd in clawd-usage-gnome)
    if (contextUsagePercent >= 80) {
      drawPx(13, 3 + bob, Color.parseColor("#38bdf8"))
      drawPx(13, 4 + bob, Color.parseColor("#0284c7"))
    }

    // 4. Feet Walk & Float Mechanics at base prongs
    if (currentState == PetState.WALK || currentState == PetState.WORKING) {
      if (animFrame % 4 == 1) {
        drawPx(3, 13, Color.parseColor("#38bdf8"))
        drawPx(4, 13, Color.parseColor("#0284c7"))
        drawPx(15, 11, Color.parseColor("#6366f1"))
      } else if (animFrame % 4 == 3) {
        drawPx(15, 13, Color.parseColor("#6366f1"))
        drawPx(16, 13, Color.parseColor("#4338ca"))
        drawPx(4, 11, Color.parseColor("#38bdf8"))
      } else {
        drawPx(4, 13, Color.parseColor("#0284c7"))
        drawPx(5, 13, Color.parseColor("#0284c7"))
        drawPx(14, 13, Color.parseColor("#4338ca"))
        drawPx(15, 13, Color.parseColor("#4338ca"))
      }
    } else if (currentState == PetState.DRAGGING || currentState == PetState.FLOAT) {
      // Cute dangling / kicking feet in the air (no beam between legs!)
      val kick = (tickCount % 20) < 10
      if (kick) {
        drawPx(4, 13 + bob, Color.parseColor("#0284c7"))
        drawPx(5, 13 + bob, Color.parseColor("#0284c7"))
        drawPx(14, 12 + bob, Color.parseColor("#4338ca"))
        drawPx(15, 12 + bob, Color.parseColor("#4338ca"))
      } else {
        drawPx(4, 12 + bob, Color.parseColor("#0284c7"))
        drawPx(5, 12 + bob, Color.parseColor("#0284c7"))
        drawPx(14, 13 + bob, Color.parseColor("#4338ca"))
        drawPx(15, 13 + bob, Color.parseColor("#4338ca"))
      }
    } else if (currentState == PetState.SIT || currentState == PetState.SLEEP) {
      drawPx(4, 12, Color.parseColor("#0284c7"))
      drawPx(5, 12, Color.parseColor("#0284c7"))
      drawPx(14, 12, Color.parseColor("#4338ca"))
      drawPx(15, 12, Color.parseColor("#4338ca"))
    }

    // 5. Orbital Satellite Gem circling around the 'A'
    val angle = tickCount * 0.08
    val satX = (10 + (Math.cos(angle) * 8)).toInt()
    val satY = (7 + (Math.sin(angle) * 4) + bob - 5).toInt()
    drawPx(satX, satY, if ((tickCount % 10) < 5) cCyanGlow else cWhite)

    canvas.restore()
  }

  companion object {
    // -------------------------------------------------------------
    // THE EXACT 50 OFFICIAL PIXELS OF THE ANTIGRAVITY "A" LOGO
    // -------------------------------------------------------------
    val OFFICIAL_PIXELS = arrayOf(
      // Apex (Orange / Amber)
      AgyPixel(9, 3, Color.parseColor("#f2922e")),
      AgyPixel(10, 3, Color.parseColor("#f07237")),
      AgyPixel(8, 4, Color.parseColor("#dab131")),
      AgyPixel(9, 4, Color.parseColor("#f6912e")),
      AgyPixel(10, 4, Color.parseColor("#f37337")),
      AgyPixel(11, 4, Color.parseColor("#f0583b")),
      // Upper Face Block (Lime, Green, Amber, Red)
      AgyPixel(7, 5, Color.parseColor("#9dc345")),
      AgyPixel(8, 5, Color.parseColor("#b5b43e")),
      AgyPixel(9, 5, Color.parseColor("#e2993d")),
      AgyPixel(10, 5, Color.parseColor("#f67a34")),
      AgyPixel(11, 5, Color.parseColor("#f86a35")),
      AgyPixel(12, 5, Color.parseColor("#ef5442")),
      AgyPixel(7, 6, Color.parseColor("#86c64e")),
      AgyPixel(8, 6, Color.parseColor("#75b45e")),
      AgyPixel(9, 6, Color.parseColor("#cc954d")),
      AgyPixel(10, 6, Color.parseColor("#ef7947")),
      AgyPixel(11, 6, Color.parseColor("#e16652")),
      AgyPixel(12, 6, Color.parseColor("#e14f59")),
      // The Crossbar of the "A" (Row 7)
      AgyPixel(6, 7, Color.parseColor("#7cc251")),
      AgyPixel(7, 7, Color.parseColor("#71c25c")),
      AgyPixel(8, 7, Color.parseColor("#5ca98e")),
      AgyPixel(9, 7, Color.parseColor("#5c91b3")),
      AgyPixel(10, 7, Color.parseColor("#8373b0")),
      AgyPixel(11, 7, Color.parseColor("#746fc3")),
      AgyPixel(12, 7, Color.parseColor("#995da8")),
      AgyPixel(13, 7, Color.parseColor("#9c5b97")),
      // Legs Split & Arch (Row 8)
      AgyPixel(6, 8, Color.parseColor("#61c37d")),
      AgyPixel(7, 8, Color.parseColor("#54b880")),
      AgyPixel(8, 8, Color.parseColor("#4197de")),
      AgyPixel(11, 8, Color.parseColor("#4197de")),
      AgyPixel(12, 8, Color.parseColor("#706ece")),
      AgyPixel(13, 8, Color.parseColor("#8f64b4")),
      // Row 9
      AgyPixel(6, 9, Color.parseColor("#61c37d")),
      AgyPixel(7, 9, Color.parseColor("#43aeab")),
      AgyPixel(12, 9, Color.parseColor("#4a80ea")),
      AgyPixel(13, 9, Color.parseColor("#6c73d8")),
      // Row 10
      AgyPixel(5, 10, Color.parseColor("#6dc694")),
      AgyPixel(6, 10, Color.parseColor("#62bad5")),
      AgyPixel(7, 10, Color.parseColor("#47a8dc")),
      AgyPixel(12, 10, Color.parseColor("#3d89fb")),
      AgyPixel(13, 10, Color.parseColor("#4a81f0")),
      AgyPixel(14, 10, Color.parseColor("#6579e1")),
      // Row 11
      AgyPixel(5, 11, Color.parseColor("#6bc7a3")),
      AgyPixel(6, 11, Color.parseColor("#64b6f6")),
      AgyPixel(13, 11, Color.parseColor("#3886fb")),
      AgyPixel(14, 11, Color.parseColor("#4881f4")),
      // Row 12 (Base prongs / Feet)
      AgyPixel(4, 12, Color.parseColor("#67b9f4")),
      AgyPixel(5, 12, Color.parseColor("#64b6f6")),
      AgyPixel(14, 12, Color.parseColor("#3883f9")),
      AgyPixel(15, 12, Color.parseColor("#3d85fc"))
    )
  }
}
