package com.example

import com.example.alerts.WakeAlarmPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeAlarmPolicyTest {
    @Test
    fun thresholdIsTimeBasedWithinSafeBounds() {
        assertEquals(3600.0, WakeAlarmPolicy.thresholdMeters(12.0, 5), 0.01)
        assertEquals(WakeAlarmPolicy.MAX_THRESHOLD_METERS, WakeAlarmPolicy.thresholdMeters(25.0, 10), 0.01)
        assertEquals(WakeAlarmPolicy.MIN_THRESHOLD_METERS, WakeAlarmPolicy.thresholdMeters(0.0, 1), 0.01)
    }

    @Test
    fun progressIsClamped() {
        assertEquals(100, WakeAlarmPolicy.progressPercent(0.0))
        assertEquals(0, WakeAlarmPolicy.progressPercent(25_000.0))
    }
}
