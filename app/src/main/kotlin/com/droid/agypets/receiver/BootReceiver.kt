package com.droid.agypets.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.droid.agypets.data.PetPreferences
import com.droid.agypets.service.FloatingPetService

class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    Log.d(TAG, "Received broadcast action: $action")

    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
      action == "android.intent.action.QUICKBOOT_POWERON" ||
      action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
      action == Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      val prefs = PetPreferences(context)
      val canDraw = Settings.canDrawOverlays(context)

      // Start if overlay permission is granted and either startOnBoot is true or pet was previously enabled
      if (canDraw && prefs.startOnBoot && prefs.isPetEnabled) {
        Log.i(TAG, "Auto-starting FloatingPetService on system boot")
        try {
          FloatingPetService.start(context)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to start FloatingPetService from BootReceiver", e)
        }
      } else {
        Log.d(TAG, "Auto-start skipped (canDraw=$canDraw, startOnBoot=${prefs.startOnBoot}, isPetEnabled=${prefs.isPetEnabled})")
      }
    }
  }

  companion object {
    private const val TAG = "BootReceiver"
  }
}
