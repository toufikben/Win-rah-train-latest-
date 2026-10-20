package com.example.location

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.LiveTrainRepository
import com.example.data.local.LocalMonitorSessionStore
import com.example.data.local.PersistentAppLogger
import com.example.data.remote.dto.ObservationRequest
import com.example.model.MonitorBinding
import com.example.model.TrainDirection
import com.example.notification.TrainNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

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
        private const val EXPIRY_CHECK_INTERVAL_MS = 60_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var repository: LiveTrainRepository
    private lateinit var sessionStore: LocalMonitorSessionStore
    private var sessionJob: Job? = null
    private var initialized = false
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        log("SERVICE_ON_CREATE")
        try {
            TrainNotificationHelper.initNotificationChannels(this)
            locationCoordinator = LocationTrackingCoordinatorProvider.get(this)
            repository = LiveTrainRepository(context = this)
            sessionStore = LocalMonitorSessionStore(this)
            initialized = true
        } catch (error: Exception) {
            log("SERVICE_INITIALIZATION_FAILED", error)
            BroadcastStatusRepository.update(BroadcastState.Stopped(StopReason.ERROR))
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("SERVICE_ON_START_COMMAND action=${intent?.action} startId=$startId")
        if (!initialized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            finish(StopReason.USER, clearSession = true)
            return START_NOT_STICKY
        }

        val persisted = runCatching { sessionStore.load() }.getOrNull()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: persisted?.sessionId
        val lineId = intent?.getStringExtra(EXTRA_LINE_ID) ?: persisted?.lineId
        val direction = runCatching {
            TrainDirection.valueOf(intent?.getStringExtra(EXTRA_DIRECTION) ?: persisted?.direction?.name.orEmpty())
        }.getOrNull()
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: persisted?.tripId
        val trainId = intent?.getStringExtra(EXTRA_TRAIN_ID) ?: persisted?.trainId
        if (sessionId.isNullOrBlank() || lineId.isNullOrBlank() || direction == null) {
            log("START_REJECTED_INVALID_BINDING")
            finish(StopReason.SESSION_INVALID, clearSession = true)
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            log("START_REJECTED_LOCATION_PERMISSION_MISSING")
            finish(StopReason.PERMISSION, clearSession = true)
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            finish(StopReason.ERROR, clearSession = false)
            return START_NOT_STICKY
        }

        val binding = MonitorBinding(sessionId, lineId, direction, tripId, trainId)
        if (sessionJob?.isActive == true) return START_REDELIVER_INTENT
        BroadcastStatusRepository.update(BroadcastState.Starting)
        sessionJob = serviceScope.launch { runSession(binding) }
        // Android can recreate the service after process death and redeliver the binding intent.
        return START_REDELIVER_INTENT
    }

    private suspend fun runSession(binding: MonitorBinding) {
        if (!resumeWithRetry(binding)) return
        sessionStore.save(binding)

        if (!locationCoordinator.acquire(GpsOwner.BROADCAST_SERVICE)) {
            log("LOCATION_START_FAILED")
            finish(StopReason.PERMISSION, clearSession = true)
            return
        }

        val startedAtMs = System.currentTimeMillis()
        var lastAcceptedAtMs: Long? = null
        val throttle = ObservationThrottle()
        val backoff = ServerBackoff()
        var serverRetryAtMs = 0L

        serviceScope.launch {
            while (isActive && !stopping) {
                delay(EXPIRY_CHECK_INTERVAL_MS)
                if (SessionRestorePolicy.isExpired(startedAtMs, lastAcceptedAtMs, System.currentTimeMillis())) {
                    log("SESSION_EXPIRED")
                    finish(StopReason.EXPIRED, clearSession = true, endOnServer = true, sessionId = binding.sessionId)
                }
            }
        }

        BroadcastStatusRepository.update(BroadcastState.Sending(lastAcceptedAtMs = null, lastAttemptAtMs = null))
        locationCoordinator.gpsData
            .filter { it.isGpsActive && it.latitude != 0.0 && it.longitude != 0.0 }
            .collect { gps ->
                val now = System.currentTimeMillis()
                val accuracy = gps.accuracyMeters
                if (stopping || accuracy == null || gps.isMock) return@collect
                if (now < serverRetryAtMs || !throttle.shouldAttempt(now)) return@collect
                throttle.onAttempt(now)
                try {
                    val response = repository.submitObservation(
                        ObservationRequest(
                            sessionId = binding.sessionId,
                            lineId = binding.lineId,
                            direction = binding.direction.name,
                            tripId = binding.tripId,
                            trainId = binding.trainId,
                            latitude = gps.latitude,
                            longitude = gps.longitude,
                            accuracy = accuracy,
                            speed = gps.speedKmh / 3.6f,
                            heading = null,
                            timestamp = if (gps.timestamp > 0L) gps.timestamp else now,
                        )
                    )
                    backoff.reset()
                    if (response["accepted"] == true) {
                        lastAcceptedAtMs = now
                        BroadcastStatusRepository.update(BroadcastState.Sending(lastAcceptedAtMs, now))
                    } else {
                        val reason = (response["rejection_reason"] ?: response["reason"] ?: "unknown").toString()
                        BroadcastStatusRepository.update(BroadcastState.Rejected(reason, now))
                    }
                } catch (error: HttpException) {
                    log("OBSERVATION_HTTP_${error.code()}", error)
                    when (error.code()) {
                        404, 409 -> finish(StopReason.SESSION_INVALID, clearSession = true)
                        in 500..599, 429 -> {
                            val waitMs = backoff.nextDelayMs()
                            serverRetryAtMs = now + waitMs
                            BroadcastStatusRepository.update(BroadcastState.ServerUnavailable(now, waitMs))
                        }
                        else -> BroadcastStatusRepository.update(BroadcastState.Rejected("http_${error.code()}", now))
                    }
                } catch (error: IOException) {
                    val waitMs = backoff.nextDelayMs()
                    serverRetryAtMs = now + waitMs
                    BroadcastStatusRepository.update(BroadcastState.ServerUnavailable(now, waitMs))
                }
            }
    }

    /** Validates the session with the server, retrying while the server is unreachable. */
    private suspend fun resumeWithRetry(binding: MonitorBinding): Boolean {
        val backoff = ServerBackoff()
        while (!stopping) {
            try {
                val verified = repository.resumeMonitorSession(
                    binding.sessionId, binding.lineId, binding.direction, binding.tripId, binding.trainId,
                )
                val matches = verified.id == binding.sessionId &&
                    verified.lineId == binding.lineId &&
                    verified.direction == binding.direction.name &&
                    verified.tripId == binding.tripId &&
                    verified.trainId == binding.trainId &&
                    verified.status in setOf("STARTING", "ACTIVE")
                if (!matches) {
                    finish(StopReason.SESSION_INVALID, clearSession = true)
                    return false
                }
                return true
            } catch (error: HttpException) {
                log("SESSION_RESUME_HTTP_${error.code()}", error)
                if (error.code() in 500..599 || error.code() == 429) {
                    waitForServer(backoff)
                } else {
                    finish(StopReason.SESSION_INVALID, clearSession = error.code() in setOf(404, 409))
                    return false
                }
            } catch (error: IOException) {
                log("SESSION_RESUME_NETWORK", error)
                waitForServer(backoff)
            }
        }
        return false
    }

    private suspend fun waitForServer(backoff: ServerBackoff) {
        val waitMs = backoff.nextDelayMs()
        BroadcastStatusRepository.update(BroadcastState.ServerUnavailable(System.currentTimeMillis(), waitMs))
        delay(waitMs)
    }

    private fun enterForeground(): Boolean {
        val notification = runCatching {
            TrainNotificationHelper.buildOngoingTripNotification(
                this,
                currentSpeedKmh = 0f,
                nextStationName = "جارٍ التحقق من الجلسة",
                isTunnel = false,
                stopIntent = stopPendingIntent(),
            )
        }.getOrElse {
            log("NOTIFICATION_BUILD_FAILED", it)
            return false
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TrainNotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TrainNotificationHelper.NOTIFICATION_ID_ONGOING, notification)
            }
        }.onFailure { log("START_FOREGROUND_FAILED", it) }.isSuccess
    }

    private fun stopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, TrainTrackingService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Single exit path: publishes the reason, releases GPS, optionally clears and ends the session. */
    private fun finish(
        reason: StopReason,
        clearSession: Boolean,
        endOnServer: Boolean = reason == StopReason.USER,
        sessionId: String? = null,
    ) {
        if (stopping) return
        stopping = true
        log("SERVICE_FINISH reason=$reason clear=$clearSession")
        BroadcastStatusRepository.update(BroadcastState.Stopped(reason))
        val idToEnd = sessionId ?: runCatching { sessionStore.load()?.sessionId }.getOrNull()
        if (clearSession) runCatching { sessionStore.clear() }.onFailure { Log.w(TAG, "clear failed", it) }
        if (endOnServer && idToEnd != null) {
            // Best effort; the server also expires silent sessions.
            CoroutineScope(Dispatchers.IO).launch { runCatching { repository.endMonitorSession(idToEnd) } }
        }
        runCatching { locationCoordinator.release(GpsOwner.BROADCAST_SERVICE) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        log("SERVICE_ON_DESTROY")
        if (initialized) {
            if (BroadcastStatusRepository.state.value.isActive) {
                BroadcastStatusRepository.update(BroadcastState.Stopped(StopReason.ERROR))
            }
            runCatching { locationCoordinator.release(GpsOwner.BROADCAST_SERVICE) }
            runCatching { TrainNotificationHelper.clearOngoingNotification(this) }
            runCatching { sessionStore.close() }
        }
        serviceScope.cancel()
        initialized = false
        super.onDestroy()
    }

    private fun log(event: String, error: Throwable? = null) {
        PersistentAppLogger.write(event, error)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
