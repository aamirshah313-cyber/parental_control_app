package com.guardianlink.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardianlink.policy.PolicyStore
import com.guardianlink.sync.DeviceSessionStore
import com.guardianlink.sync.SupabaseApi
import org.json.JSONObject

/** Standard mode cannot stop installation, but blocks use of a new app until parent approval. */
class PackageChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return
        val appName = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val store = PolicyStore(context)
        val policy = store.load()
        if (policy.requireAppApproval && packageName !in policy.approvedPackages) {
            store.markPendingApproval(packageName)
        }
        val pending = goAsync()
        Thread {
            val session = DeviceSessionStore(context)
            AppInventoryReporter(context).reportPackage(packageName, policy.requireAppApproval && packageName !in policy.approvedPackages)
            session.api()?.postEvent("app_installed", JSONObject().apply {
                put("package_name", packageName)
                put("app_name", appName)
                put("pending_approval", policy.requireAppApproval && packageName !in policy.approvedPackages)
            })
            pending.finish()
        }.start()
    }
}
