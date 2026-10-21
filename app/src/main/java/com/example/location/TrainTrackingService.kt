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
import com.example.data.TrainRepository
import com.example.data.local.LocalMonitorSessionStore
import com.example.data.local.PersistentAppLogger
import com.example.data.remote.dto.ObservationRequest
import com.example.model.CorridorExitPolicy
import com.example.model.CorridorExitState
import com.example.model.MonitorBinding
import com.example.model.TrainDirection
import com.example.notification.TrainNotificationHelper
import com.example.utils.GeoUtils
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps an active, user-started broadcast alive while the app is backgrounded.
 *
 * Note: after process death Android may redeliver the start intent, but on
 * Android 12+ a location foreground service cannot be started from the
 * background, so such a restart ends as Stopped(ERROR) instead of resuming.
 */
class TrainTrackingService : Service() {
    companion object {
        const val ACTION_START = "dz.winrah.trainradar.action.START_TRACKING"
        const val ACTION_STOP = "dz.winrah.trainradar.action.STOP_TRACKING"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_LINE_ID = "line_id"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TRAIN_ID = "train_id"
        const val EXTRA_STOP_REASON = "stop_reason"
        const val TAG = "WinRahTrackingService"
        private const val EXPIRY_CHECK_INTERVAL_MS = 60_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var repository: LiveTrainRepository
    private lateinit var sessionStore: LocalMonitorSessionStore
    private var sessionJob: Job? = null
    private var initialized = false
    private val stopping = AtomicBoolean(false)

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
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("SERVICE_ON_START_COMMAND action=${intent?.action} startId=$startId")
        // startForegroundService() obliges startForeground() before any stop,
        // otherwise Android 8+ crashes the app.
        if (!enterForeground()) {
            BroadcastStatusRepository.update(BroadcastState.Stopped(StopReason.ERROR))
            stopSelf()
            return START_NOT_STICKY
        }
        if (!initialized) {
            finish(StopReason.ERROR, clearSession = false)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            val reason = runCatching {
                StopReason.valueOf(intent.getStringExtra(EXTRA_STOP_REASON) ?: StopReason.USER.name)
            }.getOrDefault(StopReason.USER)
            finish(reason, clearSession = true, endOnServer = true)
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
            finish(StopReason.PERMISSION, clearSession = true, endOnServer = true, sessionId = sessionId)
            return START_NOT_STICKY
        }

        if (sessionJob?.isActive == true) return START_REDELIVER_INTENT
        val binding = MonitorBinding(sessionId, lineId, direction, tripId, trainId)
        BroadcastStatusRepository.update(BroadcastState.Starting)
        sessionJob = serviceScope.launch { runSession(binding) }
        return START_REDELIVER_INTENT
    }

