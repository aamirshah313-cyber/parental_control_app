package com.guardianlink.sync

import android.content.Context

/** Device-scoped credentials are issued only by the protected pairing function. */
class DeviceSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("guardian_device_session", Context.MODE_PRIVATE)

    val deviceId: String? get() = prefs.getString("device_id", null)
    val accessToken: String? get() = prefs.getString("access_token", null)
    val refreshToken: String? get() = prefs.getString("refresh_token", null)
    val lastHandledCommandId: String? get() = prefs.getString("last_handled_command_id", null)
    fun save(deviceId: String, accessToken: String, refreshToken: String? = null) = prefs.edit()
        .putString("device_id", deviceId)
        .putString("access_token", accessToken)
        .putString("refresh_token", refreshToken)
        .apply()
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
