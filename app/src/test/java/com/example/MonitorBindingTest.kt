package com.example

import com.example.model.MonitorBinding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorBindingTest {

    @Test
    fun matchingTripAndTrainAreAccepted() {
        val binding = MonitorBinding(
            sessionId = "session-a",
            tripId = "trip-a",
            trainId = "train-a"
        )

        assertTrue(binding.matches(tripId = "trip-a", trainId = "train-a"))
    }

    @Test
    fun crossBindingTripOrTrainIsRejected() {
        val binding = MonitorBinding(
            sessionId = "session-a",
            tripId = "trip-a",
            trainId = "train-a"
        )

        assertFalse(binding.matches(tripId = "trip-b", trainId = "train-a"))
        assertFalse(binding.matches(tripId = "trip-a", trainId = "train-b"))
        assertFalse(binding.matches(tripId = "trip-b", trainId = "train-b"))
    }

    @Test
    fun sessionIdDoesNotAuthorizeDifferentTripAndTrain() {
        val binding = MonitorBinding(
            sessionId = "session-a",
            tripId = "trip-a",
            trainId = "train-a"
        )

        assertFalse(binding.matches(tripId = "trip-b", trainId = "train-b"))
    }
}
