package com.guardianlink.sync

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import com.guardianlink.enforcement.LocationService
import com.guardianlink.enforcement.AppInventoryReporter
import com.guardianlink.policy.PolicyStore
import com.guardianlink.enforcement.UsageMonitor
import android.app.AppOpsManager

/** Downloads only the active policy and latest command. Local enforcement remains active if this fails. */
class PolicySynchronizer(private val context: Context) {
    fun sync(): Boolean = runCatching {
        val session = DeviceSessionStore(context)
        if (!session.isPaired()) return true
        val api = session.api() ?: return false
        // Do not tell the child that rules are synchronized if authentication, RLS, or the network rejected the device session.
        if (!api.verifyDeviceSession() || !api.touchLastSeen()) return false
        AppInventoryReporter(context).reportIfDue()
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?.let { it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1).takeIf { value -> value >= 0 } }
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val usageAllowed = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        api.reportHealth(battery, context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE).getBoolean("permissions_unlocked", false), usageAllowed, if (usageAllowed) UsageMonitor(context).totalScreenMinutesToday() else 0)
        val store = PolicyStore(context)
        api.fetchActivePolicy()?.let { raw -> store.saveFromCloudJson(raw) }
        val updatedPolicy = store.load()
        if (!updatedPolicy.locationEnabled) context.stopService(Intent(context, LocationService::class.java))
        else if (context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE).getBoolean("permissions_unlocked", false) &&
            (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            // Once the child has completed step 5 and explicitly granted location, future parent enable/disable changes can work remotely.
            runCatching { context.startForegroundService(Intent(context, LocationService::class.java)) }
        }
        api.fetchLatestCommand()?.takeIf { it.id != session.lastHandledCommandId }?.let { command ->
            when {
                command.expiresAtEpochMs != null && command.expiresAtEpochMs < System.currentTimeMillis() -> api.acknowledge(command.id, "expired")
                command.type == "pause" -> { store.setPause(command.expiresAtEpochMs, command.scope == "all_child_apps"); api.acknowledge(command.id, "applied") }
                command.type == "resume" -> { store.resume(); api.acknowledge(command.id, "applied") }
                command.type == "grant_time" -> { store.addDailyBonusMinutes(command.payload.optInt("minutes", 0)); api.acknowledge(command.id, "applied") }
                else -> api.acknowledge(command.id, "received")
            }
            session.markHandled(command.id)
        }
        true
    }.getOrDefault(false)
}
