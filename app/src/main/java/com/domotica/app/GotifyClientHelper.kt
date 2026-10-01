package com.domotica.app

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object GotifyClientHelper {

    private const val TAG = "GotifyClientHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun ensureScheme(url: String): String {
        var clean = url.trim().trimEnd('/')
        if (clean.isNotBlank() && !clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "https://$clean"
        }
        return clean
    }

    /**
     * Authenticates with Gotify server via Basic Auth (POST /client or GET /client fallback)
     * and returns the client token.
     */
    fun authenticateAndGetClientToken(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String
    ): String? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            Log.w(TAG, "Gotify parameters incomplete (serverUrl, username, or password empty).")
            return null
        }

        val baseUrl = ensureScheme(serverUrl)
        val endpoint = "$baseUrl/client"
        val credential = Credentials.basic(username, password, StandardCharsets.UTF_8)
        val clientName = "Domotica App ($deviceName)"

        // 1. Try GET /client with Basic Auth to see if a token already exists for this device name
        try {
            val getRequest = Request.Builder()
                .url(endpoint)
                .header("Authorization", credential)
                .get()
                .build()

            client.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val clientObj = jsonArray.getJSONObject(i)
                        if (clientObj.optString("name") == clientName) {
                            val existingToken = clientObj.optString("token", "")
                            if (existingToken.isNotBlank()) {
                                Log.d(TAG, "Found existing Gotify client token for '$clientName': $existingToken")
                                return existingToken
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET /client search failed; proceeding to POST /client: ${e.message}")
        }

        // 2. If not found, POST /client to create a new client token
        val jsonPayload = JSONObject().apply {
            put("name", clientName)
        }.toString()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val postRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", credential)
            .post(requestBody)
            .build()

        return try {
            client.newCall(postRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val token = json.optString("token", "")
                    Log.d(TAG, "Gotify Authentication successful! Token obtained: $token")
                    if (token.isNotBlank()) token else null
                } else {
                    Log.e(TAG, "Gotify Authentication failed. Response code: ${response.code} (${response.message})")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Gotify server at $endpoint: ${e.message}", e)
            null
        }
    }
}
