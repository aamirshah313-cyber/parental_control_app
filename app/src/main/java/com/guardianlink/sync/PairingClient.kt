package com.guardianlink.sync

import com.guardianlink.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PairingResult(val deviceId: String, val accessToken: String, val refreshToken: String)

/** Claims a short-lived pairing code; the raw code is never stored on the device after setup. */
class PairingClient {
    fun claim(pairCode: String, deviceName: String): PairingResult? = runCatching {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(baseUrl.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) { "Supabase is not configured" }
        val response = (URL("$baseUrl/functions/v1/claim-child-device").openConnection() as HttpURLConnection).run {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
            outputStream.bufferedWriter().use { it.write(JSONObject().apply {
                put("pair_code", pairCode.trim())
                put("device_name", deviceName.trim())
            }.toString()) }
            if (responseCode !in 200..299) error("Pairing code was rejected")
            inputStream.bufferedReader().use { it.readText() }
        }
        val result = JSONObject(response)
        PairingResult(result.getString("device_id"), result.getString("access_token"), result.getString("refresh_token"))
    }.getOrNull()
}
