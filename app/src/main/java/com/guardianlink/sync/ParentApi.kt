package com.guardianlink.sync

import com.guardianlink.BuildConfig
import com.guardianlink.model.ChildPolicy
import com.guardianlink.policy.PolicyJson
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class FamilyRecord(val id: String, val name: String)
data class DeviceRecord(val id: String, val displayName: String, val lastSeenAt: String?)
data class PairingCode(val code: String, val expiresInSeconds: Int)
data class LocationRecord(val latitude: Double, val longitude: Double, val accuracyMeters: Float?, val recordedAt: String)
data class VersionedPolicy(val version: Int, val policy: ChildPolicy)
data class SosAlert(val id: String, val deviceId: String, val message: String, val createdAt: String)
data class DeviceEvent(val eventType: String, val details: JSONObject, val createdAt: String)
data class CommandDeliveryStatus(val commandType: String, val sentAt: String, val acknowledgement: String?, val acknowledgedAt: String?)
data class ReportedApp(val packageName: String, val displayName: String, val pendingApproval: Boolean, val lastReportedAt: String)
data class AppInstallRequest(val id: String, val deviceId: String, val appName: String, val packageName: String, val createdAt: String)

/** Dependency-free parent REST client. All database access is still protected by Supabase RLS. */
class ParentApi(private val session: ParentSession) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_ANON_KEY

    companion object {
        fun signIn(email: String, password: String): ParentSession? = runCatching {
            val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            val apiKey = BuildConfig.SUPABASE_ANON_KEY
            require(baseUrl.isNotBlank() && apiKey.isNotBlank()) { "Supabase is not configured" }
            val result = requestRaw("POST", "$baseUrl/auth/v1/token?grant_type=password", apiKey, null, JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }.toString()) ?: error("Sign-in failed")
            val json = JSONObject(result)
            ParentSession(json.getJSONObject("user").getString("id"), json.getString("access_token"), json.optString("refresh_token").ifBlank { null })
        }.getOrNull()

        private fun requestRaw(method: String, url: String, apiKey: String, token: String?, body: String?): String? = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Content-Type", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) { doOutput = true; outputStream.bufferedWriter().use { it.write(body) } }
                if (responseCode !in 200..299) return@run null
                inputStream.bufferedReader().use { it.readText() }
            }
        }.getOrNull()
    }

    fun families(): List<FamilyRecord> = get("/rest/v1/families?select=id,name&order=created_at.asc")?.let { rows ->
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { FamilyRecord(it.getString("id"), it.getString("name")) } }
    } ?: emptyList()

    fun createFamily(name: String): FamilyRecord? {
        val rows = post("/rest/v1/families", JSONObject().apply { put("owner_id", session.userId); put("name", name.trim()) }, true) ?: return null
        return rows.takeIf { it.length() > 0 }?.getJSONObject(0)?.let { FamilyRecord(it.getString("id"), it.getString("name")) }
    }

    fun devices(familyId: String): List<DeviceRecord> = get("/rest/v1/devices?family_id=eq.$familyId&select=id,display_name,last_seen_at&order=created_at.asc")?.let { rows ->
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { DeviceRecord(it.getString("id"), it.getString("display_name"), it.optString("last_seen_at").ifBlank { null }) } }
    } ?: emptyList()

    fun createPairing(familyId: String, childName: String, validitySeconds: Int): PairingCode? {
        val result = function("create-pairing", JSONObject().apply {
            put("family_id", familyId); put("child_name", childName.trim()); put("valid_for_seconds", validitySeconds)
        }) ?: return null
        return PairingCode(result.getString("pair_code"), result.optInt("expires_in_seconds", validitySeconds))
    }

    fun sendCommand(deviceId: String, command: String, expiresAtEpochMs: Long? = null, scope: String = "managed_apps"): Boolean = post("/rest/v1/parent_commands", JSONObject().apply {
        put("device_id", deviceId); put("command_type", command); put("scope", scope)
        expiresAtEpochMs?.let { put("expires_at", java.time.Instant.ofEpochMilli(it).toString()) }
    }) != null

    /** Latest command plus the child's explicit acknowledgement, if the child has synced. */
    fun latestCommandStatus(deviceId: String): CommandDeliveryStatus? {
        val commands = get("/rest/v1/parent_commands?device_id=eq.$deviceId&select=id,command_type,created_at&order=created_at.desc&limit=1") ?: return null
        if (commands.length() == 0) return null
        val command = commands.getJSONObject(0)
        val commandId = command.getString("id")
        val acknowledgements = get("/rest/v1/device_acknowledgements?command_id=eq.$commandId&select=status,created_at&order=created_at.desc&limit=1")
        val acknowledgement = acknowledgements?.takeIf { it.length() > 0 }?.getJSONObject(0)
        return CommandDeliveryStatus(
            command.getString("command_type"),
            command.getString("created_at"),
            acknowledgement?.getString("status"),
            acknowledgement?.getString("created_at")
        )
    }

    fun activePolicy(deviceId: String): VersionedPolicy? {
        val rows = get("/rest/v1/device_policies?device_id=eq.$deviceId&active=eq.true&select=version,policy&order=version.desc&limit=1") ?: return null
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return VersionedPolicy(row.getInt("version"), PolicyJson.decode(row.getJSONObject("policy").toString()))
    }

    fun publishPolicy(deviceId: String, previousVersion: Int, policy: ChildPolicy): Boolean = post("/rest/v1/device_policies", JSONObject().apply {
        put("device_id", deviceId)
        put("version", previousVersion + 1)
        put("policy", JSONObject(PolicyJson.encode(policy.copy(version = previousVersion + 1))))
        put("active", true)
    }) != null

    fun latestLocation(deviceId: String): LocationRecord? {
        val rows = get("/rest/v1/device_locations?device_id=eq.$deviceId&select=latitude,longitude,accuracy_meters,recorded_at&order=recorded_at.desc&limit=1") ?: return null
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        val accuracy = row.optDouble("accuracy_meters", Double.NaN)
        return LocationRecord(row.getDouble("latitude"), row.getDouble("longitude"), accuracy.takeIf { !it.isNaN() }?.toFloat(), row.getString("recorded_at"))
    }

    fun recentSosAlerts(): List<SosAlert> = get("/rest/v1/device_events?event_type=eq.sos&select=id,device_id,details,created_at&order=created_at.desc&limit=10")?.let { rows ->
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            SosAlert(row.getString("id"), row.getString("device_id"), row.optJSONObject("details")?.optString("message", "Emergency SOS") ?: "Emergency SOS", row.getString("created_at"))
        }
    } ?: emptyList()

    /** The parent-readable audit trail. It intentionally excludes browsing history and message content. */
    fun recentEvents(deviceId: String): List<DeviceEvent> = get("/rest/v1/device_events?device_id=eq.$deviceId&select=event_type,details,created_at&order=created_at.desc&limit=30")?.let { rows ->
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            DeviceEvent(row.getString("event_type"), row.optJSONObject("details") ?: JSONObject(), row.getString("created_at"))
        }
    } ?: emptyList()

    fun reportedApps(deviceId: String): List<ReportedApp> = get("/rest/v1/device_apps?device_id=eq.$deviceId&select=package_name,display_name,pending_approval,last_reported_at&order=display_name.asc")?.let { rows ->
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ReportedApp(row.getString("package_name"), row.getString("display_name"), row.optBoolean("pending_approval", false), row.getString("last_reported_at"))
        }
    } ?: emptyList()

    fun recentAppInstallRequests(): List<AppInstallRequest> = get("/rest/v1/device_events?event_type=eq.app_installed&select=id,device_id,details,created_at&order=created_at.desc&limit=20")?.let { rows ->
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val details = row.optJSONObject("details") ?: return@mapNotNull null
            if (!details.optBoolean("pending_approval", false)) return@mapNotNull null
            val packageName = details.optString("package_name", "Unknown app")
            AppInstallRequest(row.getString("id"), row.getString("device_id"), details.optString("app_name", packageName), packageName, row.getString("created_at"))
        }
    } ?: emptyList()

    private fun get(path: String): JSONArray? = request("GET", path, null)?.let(::JSONArray)
    private fun post(path: String, body: JSONObject, returnRepresentation: Boolean = false): JSONArray? = request("POST", path, body.toString(), returnRepresentation)?.takeIf { it.isNotBlank() }?.let(::JSONArray) ?: if (!returnRepresentation) JSONArray() else null
    private fun function(name: String, body: JSONObject): JSONObject? = request("POST", "/functions/v1/$name", body.toString())?.let(::JSONObject)

    private fun request(method: String, path: String, body: String?, returnRepresentation: Boolean = false): String? = runCatching {
        val encodedPath = path.replace(" ", URLEncoder.encode(" ", StandardCharsets.UTF_8.name()))
        (URL(baseUrl + encodedPath).openConnection() as HttpURLConnection).run {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", if (returnRepresentation) "return=representation" else "return=minimal")
            if (body != null) { doOutput = true; outputStream.bufferedWriter().use { it.write(body) } }
            if (responseCode !in 200..299) return@run null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()
}
