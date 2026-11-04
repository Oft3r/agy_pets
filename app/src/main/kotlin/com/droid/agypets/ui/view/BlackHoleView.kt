package com.droid.agypets.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full-overlay effect that swallows the AntiGravity mascot into a black hole with
 * spaghettification (tidal stretching of each pixel into a spiralling noodle), or plays the
 * effect in reverse so the mascot is ejected and reassembled when it appears.
 *
 * The view is square; its center is the black hole center, which coincides with the center of
 * the pet window. [petSizePx] must match the pet view size so the pixels line up exactly.
 */
class BlackHoleView(
  context: Context,
  private val petSizePx: Int,
  private val facingLeft: Boolean,
  private val mode: Mode
) : View(context) {

  enum class Mode { COLLAPSE, EMERGE }

  private data class Particle(
    val color: Int,
    val r0: Float,
    val a0: Float,
    val delay: Float
  )

  private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val square = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
  private val path = Path()
  private val oval = RectF()
  private val rect = RectF()

  private var particles: List<Particle> = emptyList()
  private var pixelSize = 1f
  private var t = 0f // 0..1 collapse progress (EMERGE runs it backwards)

  private val diskColors = intArrayOf(
    Color.parseColor("#f2922e"),
    Color.parseColor("#f0583b"),
    Color.parseColor("#995da8"),
    Color.parseColor("#4197de"),
    Color.parseColor("#00E5FF"),
    Color.parseColor("#9dc345")
  )

  private var animator: ValueAnimator? = null

  fun start(durationMs: Long, onEnd: () -> Unit) {
    animator?.cancel()
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = durationMs
      interpolator = LinearInterpolator()
      addUpdateListener {
        val v = it.animatedValue as Float
        t = if (mode == Mode.COLLAPSE) v else 1f - v
        invalidate()
      }
      addListener(object : AnimatorListenerAdapter() {
        private var done = false
        override fun onAnimationEnd(animation: Animator) {
          if (!done) { done = true; onEnd() }
        }
        override fun onAnimationCancel(animation: Animator) {
          if (!done) { done = true; onEnd() }
        }
      })
      start()
    }
  }

  override fun onDetachedFromWindow() {
    animator?.removeAllUpdateListeners()
    animator?.cancel()
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    buildParticles(w, h)
  }

  private fun buildParticles(w: Int, h: Int) {
    val ps = petSizePx / 20f
    pixelSize = ps
    val petLeft = (w - petSizePx) / 2f
    val petTop = (h - petSizePx) / 2f
    val oy = (petSizePx - 16 * ps) / 2f
    val cx = w / 2f
    val cy = h / 2f

    val eyeColors = mapOf(
      (8 to 5) to Color.parseColor("#060919"),
      (11 to 5) to Color.parseColor("#060919"),
      (8 to 4) to Color.WHITE,
      (11 to 4) to Color.WHITE
    )
    val raw = PixelPetView.OFFICIAL_PIXELS.map { base ->
      val px = eyeColors[base.x to base.y]?.let { base.copy(color = it) } ?: base
      var localX = (px.x + 0.5f) * ps
      if (facingLeft) localX = petSizePx - localX
      val x = petLeft + localX
      val y = petTop + oy + (px.y + 0.5f) * ps
      val dx = x - cx
      val dy = y - cy
      Triple(px, hypot(dx, dy), atan2(dy, dx))
    }
    val maxDist = raw.maxOf { it.second }.coerceAtLeast(1f)
    particles = raw.map { (px, dist, ang) ->
      // Deterministic jitter so every pixel peels away at a slightly different moment
      val jitter = (((px.x * 73 + px.y * 151) % 17) / 17f) * 0.08f
      Particle(
        color = px.color,
        r0 = dist,
        a0 = ang,
        delay = 0.14f + 0.30f * (dist / maxDist) + jitter
      )
    }
  }

  private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x)

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val cx = width / 2f
    val cy = height / 2f
    val maxR = petSizePx * 0.30f

    // Event horizon radius over time
    val holeR = when {
      t < 0.18f -> maxR * easeOut(t / 0.18f)
      t < 0.84f -> maxR * (1f + 0.04f * sin(t * 40f))
      else -> maxR * (1f - easeOut((t - 0.84f) / 0.16f))
    }.coerceAtLeast(0f)

    val spin = t * 14f // radians, disk rotation

    if (holeR > 0.5f) {
      // 1. Gravitational lensing glow
      fill.shader = RadialGradient(
        cx, cy, holeR * 2.6f,
        intArrayOf(
          Color.BLACK,
          Color.argb(150, 124, 58, 237),
          Color.argb(90, 249, 115, 22),
          Color.TRANSPARENT
        ),
        floatArrayOf(0.36f, 0.5f, 0.66f, 1f),
        Shader.TileMode.CLAMP
      )
      canvas.drawCircle(cx, cy, holeR * 2.6f, fill)
      fill.shader = null

      // 2. Tilted accretion disk (swirling arcs)
      stroke.strokeCap = Paint.Cap.ROUND
      for (k in 0 until 6) {
        val rx = holeR * (1.55f + 0.32f * k)
        val ry = rx * 0.34f
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        stroke.strokeWidth = pixelSize * (0.75f - 0.08f * k)
        stroke.color = diskColors[k % diskColors.size]
        stroke.alpha = (220 - 28 * k).coerceIn(40, 255)
        val start = Math.toDegrees((spin * (1f + 0.25f * k)).toDouble()).toFloat() + k * 57f
        canvas.drawArc(oval, start, 210f, false, stroke)
      }

      // 3. Photon ring
      stroke.strokeWidth = pixelSize * 0.5f
      stroke.color = Color.parseColor("#FDE68A")
      stroke.alpha = 200
      canvas.drawCircle(cx, cy, holeR * 1.1f, stroke)

      // 4. Event horizon
      fill.color = Color.BLACK
      canvas.drawCircle(cx, cy, holeR, fill)
    }

    // 5. Spaghettified pixels
    for (p in particles) {
      val prog = ((t - p.delay) / 0.38f).coerceIn(0f, 1f)
      if (prog >= 1f) continue

      if (prog <= 0f) {
        // Still intact: draw as a crisp pixel with a hint of tidal wobble
        val x = cx + cos(p.a0) * p.r0
        val y = cy + sin(p.a0) * p.r0
        val h = pixelSize / 2f
        square.color = p.color
        square.alpha = 255
        rect.set(x - h, y - h, x + h, y + h)
        canvas.drawRect(rect, square)
        continue
      }

      // Head accelerates inward (free fall), tail lags behind -> stretched noodle
      val eHead = prog * prog
      val eTail = (prog - 0.22f).coerceAtLeast(0f).let { it * it } * 0.9f
      val segments = 8
      path.reset()
      var headR = 0f
      for (s in 0..segments) {
        val e = eTail + (eHead - eTail) * (s / segments.toFloat())
        val r = p.r0 * (1f - e)
        val a = p.a0 + e * 2.4f * Math.PI.toFloat() + spin * 0.15f * e
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        if (s == segments) headR = r
      }

      // Thinner as it stretches; fades as it crosses the event horizon
      val horizonFade = if (holeR > 0f) (headR / (holeR * 1.15f)).coerceIn(0f, 1f) else 1f
      val alpha = (255 * horizonFade * (1f - sqrt(prog) * 0.35f)).toInt().coerceIn(0, 255)
      stroke.strokeWidth = (pixelSize * (1f - 0.8f * prog)).coerceAtLeast(1.2f)
      stroke.color = p.color
      stroke.alpha = alpha
      canvas.drawPath(path, stroke)
    }

    // 6. Final collapse flash
    if (t > 0.84f) {
      val f = (t - 0.84f) / 0.16f
      stroke.strokeWidth = pixelSize * (1.2f - f)
      stroke.color = Color.parseColor("#00E5FF")
      stroke.alpha = (255 * (1f - f)).toInt()
      canvas.drawCircle(cx, cy, maxR * (0.2f + 2.4f * f), stroke)
      fill.color = Color.WHITE
      fill.alpha = (220 * (1f - f)).toInt()
      canvas.drawCircle(cx, cy, pixelSize * 1.2f * (1f - f), fill)
      fill.alpha = 255
    }
  }
}
