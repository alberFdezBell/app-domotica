package com.domotica.app

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GotifyClientHelper {

    private const val TAG = "GotifyClientHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Authenticates with Gotify server via Basic Auth (POST /client) and returns the client token.
     */
    fun authenticateAndGetClientToken(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String
    ): String? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return null
        }

        val baseUrl = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
        val endpoint = "$baseUrl/client"
        val credential = Credentials.basic(username, password)

        val jsonPayload = JSONObject().apply {
            put("name", "Domotica App ($deviceName)")
        }.toString()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", credential)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val token = json.optString("token", "")
                    Log.d(TAG, "Gotify Authentication successful! Token obtained: $token")
                    if (token.isNotBlank()) token else null
                } else {
                    Log.e(TAG, "Gotify Authentication failed. Response code: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Gotify server at $endpoint", e)
            null
        }
    }
}
