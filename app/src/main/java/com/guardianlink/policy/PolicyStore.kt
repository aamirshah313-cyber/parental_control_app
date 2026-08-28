package com.guardianlink.policy

import android.content.Context
import com.guardianlink.model.ChildPolicy
import java.time.DayOfWeek
import java.time.LocalTime

/** Local cache deliberately remains the source of enforcement when the cloud is unreachable. */
class PolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("guardian_policy", Context.MODE_PRIVATE)

    fun load(): ChildPolicy {
        val defaults = ChildPolicy(
            schedules = listOf(
                com.guardianlink.model.ScheduleRule(
                    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    LocalTime.of(21, 0), LocalTime.of(7, 0), "Bedtime"
                )
            )
        )
        val basePolicy = prefs.getString("policy_json", null)?.let(PolicyJson::decode) ?: defaults
        val pendingBlocks = prefs.getStringSet("pending_approval_packages", emptySet()) ?: emptySet()
        val policy = basePolicy.copy(blockedPackages = basePolicy.blockedPackages + pendingBlocks)
        val pauseActive = prefs.getBoolean("command_pause_active", false)
        val until = prefs.getLong("command_pause_until", 0).takeIf { it > 0 }
        if (pauseActive && until != null && System.currentTimeMillis() >= until) {
            resume()
            return policy
        }
        return policy.copy(paused = pauseActive, pauseUntilEpochMs = until, pauseAllApps = prefs.getBoolean("command_pause_all_apps", false))
    }

    fun save(policy: ChildPolicy) {
        // Pause is a command state, kept separately so a normal cloud policy refresh cannot cancel it.
        val pending = (prefs.getStringSet("pending_approval_packages", emptySet()) ?: emptySet()) - policy.approvedPackages
        prefs.edit()
            .putString("policy_json", PolicyJson.encode(policy.copy(paused = false, pauseUntilEpochMs = null, pauseAllApps = false)))
            .putStringSet("pending_approval_packages", pending)
            .apply()
    }

    fun setPause(untilEpochMs: Long?, allApps: Boolean = false) = prefs.edit()
        .putBoolean("command_pause_active", true)
        .putBoolean("command_pause_all_apps", allApps)
        .putLong("command_pause_until", untilEpochMs ?: 0)
        .apply()

    fun resume() = prefs.edit()
        .putBoolean("command_pause_active", false)
        .remove("command_pause_all_apps")
        .remove("command_pause_until")
        .apply()

    fun markPendingApproval(packageName: String) {
        val pending = (prefs.getStringSet("pending_approval_packages", emptySet()) ?: emptySet()) + packageName
        prefs.edit().putStringSet("pending_approval_packages", pending).apply()
    }
    fun saveFromCloudJson(raw: String) = save(PolicyJson.decode(raw))
}
