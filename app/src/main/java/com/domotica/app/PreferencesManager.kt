package com.domotica.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "domotica_preferences"
        private const val KEY_LOCAL_URL = "key_local_url"
        private const val KEY_VPN_URL = "key_vpn_url"
        private const val KEY_LOCAL_SSID = "key_local_ssid"
        private const val KEY_DEVICE_NAME = "key_device_name"
        private const val KEY_FIRST_RUN = "key_first_run"
        private const val KEY_FCM_TOKEN = "key_fcm_token"

        private const val KEY_GOTIFY_URL = "key_gotify_url"
        private const val KEY_GOTIFY_USER = "key_gotify_user"
        private const val KEY_GOTIFY_PASS = "key_gotify_pass"
        private const val KEY_GOTIFY_TOKEN = "key_gotify_token"
        private const val KEY_LAST_GOTIFY_MSG_ID = "key_last_gotify_msg_id"
        private const val KEY_TAILSCALE_AUTH_KEY = "key_tailscale_auth_key"

        const val DEFAULT_LOCAL_URL = "http://192.168.0.24:8123"
        const val DEFAULT_VPN_URL = "http://100.96.82.4:8123"
        const val DEFAULT_LOCAL_SSID = "Livebox6-0F37"
        const val DEFAULT_GOTIFY_URL = "https://gotify.aferbel.es"
    }

    var localUrl: String
        get() = prefs.getString(KEY_LOCAL_URL, DEFAULT_LOCAL_URL) ?: DEFAULT_LOCAL_URL
        set(value) = prefs.edit().putString(KEY_LOCAL_URL, value.trim()).apply()

    var vpnUrl: String
        get() = prefs.getString(KEY_VPN_URL, DEFAULT_VPN_URL) ?: DEFAULT_VPN_URL
        set(value) = prefs.edit().putString(KEY_VPN_URL, value.trim()).apply()

    var localSsid: String
        get() = prefs.getString(KEY_LOCAL_SSID, DEFAULT_LOCAL_SSID) ?: DEFAULT_LOCAL_SSID
        set(value) = prefs.edit().putString(KEY_LOCAL_SSID, sanitizeSsid(value)).apply()

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, defaultDeviceName()) ?: defaultDeviceName()
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, sanitizeDeviceTopic(value)).apply()

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    var gotifyUrl: String
        get() = prefs.getString(KEY_GOTIFY_URL, DEFAULT_GOTIFY_URL) ?: DEFAULT_GOTIFY_URL
        set(value) = prefs.edit().putString(KEY_GOTIFY_URL, cleanUrl(value)).apply()

    var gotifyUsername: String
        get() = prefs.getString(KEY_GOTIFY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOTIFY_USER, value.trim()).apply()

    var gotifyPassword: String
        get() = prefs.getString(KEY_GOTIFY_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOTIFY_PASS, value).apply()

    var gotifyClientToken: String
        get() = prefs.getString(KEY_GOTIFY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOTIFY_TOKEN, value.trim()).apply()

    var lastGotifyMessageId: Long
        get() = prefs.getLong(KEY_LAST_GOTIFY_MSG_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GOTIFY_MSG_ID, value).apply()

    var tailscaleAuthKey: String
        get() = prefs.getString(KEY_TAILSCALE_AUTH_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TAILSCALE_AUTH_KEY, value.trim()).apply()

    fun saveSettings(
        localUrl: String,
        vpnUrl: String,
        localSsid: String,
        deviceName: String,
        gotifyUrl: String = DEFAULT_GOTIFY_URL,
        gotifyUser: String = "",
        gotifyPass: String = "",
        tailscaleAuthKey: String = ""
    ) {
        prefs.edit()
            .putString(KEY_LOCAL_URL, localUrl.trim())
            .putString(KEY_VPN_URL, vpnUrl.trim())
            .putString(KEY_LOCAL_SSID, sanitizeSsid(localSsid))
            .putString(KEY_DEVICE_NAME, sanitizeDeviceTopic(deviceName))
            .putString(KEY_GOTIFY_URL, cleanUrl(gotifyUrl))
            .putString(KEY_GOTIFY_USER, gotifyUser.trim())
            .putString(KEY_GOTIFY_PASS, gotifyPass)
            .putString(KEY_TAILSCALE_AUTH_KEY, tailscaleAuthKey.trim())
            .putBoolean(KEY_FIRST_RUN, false)
            .apply()
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL.lowercase().replace("[^a-z0-9_]".toRegex(), "_")
        return if (model.isBlank()) "dispositivo_movil" else "movil_$model"
    }

    fun sanitizeDeviceTopic(name: String): String {
        var clean = name.trim().lowercase()
            .replace(" ", "_")
            .replace("[^a-z0-9_]".toRegex(), "")
        if (clean.isBlank()) clean = "dispositivo_movil"
        return clean
    }

    private fun sanitizeSsid(ssid: String): String {
        var clean = ssid.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length >= 2) {
            clean = clean.substring(1, clean.length - 1)
        }
        return clean
    }
}
