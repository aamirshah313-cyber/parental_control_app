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
import java.io.File
import java.util.UUID

data class FamilyRecord(val id: String, val name: String)
data class DeviceRecord(val id: String, val displayName: String, val lastSeenAt: String?)
data class PairingCode(val code: String, val expiresInSeconds: Int)
data class ParentSignUpResult(val session: ParentSession?, val confirmationRequired: Boolean)
data class LocationRecord(val latitude: Double, val longitude: Double, val accuracyMeters: Float?, val recordedAt: String)
data class VersionedPolicy(val version: Int, val policy: ChildPolicy)
data class SosAlert(val id: String, val deviceId: String, val message: String, val createdAt: String)
data class DeviceEvent(val eventType: String, val details: JSONObject, val createdAt: String)
data class CommandDeliveryStatus(val commandType: String, val sentAt: String, val acknowledgement: String?, val acknowledgedAt: String?, val appliedStatus: String? = null, val appliedAt: String? = null)
data class ReportedApp(val packageName: String, val displayName: String, val pendingApproval: Boolean, val lastReportedAt: String)
data class AppInstallRequest(val id: String, val deviceId: String, val appName: String, val packageName: String, val createdAt: String)
data class FamilyMessage(val id: String, val senderRole: String, val templateKey: String, val body: String, val createdAt: String)
data class TimeRequest(val id: String, val deviceId: String, val requestedMinutes: Int, val createdAt: String)
data class DeviceHealth(val batteryPercent: Int?, val protectionActive: Boolean, val usageAccessAvailable: Boolean, val screenMinutesToday: Int, val reportedAt: String, val appliedPolicyVersion: Int? = null)
data class FamilyChatMessage(val id: String, val senderRole: String, val body: String, val audioPath: String?, val createdAt: String, val messageKind: String = "chat", val templateKey: String? = null)
data class PauseState(val active: Boolean, val scope: String = "managed_apps", val expiresAtEpochMs: Long? = null)

