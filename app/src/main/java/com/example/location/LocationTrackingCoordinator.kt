package com.example.location

import android.content.Context
import com.example.model.LiveGpsData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Single owner of the fused location subscription for foreground and background consumers. */
enum class FirstFixResult {
    RECEIVED,
    START_FAILED,
    TIMEOUT,
}

class LocationTrackingCoordinator(context: Context) {
    private val tracker = LocationTracker(context.applicationContext)
    val gpsData: StateFlow<LiveGpsData> = tracker.gpsData
    val isTracking: StateFlow<Boolean> = tracker.isTracking

    @Synchronized
    fun start(): Boolean = if (tracker.isTracking.value) true else tracker.startLocationUpdates()

    /** Starts real location updates and waits for the first non-zero GPS fix. */
    suspend fun startAndAwaitFirstFix(timeoutMs: Long = 45_000L): FirstFixResult {
        if (!start()) return FirstFixResult.START_FAILED

        if (gpsData.value.isGpsActive &&
            gpsData.value.latitude != 0.0 &&
            gpsData.value.longitude != 0.0
        ) {
            return FirstFixResult.RECEIVED
        }

        return withTimeoutOrNull(timeoutMs) {
            gpsData.first { gps ->
                gps.isGpsActive &&
                    gps.latitude != 0.0 &&
                    gps.longitude != 0.0 &&
                    gps.timestamp > 0L
            }
            FirstFixResult.RECEIVED
        } ?: FirstFixResult.TIMEOUT
    }

    /**
     * Cancels any existing fused-location subscription and starts a fresh one.
     * Used by the foreground service when it takes ownership after restoration.
     */
    @Synchronized
    fun restartAsServiceOwner(): Boolean {
        tracker.stopLocationUpdates()
        return tracker.startLocationUpdates()
    }

    @Synchronized
    fun stop() {
        tracker.stopLocationUpdates()
    }

    fun close() {
        tracker.close()
    }
}
