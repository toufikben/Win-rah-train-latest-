package com.example

import com.example.location.RestoreDecision
import com.example.location.SessionRestorePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestorePolicyTest {
    private val hour = 60 * 60 * 1000L
    private val now = 100 * hour

    @Test
    fun runningServiceIsAttachedNotRestarted() {
        assertEquals(RestoreDecision.ATTACH_TO_RUNNING, SessionRestorePolicy.decide(now - 10 * hour, now, serviceRunning = true))
    }

    @Test
    fun recentSessionAsksTheUser() {
        assertEquals(RestoreDecision.ASK_USER, SessionRestorePolicy.decide(now - hour, now, serviceRunning = false))
    }

    @Test
    fun staleOrUnknownAgeSessionIsDiscarded() {
        assertEquals(RestoreDecision.DISCARD, SessionRestorePolicy.decide(now - 4 * hour, now, serviceRunning = false))
        assertEquals(RestoreDecision.DISCARD, SessionRestorePolicy.decide(0L, now, serviceRunning = false))
        assertEquals(RestoreDecision.DISCARD, SessionRestorePolicy.decide(now + hour, now, serviceRunning = false))
    }

    @Test
    fun runningSessionExpiresAfterSilenceOrMaxAge() {
        val minute = 60_000L
        assertFalse(SessionRestorePolicy.isExpired(now - 10 * minute, now - minute, now))
        assertTrue(SessionRestorePolicy.isExpired(now - hour, now - 31 * minute, now))
        assertTrue(SessionRestorePolicy.isExpired(now - 31 * minute, null, now))
        assertTrue(SessionRestorePolicy.isExpired(now - 4 * hour, now - minute, now))
    }
}
