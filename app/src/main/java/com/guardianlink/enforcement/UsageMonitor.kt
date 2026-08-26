package com.guardianlink.enforcement

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class UsageMonitor(context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    fun currentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 15_000, now)
        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = event.packageName
        }
        return latest
    }

    fun usedMinutesToday(packageName: String): Int {
        val start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return (usageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
            .firstOrNull { it.packageName == packageName }?.totalTimeInForeground ?: 0L).div(60_000).toInt()
    }
}
