package com.domotica.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.domotica.app.databinding.ActivityMainBinding
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var networkMonitor: NetworkMonitor

    private var activeTargetUrl: String? = null
    private var isCurrentlyLocalWifi: Boolean? = null
    private var vpnProbeJob: Job? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val VPN_PROBE_TIMEOUT_MS = 20000L // Maximum 20 seconds waiting for VPN tunnel
        private const val VPN_PROBE_INTERVAL_MS = 500L  // Check every 500ms
    }

    // Permission Launcher for Location, Wi-Fi, and Notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        networkMonitor.checkNetworkState(prefsManager.localSsid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefsManager = PreferencesManager(this)

        // Check if first run; if so, open SettingsActivity
        if (prefsManager.isFirstRun) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupUIListeners()
        setupBackPressedHandler()
        fetchFcmTokenAndSubscribeTopics()
        startGotifyServiceIfNeeded()

        networkMonitor = NetworkMonitor(this) { isLocalWifi, _ ->
            runOnUiThread {
                handleNetworkStateChange(isLocalWifi)
            }
        }

        checkAndRequestPermissions()
        requestBatteryOptimizationExemption()
    }

    private fun startGotifyServiceIfNeeded() {
        if (prefsManager.gotifyClientToken.isNotBlank()) {
            GotifyNotificationService.startService(this)
        }
    }

    private fun fetchFcmTokenAndSubscribeTopics() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Device Token: $token")
                    if (!token.isNullOrEmpty()) {
                        prefsManager.fcmToken = token
                    }
                } else {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                }
            }

            // Subscribe to "todos" topic for all household devices
            FirebaseMessaging.getInstance().subscribeToTopic("todos").addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed successfully to FCM topic: 'todos'")
                }
            }

            // Subscribe to device-specific topic e.g. "dispositivo_movil_alberto"
            val deviceTopic = "dispositivo_${prefsManager.deviceName}"
            FirebaseMessaging.getInstance().subscribeToTopic(deviceTopic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed successfully to FCM topic: '$deviceTopic'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase not yet configured with google-services.json", e)
        }
    }

    /**
     * Asks the system to whitelist this app from battery optimization so the
     * Gotify foreground service is never killed by aggressive OEM battery savers
     * (Samsung, Xiaomi MIUI, Huawei EMUI, OnePlus, etc.).
     * Only shown once — Android remembers the user's choice.
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open battery optimization settings", e)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = binding.webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    binding.progressIndicator.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                } else {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.progressIndicator.progress = newProgress
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.errorContainer.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.errorContainer.visibility = View.VISIBLE
                    val failingUrl = request.url?.toString() ?: ""
                    binding.tvErrorDetails.text = getString(R.string.error_webview_msg) + "\n(" + failingUrl + ")"
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    return false
                }
            }
        }
    }

    private fun setupUIListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }

        binding.btnRetry.setOnClickListener {
            binding.errorContainer.visibility = View.GONE
            networkMonitor.checkNetworkState(prefsManager.localSsid)
        }

        binding.btnSettingsFromError.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleNetworkStateChange(isLocalWifi: Boolean) {
        val targetUrl = if (isLocalWifi) prefsManager.localUrl else prefsManager.vpnUrl

        if (isCurrentlyLocalWifi == isLocalWifi && activeTargetUrl == targetUrl) {
            return
        }

        isCurrentlyLocalWifi = isLocalWifi
        activeTargetUrl = targetUrl

        vpnProbeJob?.cancel()

        if (isLocalWifi) {
            // Local Wi-Fi connected -> Disconnect VPN & load local URL immediately
            TailscaleManager.disconnectVpn(this)
            binding.vpnLoadingContainer.visibility = View.GONE
            binding.webView.loadUrl(targetUrl)
        } else {
            // VPN mode -> Broadcast CONNECT_VPN and wait until Tailscale tunnel is reachable
            TailscaleManager.connectVpn(this)
            awaitVpnTunnelAndLoad(targetUrl)
        }
    }

    private fun awaitVpnTunnelAndLoad(targetUrl: String) {
        binding.vpnLoadingContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE

        vpnProbeJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            var isReachable = false

            Log.d(TAG, "Probing Tailscale VPN tunnel reachability for target URL: $targetUrl")

            while (System.currentTimeMillis() - startTime < VPN_PROBE_TIMEOUT_MS) {
                isReachable = withContext(Dispatchers.IO) {
                    isHostReachable(targetUrl)
                }

                if (isReachable) {
                    Log.d(TAG, "Tailscale VPN tunnel established and reachable!")
                    break
                }

                delay(VPN_PROBE_INTERVAL_MS)
            }

            binding.vpnLoadingContainer.visibility = View.GONE

            if (isReachable) {
                binding.webView.loadUrl(targetUrl)
            } else {
                Log.e(TAG, "Tailscale VPN tunnel reachability probe timed out")
                binding.errorContainer.visibility = View.VISIBLE
                binding.tvErrorDetails.text = getString(R.string.error_webview_msg) + "\nNo se pudo establecer el túnel VPN a tiempo."
            }
        }
    }

    private fun isHostReachable(urlStr: String): Boolean {
        return try {
            val uri = Uri.parse(urlStr)
            val host = uri.host ?: return false
            var port = uri.port
            if (port == -1) {
                port = if (uri.scheme?.lowercase() == "https") 443 else 80
            }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1200)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!prefsManager.isFirstRun) {
            networkMonitor.startMonitoring(prefsManager.localSsid)
        }
        startGotifyServiceIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        vpnProbeJob?.cancel()
    }

    override fun onStop() {
        super.onStop()
        networkMonitor.stopMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnProbeJob?.cancel()
        TailscaleManager.disconnectVpn(this)
    }
}
