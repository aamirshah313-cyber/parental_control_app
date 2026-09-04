package com.guardianlink.model

import java.time.Instant

/** Pure logic shared by ApprovalRequestsActivity (parent) and ChildAppRequestsActivity (child),
 * kept free of Android framework types so it can run in a plain JVM unit test. */
object AppActionRequestStatus {
    enum class AppState { BLOCKED, AWAITING_APPROVAL, ALLOWED }

    /** A request past its expiry with no decision reads as expired on both screens even though
     * the stored row still says 'pending' -- no background job flips it. */
    fun isExpired(status: String, expiresAt: String, now: Instant = Instant.now()): Boolean {
        if (status != "pending") return false
        val expiry = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return false
        return now.isAfter(expiry)
    }

    fun effectiveStatus(status: String, expiresAt: String, now: Instant = Instant.now()): String =
        if (isExpired(status, expiresAt, now)) "expired" else status

    fun classify(packageName: String, blockedPackages: Set<String>, approvedPackages: Set<String>, pendingApproval: Boolean): AppState = when {
        pendingApproval && packageName !in approvedPackages -> AppState.AWAITING_APPROVAL
        packageName in blockedPackages -> AppState.BLOCKED
        else -> AppState.ALLOWED
    }

    /** null means the app needs no request right now -- it is already usable. */
    fun requestableAction(state: AppState): String? = when (state) {
        AppState.AWAITING_APPROVAL -> "enable"
        AppState.BLOCKED -> "unblock"
        AppState.ALLOWED -> null
    }
}
