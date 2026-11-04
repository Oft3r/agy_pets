package com.droid.agypets.data

import android.content.Context
import android.content.SharedPreferences

class PetPreferences(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("agypets_prefs", Context.MODE_PRIVATE)

  var isPetEnabled: Boolean
    get() = prefs.getBoolean(KEY_PET_ENABLED, false)
    set(value) = prefs.edit().putBoolean(KEY_PET_ENABLED, value).apply()

  var petScale: Float
    get() = prefs.getFloat(KEY_PET_SCALE, 2.0f)
    set(value) = prefs.edit().putFloat(KEY_PET_SCALE, value).apply()

  var speedMultiplier: Float
    get() = prefs.getFloat(KEY_PET_SPEED, 1.2f)
    set(value) = prefs.edit().putFloat(KEY_PET_SPEED, value).apply()

  var zeroGravity: Boolean
    get() = prefs.getBoolean(KEY_ZERO_GRAVITY, true)
    set(value) = prefs.edit().putBoolean(KEY_ZERO_GRAVITY, value).apply()

  var showUsageBadge: Boolean
    get() = prefs.getBoolean(KEY_SHOW_USAGE_BADGE, true)
    set(value) = prefs.edit().putBoolean(KEY_SHOW_USAGE_BADGE, value).apply()

  var contextWindowLimitTokens: Long
    get() = prefs.getLong(KEY_CONTEXT_LIMIT, 200_000L)
    set(value) = prefs.edit().putLong(KEY_CONTEXT_LIMIT, value).apply()

  var liveTrackingEnabled: Boolean
    get() = prefs.getBoolean(KEY_LIVE_TRACKING, true)
    set(value) = prefs.edit().putBoolean(KEY_LIVE_TRACKING, value).apply()

  var startOnBoot: Boolean
    get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
    set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

  var autoShowWithAntigravity: Boolean
    get() = prefs.getBoolean(KEY_AUTO_SHOW_ANTIGRAVITY, true)
    set(value) = prefs.edit().putBoolean(KEY_AUTO_SHOW_ANTIGRAVITY, value).apply()

  companion object {
    private const val KEY_PET_ENABLED = "key_pet_enabled"
    private const val KEY_PET_SCALE = "key_pet_scale"
    private const val KEY_PET_SPEED = "key_pet_speed"
    private const val KEY_ZERO_GRAVITY = "key_zero_gravity"
    private const val KEY_SHOW_USAGE_BADGE = "key_show_usage_badge"
    private const val KEY_CONTEXT_LIMIT = "key_context_limit"
    private const val KEY_LIVE_TRACKING = "key_live_tracking"
    private const val KEY_START_ON_BOOT = "key_start_on_boot"
    private const val KEY_AUTO_SHOW_ANTIGRAVITY = "key_auto_show_antigravity"
  }
}
