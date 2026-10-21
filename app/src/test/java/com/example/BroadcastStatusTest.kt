package com.example

import com.example.location.BroadcastState
import com.example.location.ObservationThrottle
import com.example.location.ServerBackoff
import com.example.location.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastStatusTest {
    @Test
    fun sendFailureDoesNotBypassThrottle() {
        val throttle = ObservationThrottle(minIntervalMs = 10_000L)
        assertTrue(throttle.shouldAttempt(0L))
        throttle.onAttempt(0L) // attempt rejected by the server

        assertFalse(throttle.shouldAttempt(2_000L))
        assertFalse(throttle.shouldAttempt(9_999L))
        assertTrue(throttle.shouldAttempt(10_000L))
    }

    @Test
    fun backoffDoublesOnServerUnavailableUpTo60s() {
        val backoff = ServerBackoff()
        val delays = List(7) { backoff.nextDelayMs() }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L), delays)
        backoff.reset()
        assertEquals(5_000L, backoff.nextDelayMs())
    }

    @Test
    fun activeStatesAreOnlyLiveSessionStates() {
        assertTrue(BroadcastState.Starting.isActive)
        assertTrue(BroadcastState.Sending(null, null).isActive)
        assertTrue(BroadcastState.Rejected("x", 0L).isActive)
        assertTrue(BroadcastState.ServerUnavailable(0L, 5_000L).isActive)
        assertFalse(BroadcastState.Idle.isActive)
        assertFalse(BroadcastState.Stopped(StopReason.USER).isActive)
    }
}