/** Dependency-free parent REST client. All database access is still protected by Supabase RLS. */
class ParentApi(private val session: ParentSession) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_ANON_KEY

    companion object {
        private const val MAX_VOICE_BYTES = 5L * 1024L * 1024L
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

        /** Creates a generic parent account. Supabase email-confirmation settings decide whether it signs in immediately. */
        fun signUp(email: String, password: String): ParentSignUpResult? = runCatching {
            val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            val apiKey = BuildConfig.SUPABASE_ANON_KEY
            require(baseUrl.isNotBlank() && apiKey.isNotBlank()) { "Supabase is not configured" }
            val result = requestRaw("POST", "$baseUrl/auth/v1/signup", apiKey, null, JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }.toString()) ?: error("Sign-up failed")
            val json = JSONObject(result)
            val access = json.optString("access_token")
            val session = access.takeIf { it.isNotBlank() }?.let {
                ParentSession(json.getJSONObject("user").getString("id"), it, json.optString("refresh_token").ifBlank { null })
            }
            ParentSignUpResult(session, session == null)
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

    /** Only an actually paired child identity can receive this family's messages and controls. */
    fun devices(familyId: String): List<DeviceRecord> = get("/rest/v1/devices?family_id=eq.$familyId&retired_at=is.null&child_auth_user_id=not.is.null&select=id,display_name,last_seen_at,created_at&order=last_seen_at.desc.nullslast,created_at.desc")?.let { rows ->
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { DeviceRecord(it.getString("id"), it.getString("display_name"), it.optString("last_seen_at").ifBlank { null }) } }
            .groupBy { it.displayName.trim().lowercase() }
            .values
            .map { matches -> matches.maxWithOrNull(compareBy<DeviceRecord> { it.lastSeenAt ?: "" }.thenBy { it.id })!! }
            .sortedWith(compareByDescending<DeviceRecord> { it.lastSeenAt ?: "" }.thenBy { it.displayName.lowercase() })
    } ?: emptyList()

    fun createPairing(familyId: String, childName: String, validitySeconds: Int): PairingCode? {
        val result = function("create-pairing", JSONObject().apply {
            put("family_id", familyId); put("child_name", childName.trim()); put("valid_for_seconds", validitySeconds)
        }) ?: return null
        return PairingCode(result.getString("pair_code"), result.optInt("expires_in_seconds", validitySeconds))
    }

    fun sendCommand(deviceId: String, command: String, expiresAtEpochMs: Long? = null, scope: String = "managed_apps", payload: JSONObject? = null): Boolean = post("/rest/v1/parent_commands", JSONObject().apply {
        put("device_id", deviceId); put("command_type", command); put("scope", scope)
        expiresAtEpochMs?.let { put("expires_at", java.time.Instant.ofEpochMilli(it).toString()) }
        payload?.let { put("payload", it) }
    }) != null

    /** The latest pause/resume command is the parent-facing requested state; delivery remains visible separately. */
    fun pauseState(deviceId: String): PauseState {
        val rows = get("/rest/v1/parent_commands?device_id=eq.$deviceId&command_type=in.(pause,resume)&select=command_type,scope,expires_at&order=created_at.desc&limit=1") ?: return PauseState(false)
        if (rows.length() == 0) return PauseState(false)
        val row = rows.getJSONObject(0)
        val expiresAt = row.optString("expires_at").takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        val active = row.getString("command_type") == "pause" && (expiresAt == null || expiresAt > System.currentTimeMillis())
        return PauseState(active, row.optString("scope", "managed_apps"), expiresAt)
    }

    /** Uses a server-side ownership check because child sessions may update only last_seen_at. */
    fun retireDevice(deviceId: String): Boolean = function("retire-device", JSONObject().put("device_id", deviceId))?.optBoolean("retired", false) == true

    fun deviceHealth(deviceId: String): DeviceHealth? = (get("/rest/v1/device_health?device_id=eq.$deviceId&select=battery_percent,protection_active,usage_access_available,screen_minutes_today,reported_at,applied_policy_version&order=reported_at.desc&limit=1")
        ?: get("/rest/v1/device_health?device_id=eq.$deviceId&select=battery_percent,protection_active,usage_access_available,screen_minutes_today,reported_at&order=reported_at.desc&limit=1"))
        ?.takeIf { it.length() > 0 }?.getJSONObject(0)?.let { row ->
            DeviceHealth(row.optInt("battery_percent", -1).takeIf { it >= 0 }, row.optBoolean("protection_active"), row.optBoolean("usage_access_available"), row.optInt("screen_minutes_today", 0), row.getString("reported_at"), row.optInt("applied_policy_version", -1).takeIf { it >= 0 })
        }

    fun pendingTimeRequests(deviceId: String): List<TimeRequest> = get("/rest/v1/child_time_requests?device_id=eq.$deviceId&status=eq.pending&select=id,device_id,requested_minutes,created_at&order=created_at.asc")?.let { rows ->
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { TimeRequest(it.getString("id"), it.getString("device_id"), it.getInt("requested_minutes"), it.getString("created_at")) } }
    } ?: emptyList()

    fun resolveTimeRequest(requestId: String, status: String, grantedMinutes: Int? = null): Boolean = patch("/rest/v1/child_time_requests?id=eq.$requestId", JSONObject().apply {
        put("status", status); put("resolved_at", java.time.Instant.now().toString()); grantedMinutes?.let { put("granted_minutes", it) }
    })

    /** Latest command plus the child's explicit acknowledgement, if the child has synced. */
    fun latestCommandStatus(deviceId: String): CommandDeliveryStatus? {
        val commands = get("/rest/v1/parent_commands?device_id=eq.$deviceId&select=id,command_type,created_at,child_processed_status,child_processed_at&order=created_at.desc&limit=1")
            ?: get("/rest/v1/parent_commands?device_id=eq.$deviceId&select=id,command_type,created_at&order=created_at.desc&limit=1") ?: return null
        if (commands.length() == 0) return null
        val command = commands.getJSONObject(0)
        val commandId = command.getString("id")
        val acknowledgements = get("/rest/v1/device_acknowledgements?command_id=eq.$commandId&select=status,created_at&order=created_at.desc&limit=1")
        val acknowledgement = acknowledgements?.takeIf { it.length() > 0 }?.getJSONObject(0)
        return CommandDeliveryStatus(
            command.getString("command_type"),
            command.getString("created_at"),
            acknowledgement?.getString("status"),
            acknowledgement?.getString("created_at"),
            command.optString("child_processed_status").ifBlank { null },
            command.optString("child_processed_at").ifBlank { null }
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
        return locationRecord(rows.getJSONObject(0))
    }

    /** Kept out of the main dashboard; callers display this only in the dedicated Location Log. */
    fun locationHistory(deviceId: String, limit: Int = 30): List<LocationRecord> =
        get("/rest/v1/device_locations?device_id=eq.$deviceId&select=latitude,longitude,accuracy_meters,recorded_at&order=recorded_at.desc&limit=${limit.coerceIn(1, 100)}")?.let { rows ->
            (0 until rows.length()).map { locationRecord(rows.getJSONObject(it)) }
        } ?: emptyList()

    private fun locationRecord(row: JSONObject): LocationRecord {
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

    fun quickMessages(deviceId: String): List<FamilyMessage> = get("/rest/v1/family_messages?device_id=eq.$deviceId&select=id,sender_role,template_key,body,created_at&order=created_at.desc&limit=50")?.let { rows ->
        (0 until rows.length()).map { index -> messageRecord(rows.getJSONObject(index)) }.reversed()
    } ?: emptyList()

    fun latestQuickMessage(deviceId: String, senderRole: String): FamilyMessage? =
        get("/rest/v1/family_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,template_key,body,created_at&order=created_at.desc&limit=1")
            ?.takeIf { it.length() > 0 }
            ?.let { messageRecord(it.getJSONObject(0)) }

    fun sendQuickMessage(familyId: String, deviceId: String, templateKey: String, body: String): Boolean = post("/rest/v1/family_messages", JSONObject().apply {
        put("family_id", familyId); put("device_id", deviceId); put("sender_role", "parent"); put("template_key", templateKey); put("body", body)
    }) != null

    fun chatMessages(deviceId: String): List<FamilyChatMessage> = (get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&select=id,sender_role,body,audio_path,created_at,message_kind,template_key&order=created_at.desc&limit=80")
        ?: get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&select=id,sender_role,body,audio_path,created_at&order=created_at.desc&limit=80"))?.let { rows ->
        (0 until rows.length()).map { index -> chatRecord(rows.getJSONObject(index)) }.reversed()
    } ?: emptyList()

    fun latestChatMessage(deviceId: String, senderRole: String): FamilyChatMessage? = (get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,body,audio_path,created_at,message_kind,template_key&order=created_at.desc&limit=1")
        ?: get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,body,audio_path,created_at&order=created_at.desc&limit=1"))
        ?.takeIf { it.length() > 0 }?.let { chatRecord(it.getJSONObject(0)) }

    fun sendChatMessage(familyId: String, deviceId: String, body: String, audioPath: String? = null, messageKind: String = "chat", templateKey: String? = null): Boolean {
        if (body.length > 600) return false
        val sent = post("/rest/v1/family_chat_messages", JSONObject().apply {
            put("family_id", familyId); put("device_id", deviceId); put("sender_role", "parent"); put("body", body); audioPath?.let { put("audio_path", it) }
            if (messageKind != "chat") put("message_kind", messageKind)
            templateKey?.let { put("template_key", it) }
        }) != null
        // A notification failure must never turn a delivered chat message into a false failure.
        if (sent) postNotification(familyId, deviceId, "child", if (messageKind == "quick_update") "Quick update from parent" else "New message from parent", body.ifBlank { "New voice note" }, messageKind)
        return sent
    }

    /** Parent inbox is family-wide so a selected dashboard profile can never hide a child's update. */
    fun familyNotifications(familyId: String): List<FamilyNotification> =
        get("/rest/v1/family_notifications?family_id=eq.$familyId&target_role=eq.parent&select=id,device_id,title,body,event_type,created_at,read_at&order=created_at.desc&limit=80")?.let { rows ->
            (0 until rows.length()).map { notificationRecord(rows.getJSONObject(it)) }
        } ?: emptyList()

    fun notifications(deviceId: String): List<FamilyNotification> =
        get("/rest/v1/family_notifications?device_id=eq.$deviceId&target_role=eq.parent&select=id,device_id,title,body,event_type,created_at,read_at&order=created_at.desc&limit=50")?.let { rows ->
            (0 until rows.length()).map { notificationRecord(rows.getJSONObject(it)) }
        } ?: emptyList()

    fun latestNotification(deviceId: String): FamilyNotification? = notifications(deviceId).firstOrNull()

    fun markNotificationsRead(deviceId: String, ids: Collection<String>): Boolean {
        if (ids.isEmpty()) return true
        return patch("/rest/v1/family_notifications?device_id=eq.$deviceId&target_role=eq.parent&id=in.(${ids.joinToString(",")})", JSONObject().put("read_at", java.time.Instant.now().toString()))
    }

    fun markFamilyNotificationsRead(familyId: String, ids: Collection<String>): Boolean {
        if (ids.isEmpty()) return true
        return patch("/rest/v1/family_notifications?family_id=eq.$familyId&target_role=eq.parent&id=in.(${ids.joinToString(",")})", JSONObject().put("read_at", java.time.Instant.now().toString()))
    }

    fun uploadVoice(deviceId: String, file: File): String? {
        if (!file.isFile || file.length() !in 1..MAX_VOICE_BYTES) return null
        val objectPath = "$deviceId/${UUID.randomUUID()}.m4a"
        return if (uploadObject(objectPath, file)) objectPath else null
    }

    fun voiceObjectUrl(audioPath: String) = "$baseUrl/storage/v1/object/guardian-voice/$audioPath"
    fun voiceRequestHeaders(): Map<String, String> = mapOf("apikey" to apiKey, "Authorization" to "Bearer ${session.accessToken}")

    private fun messageRecord(row: JSONObject) = FamilyMessage(row.getString("id"), row.getString("sender_role"), row.getString("template_key"), row.getString("body"), row.getString("created_at"))
    private fun chatRecord(row: JSONObject) = FamilyChatMessage(row.getString("id"), row.getString("sender_role"), row.optString("body"), row.optString("audio_path").ifBlank { null }, row.getString("created_at"), row.optString("message_kind", "chat"), row.optString("template_key").ifBlank { null })
    private fun notificationRecord(row: JSONObject) = FamilyNotification(row.getString("id"), row.getString("device_id"), row.getString("title"), row.getString("body"), row.getString("event_type"), row.getString("created_at"), row.optString("read_at").ifBlank { null })
    private fun postNotification(familyId: String, deviceId: String, targetRole: String, title: String, body: String, eventType: String): Boolean = post("/rest/v1/family_notifications", JSONObject().apply {
        put("family_id", familyId); put("device_id", deviceId); put("target_role", targetRole); put("title", title); put("body", body); put("event_type", eventType)
    }) != null

    private fun uploadObject(objectPath: String, file: File): Boolean = runCatching {
        (URL("$baseUrl/storage/v1/object/guardian-voice/$objectPath").openConnection() as HttpURLConnection).run {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("apikey", apiKey); setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "audio/mp4"); setRequestProperty("x-upsert", "false")
            doOutput = true; file.inputStream().use { input -> outputStream.use { output -> input.copyTo(output) } }
            responseCode in 200..299
        }
    }.getOrDefault(false)

    private fun get(path: String): JSONArray? = request("GET", path, null)?.let(::JSONArray)
    /** Never manufacture a success result: callers must be able to distinguish an RLS/network rejection. */
    private fun post(path: String, body: JSONObject, returnRepresentation: Boolean = false): JSONArray? {
        val raw = request("POST", path, body.toString(), returnRepresentation) ?: return null
        return if (returnRepresentation) raw.takeIf { it.isNotBlank() }?.let(::JSONArray) else JSONArray()
    }
    private fun function(name: String, body: JSONObject): JSONObject? = request("POST", "/functions/v1/$name", body.toString())?.let(::JSONObject)
    private fun patch(path: String, body: JSONObject): Boolean = request("PATCH", path, body.toString()) != null

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
