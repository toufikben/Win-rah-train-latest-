package com.example.location

import android.content.Context
import com.example.model.LiveGpsData
import kotlinx.coroutines.flow.StateFlow

/** Single owner of the fused location subscription for foreground and background consumers. */
class LocationTrackingCoordinator(context: Context) {
    private val tracker = LocationTracker(context.applicationContext)
    val gpsData: StateFlow<LiveGpsData> = tracker.gpsData
    val isTracking: StateFlow<Boolean> = tracker.isTracking

    @Synchronized
    fun start(): Boolean = if (tracker.isTracking.value) true else tracker.startLocationUpdates()

    @Synchronized
    fun stop() {
        tracker.stopLocationUpdates()
    }

    fun close() {
        tracker.close()
    }
}
