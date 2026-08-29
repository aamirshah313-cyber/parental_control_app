package com.guardianlink.enforcement

import android.content.Context
import android.content.Intent
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.ReportedAppPayload
import com.guardianlink.sync.SupabaseApi

/** Reports launchable apps and package-install broadcasts without using QUERY_ALL_PACKAGES. */
class AppInventoryReporter(private val context: Context) {
    private val prefs = context.getSharedPreferences("guardian_app_inventory", Context.MODE_PRIVATE)

    fun reportIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("last_report_at", 0) < SIX_HOURS) return
        reportNow()
    }

    /** User-visible child action: refresh the parent app list immediately, including launchable pre-installed apps. */
    fun reportNow(): Boolean = report(launchableApps()).also { if (it) prefs.edit().putLong("last_report_at", System.currentTimeMillis()).apply() }

    fun reportPackage(packageName: String, pendingApproval: Boolean) {
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return
        report(listOf(ReportedAppPayload(packageName, context.packageManager.getApplicationLabel(info).toString().ifBlank { packageName }, pendingApproval)))
    }

    private fun report(apps: List<ReportedAppPayload>): Boolean {
        val session = DeviceSessionStore(context)
        return session.api()?.upsertReportedApps(apps) ?: false
    }

    private fun launchableApps(): List<ReportedAppPayload> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homePackage = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        return context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            // Family-relevant apps such as YouTube and Chrome can be pre-installed system apps.
            .filter { info -> info.packageName !in ESSENTIAL_PACKAGES && info.packageName != context.packageName && info.packageName != homePackage }
            .distinctBy { it.packageName }
            .map { info -> ReportedAppPayload(info.packageName, context.packageManager.getApplicationLabel(info).toString().ifBlank { info.packageName }) }
    }

    private companion object {
        const val SIX_HOURS = 6 * 60 * 60 * 1000L
        val ESSENTIAL_PACKAGES = setOf("android", "com.android.systemui", "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller")
    }
}
