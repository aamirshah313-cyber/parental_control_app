package com.guardianlink.policy

import android.content.Context
import com.guardianlink.model.AppLimit
import com.guardianlink.model.ChildPolicy
import com.guardianlink.model.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

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
        return prefs.getString("policy_json", null)?.let(::decode) ?: defaults
    }

    fun save(policy: ChildPolicy) {
        prefs.edit().putString("policy_json", encode(policy)).apply()
    }

    fun setPause(untilEpochMs: Long?) = save(load().copy(paused = true, pauseUntilEpochMs = untilEpochMs))
    fun resume() = save(load().copy(paused = false, pauseUntilEpochMs = null))
    fun saveFromCloudJson(raw: String) = save(decode(raw))

    private fun encode(policy: ChildPolicy): String = JSONObject().apply {
        put("version", policy.version)
        put("paused", policy.paused)
        put("pause_until_epoch_ms", policy.pauseUntilEpochMs)
        put("managed_packages", JSONArray(policy.managedPackages.toList()))
        put("blocked_packages", JSONArray(policy.blockedPackages.toList()))
        put("blocked_domains", JSONArray(policy.blockedDomains.toList()))
        put("blocked_keywords", JSONArray(policy.blockedKeywords.toList()))
        put("block_youtube_shorts", policy.blockYoutubeShorts)
        put("schedules", JSONArray().apply { policy.schedules.forEach { rule -> put(JSONObject().apply {
            put("days", JSONArray(rule.days.map { it.name }))
            put("start", rule.start.toString())
            put("end", rule.end.toString())
            put("label", rule.label)
        }) } })
        put("app_limits", JSONArray().apply { policy.appLimits.forEach { limit -> put(JSONObject().apply {
            put("package_name", limit.packageName)
            put("daily_minutes", limit.dailyMinutes)
        }) } })
    }.toString()

    private fun decode(raw: String): ChildPolicy = runCatching {
        val data = JSONObject(raw)
        ChildPolicy(
            version = data.optInt("version", 1),
            paused = data.optBoolean("paused", false),
            pauseUntilEpochMs = data.optLong("pause_until_epoch_ms", 0).takeIf { it > 0 },
            managedPackages = data.stringSet("managed_packages", setOf("com.google.android.youtube")),
            blockedPackages = data.stringSet("blocked_packages", emptySet()),
            blockedDomains = data.stringSet("blocked_domains", emptySet()),
            blockedKeywords = data.stringSet("blocked_keywords", emptySet()),
            blockYoutubeShorts = data.optBoolean("block_youtube_shorts", true),
            schedules = data.optJSONArray("schedules")?.let { rules -> (0 until rules.length()).map { index ->
                val rule = rules.getJSONObject(index)
                ScheduleRule(rule.stringSet("days", emptySet()).map(DayOfWeek::valueOf).toSet(), LocalTime.parse(rule.getString("start")), LocalTime.parse(rule.getString("end")), rule.optString("label", "Scheduled break"))
            } } ?: emptyList(),
            appLimits = data.optJSONArray("app_limits")?.let { limits -> (0 until limits.length()).map { index ->
                val limit = limits.getJSONObject(index)
                AppLimit(limit.getString("package_name"), limit.getInt("daily_minutes"))
            } } ?: emptyList()
        )
    }.getOrElse { ChildPolicy() }

    private fun JSONObject.stringSet(key: String, fallback: Set<String>): Set<String> {
        val values = optJSONArray(key) ?: return fallback
        return (0 until values.length()).mapNotNull { index -> values.optString(index).takeIf(String::isNotBlank) }.toSet()
    }
}
