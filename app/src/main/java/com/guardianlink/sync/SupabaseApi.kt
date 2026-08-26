package com.guardianlink.sync

import com.guardianlink.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteCommand(val id: String, val type: String, val expiresAtEpochMs: Long?)

/** Small dependency-free REST client. The app uses its public anon key plus the child device JWT. */
class SupabaseApi(private val deviceId: String, private val accessToken: String) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val enabled = baseUrl.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun fetchActivePolicy(): String? {
        if (!enabled) return null
        val records = get("/rest/v1/device_policies?device_id=eq.$deviceId&active=eq.true&order=version.desc&limit=1&select=policy")
        return records?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getJSONObject("policy")?.toString()
    }

    fun fetchLatestCommand(): RemoteCommand? {
        if (!enabled) return null
        val records = get("/rest/v1/parent_commands?device_id=eq.$deviceId&order=created_at.desc&limit=1&select=id,command_type,expires_at") ?: return null
        if (records.length() == 0) return null
        val command = records.getJSONObject(0)
        val expiry = command.optString("expires_at").takeIf { it.isNotBlank() }?.let { java.time.Instant.parse(it).toEpochMilli() }
        return RemoteCommand(command.getString("id"), command.getString("command_type"), expiry)
    }

    fun acknowledge(commandId: String, status: String) {
        if (!enabled) return
        post("/rest/v1/device_acknowledgements", JSONObject().apply {
            put("command_id", commandId)
            put("device_id", deviceId)
            put("status", status)
        })
    }

    private fun get(path: String): JSONArray? = request("GET", path, null)?.let(::JSONArray)
    private fun post(path: String, body: JSONObject) { request("POST", path, body.toString()) }

    private fun request(method: String, path: String, body: String?): String? = runCatching {
        (URL(baseUrl + path).openConnection() as HttpURLConnection).run {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            if (body != null) { doOutput = true; outputStream.bufferedWriter().use { it.write(body) } }
            if (responseCode !in 200..299) return@run null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()
}
