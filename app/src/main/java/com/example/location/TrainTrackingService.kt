package com.example.location

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.LiveTrainRepository
import com.example.data.local.LocalMonitorSessionStore
import com.example.data.local.PersistentAppLogger
import com.example.model.MonitorBinding
import com.example.model.TrainDirection
import com.example.data.remote.dto.ObservationRequest
import com.example.notification.TrainNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Keeps an active, user-started broadcast alive while the app is backgrounded. */
class TrainTrackingService : Service() {
    companion object {
        const val ACTION_START = "dz.winrah.trainradar.action.START_TRACKING"
        const val ACTION_STOP = "dz.winrah.trainradar.action.STOP_TRACKING"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_LINE_ID = "line_id"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TRAIN_ID = "train_id"
        const val TAG = "WinRahTrackingService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var repository: LiveTrainRepository
    private lateinit var sessionStore: LocalMonitorSessionStore
    private var observationJob: Job? = null
    private var serviceOwnsLocation = false
    private var lastSentAt = 0L
    private var initialized = false

    override fun onCreate() {
        super.onCreate()
        log("SERVICE_ON_CREATE")
        try {
            TrainNotificationHelper.initNotificationChannels(this)
            locationCoordinator = LocationTrackingCoordinatorProvider.get(this)
            repository = LiveTrainRepository(context = this)
            sessionStore = LocalMonitorSessionStore(this)
            initialized = true
            log("SERVICE_INITIALIZED")
        } catch (error: Exception) {
            log("SERVICE_INITIALIZATION_FAILED", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("SERVICE_ON_START_COMMAND action=${intent?.action} startId=$startId")
        if (!initialized) {
            log("START_REJECTED_NOT_INITIALIZED")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            log("STOP_REQUEST_RECEIVED")
            runCatching { sessionStore.clear() }
                .onFailure { Log.w(TAG, "Unable to clear persisted session", it) }
            stopServiceSafely(startId, clearSession = false)
            return START_NOT_STICKY
        }

        val persisted = runCatching { sessionStore.load() }
            .onFailure { Log.e(TAG, "Unable to load persisted session", it) }
            .getOrNull()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: persisted?.sessionId
        val lineId = intent?.getStringExtra(EXTRA_LINE_ID) ?: persisted?.lineId
        val direction = runCatching {
            TrainDirection.valueOf(intent?.getStringExtra(EXTRA_DIRECTION) ?: persisted?.direction?.name.orEmpty())
        }.getOrNull()
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: persisted?.tripId
        val trainId = intent?.getStringExtra(EXTRA_TRAIN_ID) ?: persisted?.trainId
        log("BINDING_READ sessionPresent=${!sessionId.isNullOrBlank()} linePresent=${!lineId.isNullOrBlank()} direction=${direction?.name}")
        if (sessionId.isNullOrBlank() || lineId.isNullOrBlank() || direction == null) {
            log("START_REJECTED_INVALID_BINDING")
            stopServiceSafely(startId, clearSession = true)
            return START_NOT_STICKY
        }
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            log("START_REJECTED_LOCATION_PERMISSION_MISSING")
            stopServiceSafely(startId, clearSession = true)
            return START_NOT_STICKY
        }

        log("LOCATION_PERMISSION_OK")
        val notification = runCatching {
            TrainNotificationHelper.buildOngoingTripNotification(
                this,
                currentSpeedKmh = 0f,
                nextStationName = "جارٍ التحقق من الجلسة",
                isTunnel = false,
            )
        }.getOrElse {
            log("NOTIFICATION_BUILD_FAILED", it)
            stopServiceSafely(startId, clearSession = false)
            return START_NOT_STICKY
        }
        val foregroundStarted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TrainNotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TrainNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
            }
        }.isSuccess
        if (!foregroundStarted) {
            log("START_FOREGROUND_FAILED")
            stopServiceSafely(startId, clearSession = false)
            return START_NOT_STICKY
        }

        log("START_FOREGROUND_SUCCESS")
        serviceScope.launch {
            try {
                log("SESSION_RESUME_BEGIN sessionId=$sessionId")
            val validation = runCatching {
                repository.resumeMonitorSession(sessionId, lineId, direction, tripId, trainId)
            }
            if (validation.isFailure) {
                val message = validation.exceptionOrNull()?.message.orEmpty()
                if (message.contains("HTTP 404") || message.contains("HTTP 409")) {
                    sessionStore.clear()
                }
                stopSelf()
                return@launch
            }
            val verified = validation.getOrThrow()
            log("SESSION_RESUME_SUCCESS status=${verified.status}")
            if (verified.id != sessionId || verified.lineId != lineId || verified.direction != direction.name || verified.tripId != tripId || verified.trainId != trainId || verified.status !in setOf("STARTING", "ACTIVE")) {
                sessionStore.clear()
                stopSelf()
                return@launch
            }
            sessionStore.save(MonitorBinding(sessionId, lineId, direction, tripId, trainId))

            val wasAlreadyTracking = locationCoordinator.isTracking.value
            log("LOCATION_START_BEGIN")
            if (!locationCoordinator.start()) {
                log("LOCATION_START_RETURNED_FALSE")
                stopSelf()
                return@launch
            }
            serviceOwnsLocation = !wasAlreadyTracking
            log("LOCATION_START_SUCCESS serviceOwnsLocation=$serviceOwnsLocation")
            observationJob?.cancel()
            observationJob = launch {
                locationCoordinator.gpsData
                    .filter { it.isGpsActive && it.latitude != 0.0 && it.longitude != 0.0 }
                    .collect { gps ->
                        val now = System.currentTimeMillis()
                        if (now - lastSentAt < 10_000L) return@collect
                        runCatching {
                            log("OBSERVATION_SEND_BEGIN accuracy=${gps.accuracyMeters}")
                            val response = repository.submitObservation(
                                ObservationRequest(
                                    sessionId = sessionId,
                                    lineId = lineId,
                                    direction = direction.name,
                                    tripId = tripId,
                                    trainId = trainId,
                                    latitude = gps.latitude,
                                    longitude = gps.longitude,
                                    accuracy = gps.accuracyMeters,
                                    speed = gps.speedKmh / 3.6f,
                                    heading = null,
                                    timestamp = if (gps.timestamp > 0L) gps.timestamp else now,
                                )
                            )
                            if (response["accepted"] != true) {
                                error("observation_not_accepted")
                            }
                            lastSentAt = now
                            log("OBSERVATION_SEND_SUCCESS")
                        }.onFailure { error ->
                            log("OBSERVATION_SEND_FAILED", error)
                        }
                    }
            }
            } catch (error: Exception) {
                log("BACKGROUND_TRACKING_FAILED", error)
                stopServiceSafely(startId, clearSession = false)
            }
        }
        // Android can recreate the service after process death and redeliver the binding intent.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        log("SERVICE_ON_DESTROY")
        observationJob?.cancel()
        observationJob = null
        if (initialized) {
            runCatching { if (serviceOwnsLocation) locationCoordinator.stop() }
                .onFailure { Log.w(TAG, "Unable to stop location updates", it) }
            runCatching { TrainNotificationHelper.clearOngoingNotification(this) }
                .onFailure { Log.w(TAG, "Unable to clear foreground notification", it) }
            runCatching { sessionStore.close() }
                .onFailure { Log.w(TAG, "Unable to close local session store", it) }
        }
        serviceScope.cancel()
        initialized = false
        super.onDestroy()
    }

    private fun stopServiceSafely(startId: Int, clearSession: Boolean) {
        if (clearSession && initialized) {
            runCatching { sessionStore.clear() }
                .onFailure { Log.w(TAG, "Unable to clear local session during stop", it) }
        }
        runCatching { stopForeground(true) }
            .onFailure { Log.w(TAG, "Unable to stop foreground state", it) }
        runCatching { stopSelf(startId) }
            .onFailure { Log.w(TAG, "Unable to stop service", it) }
    }

    private fun log(event: String, error: Throwable? = null) {
        PersistentAppLogger.write(event, error)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
