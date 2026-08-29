package com.guardianlink.policy

import com.guardianlink.model.AppLimit
import com.guardianlink.model.ChildPolicy
import com.guardianlink.model.ScheduleRule
import com.guardianlink.model.SafePlace
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/** Shared, backwards-compatible policy codec used by parent publishing and child enforcement. */
object PolicyJson {
    fun encode(policy: ChildPolicy): String = JSONObject().apply {
        put("version", policy.version)
        put("paused", policy.paused)
        put("pause_until_epoch_ms", policy.pauseUntilEpochMs)
        put("pause_all_apps", policy.pauseAllApps)
        put("managed_packages", JSONArray(policy.managedPackages.toList()))
        put("blocked_packages", JSONArray(policy.blockedPackages.toList()))
        put("blocked_domains", JSONArray(policy.blockedDomains.toList()))
        put("blocked_keywords", JSONArray(policy.blockedKeywords.toList()))
        put("block_youtube_shorts", policy.blockYoutubeShorts)
        put("require_app_approval", policy.requireAppApproval)
        put("approved_packages", JSONArray(policy.approvedPackages.toList()))
        put("location_enabled", policy.locationEnabled)
        put("location_interval_minutes", policy.locationIntervalMinutes.coerceIn(5, 120))
        put("safe_places", JSONArray().apply { policy.safePlaces.forEach { place -> put(JSONObject().apply {
            put("name", place.name)
            put("latitude", place.latitude)
            put("longitude", place.longitude)
            put("radius_meters", place.radiusMeters.coerceIn(50, 5_000))
        }) } })
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
        put("daily_screen_limit_minutes", policy.dailyScreenLimitMinutes.coerceIn(0, 1_440))
    }.toString()

    fun decode(raw: String): ChildPolicy = runCatching {
        val data = JSONObject(raw)
        ChildPolicy(
            version = data.optInt("version", 1),
            paused = data.optBoolean("paused", false),
            pauseUntilEpochMs = data.optLong("pause_until_epoch_ms", 0).takeIf { it > 0 },
            pauseAllApps = data.optBoolean("pause_all_apps", false),
            managedPackages = data.stringSet("managed_packages", setOf("com.google.android.youtube")),
            blockedPackages = data.stringSet("blocked_packages", emptySet()),
            blockedDomains = data.stringSet("blocked_domains", emptySet()),
            blockedKeywords = data.stringSet("blocked_keywords", emptySet()),
            blockYoutubeShorts = data.optBoolean("block_youtube_shorts", true),
            requireAppApproval = data.optBoolean("require_app_approval", true),
            approvedPackages = data.stringSet("approved_packages", emptySet()),
            locationEnabled = data.optBoolean("location_enabled", false),
            locationIntervalMinutes = data.optInt("location_interval_minutes", 15).coerceIn(5, 120),
            safePlaces = data.optJSONArray("safe_places")?.let { places -> (0 until places.length()).mapNotNull { index ->
                val place = places.getJSONObject(index)
                val name = place.optString("name").trim()
                val latitude = place.optDouble("latitude", Double.NaN)
                val longitude = place.optDouble("longitude", Double.NaN)
                if (name.isBlank() || latitude.isNaN() || longitude.isNaN()) null
                else SafePlace(name, latitude, longitude, place.optInt("radius_meters", 150).coerceIn(50, 5_000))
            } } ?: emptyList(),
            schedules = data.optJSONArray("schedules")?.let { rules -> (0 until rules.length()).map { index ->
                val rule = rules.getJSONObject(index)
                ScheduleRule(rule.stringSet("days", emptySet()).map(DayOfWeek::valueOf).toSet(), LocalTime.parse(rule.getString("start")), LocalTime.parse(rule.getString("end")), rule.optString("label", "Scheduled break"))
            } } ?: emptyList(),
            appLimits = data.optJSONArray("app_limits")?.let { limits -> (0 until limits.length()).map { index ->
                val limit = limits.getJSONObject(index)
                AppLimit(limit.getString("package_name"), limit.getInt("daily_minutes"))
            } } ?: emptyList(),
            dailyScreenLimitMinutes = data.optInt("daily_screen_limit_minutes", 0).coerceIn(0, 1_440)
        )
    }.getOrElse { ChildPolicy() }

    private fun JSONObject.stringSet(key: String, fallback: Set<String>): Set<String> {
        val values = optJSONArray(key) ?: return fallback
        return (0 until values.length()).mapNotNull { index -> values.optString(index).takeIf(String::isNotBlank) }.toSet()
    }
}
