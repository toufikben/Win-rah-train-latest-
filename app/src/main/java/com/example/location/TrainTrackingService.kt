package com.example.location

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.data.LiveTrainRepository
import com.example.data.local.LocalMonitorSessionStore
import com.example.model.MonitorBinding
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
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TRAIN_ID = "train_id"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var repository: LiveTrainRepository
    private lateinit var sessionStore: LocalMonitorSessionStore
    private var observationJob: Job? = null
    private var serviceOwnsLocation = false
    private var lastSentAt = 0L

    override fun onCreate() {
        super.onCreate()
        TrainNotificationHelper.initNotificationChannels(this)
        locationCoordinator = LocationTrackingCoordinatorProvider.get(this)
        repository = LiveTrainRepository(context = this)
        sessionStore = LocalMonitorSessionStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessionStore.clear()
            stopSelf()
            return START_NOT_STICKY
        }

        val persisted = sessionStore.load()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: persisted?.sessionId
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: persisted?.tripId
        val trainId = intent?.getStringExtra(EXTRA_TRAIN_ID) ?: persisted?.trainId
        if (sessionId.isNullOrBlank() || tripId.isNullOrBlank() || trainId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = TrainNotificationHelper.buildOngoingTripNotification(
            this,
            currentSpeedKmh = 0f,
            nextStationName = "جارٍ التحقق من الجلسة",
            isTunnel = false,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TrainNotificationHelper.NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(TrainNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
        }

        serviceScope.launch {
            val validation = runCatching {
                repository.resumeMonitorSession(sessionId, tripId, trainId)
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
            if (verified.id != sessionId || verified.tripId != tripId || verified.trainId != trainId || verified.status !in setOf("STARTING", "ACTIVE")) {
                sessionStore.clear()
                stopSelf()
                return@launch
            }
            sessionStore.save(MonitorBinding(sessionId, tripId, trainId))

            val wasAlreadyTracking = locationCoordinator.isTracking.value
            if (!locationCoordinator.start()) {
                stopSelf()
                return@launch
            }
            serviceOwnsLocation = !wasAlreadyTracking
            observationJob?.cancel()
            observationJob = launch {
                locationCoordinator.gpsData
                    .filter { it.isGpsActive && it.latitude != 0.0 && it.longitude != 0.0 }
                    .collect { gps ->
                        val now = System.currentTimeMillis()
                        if (now - lastSentAt < 10_000L) return@collect
                        runCatching {
                            val response = repository.submitObservation(
                                ObservationRequest(
                                    sessionId = sessionId,
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
                        }
                    }
            }
        }
        // Android can recreate the service after process death and redeliver the binding intent.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        observationJob?.cancel()
        observationJob = null
        if (serviceOwnsLocation) locationCoordinator.stop()
        serviceScope.cancel()
        TrainNotificationHelper.clearOngoingNotification(this)
        sessionStore.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
