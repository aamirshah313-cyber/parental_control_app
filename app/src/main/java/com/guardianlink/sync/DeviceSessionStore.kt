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
    fun save(deviceId: String, accessToken: String, refreshToken: String? = null) {
        prefs.edit().putString("device_id", deviceId).apply()
        secure.put("access_token", accessToken)
        secure.put("refresh_token", refreshToken)
    }
    fun clear() = prefs.edit().clear().apply()
    fun markHandled(commandId: String) = prefs.edit().putString("last_handled_command_id", commandId).apply()
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
