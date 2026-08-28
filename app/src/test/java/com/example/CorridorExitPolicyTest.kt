package com.example

import com.example.model.CorridorExitPolicy
import com.example.model.CorridorExitState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorExitPolicyTest {
    private val outsideDistance = CorridorExitPolicy.CORRIDOR_EXIT_DISTANCE_METERS + 1.0

    @Test
    fun doesNotStopBeforeThreeOutsideSamples() {
        var state = CorridorExitState()
        repeat(2) { index ->
            val decision = CorridorExitPolicy.evaluate(
                distanceMeters = outsideDistance,
                isDeadReckoning = false,
                isBroadcasting = true,
                nowMs = index * 15_000L,
                previous = state,
            )
            state = decision.state
            assertFalse(decision.shouldStopBroadcast)
        }
    }

    @Test
    fun doesNotStopBeforeThirtySecondsEvenOnThirdSample() {
        var state = CorridorExitState()
        state = CorridorExitPolicy.evaluate(outsideDistance, false, true, 1_000L, state).state
        state = CorridorExitPolicy.evaluate(outsideDistance, false, true, 10_000L, state).state
        val decision = CorridorExitPolicy.evaluate(outsideDistance, false, true, 20_000L, state)
        assertFalse(decision.shouldStopBroadcast)
    }

    @Test
    fun stopsOnThirdSampleAfterThirtySeconds() {
        var state = CorridorExitState()
        state = CorridorExitPolicy.evaluate(outsideDistance, false, true, 1_000L, state).state
        state = CorridorExitPolicy.evaluate(outsideDistance, false, true, 16_000L, state).state
        val decision = CorridorExitPolicy.evaluate(outsideDistance, false, true, 31_000L, state)
        assertTrue(decision.shouldStopBroadcast)
        assertTrue(decision.state.outsideSampleCount == 0)
    }

    @Test
    fun deadReckoningResetsAndNeverStops() {
        val state = CorridorExitState(2, 1_000L)
        val decision = CorridorExitPolicy.evaluate(
            distanceMeters = outsideDistance,
            isDeadReckoning = true,
            isBroadcasting = true,
            nowMs = 60_000L,
            previous = state,
        )
        assertFalse(decision.shouldStopBroadcast)
        assertTrue(decision.state == CorridorExitState())
    }

    @Test
    fun returningToCorridorResetsTheCounter() {
        val state = CorridorExitState(2, 1_000L)
        val decision = CorridorExitPolicy.evaluate(
            distanceMeters = CorridorExitPolicy.CORRIDOR_EXIT_DISTANCE_METERS,
            isDeadReckoning = false,
            isBroadcasting = true,
            nowMs = 60_000L,
            previous = state,
        )
        assertFalse(decision.shouldStopBroadcast)
        assertTrue(decision.state == CorridorExitState())
    }
}

