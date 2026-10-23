package com.example.alerts

object WakeAlarmPolicy {
    const val MIN_THRESHOLD_METERS = 1_500.0
    const val MAX_THRESHOLD_METERS = 4_000.0
    private const val FALLBACK_SPEED_MPS = 12.0

    fun thresholdMeters(speedMps: Double, leadMinutes: Int): Double {
        val speed = if (speedMps.isFinite() && speedMps > 3.0) speedMps else FALLBACK_SPEED_MPS
        val minutes = leadMinutes.coerceAtLeast(1)
        return (speed * minutes * 60.0).coerceIn(MIN_THRESHOLD_METERS, MAX_THRESHOLD_METERS)
    }

    fun progressPercent(distanceMeters: Double): Int =
        (100 - (distanceMeters / 20_000.0 * 100).toInt()).coerceIn(0, 100)
}
