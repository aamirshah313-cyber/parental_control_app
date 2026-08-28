package com.guardianlink.enforcement

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.ReportedAppPayload
import com.guardianlink.sync.SupabaseApi

/** Reports launchable apps and package-install broadcasts without using QUERY_ALL_PACKAGES. */
class AppInventoryReporter(private val context: Context) {
    private val prefs = context.getSharedPreferences("guardian_app_inventory", Context.MODE_PRIVATE)

    fun reportIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("last_report_at", 0) < SIX_HOURS) return
        if (report(launchableApps())) prefs.edit().putLong("last_report_at", System.currentTimeMillis()).apply()
    }

    fun reportPackage(packageName: String, pendingApproval: Boolean) {
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return
        report(listOf(ReportedAppPayload(packageName, context.packageManager.getApplicationLabel(info).toString().ifBlank { packageName }, pendingApproval)))
    }

    private fun report(apps: List<ReportedAppPayload>): Boolean {
        val session = DeviceSessionStore(context)
        return session.takeIf { it.isPaired() }?.let { SupabaseApi(it.deviceId!!, it.accessToken!!).upsertReportedApps(apps) } ?: false
    }

    private fun launchableApps(): List<ReportedAppPayload> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .filter { info -> info.packageName != context.packageName && info.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .distinctBy { it.packageName }
            .map { info -> ReportedAppPayload(info.packageName, context.packageManager.getApplicationLabel(info).toString().ifBlank { info.packageName }) }
    }

    private companion object { const val SIX_HOURS = 6 * 60 * 60 * 1000L }
}
