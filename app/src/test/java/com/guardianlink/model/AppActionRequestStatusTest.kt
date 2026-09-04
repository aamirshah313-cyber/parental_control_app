package com.guardianlink.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Covers the pure approval/location-adjacent logic that doesn't need Android or a device --
 * see docs/FEATURES_APPROVAL_LOCATION_GUEST.md for what still needs a real device. */
class AppActionRequestStatusTest {
    private val now = Instant.parse("2026-09-04T12:00:00Z")

    @Test
    fun `pending request before its expiry is not expired`() {
        val expiresAt = now.plus(Duration.ofHours(1)).toString()
        assertFalse(AppActionRequestStatus.isExpired("pending", expiresAt, now))
        assertEquals("pending", AppActionRequestStatus.effectiveStatus("pending", expiresAt, now))
    }

    @Test
    fun `pending request past its expiry is expired`() {
        val expiresAt = now.minus(Duration.ofMinutes(1)).toString()
        assertTrue(AppActionRequestStatus.isExpired("pending", expiresAt, now))
        assertEquals("expired", AppActionRequestStatus.effectiveStatus("pending", expiresAt, now))
    }

    @Test
    fun `a decided request is never reported as expired, even past its expiry timestamp`() {
        val expiresAt = now.minus(Duration.ofDays(1)).toString()
        assertFalse(AppActionRequestStatus.isExpired("approved", expiresAt, now))
        assertFalse(AppActionRequestStatus.isExpired("denied", expiresAt, now))
        assertEquals("approved", AppActionRequestStatus.effectiveStatus("approved", expiresAt, now))
    }

    @Test
    fun `an unparseable expiry timestamp is treated as not expired rather than crashing`() {
        assertFalse(AppActionRequestStatus.isExpired("pending", "not-a-timestamp", now))
    }

    @Test
    fun `a blocked package classifies as BLOCKED and requests unblock`() {
        val state = AppActionRequestStatus.classify("com.example.game", setOf("com.example.game"), emptySet(), pendingApproval = false)
        assertEquals(AppActionRequestStatus.AppState.BLOCKED, state)
        assertEquals("unblock", AppActionRequestStatus.requestableAction(state))
    }

    @Test
    fun `a newly installed unapproved package classifies as AWAITING_APPROVAL and requests enable`() {
        val state = AppActionRequestStatus.classify("com.example.new", emptySet(), emptySet(), pendingApproval = true)
        assertEquals(AppActionRequestStatus.AppState.AWAITING_APPROVAL, state)
        assertEquals("enable", AppActionRequestStatus.requestableAction(state))
    }

    @Test
    fun `pending approval stops applying once the package is approved`() {
        val state = AppActionRequestStatus.classify("com.example.new", emptySet(), setOf("com.example.new"), pendingApproval = true)
        assertEquals(AppActionRequestStatus.AppState.ALLOWED, state)
    }

    @Test
    fun `an allowed package needs no request`() {
        val state = AppActionRequestStatus.classify("com.example.ok", emptySet(), emptySet(), pendingApproval = false)
        assertEquals(AppActionRequestStatus.AppState.ALLOWED, state)
        assertNull(AppActionRequestStatus.requestableAction(state))
    }

    @Test
    fun `blocked takes priority over an unrelated pending-approval flag being false`() {
        val state = AppActionRequestStatus.classify("com.example.game", setOf("com.example.game"), setOf("com.example.game"), pendingApproval = false)
        assertEquals(AppActionRequestStatus.AppState.BLOCKED, state)
    }
}