    private suspend fun runSession(binding: MonitorBinding) {
        // Hold GPS from the start so updates never pause while the session is validated.
        if (!locationCoordinator.acquire(GpsOwner.BROADCAST_SERVICE)) {
            log("LOCATION_START_FAILED")
            finish(StopReason.PERMISSION, clearSession = true, endOnServer = true, sessionId = binding.sessionId)
            return
        }
        if (!resumeWithRetry(binding)) return

        // The age limit counts from the first start of this session, not from each resume.
        val existing = runCatching { sessionStore.load() }.getOrNull()
        if (existing?.sessionId != binding.sessionId) sessionStore.save(binding)
        val startedAtMs = runCatching { sessionStore.savedAtMs() }.getOrNull()?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        var lastAcceptedAtMs: Long? = null
        val throttle = ObservationThrottle()
        val backoff = ServerBackoff()
        var serverRetryAtMs = 0L
        var corridorState = CorridorExitState()
        val lineStations = TrainRepository.suburbLines.firstOrNull { it.id == binding.lineId }?.stations.orEmpty()

        serviceScope.launch {
            while (isActive && !stopping.get()) {
                delay(EXPIRY_CHECK_INTERVAL_MS)
                if (SessionRestorePolicy.isExpired(startedAtMs, lastAcceptedAtMs, System.currentTimeMillis())) {
                    log("SESSION_EXPIRED")
                    finish(StopReason.EXPIRED, clearSession = true, endOnServer = true, sessionId = binding.sessionId)
                }
            }
        }

        publish(BroadcastState.Sending(lastAcceptedAtMs = null, lastAttemptAtMs = null))
        locationCoordinator.gpsData
            .filter { it.isGpsActive && it.latitude != 0.0 && it.longitude != 0.0 }
            .collect { gps ->
                val now = System.currentTimeMillis()
                if (stopping.get()) return@collect

                // Corridor exit is enforced here so it also works with the app closed.
                if (lineStations.size >= 2) {
                    val distance = GeoUtils.findClosestRailwaySegmentDistanceMeters(gps.latitude, gps.longitude, lineStations)
                    val decision = CorridorExitPolicy.evaluate(distance, gps.isDeadReckoning, true, now, corridorState)
                    corridorState = decision.state
                    if (decision.shouldStopBroadcast) {
                        finish(StopReason.OUTSIDE_CORRIDOR, clearSession = true, endOnServer = true, sessionId = binding.sessionId)
                        return@collect
                    }
                }

                val accuracy = gps.accuracyMeters
                if (accuracy == null || gps.isMock) return@collect
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
                    throttle.applySuggested(response["suggested_interval_s"] as? Number)
                    if (response["accepted"] == true) {
                        lastAcceptedAtMs = now
                        publish(BroadcastState.Sending(lastAcceptedAtMs, now))
                    } else {
                        val reason = (response["rejection_reason"] ?: response["reason"] ?: "unknown").toString()
                        publish(BroadcastState.Rejected(reason, now))
                    }
                } catch (error: HttpException) {
                    log("OBSERVATION_HTTP_${error.code()}", error)
                    when (error.code()) {
                        404, 409 -> finish(StopReason.SESSION_INVALID, clearSession = true)
                        in 500..599, 429 -> {
                            val waitMs = backoff.nextDelayMs()
                            serverRetryAtMs = now + waitMs
                            publish(BroadcastState.ServerUnavailable(now, waitMs))
                        }
                        else -> publish(BroadcastState.Rejected("http_${error.code()}", now))
                    }
                } catch (error: IOException) {
                    val waitMs = backoff.nextDelayMs()
                    serverRetryAtMs = now + waitMs
                    publish(BroadcastState.ServerUnavailable(now, waitMs))
                }
            }
    }

    /** Validates the session with the server, retrying while the server is unreachable. */
    private suspend fun resumeWithRetry(binding: MonitorBinding): Boolean {
        val backoff = ServerBackoff()
        while (!stopping.get()) {
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
        publish(BroadcastState.ServerUnavailable(System.currentTimeMillis(), waitMs))
        delay(waitMs)
    }

    /** Publishes the state and mirrors it in the ongoing notification. */
    private fun publish(state: BroadcastState) {
        BroadcastStatusRepository.update(state)
        if (stopping.get()) return
        runCatching {
            TrainNotificationHelper.updateSharingNotification(this, statusText(state), stopPendingIntent())
        }
    }

    private fun statusText(state: BroadcastState): String = when (state) {
        is BroadcastState.Sending ->
            if (state.lastAcceptedAtMs != null) "يصل موقعك إلى الخادم بنجاح" else "في انتظار أول موقع مقبول"
        is BroadcastState.Rejected -> "الخادم يرفض الموقع حاليًا (ابتعدت عن السكة؟)"
        is BroadcastState.ServerUnavailable -> "الخادم غير متاح؛ إعادة المحاولة تلقائيًا"
        BroadcastState.Starting -> "جارٍ التحقق من الجلسة"
        else -> "مشاركة الموقع"
    }

    private fun enterForeground(): Boolean {
        val notification = runCatching {
            TrainNotificationHelper.buildSharingNotification(this, statusText(BroadcastState.Starting), stopPendingIntent())
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
        endOnServer: Boolean = false,
        sessionId: String? = null,
    ) {
        if (!stopping.compareAndSet(false, true)) return
        log("SERVICE_FINISH reason=$reason clear=$clearSession")
        BroadcastStatusRepository.update(BroadcastState.Stopped(reason))
        val idToEnd = sessionId ?: if (initialized) runCatching { sessionStore.load()?.sessionId }.getOrNull() else null
        if (clearSession && initialized) runCatching { sessionStore.clear() }.onFailure { Log.w(TAG, "clear failed", it) }
        if (endOnServer && idToEnd != null && initialized) {
            // Best effort on a scope that outlives this service; the server also expires silent sessions.
            CoroutineScope(Dispatchers.IO).launch { runCatching { repository.endMonitorSession(idToEnd) } }
        }
        if (initialized) runCatching { locationCoordinator.release(GpsOwner.BROADCAST_SERVICE) }
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
