package com.domotica.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered with action: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            val prefs = PreferencesManager(context)
            if (prefs.gotifyClientToken.isNotBlank()) {
                Log.d(TAG, "Starting GotifyNotificationService on Boot...")
                GotifyNotificationService.startService(context)
            }
        }
    }
}
