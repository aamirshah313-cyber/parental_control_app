package com.guardianlink.sync

import com.guardianlink.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.util.UUID

data class RemoteCommand(val id: String, val type: String, val scope: String, val expiresAtEpochMs: Long?, val payload: JSONObject = JSONObject())
data class ReportedAppPayload(val packageName: String, val displayName: String, val pendingApproval: Boolean? = null)
data class ChildQuickMessage(val id: String, val senderRole: String, val templateKey: String, val body: String, val createdAt: String)
data class ChildFamilyChatMessage(val id: String, val senderRole: String, val body: String, val audioPath: String?, val createdAt: String, val messageKind: String = "chat", val templateKey: String? = null)
data class FamilyNotification(val id: String, val deviceId: String, val title: String, val body: String, val eventType: String, val createdAt: String, val readAt: String? = null)

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
        val records = get("/rest/v1/parent_commands?device_id=eq.$deviceId&order=created_at.desc&limit=1&select=id,command_type,scope,expires_at,payload") ?: return null
        return records.takeIf { it.length() > 0 }?.let { commandRecord(it.getJSONObject(0)) }
    }

    /**
     * Reads every command that the child has not yet completed, oldest first. The fallback
     * keeps existing installations functional until the queue migration is run.
     */
    fun fetchPendingCommands(): List<RemoteCommand> {
        if (!enabled) return emptyList()
        val records = get("/rest/v1/parent_commands?device_id=eq.$deviceId&child_processed_at=is.null&order=created_at.asc&limit=50&select=id,command_type,scope,expires_at,payload")
        return if (records == null) listOfNotNull(fetchLatestCommand())
        else (0 until records.length()).map { commandRecord(records.getJSONObject(it)) }
    }

    fun acknowledge(commandId: String, status: String): Boolean {
        if (!enabled) return false
        return post("/rest/v1/device_acknowledgements", JSONObject().apply {
            put("command_id", commandId)
            put("device_id", deviceId)
            put("status", status)
        })
    }

    /** The server-side receipt makes command delivery visible to the parent and enables a real queue. */
    fun markCommandProcessed(commandId: String, status: String): Boolean {
        if (!enabled) return false
        return request("POST", "/rest/v1/rpc/complete_child_command", JSONObject().apply {
            put("p_command_id", commandId)
            put("p_status", status)
        }.toString()) != null
    }

    /** Confirms that this child JWT can still see its own paired device before a sync is called successful. */
    fun verifyDeviceSession(): Boolean {
        if (!enabled) return false
        val rows = get("/rest/v1/devices?id=eq.$deviceId&select=id&limit=1")
        return rows?.length() == 1
    }

    fun touchLastSeen(): Boolean {
        if (!enabled) return false
        return patch("/rest/v1/devices?id=eq.$deviceId", JSONObject().apply { put("last_seen_at", java.time.Instant.now().toString()) })
    }

    fun postEvent(type: String, details: JSONObject = JSONObject()): Boolean {
        if (!enabled) return false
        return request("POST", "/rest/v1/device_events", JSONObject().apply {
            put("device_id", deviceId)
            put("event_type", type)
            put("details", details)
        }.toString()) != null
    }

    fun postLocation(latitude: Double, longitude: Double, accuracyMeters: Float?): Boolean {
        if (!enabled) return false
        return request("POST", "/rest/v1/device_locations", JSONObject().apply {
            put("device_id", deviceId)
            put("latitude", latitude)
            put("longitude", longitude)
            accuracyMeters?.let { put("accuracy_meters", it) }
        }.toString()) != null
    }

    fun quickMessages(): List<ChildQuickMessage> {
        if (!enabled) return emptyList()
        val rows = get("/rest/v1/family_messages?device_id=eq.$deviceId&select=id,sender_role,template_key,body,created_at&order=created_at.desc&limit=50") ?: return emptyList()
        return (0 until rows.length()).map { index ->
            rows.getJSONObject(index).let { ChildQuickMessage(it.getString("id"), it.getString("sender_role"), it.getString("template_key"), it.getString("body"), it.getString("created_at")) }
        }.reversed()
    }

    fun latestQuickMessage(senderRole: String): ChildQuickMessage? {
        if (!enabled) return null
        val rows = get("/rest/v1/family_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,template_key,body,created_at&order=created_at.desc&limit=1") ?: return null
        if (rows.length() == 0) return null
        return rows.getJSONObject(0).let { ChildQuickMessage(it.getString("id"), it.getString("sender_role"), it.getString("template_key"), it.getString("body"), it.getString("created_at")) }
    }

    fun sendQuickMessage(templateKey: String, body: String): Boolean {
        if (!enabled) return false
        val familyRows = get("/rest/v1/devices?id=eq.$deviceId&select=family_id") ?: return false
        if (familyRows.length() == 0) return false
        return request("POST", "/rest/v1/family_messages", JSONObject().apply {
            put("family_id", familyRows.getJSONObject(0).getString("family_id")); put("device_id", deviceId)
            put("sender_role", "child"); put("template_key", templateKey); put("body", body)
        }.toString()) != null
    }

    fun chatMessages(): List<ChildFamilyChatMessage> {
        if (!enabled) return emptyList()
        val rows = chatRows() ?: return emptyList()
        return (0 until rows.length()).map { index ->
            childChatRecord(rows.getJSONObject(index))
        }.reversed()
    }

    fun latestChatMessage(senderRole: String): ChildFamilyChatMessage? {
        if (!enabled) return null
        val rows = get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,body,audio_path,created_at,message_kind,template_key&order=created_at.desc&limit=1")
            ?: get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&sender_role=eq.$senderRole&select=id,sender_role,body,audio_path,created_at&order=created_at.desc&limit=1")
            ?: return null
        return rows.takeIf { it.length() > 0 }?.let { childChatRecord(it.getJSONObject(0)) }
    }

    fun sendChatMessage(body: String, audioPath: String? = null, messageKind: String = "chat", templateKey: String? = null): Boolean {
        if (!enabled || body.length > 600) return false
        val familyRows = get("/rest/v1/devices?id=eq.$deviceId&select=family_id") ?: return false
        if (familyRows.length() == 0) return false
        val familyId = familyRows.getJSONObject(0).getString("family_id")
        val sent = request("POST", "/rest/v1/family_chat_messages", JSONObject().apply {
            put("family_id", familyId); put("device_id", deviceId); put("sender_role", "child"); put("body", body); audioPath?.let { put("audio_path", it) }
            if (messageKind != "chat") put("message_kind", messageKind)
            templateKey?.let { put("template_key", it) }
        }.toString()) != null
        if (sent) postNotification(familyId, "parent", if (messageKind == "quick_update") "Quick update from child" else "New message from child", body.ifBlank { "New voice note" }, messageKind)
        return sent
    }

    fun notifications(): List<FamilyNotification> {
        if (!enabled) return emptyList()
        val rows = get("/rest/v1/family_notifications?device_id=eq.$deviceId&target_role=eq.child&select=id,device_id,title,body,event_type,created_at,read_at&order=created_at.desc&limit=50") ?: return emptyList()
        return (0 until rows.length()).map { notificationRecord(rows.getJSONObject(it)) }
    }

    fun latestNotification(): FamilyNotification? = notifications().firstOrNull()

    fun markNotificationsRead(ids: Collection<String>): Boolean {
        if (!enabled || ids.isEmpty()) return true
        return patch("/rest/v1/family_notifications?target_role=eq.child&id=in.(${ids.joinToString(",")})", JSONObject().put("read_at", java.time.Instant.now().toString()))
    }

    fun uploadVoice(file: File): String? {
        if (!enabled || !file.isFile || file.length() !in 1..MAX_VOICE_BYTES) return null
        val objectPath = "$deviceId/${UUID.randomUUID()}.m4a"
        return if (uploadObject(objectPath, file)) objectPath else null
    }

    fun voiceObjectUrl(audioPath: String) = "$baseUrl/storage/v1/object/guardian-voice/$audioPath"
    fun voiceRequestHeaders(): Map<String, String> = mapOf("apikey" to BuildConfig.SUPABASE_ANON_KEY, "Authorization" to "Bearer $accessToken")

    fun requestMoreScreenTime(minutes: Int): Boolean {
        if (!enabled || minutes !in 5..120) return false
        return request("POST", "/rest/v1/child_time_requests", JSONObject().apply {
            put("device_id", deviceId); put("request_type", "more_time"); put("requested_minutes", minutes)
        }.toString()) != null
    }

    fun reportHealth(batteryPercent: Int?, protectionActive: Boolean, usageAccessAvailable: Boolean, screenMinutesToday: Int, appliedPolicyVersion: Int) {
        if (!enabled) return
        val health = JSONObject().apply {
            put("device_id", deviceId); batteryPercent?.let { put("battery_percent", it.coerceIn(0, 100)) }
            put("protection_active", protectionActive); put("usage_access_available", usageAccessAvailable)
            put("screen_minutes_today", screenMinutesToday.coerceAtLeast(0)); put("reported_at", java.time.Instant.now().toString())
            put("applied_policy_version", appliedPolicyVersion.coerceAtLeast(0))
        }
        // Older database deployments do not have the delivery column yet. Report basic health
        // rather than making a sync look failed while the administrator applies the migration.
        if (request("POST", "/rest/v1/device_health?on_conflict=device_id", health.toString(), "resolution=merge-duplicates,return=minimal") == null) {
            health.remove("applied_policy_version")
            request("POST", "/rest/v1/device_health?on_conflict=device_id", health.toString(), "resolution=merge-duplicates,return=minimal")
        }
    }

    fun upsertReportedApps(apps: List<ReportedAppPayload>): Boolean {
        if (!enabled || apps.isEmpty()) return false
        val body = JSONArray().apply { apps.forEach { app -> put(JSONObject().apply {
            put("device_id", deviceId); put("package_name", app.packageName); put("display_name", app.displayName); put("last_reported_at", java.time.Instant.now().toString())
            app.pendingApproval?.let { put("pending_approval", it) }
        }) } }
        return request("POST", "/rest/v1/device_apps?on_conflict=device_id,package_name", body.toString(), "resolution=merge-duplicates,return=minimal") != null
    }

    /** A manual inventory refresh is authoritative: remove entries for apps no longer installed on this child phone. */
    fun replaceReportedApps(apps: List<ReportedAppPayload>): Boolean {
        if (!upsertReportedApps(apps)) return false
        val rows = get("/rest/v1/device_apps?device_id=eq.$deviceId&select=package_name") ?: return false
        val currentPackages = apps.mapTo(linkedSetOf()) { it.packageName }
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("package_name") }
            .filter { it !in currentPackages }
            .all { packageName -> delete("/rest/v1/device_apps?device_id=eq.$deviceId&package_name=eq.$packageName") }
    }

    private fun commandRecord(command: JSONObject): RemoteCommand {
        val expiry = command.optString("expires_at").takeIf { it.isNotBlank() }?.let { java.time.Instant.parse(it).toEpochMilli() }
        return RemoteCommand(command.getString("id"), command.getString("command_type"), command.optString("scope", "managed_apps"), expiry, command.optJSONObject("payload") ?: JSONObject())
    }

    private fun chatRows(): JSONArray? = get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&select=id,sender_role,body,audio_path,created_at,message_kind,template_key&order=created_at.desc&limit=80")
        ?: get("/rest/v1/family_chat_messages?device_id=eq.$deviceId&select=id,sender_role,body,audio_path,created_at&order=created_at.desc&limit=80")
    private fun childChatRecord(row: JSONObject) = ChildFamilyChatMessage(row.getString("id"), row.getString("sender_role"), row.optString("body"), row.optString("audio_path").ifBlank { null }, row.getString("created_at"), row.optString("message_kind", "chat"), row.optString("template_key").ifBlank { null })
    private fun notificationRecord(row: JSONObject) = FamilyNotification(row.getString("id"), row.getString("device_id"), row.getString("title"), row.getString("body"), row.getString("event_type"), row.getString("created_at"), row.optString("read_at").ifBlank { null })
    private fun postNotification(familyId: String, targetRole: String, title: String, body: String, eventType: String): Boolean = request("POST", "/rest/v1/family_notifications", JSONObject().apply {
        put("family_id", familyId); put("device_id", deviceId); put("target_role", targetRole); put("title", title); put("body", body); put("event_type", eventType)
    }.toString()) != null

    private fun get(path: String): JSONArray? = request("GET", path, null)?.let(::JSONArray)
    private fun post(path: String, body: JSONObject): Boolean = request("POST", path, body.toString()) != null
    private fun patch(path: String, body: JSONObject): Boolean = request("PATCH", path, body.toString()) != null
    private fun delete(path: String): Boolean = request("DELETE", path, null) != null

    private fun uploadObject(objectPath: String, file: File): Boolean = runCatching {
        (URL("$baseUrl/storage/v1/object/guardian-voice/$objectPath").openConnection() as HttpURLConnection).run {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY); setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "audio/mp4"); setRequestProperty("x-upsert", "false")
            doOutput = true; file.inputStream().use { input -> outputStream.use { output -> input.copyTo(output) } }
            responseCode in 200..299
        }
    }.getOrDefault(false)

    private fun request(method: String, path: String, body: String?, prefer: String = "return=minimal"): String? = runCatching {
        (URL(baseUrl + path).openConnection() as HttpURLConnection).run {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", prefer)
            if (body != null) { doOutput = true; outputStream.bufferedWriter().use { it.write(body) } }
            if (responseCode !in 200..299) return@run null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    private companion object { const val MAX_VOICE_BYTES = 5L * 1024L * 1024L }
}
