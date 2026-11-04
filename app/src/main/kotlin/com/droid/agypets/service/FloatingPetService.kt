package com.droid.agypets.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.droid.agypets.MainActivity
import com.droid.agypets.R
import com.droid.agypets.data.AgyUsageData
import com.droid.agypets.data.AgyUsageRepository
import com.droid.agypets.data.PetPreferences
import com.droid.agypets.engine.Direction
import com.droid.agypets.engine.PetBehaviorEngine
import com.droid.agypets.engine.PetState
import com.droid.agypets.ui.view.AgyStatusPillView
import com.droid.agypets.ui.view.AgyUsageHudView
import com.droid.agypets.ui.view.BlackHoleView
import com.droid.agypets.ui.view.PixelPetView
import com.droid.agypets.util.AntigravityDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingPetService : Service() {

  private var windowManager: WindowManager? = null
  private var petView: PixelPetView? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var engine: PetBehaviorEngine? = null

  // Mini Status Pill
  private var pillView: AgyStatusPillView? = null
  private var pillParams: WindowManager.LayoutParams? = null

  // Expanded HUD Card
  private var hudView: AgyUsageHudView? = null
  private var hudParams: WindowManager.LayoutParams? = null
  private var isHudVisible = false

  // Black hole show/hide transition
  private var blackHoleView: BlackHoleView? = null
  private var isTransitioning = false

  private val handler = Handler(Looper.getMainLooper())
  private var isRunning = false
  private var tickCount = 0L

  private lateinit var prefs: PetPreferences
  private lateinit var usageRepo: AgyUsageRepository
  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
  private var currentUsage = AgyUsageData()

  // Touch tracking
  private var initialPetX = 0
  private var initialPetY = 0
  private var touchDownX = 0f
  private var touchDownY = 0f
  private var lastMoveX = 0f
  private var lastMoveY = 0f
  private var touchDownTime = 0L

  private val tickRunnable = object : Runnable {
    override fun run() {
      if (!isRunning) return
      tickCount++

      engine?.let { eng ->
        // Periodically refresh screen bounds in case display/shelf resized
        if (tickCount % 90 == 0L) {
          updateScreenBounds(eng)
        }

        eng.tick()

        layoutParams?.let { params ->
          params.x = eng.x.toInt()
          params.y = eng.y.toInt()
          try {
            petView?.let { v ->
              if (v.isAttachedToWindow) {
                windowManager?.updateViewLayout(v, params)
              }
            }
          } catch (e: Exception) {
            Log.e("FloatingPetService", "updateViewLayout failed", e)
          }
        }

        val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

        // Update Pill position right above the pet
        pillView?.let { pv ->
          pillParams?.let { pp ->
            if (pv.isAttachedToWindow && prefs.showUsageBadge) {
              pp.x = (eng.x + (eng.petWidth / 2) - (65 * density)).toInt()
              pp.y = (eng.y - (26 * density)).toInt()
              try {
                windowManager?.updateViewLayout(pv, pp)
              } catch (e: Exception) {}
            }
          }
        }

        // If HUD is visible, keep it smoothly anchored above the pet!
        if (isHudVisible && hudView != null && hudParams != null) {
          updateHudPosition(eng)
        }

        petView?.updateAnimation(
          state = eng.currentState,
          direction = eng.direction,
          frame = eng.animFrame,
          ticks = tickCount,
          quotaPercent = currentUsage.activePrimaryUsedPercent
        )
      }

      handler.postDelayed(this, 33L) // ~30 FPS
    }
  }

  override fun onCreate() {
    super.onCreate()
    prefs = PetPreferences(this)
    usageRepo = AgyUsageRepository(this)
    createNotificationChannel()
    startUsagePolling()
  }

  private var lastActivityTime = System.currentTimeMillis()
  private var missedActivityPings = 0

  private fun startUsagePolling() {
    serviceScope.launch {
      usageRepo.usageState.collect { data ->
        val wasWorking = currentUsage.isAgentRunning
        currentUsage = data
        engine?.setAgentWorking(data.isAgentRunning)
        pillView?.updateData(data)
        hudView?.updateData(data)
      }
    }

    // Quota refresh loop
    serviceScope.launch {
      while (isActive) {
        if (prefs.liveTrackingEnabled) {
          usageRepo.refresh()
        }
        delay(1500L) // 1.5s interval for responsive agent working / celebration reactions
      }
    }

    // Antigravity visibility loop (fast, ~1s) — strictly based on whether the
    // com.google.android.apps.antigravity app has a visible window.
    AntigravityDetector.reset()
    serviceScope.launch {
      while (isActive) {
        if (prefs.autoShowWithAntigravity && !isTransitioning) {
          val det = kotlinx.coroutines.withContext(Dispatchers.IO) {
            AntigravityDetector.detect(this@FloatingPetService)
          }
          if (det.isOpen) {
            missedActivityPings = 0
            if (petView == null && Settings.canDrawOverlays(this@FloatingPetService)) {
              Log.i("FloatingPetService", "Antigravity open (usage=${det.usageStatsVisible}, ls=${det.lsAlive}). Showing mascot")
              setupOverlayView()
            }
          } else {
            missedActivityPings++
            // 2 consecutive misses (~2s) to avoid flicker during activity transitions
            if (missedActivityPings >= 2 && petView != null) {
              Log.i("FloatingPetService", "Antigravity closed (usage=${det.usageStatsVisible}, ls=${det.lsAlive}). Hiding mascot")
              hideMascotViews()
            }
          }
        }
        delay(1000L)
      }
    }
  }

  /**
   * Hides the mascot. When [animated], the pet is swallowed by a black hole (spaghettification)
   * before the overlay windows are removed.
   */
  private fun hideMascotViews(animated: Boolean = true) {
    val pv = petView
    val lp = layoutParams
    if (animated && pv != null && lp != null && pv.isAttachedToWindow && !isTransitioning) {
      // Remove HUD & pill immediately, freeze the pet, then play the collapse
      hideHud()
      try {
        pillView?.let { v -> if (v.isAttachedToWindow) windowManager?.removeView(v) }
      } catch (e: Exception) {}
      pillView = null
      isRunning = false
      handler.removeCallbacks(tickRunnable)

      val facingLeft = engine?.direction == Direction.LEFT
      isTransitioning = true
      val started = playBlackHole(
        mode = BlackHoleView.Mode.COLLAPSE,
        centerX = lp.x + lp.width / 2,
        centerY = lp.y + lp.height / 2,
        petSize = lp.width,
        facingLeft = facingLeft,
        durationMs = 1700L
      ) {
        isTransitioning = false
        hideMascotViews(animated = false)
      }
      if (started) {
        pv.visibility = View.INVISIBLE
        return
      }
      isTransitioning = false
    }
    removeMascotWindows()
  }

  /** Adds a temporary black hole overlay centered on the pet. Returns false if it failed. */
  private fun playBlackHole(
    mode: BlackHoleView.Mode,
    centerX: Int,
    centerY: Int,
    petSize: Int,
    facingLeft: Boolean,
    durationMs: Long,
    onEnd: () -> Unit
  ): Boolean {
    val wm = windowManager ?: return false
    removeBlackHole()
    val size = (petSize * 2.8f).toInt()
    val view = BlackHoleView(this, petSize, facingLeft, mode)
    val params = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
          WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = centerX - size / 2
      y = centerY - size / 2
    }
    return try {
      wm.addView(view, params)
      blackHoleView = view
      view.start(durationMs) {
        handler.post {
          removeBlackHole()
          onEnd()
        }
      }
      true
    } catch (e: Exception) {
      Log.e("FloatingPetService", "Failed to add black hole overlay", e)
      false
    }
  }

  private fun removeBlackHole() {
    try {
      blackHoleView?.let { v -> if (v.isAttachedToWindow) windowManager?.removeView(v) }
    } catch (e: Exception) {}
    blackHoleView = null
  }

  private fun removeMascotWindows() {
    try {
      hudView?.let { v ->
        if (v.isAttachedToWindow) windowManager?.removeView(v)
      }
    } catch (e: Exception) {}
    hudView = null
    isHudVisible = false

    try {
      pillView?.let { v ->
        if (v.isAttachedToWindow) windowManager?.removeView(v)
      }
    } catch (e: Exception) {}
    pillView = null

    try {
      petView?.let { v ->
        if (v.isAttachedToWindow) windowManager?.removeView(v)
      }
    } catch (e: Exception) {}
    petView = null
    isRunning = false
    handler.removeCallbacks(tickRunnable)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopPet()
        return START_NOT_STICKY
      }
      ACTION_RESET_POSITION -> {
        resetPosition()
        return START_STICKY
      }
      ACTION_RELOAD_SETTINGS -> {
        if (petView == null && !prefs.autoShowWithAntigravity && prefs.isPetEnabled &&
          Settings.canDrawOverlays(this)
        ) {
          setupOverlayView()
        } else {
          reloadSettings()
        }
        return START_STICKY
      }
      ACTION_TOGGLE_HUD -> {
        toggleHud()
        return START_STICKY
      }
    }

    try {
      startForegroundNotification()
    } catch (e: Exception) {
      Log.w("FloatingPetService", "startForegroundNotification failed, continuing", e)
    }

    if (!Settings.canDrawOverlays(this)) {
      Log.w("FloatingPetService", "No overlay permission")
      stopSelf()
      return START_NOT_STICKY
    }

    if (petView == null) {
      // In auto mode, only show now if Antigravity is actually open; the visibility loop
      // handles showing/hiding afterwards.
      val shouldShowNow = !prefs.autoShowWithAntigravity ||
          AntigravityDetector.detect(this).isOpen
      if (shouldShowNow) setupOverlayView()
    } else {
      reloadSettings()
    }

    return START_STICKY
  }

  private fun startForegroundNotification() {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE
    )

    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, FloatingPetService::class.java).apply { action = ACTION_STOP },
      PendingIntent.FLAG_IMMUTABLE
    )

    val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("AntiGravity Companion")
      .setContentText("Official desktop mascot and live quota monitor active")
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentIntent(pendingIntent)
      .addAction(0, "Hide", stopIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun getTrueScreenDimensions(): Pair<Int, Int> {
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    val realPoint = Point()
    @Suppress("DEPRECATION")
    defaultDisplay?.getRealSize(realPoint)

    val realDm = DisplayMetrics()
    @Suppress("DEPRECATION")
    defaultDisplay?.getRealMetrics(realDm)

    val sysDm = Resources.getSystem().displayMetrics

    val maxBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        wm.maximumWindowMetrics.bounds
      } catch (e: Exception) {
        Rect()
      }
    } else {
      Rect()
    }

    val width = maxOf(realPoint.x, realDm.widthPixels, sysDm.widthPixels, maxBounds.width()).coerceAtLeast(1024)
    val height = maxOf(realPoint.y, realDm.heightPixels, sysDm.heightPixels, maxBounds.height()).coerceAtLeast(700)

    return Pair(width, height)
  }

  private fun updateScreenBounds(eng: PetBehaviorEngine) {
    val (screenWidth, screenHeight) = getTrueScreenDimensions()
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)
    val taskbarOffset = (52 * density).toInt()

    eng.screenWidth = maxOf(eng.screenWidth, screenWidth)
    eng.screenHeight = maxOf(eng.screenHeight, screenHeight)
    eng.groundY = (eng.screenHeight - eng.petWidth - taskbarOffset).toFloat().coerceAtLeast(100f)
  }

  private fun setupOverlayView() {
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val (screenWidth, screenHeight) = getTrueScreenDimensions()
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

    val scale = prefs.petScale.coerceIn(1.0f, 4.0f)
    val petSizePx = (64 * scale * density).toInt().coerceIn(80, 360)
    val taskbarOffset = (52 * density).toInt()
    val groundY = (screenHeight - petSizePx - taskbarOffset).toFloat().coerceAtLeast(100f)

    val startX = (screenWidth / 2f) - (petSizePx / 2f)
    val startY = groundY

    engine = PetBehaviorEngine(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      petWidth = petSizePx,
      groundY = groundY,
      speedMultiplier = prefs.speedMultiplier,
      zeroGravity = prefs.zeroGravity,
      x = startX,
      y = startY,
      currentState = PetState.WALK,
      direction = Direction.RIGHT,
      vx = 2.0f
    )

    petView = PixelPetView(this).apply {
      setOnTouchListener { _, event -> handleTouch(event) }
    }

    val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    layoutParams = WindowManager.LayoutParams(
      petSizePx,
      petSizePx,
      type,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = startX.toInt()
      y = startY.toInt()
    }

    try {
      windowManager?.addView(petView, layoutParams)
      prefs.isPetEnabled = true

      val startWalking = {
        petView?.visibility = View.VISIBLE
        if (!isRunning) {
          isRunning = true
          handler.post(tickRunnable)
        }
        // Setup Floating Status Pill
        if (prefs.showUsageBadge && petView != null) {
          val eng = engine
          val px = eng?.x?.toInt() ?: startX.toInt()
          val py = ((eng?.y ?: startY) - (26 * density)).toInt()
          setupPillView(px, py)
        }
      }

      // Emerge from a black hole (reverse spaghettification)
      petView?.visibility = View.INVISIBLE
      isTransitioning = true
      val started = playBlackHole(
        mode = BlackHoleView.Mode.EMERGE,
        centerX = startX.toInt() + petSizePx / 2,
        centerY = startY.toInt() + petSizePx / 2,
        petSize = petSizePx,
        facingLeft = false,
        durationMs = 1500L
      ) {
        isTransitioning = false
        startWalking()
      }
      if (!started) {
        isTransitioning = false
        startWalking()
      }
    } catch (e: Exception) {
      Log.e("FloatingPetService", "Failed to addView to WindowManager", e)
    }
  }

  private fun setupPillView(initialX: Int, initialY: Int) {
    if (pillView != null) return

    pillView = AgyStatusPillView(this) {
      toggleHud()
    }.apply {
      updateData(currentUsage)
    }

    pillParams = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = initialX
      y = initialY
    }

    try {
      windowManager?.addView(pillView, pillParams)
    } catch (e: Exception) {
      Log.e("FloatingPetService", "Failed to add pillView", e)
    }
  }

  private fun toggleHud() {
    if (isHudVisible) {
      hideHud()
    } else {
      showHud()
    }
  }

  private fun calculateHudCoords(eng: PetBehaviorEngine, hudWidth: Int, density: Float): Pair<Int, Int> {
    val (screenWidth, screenHeight) = getTrueScreenDimensions()
    val estimatedHudHeight = (330 * density).toInt()

    // Center horizontally on the pet
    val idealX = (eng.x + (eng.petWidth / 2f) - (hudWidth / 2f)).toInt()
    val clampedX = idealX.coerceIn(16, (screenWidth - hudWidth - 16).coerceAtLeast(16))

    // Position above the mascot (above the pill)
    val idealYAbove = (eng.y - estimatedHudHeight - (14 * density)).toInt()

    val targetY = if (idealYAbove >= (30 * density).toInt()) {
      idealYAbove
    } else {
      // If mascot is too close to top of screen, place HUD just below the mascot
      (eng.y + eng.petWidth + (12 * density)).toInt().coerceAtMost(screenHeight - estimatedHudHeight - 20)
    }

    return Pair(clampedX, targetY.coerceAtLeast(20))
  }

  private fun updateHudPosition(eng: PetBehaviorEngine) {
    val wm = windowManager ?: return
    val hv = hudView ?: return
    val hp = hudParams ?: return
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

    val (targetX, targetY) = calculateHudCoords(eng, hp.width, density)
    if (hp.x != targetX || hp.y != targetY) {
      hp.x = targetX
      hp.y = targetY
      try {
        if (hv.isAttachedToWindow) {
          wm.updateViewLayout(hv, hp)
        }
      } catch (e: Exception) {}
    }
  }

  private fun showHud() {
    if (isHudVisible) return
    val wm = windowManager ?: return
    val eng = engine ?: return
    val (screenWidth, _) = getTrueScreenDimensions()
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

    val hudWidth = minOf((screenWidth * 0.92f).toInt(), (370 * density).toInt())

    hudView = AgyUsageHudView(
      context = this,
      onRefreshClicked = {
        serviceScope.launch {
          usageRepo.refresh()
        }
      },
      onCloseClicked = {
        hideHud()
      }
    ).apply {
      updateData(currentUsage)
    }

    // Dynamic placement anchored directly above the pet!
    val (targetX, targetY) = calculateHudCoords(eng, hudWidth, density)

    hudParams = WindowManager.LayoutParams(
      hudWidth,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = targetX
      y = targetY
    }

    try {
      wm.addView(hudView, hudParams)
      isHudVisible = true
    } catch (e: Exception) {
      Log.e("FloatingPetService", "Failed to add hudView", e)
    }
  }

  private fun hideHud() {
    if (!isHudVisible) return
    try {
      hudView?.let { v ->
        if (v.isAttachedToWindow) {
          windowManager?.removeView(v)
        }
      }
    } catch (e: Exception) {}
    hudView = null
    isHudVisible = false
  }

  private fun handleTouch(event: MotionEvent): Boolean {
    if (isTransitioning) return true
    val eng = engine ?: return false
    val params = layoutParams ?: return false
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        touchDownTime = System.currentTimeMillis()
        touchDownX = event.rawX
        touchDownY = event.rawY
        lastMoveX = event.rawX
        lastMoveY = event.rawY
        initialPetX = params.x
        initialPetY = params.y

        if (event.rawX > eng.screenWidth) {
          eng.screenWidth = event.rawX.toInt() + eng.petWidth
        }
        if (event.rawY > eng.screenHeight) {
          eng.screenHeight = event.rawY.toInt() + eng.petWidth
          eng.groundY = (eng.screenHeight - eng.petWidth - (52 * density).toInt()).toFloat()
        }

        eng.onStartDrag(params.x.toFloat(), params.y.toFloat())
        petView?.updateAnimation(eng.currentState, eng.direction, 0, tickCount, currentUsage.activePrimaryUsedPercent)
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - touchDownX
        val dy = event.rawY - touchDownY

        if (event.rawX > eng.screenWidth - 50) {
          eng.screenWidth = (event.rawX + eng.petWidth + 100).toInt()
        }
        if (event.rawY > eng.screenHeight - 50) {
          eng.screenHeight = (event.rawY + eng.petWidth + 100).toInt()
          eng.groundY = (eng.screenHeight - eng.petWidth - (52 * density).toInt()).toFloat()
        }

        eng.onDragMove(initialPetX + dx, initialPetY + dy)
        params.x = eng.x.toInt()
        params.y = eng.y.toInt()

        try {
          if (petView?.isAttachedToWindow == true) {
            windowManager?.updateViewLayout(petView, params)
          }
        } catch (e: Exception) {}

        lastMoveX = event.rawX
        lastMoveY = event.rawY
        return true
      }

      MotionEvent.ACTION_UP -> {
        val duration = System.currentTimeMillis() - touchDownTime
        val dist = Math.hypot(
          (event.rawX - touchDownX).toDouble(),
          (event.rawY - touchDownY).toDouble()
        )

        if (duration < 300 && dist < 24) {
          // Quick Tap! Toggles the Usage & Quota HUD and pokes pet
          eng.onPoke()
          toggleHud()
        } else {
          val tossVx = (event.rawX - lastMoveX) * 0.4f
          val tossVy = (event.rawY - lastMoveY) * 0.4f
          eng.onRelease(tossVx, tossVy)
        }

        petView?.updateAnimation(eng.currentState, eng.direction, 0, tickCount, currentUsage.activePrimaryUsedPercent)
        return true
      }
    }
    return false
  }

  private fun reloadSettings() {
    val eng = engine ?: return
    val (screenWidth, screenHeight) = getTrueScreenDimensions()
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)

    val scale = prefs.petScale.coerceIn(1.0f, 4.0f)
    val petSizePx = (64 * scale * density).toInt().coerceIn(80, 360)
    val taskbarOffset = (52 * density).toInt()

    eng.screenWidth = maxOf(eng.screenWidth, screenWidth)
    eng.screenHeight = maxOf(eng.screenHeight, screenHeight)
    eng.petWidth = petSizePx
    eng.groundY = (eng.screenHeight - petSizePx - taskbarOffset).toFloat().coerceAtLeast(100f)
    eng.speedMultiplier = prefs.speedMultiplier
    eng.zeroGravity = prefs.zeroGravity

    layoutParams?.let { params ->
      params.width = petSizePx
      params.height = petSizePx
      try {
        if (petView?.isAttachedToWindow == true) {
          windowManager?.updateViewLayout(petView, params)
        }
      } catch (e: Exception) {}
    }

    // Toggle pill visibility if needed
    if (prefs.showUsageBadge && pillView == null) {
      setupPillView(eng.x.toInt(), (eng.y - (26 * density)).toInt())
    } else if (!prefs.showUsageBadge && pillView != null) {
      try {
        if (pillView?.isAttachedToWindow == true) {
          windowManager?.removeView(pillView)
        }
      } catch (e: Exception) {}
      pillView = null
    }
  }

  private fun resetPosition() {
    val (screenWidth, screenHeight) = getTrueScreenDimensions()
    val density = Resources.getSystem().displayMetrics.density.coerceAtLeast(1.0f)
    val taskbarOffset = (52 * density).toInt()

    engine?.let { eng ->
      eng.screenWidth = maxOf(eng.screenWidth, screenWidth)
      eng.screenHeight = maxOf(eng.screenHeight, screenHeight)
      val groundY = (eng.screenHeight - eng.petWidth - taskbarOffset).toFloat().coerceAtLeast(100f)
      eng.groundY = groundY

      val targetX = (eng.screenWidth / 2f) - (eng.petWidth / 2f)
      val targetY = groundY
      eng.x = targetX
      eng.y = targetY
      eng.currentState = PetState.WALK
      eng.direction = Direction.RIGHT
      eng.vx = 2.0f
      eng.vy = 0f

      layoutParams?.let { params ->
        params.x = targetX.toInt()
        params.y = targetY.toInt()
        try {
          if (petView?.isAttachedToWindow == true) {
            windowManager?.updateViewLayout(petView, params)
          }
        } catch (e: Exception) {}
      }
    }
  }

  private fun stopPet() {
    isRunning = false
    isTransitioning = false
    removeBlackHole()
    handler.removeCallbacks(tickRunnable)
    hideHud()

    try {
      pillView?.let { v ->
        if (v.isAttachedToWindow) {
          windowManager?.removeView(v)
        }
      }
    } catch (e: Exception) {}
    pillView = null

    try {
      petView?.let { v ->
        if (v.isAttachedToWindow) {
          windowManager?.removeView(v)
        }
      }
    } catch (e: Exception) {}
    petView = null

    prefs.isPetEnabled = false
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (e: Exception) {}
    stopSelf()
  }

  override fun onDestroy() {
    stopPet()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      "AntiGravity Pet Service",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Keeps desktop mascot and live quota monitor on screen"
      setShowBadge(false)
    }
    val nm = getSystemService(NotificationManager::class.java)
    nm?.createNotificationChannel(channel)
  }

  companion object {
    const val CHANNEL_ID = "agypets_floating_service"
    const val NOTIFICATION_ID = 101

    const val ACTION_START = "com.droid.agypets.ACTION_START"
    const val ACTION_STOP = "com.droid.agypets.ACTION_STOP"
    const val ACTION_RESET_POSITION = "com.droid.agypets.ACTION_RESET_POSITION"
    const val ACTION_RELOAD_SETTINGS = "com.droid.agypets.ACTION_RELOAD_SETTINGS"
    const val ACTION_TOGGLE_HUD = "com.droid.agypets.ACTION_TOGGLE_HUD"

    fun start(context: Context) {
      val intent = Intent(context, FloatingPetService::class.java).apply {
        action = ACTION_START
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        try {
          context.startService(intent)
        } catch (e2: Exception) {
          Log.e("FloatingPetService", "Failed to start service", e2)
        }
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, FloatingPetService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {}
    }

    fun reset(context: Context) {
      val intent = Intent(context, FloatingPetService::class.java).apply {
        action = ACTION_RESET_POSITION
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {}
    }

    fun reload(context: Context) {
      val intent = Intent(context, FloatingPetService::class.java).apply {
        action = ACTION_RELOAD_SETTINGS
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {}
    }

    fun toggleHud(context: Context) {
      val intent = Intent(context, FloatingPetService::class.java).apply {
        action = ACTION_TOGGLE_HUD
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {}
    }
  }
}
