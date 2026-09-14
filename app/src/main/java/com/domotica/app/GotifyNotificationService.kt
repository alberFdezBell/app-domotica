package com.domotica.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GotifyNotificationService : Service() {

    private var webSocket: WebSocket? = null
    private var isServiceRunning = false
    private var reconnectAttempts = 0
    private var prefsManager: PreferencesManager? = null

    // Client for the persistent WebSocket (infinite read timeout)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    // Client for short REST calls (fetching missed messages)
    private val restClient = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GotifyService"

        const val SERVICE_CHANNEL_ID = "domotica_gotify_service_channel"
        const val SERVICE_NOTIFICATION_ID = 1001

        const val MESSAGE_CHANNEL_ID = "domotica_gotify_messages_channel"
        const val MESSAGE_CHANNEL_NAME = "Notificaciones Gotify"

        fun startService(context: Context) {
            val intent = Intent(context, GotifyNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GotifyNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(applicationContext)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (e: Exception) {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }

        if (!isServiceRunning) {
            isServiceRunning = true
            connectWebSocket()
        }

        return START_STICKY
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Foreground Service Low-Priority Channel
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Servicio de Alertas Domótica",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Mantiene activa la recepción de notificaciones 24/7"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // High-Priority Push Messages Channel
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                MESSAGE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alertas de Gotify y Home Assistant"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_house)
            .setContentTitle("Domótica")
            .setContentText("Servicio de notificaciones 24/7 activo")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @Synchronized
    private fun connectWebSocket() {
        val prefs = prefsManager ?: return
        val serverUrl = prefs.gotifyUrl
        val clientToken = prefs.gotifyClientToken

        if (serverUrl.isBlank() || clientToken.isBlank()) {
            Log.w(TAG, "Gotify URL or Client Token missing. Cannot start WebSocket.")
            return
        }

        val wsUrl = buildWebSocketUrl(serverUrl, clientToken)
        Log.d(TAG, "Connecting Gotify WebSocket: $wsUrl")

        val request = Request.Builder().url(wsUrl).build()

        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gotify WebSocket Connected Successfully!")
                reconnectAttempts = 0
                // Fetch any messages that arrived while the app was closed
                thread { fetchMissedMessages() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Gotify WebSocket Message Received: $text")
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gotify WebSocket Failure: ${t.message}", t)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gotify WebSocket Closed: $reason ($code)")
                scheduleReconnect()
            }
        })
    }

    private fun buildWebSocketUrl(serverUrl: String, token: String): String {
        var base = serverUrl.trim()
        if (base.endsWith("/")) {
            base = base.substring(0, base.length - 1)
        }
        val wsScheme = if (base.startsWith("https://", ignoreCase = true)) {
            "wss://" + base.substring(8)
        } else if (base.startsWith("http://", ignoreCase = true)) {
            "ws://" + base.substring(7)
        } else {
            "wss://$base"
        }
        return "$wsScheme/stream?token=$token"
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val id = json.optLong("id", -1L)
            val title = json.optString("title", getString(R.string.app_name))
            val message = json.optString("message", "")
            val priority = json.optInt("priority", 5)

            showNotification(title, message, priority)

            // Track highest seen ID so we can fetch missed messages after reconnect
            val prefs = prefsManager ?: return
            if (id > prefs.lastGotifyMessageId) {
                prefs.lastGotifyMessageId = id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gotify JSON message", e)
        }
    }

    private fun showNotification(title: String, message: String, priority: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_house)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(if (priority >= 5) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Called once the WebSocket connects. Fetches any messages that arrived
     * while the service was stopped (app killed, phone off, etc.) and shows
     * them as notifications so none are ever lost.
     */
    private fun fetchMissedMessages() {
        val prefs = prefsManager ?: return
        val serverUrl = prefs.gotifyUrl
        val clientToken = prefs.gotifyClientToken
        val sinceId = prefs.lastGotifyMessageId

        if (serverUrl.isBlank() || clientToken.isBlank()) return

        val base = serverUrl.trim().trimEnd('/')
        val url = "$base/message?since=$sinceId&limit=100"

        Log.d(TAG, "Fetching missed Gotify messages since id=$sinceId from $url")

        try {
            val request = Request.Builder()
                .url(url)
                .header("X-Gotify-Key", clientToken)
                .get()
                .build()

            restClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "fetchMissedMessages HTTP ${response.code}")
                    return
                }

                val body = response.body?.string() ?: return
                val root = JSONObject(body)
                val messages: JSONArray = root.optJSONArray("messages") ?: return

                var maxId = sinceId

                for (i in 0 until messages.length()) {
                    val msg = messages.getJSONObject(i)
                    val id = msg.optLong("id", -1L)
                    if (id <= sinceId) continue          // already seen

                    val title = msg.optString("title", getString(R.string.app_name))
                    val message = msg.optString("message", "")
                    val priority = msg.optInt("priority", 5)

                    showNotification(title, message, priority)

                    if (id > maxId) maxId = id
                }

                if (maxId > sinceId) {
                    prefs.lastGotifyMessageId = maxId
                    Log.d(TAG, "Updated lastGotifyMessageId to $maxId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching missed Gotify messages", e)
        }
    }

    private fun scheduleReconnect() {
        if (!isServiceRunning) return

        reconnectAttempts++
        val backoffDelay = (Math.min(reconnectAttempts * 3, 30)) * 1000L

        Log.d(TAG, "Scheduling Gotify WebSocket reconnect in ${backoffDelay / 1000}s (Attempt #$reconnectAttempts)...")

        thread {
            try {
                Thread.sleep(backoffDelay)
                if (isServiceRunning) {
                    connectWebSocket()
                }
            } catch (e: InterruptedException) {
                // Ignore
            }
        }
    }

    /**
     * Called when the user swipes the app away from the recent-apps list.
     * Schedules an AlarmManager broadcast 3 seconds later so the service
     * restarts itself automatically and never misses Gotify notifications.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App removed from recents — scheduling service restart in 3 s")

        val restartIntent = Intent(this, RestartServiceReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAndAllowWhileIdle works from API 23+ without needing SCHEDULE_EXACT_ALARM permission
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3_000L,
            pendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        webSocket?.cancel()
        webSocket = null
        Log.d(TAG, "GotifyNotificationService Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
