package com.guardianlink.policy

import com.guardianlink.model.ChildPolicy
import com.guardianlink.model.EnforcementDecision
import java.time.LocalDateTime

class PolicyEngine {
    fun appDecision(policy: ChildPolicy, packageName: String, usedTodayMinutes: Int, now: LocalDateTime = LocalDateTime.now()): EnforcementDecision {
        val pauseActive = policy.paused && (policy.pauseUntilEpochMs == null || System.currentTimeMillis() < policy.pauseUntilEpochMs)
        if (pauseActive && (policy.pauseAllApps && !isEssentialPackage(packageName) || packageName in policy.managedPackages)) return EnforcementDecision(true, "Paused by your parent")
        if (packageName in policy.blockedPackages) return EnforcementDecision(true, "This app is blocked")
        if (packageName !in policy.managedPackages) return EnforcementDecision(false)
        policy.schedules.firstOrNull { schedule ->
            isWithinSchedule(now, schedule)
        }?.let { return EnforcementDecision(true, it.label) }
        policy.appLimits.firstOrNull { it.packageName == packageName && usedTodayMinutes >= it.dailyMinutes }
            ?.let { return EnforcementDecision(true, "Daily limit reached") }
        return EnforcementDecision(false)
    }

    fun pageDecision(policy: ChildPolicy, url: String, titleOrVisibleText: String): EnforcementDecision {
        val page = "$url $titleOrVisibleText".lowercase()
        policy.blockedDomains.firstOrNull { domain ->
            val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
            host.equals(domain, true) || host.endsWith(".$domain", true)
        }?.let { return EnforcementDecision(true, "This website is blocked") }
        if (policy.blockYoutubeShorts && (url.contains("youtube.com/shorts", true) || url.contains("youtu.be/shorts", true))) {
            return EnforcementDecision(true, "YouTube Shorts are blocked")
        }
        policy.blockedKeywords.firstOrNull { keyword -> keyword.trim().isNotEmpty() && page.contains(keyword.lowercase()) }
            ?.let { return EnforcementDecision(true, "Blocked by a family keyword rule") }
        return EnforcementDecision(false)
    }

    private fun isWithinSchedule(now: LocalDateTime, schedule: com.guardianlink.model.ScheduleRule): Boolean {
        val currentTime = now.toLocalTime()
        if (schedule.start <= schedule.end) {
            return now.dayOfWeek in schedule.days && currentTime >= schedule.start && currentTime < schedule.end
        }
        // An overnight rule belongs to its start day: Friday 21:00–07:00 remains active Saturday at 03:00.
        return (now.dayOfWeek in schedule.days && currentTime >= schedule.start) ||
            (now.minusDays(1).dayOfWeek in schedule.days && currentTime < schedule.end)
    }

    /** Normal app mode cannot safely lock the launcher, system UI, emergency dialer, or Guardian Link itself. */
    private fun isEssentialPackage(packageName: String): Boolean = packageName == "com.guardianlink" || packageName in setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.dialer", "com.google.android.dialer"
    )
}
