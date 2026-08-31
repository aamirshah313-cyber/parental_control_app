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

/** Downloads the active policy and applies every queued command. Local enforcement remains active if this fails. */
class PolicySynchronizer(private val context: Context) {
    fun sync(): Boolean = synchronized(syncLock) { runCatching {
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
        val store = PolicyStore(context)
        api.fetchActivePolicy()?.let { raw -> store.saveFromCloudJson(raw) }
        val updatedPolicy = store.load()
        api.reportHealth(battery, context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE).getBoolean("permissions_unlocked", false), usageAllowed, if (usageAllowed) UsageMonitor(context).totalScreenMinutesToday() else 0, updatedPolicy.version)
        val childLocationEnabled = context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE).getBoolean("child_location_enabled", false)
        if (!updatedPolicy.locationEnabled || !childLocationEnabled) context.stopService(Intent(context, LocationService::class.java))
        else if (context.getSharedPreferences("guardian_child_setup", Context.MODE_PRIVATE).getBoolean("permissions_unlocked", false) &&
            (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            // Once the child has completed step 5 and explicitly granted location, future parent enable/disable changes can work remotely.
            runCatching { context.startForegroundService(Intent(context, LocationService::class.java)) }
        }
        api.fetchPendingCommands().forEach { command ->
            val status = if (session.hasHandled(command.id)) "applied" else when {
                command.expiresAtEpochMs != null && command.expiresAtEpochMs < System.currentTimeMillis() -> "expired"
                command.type == "pause" -> { store.setPause(command.expiresAtEpochMs, command.scope == "all_child_apps"); "applied" }
                command.type == "resume" -> { store.resume(); "applied" }
                command.type == "grant_time" -> {
                    // A bonus extends an existing daily limit; it must never create one by itself.
                    if (store.load().dailyScreenLimitMinutes > 0) store.addDailyBonusMinutes(command.payload.optInt("minutes", 0))
                    "applied"
                }
                command.type == "refresh_policy" -> "applied"
                else -> "received"
            }
            // Persist before sending receipts: a retry can then complete the receipt without
            // applying a grant or pause twice after an intermittent network failure.
            if (!session.hasHandled(command.id)) session.markHandled(command.id)
            api.acknowledge(command.id, status)
            api.markCommandProcessed(command.id, status)
        }
        true
    }.getOrDefault(false) }

    private companion object { val syncLock = Any() }
}
