package com.domotica.app

import android.content.Context
import android.content.Intent
import android.util.Log

object TailscaleManager {

    private const val TAG = "TailscaleManager"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    private const val ACTION_CONNECT_VPN = "com.tailscale.ipn.CONNECT_VPN"
    private const val ACTION_DISCONNECT_VPN = "com.tailscale.ipn.DISCONNECT_VPN"

    /**
     * Sends a background broadcast intent to Tailscale app to establish the VPN connection.
     */
    fun connectVpn(context: Context) {
        try {
            val intent = Intent(ACTION_CONNECT_VPN).apply {
                setPackage(TAILSCALE_PACKAGE)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Sent CONNECT_VPN broadcast intent to Tailscale ($TAILSCALE_PACKAGE)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending CONNECT_VPN intent to Tailscale", e)
        }
    }

    /**
     * Sends a background broadcast intent to Tailscale app to disconnect the VPN connection.
     */
    fun disconnectVpn(context: Context) {
        try {
            val intent = Intent(ACTION_DISCONNECT_VPN).apply {
                setPackage(TAILSCALE_PACKAGE)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Sent DISCONNECT_VPN broadcast intent to Tailscale ($TAILSCALE_PACKAGE)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DISCONNECT_VPN intent to Tailscale", e)
        }
    }
}
