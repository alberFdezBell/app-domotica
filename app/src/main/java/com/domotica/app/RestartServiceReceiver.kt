package com.domotica.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the AlarmManager broadcast scheduled in GotifyNotificationService.onTaskRemoved()
 * and restarts the Gotify foreground service so notifications keep arriving even after
 * the user swipes the app away from recents.
 */
class RestartServiceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartServiceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Restart broadcast received — restarting GotifyNotificationService")
        val prefs = PreferencesManager(context)
        if (prefs.gotifyClientToken.isNotBlank()) {
            GotifyNotificationService.startService(context)
        }
    }
}
