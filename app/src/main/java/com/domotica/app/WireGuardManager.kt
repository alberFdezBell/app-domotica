package com.domotica.app

import android.content.Context
import android.util.Log

/**
 * High-level manager for the embedded WireGuard VPN tunnel.
 * Replaces Tailscale completely. Connects to home LAN via WireGuard,
 * allowing the app to use the exact same local IP (e.g. http://192.168.0.24:8123)
 * both on home Wi-Fi and over mobile data.
 */
object WireGuardManager {

    private const val TAG = "WireGuardManager"

    /**
     * Establishes the WireGuard VPN tunnel in-process.
     */
    fun connectVpn(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            Log.d(TAG, "Connecting embedded WireGuard VPN tunnel...")
            WireGuardTunnelEngine.startTunnel(context, onComplete = onComplete)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WireGuard VPN", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * Ensures WireGuard VPN connection is active without interrupting user UI.
     */
    fun wakeAndConnectVpn(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            Log.d(TAG, "Waking/Verifying WireGuard VPN connection...")
            connectVpn(context, onComplete)
        } catch (e: Exception) {
            Log.e(TAG, "Error waking WireGuard VPN", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * Disconnects the embedded WireGuard VPN tunnel.
     */
    fun disconnectVpn(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            Log.d(TAG, "Disconnecting embedded WireGuard VPN tunnel...")
            WireGuardTunnelEngine.stopTunnel(context, onComplete)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WireGuard VPN", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * Returns true if WireGuard VPN tunnel is currently active.
     */
    fun isConnected(context: Context): Boolean {
        return WireGuardTunnelEngine.isConnected(context)
    }

    /**
     * Checks if a valid WireGuard configuration is stored.
     */
    fun hasConfig(context: Context): Boolean {
        return WireGuardTunnelEngine.hasConfig(context)
    }
}
