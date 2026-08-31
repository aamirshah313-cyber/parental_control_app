package com.guardianlink.sync

import android.content.Context

/** Device-scoped credentials are issued only by the protected pairing function. */
class DeviceSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("guardian_device_session", Context.MODE_PRIVATE)
    private val secure = SecureTokenStore(context, "guardian_device_session")

    val deviceId: String? get() = prefs.getString("device_id", null)
    val accessToken: String? get() = secure.get("access_token")
    val refreshToken: String? get() = secure.get("refresh_token")
    val lastHandledCommandId: String? get() = prefs.getString("last_handled_command_id", null)
    private val handledCommandIds: Set<String>
        get() = prefs.getStringSet("handled_command_ids", emptySet()) ?: emptySet()
    fun save(deviceId: String, accessToken: String, refreshToken: String? = null) {
        prefs.edit().putString("device_id", deviceId).apply()
        secure.put("access_token", accessToken)
        secure.put("refresh_token", refreshToken)
    }
    fun clear() = prefs.edit().clear().apply()
    /**
     * Commands are applied locally before their cloud receipt is written. Keeping a small
     * local receipt journal makes a retry safe if the network fails between those steps.
     */
    fun hasHandled(commandId: String): Boolean = commandId == lastHandledCommandId || commandId in handledCommandIds
    fun markHandled(commandId: String) {
        val recent = (handledCommandIds + commandId).toList().takeLast(100).toSet()
        prefs.edit().putString("last_handled_command_id", commandId).putStringSet("handled_command_ids", recent).apply()
    }
    fun isPaired(): Boolean = !deviceId.isNullOrBlank() && !accessToken.isNullOrBlank()
    fun ensureFresh(): Boolean {
        val token = accessToken ?: return false
        if (!SupabaseAuth.expiresWithin(token)) return true
        val refreshed = refreshToken?.let(SupabaseAuth::refresh) ?: return false
        save(deviceId ?: return false, refreshed.accessToken, refreshed.refreshToken)
        return true
    }
    fun api(): SupabaseApi? {
        if (!isPaired() || !ensureFresh()) return null
        return SupabaseApi(deviceId!!, accessToken!!)
    }
}
