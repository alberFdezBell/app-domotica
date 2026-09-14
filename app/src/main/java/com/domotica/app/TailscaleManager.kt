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
     * Forces Tailscale to wake up if Android killed its process due to battery optimization.
     * Launches Tailscale process in background and re-sends the connect broadcast,
     * ensuring MainActivity stays on top.
     */
    fun wakeAndConnectVpn(context: Context) {
        try {
            Log.d(TAG, "Waking up Tailscale application process...")
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(TAILSCALE_PACKAGE)

            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                context.startActivity(launchIntent)

                // Immediately bring MainActivity back to front
                if (context is MainActivity) {
                    val bringSelf = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(bringSelf)
                }

                // Send broadcast intent again to ensure tunnel state connects
                connectVpn(context)
            } else {
                Log.w(TAG, "Tailscale package ($TAILSCALE_PACKAGE) not found on device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error waking Tailscale app", e)
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
