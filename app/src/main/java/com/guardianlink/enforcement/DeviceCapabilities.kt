package com.guardianlink.enforcement

import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * Honest capability detection for parent-approved app actions. Guardian Link ships as a
 * standard app; it is never registered as an Android Device Owner/Device Admin by this build,
 * so it cannot silently install, hide, or disable packages at the OS level. This object exists
 * so every screen that offers install/unblock/enable can check the real privilege level
 * instead of a screen quietly implying a guarantee Android will not actually honor.
 */
object DeviceCapabilities {
    fun isDeviceOwner(context: Context): Boolean = runCatching {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        dpm.isDeviceOwnerApp(context.packageName)
    }.getOrDefault(false)

    /** Plain-language statement of what an approval can actually do at the current privilege level. */
    fun enforcementDescription(context: Context): String = if (isDeviceOwner(context))
        "Device management is active on this phone: approved changes can be enforced by Android directly."
    else
        "Standard mode (no device management enrolled): an approval lifts Guardian Link's own app-level block. Android's install/enable/disable system itself is not controlled by this app."
}
