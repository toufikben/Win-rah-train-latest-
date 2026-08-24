package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.model.LiveGpsData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Collects device location fixes. It never synthesizes coordinates or speed. */
class LocationTracker(context: Context) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _gpsData = MutableStateFlow(LiveGpsData())
    val gpsData: StateFlow<LiveGpsData> = _gpsData.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var watchdogJob: Job? = null
    private var lastFixAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::publishFix)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(): Boolean {
        return try {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L
            ).setMinUpdateDistanceMeters(2f).build()
            client.requestLocationUpdates(request, callback, null)
            _isTracking.value = true
            startWatchdog()
            true
        } catch (_: SecurityException) {
            _isTracking.value = false
            false
        } catch (_: Exception) {
            _isTracking.value = false
            false
        }
    }

    private fun publishFix(location: Location) {
        lastFixAt = System.currentTimeMillis()
        _gpsData.value = LiveGpsData(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
            altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
            timestamp = location.time,
            isGpsActive = true,
            isDeadReckoning = false,
            deadReckoningDurationSec = 0
        )
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(3_000L)
                if (_isTracking.value && (lastFixAt == 0L || System.currentTimeMillis() - lastFixAt > 8_000L)) {
                    _gpsData.value = _gpsData.value.copy(
                        isGpsActive = false,
                        isDeadReckoning = false,
                        deadReckoningDurationSec = 0
                    )
                }
            }
        }
    }

    fun stopLocationUpdates() {
        watchdogJob?.cancel()
        watchdogJob = null
        client.removeLocationUpdates(callback)
        _isTracking.value = false
        _gpsData.value = _gpsData.value.copy(isGpsActive = false, isDeadReckoning = false)
    }

    fun close() {
        stopLocationUpdates()
        scope.cancel()
    }
}
