package com.guardianlink.sync

import android.content.Context
import android.content.Intent
import com.guardianlink.enforcement.LocationService
import com.guardianlink.enforcement.AppInventoryReporter
import com.guardianlink.policy.PolicyStore

/** Downloads only the active policy and latest command. Local enforcement remains active if this fails. */
class PolicySynchronizer(private val context: Context) {
    fun sync(): Boolean = runCatching {
        val session = DeviceSessionStore(context)
        if (!session.isPaired()) return true
        val api = session.api() ?: return false
        api.touchLastSeen()
        AppInventoryReporter(context).reportIfDue()
        val store = PolicyStore(context)
        api.fetchActivePolicy()?.let { raw -> store.saveFromCloudJson(raw) }
        if (!store.load().locationEnabled) context.stopService(Intent(context, LocationService::class.java))
        api.fetchLatestCommand()?.takeIf { it.id != session.lastHandledCommandId }?.let { command ->
            when {
                command.expiresAtEpochMs != null && command.expiresAtEpochMs < System.currentTimeMillis() -> api.acknowledge(command.id, "expired")
                command.type == "pause" -> { store.setPause(command.expiresAtEpochMs, command.scope == "all_child_apps"); api.acknowledge(command.id, "applied") }
                command.type == "resume" -> { store.resume(); api.acknowledge(command.id, "applied") }
                else -> api.acknowledge(command.id, "received")
            }
            session.markHandled(command.id)
        }
        true
    }.getOrDefault(false)
}
