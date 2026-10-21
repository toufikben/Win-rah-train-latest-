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

/** Features that may need GPS at the same time. Updates stop only when none remains. */
enum class GpsOwner { UI, BROADCAST_SERVICE, DESTINATION_ALARM }

/** Pure reference set; kept free of Android types so it is unit-testable. */
class GpsOwnerSet {
    private val owners = linkedSetOf<GpsOwner>()

    /** Returns true when this is the first owner (updates must start). */
    fun add(owner: GpsOwner): Boolean {
        val wasEmpty = owners.isEmpty()
        owners.add(owner)
        return wasEmpty
    }

    /** Returns true when no owner remains (updates must stop). */
    fun remove(owner: GpsOwner): Boolean {
        owners.remove(owner)
        return owners.isEmpty()
    }

    operator fun contains(owner: GpsOwner): Boolean = owner in owners

    val isEmpty: Boolean get() = owners.isEmpty()
}

class LocationTrackingCoordinator(context: Context) {
    private val tracker = LocationTracker(context.applicationContext)
    private val owners = GpsOwnerSet()
    val gpsData: StateFlow<LiveGpsData> = tracker.gpsData
    val isTracking: StateFlow<Boolean> = tracker.isTracking

    /** Registers [owner] and makes sure updates run. Returns false if updates could not start. */
    @Synchronized
    fun acquire(owner: GpsOwner): Boolean {
        owners.add(owner)
        if (tracker.isTracking.value) return true
        val started = tracker.startLocationUpdates()
        if (!started) owners.remove(owner)
        return started
    }

    /** Unregisters [owner]; updates stop only when it was the last one. */
    @Synchronized
    fun release(owner: GpsOwner) {
        if (owners.remove(owner)) tracker.stopLocationUpdates()
    }

    @Synchronized
    fun holds(owner: GpsOwner): Boolean = owner in owners

    /** Acquires for [owner] and waits for the first real, non-zero GPS fix. */
    suspend fun acquireAndAwaitFirstFix(owner: GpsOwner, timeoutMs: Long = 45_000L): FirstFixResult {
        if (!acquire(owner)) return FirstFixResult.START_FAILED

        val current = gpsData.value
        if (current.isGpsActive && current.latitude != 0.0 && current.longitude != 0.0) {
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

    fun close() {
        tracker.close()
    }
}
