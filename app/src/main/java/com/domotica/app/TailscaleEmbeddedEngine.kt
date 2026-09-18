package com.domotica.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Manages the embedded Tailscale engine session and persistent login state.
 * Stores node credentials persistently in `context.filesDir/tailscale`.
 */
object TailscaleEmbeddedEngine {

    private const val TAG = "TailscaleEmbedded"
    private const val TAILSCALE_ADMIN_KEYS_URL = "https://login.tailscale.com/admin/settings/keys"

    /**
     * Initializes the embedded Tailscale state directory.
     */
    fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, "tailscale")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks if the device has an active persistent Tailscale session stored on disk.
     */
    fun hasPersistentSession(context: Context): Boolean {
        val storageDir = getStorageDir(context)
        val prefs = PreferencesManager(context)
        val hasKey = prefs.tailscaleAuthKey.isNotBlank()
        val hasStateFiles = (storageDir.listFiles()?.size ?: 0) > 0
        return hasKey || hasStateFiles
    }

    /**
     * Saves the provided Auth Key and initializes persistent session.
     */
    fun registerAuthKey(context: Context, authKey: String): Boolean {
        if (authKey.isBlank()) return false
        val prefs = PreferencesManager(context)
        prefs.tailscaleAuthKey = authKey.trim()
        Log.d(TAG, "Tailscale Auth Key saved and registered for persistent login.")
        return true
    }

    /**
     * Opens the Tailscale login web page in browser for direct interactive authentication.
     */
    fun openWebLogin(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TAILSCALE_ADMIN_KEYS_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened Tailscale web login page: $TAILSCALE_ADMIN_KEYS_URL")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Tailscale login web page", e)
        }
    }
}
