package com.guardianlink.sync

import android.content.Context

data class ParentSession(val userId: String, val accessToken: String, val refreshToken: String?)

/** Parent credentials remain local to the parent phone and are never copied to a child device. */
class ParentSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("guardian_parent_session", Context.MODE_PRIVATE)
    private val secure = SecureTokenStore(context, "guardian_parent_session")

    fun load(): ParentSession? {
        val userId = prefs.getString("user_id", null) ?: return null
        val token = secure.get("access_token") ?: return null
        return ParentSession(userId, token, secure.get("refresh_token"))
    }

    fun save(session: ParentSession) {
        prefs.edit().putString("user_id", session.userId).apply()
        secure.put("access_token", session.accessToken)
        secure.put("refresh_token", session.refreshToken)
    }

    fun clear() = prefs.edit().clear().apply()

    /** Call from a worker thread before a parent network request. */
    fun ensureFresh(): ParentSession? {
        val current = load() ?: return null
        if (!SupabaseAuth.expiresWithin(current.accessToken)) return current
        val refreshed = current.refreshToken?.let(SupabaseAuth::refresh) ?: return null
        return current.copy(accessToken = refreshed.accessToken, refreshToken = refreshed.refreshToken).also(::save)
    }
}
