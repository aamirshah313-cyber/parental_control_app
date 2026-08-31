package com.guardianlink.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardianlink.enforcement.ProtectionService
import com.guardianlink.enforcement.SosAlertService
import com.guardianlink.ui.ParentModeActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                val childSetup = context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE)
                if (DeviceSessionStore(context).isPaired() && childSetup.getBoolean("permissions_unlocked", false)) {
                    // Protection also performs the periodic cloud sync after Android has restarted.
                    runCatching { context.startForegroundService(Intent(context, ProtectionService::class.java)) }
                }
                val alertPrefs = context.getSharedPreferences("guardian_parent_alerts", Context.MODE_PRIVATE)
                if (ParentSessionStore(context).load() != null && alertPrefs.getBoolean("monitoring_enabled", true)) {
                    // Keep SOS, install approvals, and quick-message notifications alive after reboot.
                    runCatching { context.startForegroundService(Intent(context, SosAlertService::class.java)) }
                }
                PolicySynchronizer(context).sync()
            } finally {
                pending.finish()
            }
        }.start()
    }
}
