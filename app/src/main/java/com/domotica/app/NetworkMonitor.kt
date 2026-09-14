package com.domotica.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

class NetworkMonitor(
    private val context: Context,
    private val onStateChanged: (isLocalWifi: Boolean, currentSsid: String?) -> Unit
) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    fun startMonitoring(targetSsid: String) {
        stopMonitoring()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkNetworkState(targetSsid)
            }

            override fun onLost(network: Network) {
                checkNetworkState(targetSsid)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                checkNetworkState(targetSsid)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            // Immediate check on setup
            checkNetworkState(targetSsid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
            networkCallback = null
        }
    }

    fun checkNetworkState(targetSsid: String) {
        val currentSsid = getCurrentWifiSsid()
        val isTargetWifi = isConnectedToTargetSsid(currentSsid, targetSsid)
        
        Log.d(TAG, "Current SSID: '$currentSsid' | Target SSID: '$targetSsid' | Is Target Wi-Fi: $isTargetWifi")
        onStateChanged(isTargetWifi, currentSsid)
    }

    @Suppress("DEPRECATION")
    private fun getCurrentWifiSsid(): String? {
        try {
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null

            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transportInfo = capabilities.transportInfo
                if (transportInfo is WifiInfo) {
                    val ssid = transportInfo.ssid
                    if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
                        return cleanSsid(ssid)
                    }
                }
            }

            // Fallback for older APIs or legacy WifiManager retrieval
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid
            if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
                return cleanSsid(ssid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Wi-Fi SSID", e)
        }
        return null
    }

    private fun isConnectedToTargetSsid(currentSsid: String?, targetSsid: String): Boolean {
        if (currentSsid.isNullOrBlank()) return false
        val cleanCurrent = cleanSsid(currentSsid)
        val cleanTarget = cleanSsid(targetSsid)
        return cleanCurrent.equals(cleanTarget, ignoreCase = true)
    }

    private fun cleanSsid(ssid: String): String {
        var clean = ssid.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length >= 2) {
            clean = clean.substring(1, clean.length - 1)
        }
        return clean
    }
}
