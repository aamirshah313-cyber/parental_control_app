package com.guardianlink.model

import java.time.DayOfWeek
import java.time.LocalTime

data class ScheduleRule(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val label: String
)

data class AppLimit(
    val packageName: String,
    val dailyMinutes: Int
)

data class SafePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
)

data class ChildPolicy(
    val version: Int = 1,
    val paused: Boolean = false,
    val pauseUntilEpochMs: Long? = null,
    val managedPackages: Set<String> = setOf("com.google.android.youtube"),
    val blockedPackages: Set<String> = emptySet(),
    val schedules: List<ScheduleRule> = emptyList(),
    val appLimits: List<AppLimit> = emptyList(),
    val blockedDomains: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val blockYoutubeShorts: Boolean = true,
    /** Standard-mode installs are detected and blocked on first launch until approved. */
    val requireAppApproval: Boolean = true,
    val approvedPackages: Set<String> = emptySet(),
    /** Location collection is opt-in and visibly runs as an Android foreground service. */
    val locationEnabled: Boolean = false,
    val locationIntervalMinutes: Int = 15,
    val safePlaces: List<SafePlace> = emptyList()
)

data class EnforcementDecision(val blocked: Boolean, val reason: String? = null)
