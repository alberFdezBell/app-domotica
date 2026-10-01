package com.domotica.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
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

    // Pending URL to load once VPN consent is granted
    private var pendingVpnUrl: String? = null

    // Secret gesture variables (Hold bottom-left + 3 taps bottom-right)
    private var isHoldingBottomLeft = false
    private var bottomRightTapCount = 0
    private var lastGestureTapTime = 0L

    companion object {
        private const val TAG = "MainActivity"
        private const val VPN_PROBE_TIMEOUT_MS = 25000L // Maximum 25 seconds waiting for VPN tunnel
        private const val VPN_PROBE_INTERVAL_MS = 500L  // Check every 500ms
    }

    // Launcher for Android VPN consent dialog (required before creating a VPN tunnel)
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "VPN consent granted by user — starting WireGuard tunnel")
            val url = pendingVpnUrl ?: prefsManager.localUrl
            WireGuardManager.connectVpn(this)
            awaitVpnTunnelAndLoad(url)
        } else {
            Log.w(TAG, "VPN consent denied by user")
            binding.vpnLoadingContainer.visibility = View.GONE
            binding.errorContainer.visibility = View.VISIBLE
            binding.tvErrorDetails.text = "El usuario rechazó el permiso de VPN. El acceso remoto no está disponible."
        }
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
        // Disable pull-down swipe-to-refresh to prevent accidental page reloads
        binding.swipeRefreshLayout.isEnabled = false

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

    /**
     * Secret gesture detector: Hold bottom-left corner (x < 35%, y > 65%)
     * and tap 3 times in the bottom-right corner (x > 65%, y > 65%) to open Settings.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels

        if (width > 0 && height > 0) {
            val pointerCount = ev.pointerCount

            var foundBottomLeft = false
            for (i in 0 until pointerCount) {
                val px = ev.getX(i)
                val py = ev.getY(i)
                if (px < width * 0.35f && py > height * 0.65f) {
                    foundBottomLeft = true
                    break
                }
            }

            isHoldingBottomLeft = foundBottomLeft

            val action = ev.actionMasked
            if (isHoldingBottomLeft && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
                val actionIndex = ev.actionIndex
                val tapX = ev.getX(actionIndex)
                val tapY = ev.getY(actionIndex)

                if (tapX > width * 0.65f && tapY > height * 0.65f) {
                    val now = System.currentTimeMillis()
                    if (now - lastGestureTapTime > 1500) {
                        bottomRightTapCount = 0
                    }
                    lastGestureTapTime = now
                    bottomRightTapCount++

                    Log.d(TAG, "Secret gesture tap $bottomRightTapCount/3 in bottom-right quadrant")

                    if (bottomRightTapCount >= 3) {
                        bottomRightTapCount = 0
                        isHoldingBottomLeft = false
                        Log.i(TAG, "Secret gesture recognized! Opening SettingsActivity...")

                        vibrateDevice()

                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        return true
                    }
                }
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (pointerCount <= 1) {
                    bottomRightTapCount = 0
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun vibrateDevice() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not trigger vibration feedback", e)
        }
    }

    private fun handleNetworkStateChange(isLocalWifi: Boolean) {
        val targetUrl = prefsManager.localUrl

        if (isCurrentlyLocalWifi == isLocalWifi && activeTargetUrl == targetUrl) {
            return
        }

        isCurrentlyLocalWifi = isLocalWifi
        activeTargetUrl = targetUrl

        vpnProbeJob?.cancel()

        if (isLocalWifi) {
            // Local Wi-Fi connected -> Disconnect WireGuard VPN & load local URL directly
            WireGuardManager.disconnectVpn(this)
            binding.vpnLoadingContainer.visibility = View.GONE
            binding.webView.loadUrl(targetUrl)
        } else {
            // Mobile Data / External network -> request VPN consent if needed, then connect WireGuard
            pendingVpnUrl = targetUrl
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                Log.d(TAG, "Requesting VPN user consent via system dialog...")
                binding.vpnLoadingContainer.visibility = View.VISIBLE
                binding.errorContainer.visibility = View.GONE
                vpnConsentLauncher.launch(vpnIntent)
            } else {
                Log.d(TAG, "VPN already authorized. Connecting embedded WireGuard tunnel...")
                WireGuardManager.connectVpn(this)
                awaitVpnTunnelAndLoad(targetUrl)
            }
        }
    }

    private fun awaitVpnTunnelAndLoad(targetUrl: String) {
        binding.vpnLoadingContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE

        vpnProbeJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            var isReachable = false
            var wakeAttempted = false

            Log.d(TAG, "Probing WireGuard VPN tunnel reachability for Home Assistant target: $targetUrl")

            while (System.currentTimeMillis() - startTime < VPN_PROBE_TIMEOUT_MS) {
                isReachable = withContext(Dispatchers.IO) {
                    isHostReachable(targetUrl)
                }

                if (isReachable) {
                    Log.d(TAG, "WireGuard VPN tunnel established and target is reachable!")
                    break
                }

                if (!wakeAttempted && System.currentTimeMillis() - startTime >= 1500L) {
                    wakeAttempted = true
                    Log.w(TAG, "WireGuard tunnel unreachable after 1.5s — verifying WireGuard engine state")
                    WireGuardManager.wakeAndConnectVpn(this@MainActivity)
                }

                delay(VPN_PROBE_INTERVAL_MS)
            }

            binding.vpnLoadingContainer.visibility = View.GONE

            if (isReachable) {
                binding.webView.loadUrl(targetUrl)
            } else {
                Log.e(TAG, "WireGuard VPN tunnel reachability probe timed out")
                binding.errorContainer.visibility = View.VISIBLE

                val hasWgConfig = WireGuardManager.hasConfig(this@MainActivity)
                val hintMsg = if (!hasWgConfig) {
                    "\n\n💡 Sugerencia para conectar fuera de casa:\nAbre Ajustes y escanea el código QR de tu servidor Wg-Easy."
                } else {
                    "\n\n💡 Comprueba que tu servidor Wg-Easy y la IP ($targetUrl) estén activos."
                }

                binding.tvErrorDetails.text = getString(R.string.error_webview_msg) + "\nNo se pudo establecer el túnel WireGuard a tiempo." + hintMsg
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
        WireGuardManager.disconnectVpn(this)
    }
}
