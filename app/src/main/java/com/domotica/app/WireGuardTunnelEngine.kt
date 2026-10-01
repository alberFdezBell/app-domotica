package com.domotica.app

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Manages the embedded WireGuard VPN engine using official WireGuard Android GoBackend.
 * Stores and runs WireGuard tunnel configurations exported from Wg-Easy or standard QR codes.
 */
object WireGuardTunnelEngine {

    private const val TAG = "WireGuardEngine"

    private var backend: Backend? = null
    private val tunnel = SimpleTunnel("domotica_wg")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isTunnelUp = false

    private class SimpleTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "WireGuard tunnel state changed to: $newState")
        }
    }

    @Synchronized
    private fun getBackend(context: Context): Backend {
        if (backend == null) {
            backend = GoBackend(context.applicationContext)
        }
        return backend!!
    }

    /**
     * Checks if a valid WireGuard configuration string is stored or provided.
     */
    fun hasConfig(context: Context): Boolean {
        val prefs = PreferencesManager(context)
        return prefs.wireguardConfig.isNotBlank()
    }

    /**
     * Starts the embedded WireGuard VPN tunnel.
     */
    fun startTunnel(context: Context, configText: String? = null, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            try {
                val prefs = PreferencesManager(context)
                val rawConfig = configText ?: prefs.wireguardConfig

                if (rawConfig.isBlank()) {
                    Log.w(TAG, "Cannot start WireGuard tunnel: No WireGuard configuration provided.")
                    isTunnelUp = false
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                    return@launch
                }

                val parsedConfig = Config.parse(ByteArrayInputStream(rawConfig.toByteArray()))
                val activeBackend = getBackend(context)

                Log.i(TAG, "Bringing WireGuard tunnel UP...")
                activeBackend.setState(tunnel, Tunnel.State.UP, parsedConfig)
                isTunnelUp = true

                Log.i(TAG, "WireGuard tunnel is UP and active.")
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting embedded WireGuard tunnel", e)
                isTunnelUp = false
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    /**
     * Stops the embedded WireGuard VPN tunnel.
     */
    fun stopTunnel(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            try {
                val activeBackend = getBackend(context)
                Log.i(TAG, "Bringing WireGuard tunnel DOWN...")
                activeBackend.setState(tunnel, Tunnel.State.DOWN, null)
                isTunnelUp = false
                Log.i(TAG, "WireGuard tunnel stopped successfully.")
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping WireGuard tunnel", e)
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    /**
     * Returns true if the WireGuard VPN tunnel is currently active.
     */
    fun isConnected(context: Context): Boolean {
        return try {
            val currentState = getBackend(context).getState(tunnel)
            currentState == Tunnel.State.UP
        } catch (e: Exception) {
            isTunnelUp
        }
    }
}
