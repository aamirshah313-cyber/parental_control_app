package com.guardianlink.sync

import android.util.Base64
import com.guardianlink.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RefreshedTokens(val accessToken: String, val refreshToken: String)

/** Refreshes an expiring Supabase access token before a background control path loses authorisation. */
object SupabaseAuth {
    fun expiresWithin(accessToken: String, seconds: Long = 300): Boolean = runCatching {
        val payload = accessToken.split('.').getOrNull(1) ?: return false
        val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        val expiresAt = json.optLong("exp", Long.MAX_VALUE)
        System.currentTimeMillis() >= (expiresAt - seconds) * 1_000
    }.getOrDefault(false)

    fun refresh(refreshToken: String): RefreshedTokens? = runCatching {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_ANON_KEY
        require(baseUrl.isNotBlank() && apiKey.isNotBlank())
        val response = (URL("$baseUrl/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection).run {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("apikey", apiKey); setRequestProperty("Content-Type", "application/json")
            outputStream.bufferedWriter().use { it.write(JSONObject().put("refresh_token", refreshToken).toString()) }
            if (responseCode !in 200..299) error("Token refresh failed")
            inputStream.bufferedReader().use { it.readText() }
        }
        val data = JSONObject(response)
        RefreshedTokens(data.getString("access_token"), data.optString("refresh_token", refreshToken))
    }.getOrNull()
}
