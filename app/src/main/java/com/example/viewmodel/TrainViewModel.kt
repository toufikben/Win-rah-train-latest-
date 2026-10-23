package com.example.viewmodel

import android.Manifest
import android.app.Application
import dz.winrah.trainradar.BuildConfig
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.example.audio.TrainSoundSynthesizer
import com.example.audio.TrainSoundType
import com.example.data.LiveTrainRepository
import com.example.data.MockLiveTrainFactory
import com.example.data.local.LocalMonitorSessionStore
import com.example.data.local.PersistentAppLogger
import com.example.data.TrackGeometry
import com.example.data.TrainRepository
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportDto
import com.example.data.remote.dto.ReportRequest
import com.example.location.BroadcastState
import com.example.location.BroadcastStatusRepository
import com.example.location.FirstFixResult
import com.example.location.GpsOwner
import com.example.location.LocationTrackingCoordinatorProvider
import com.example.location.RestoreDecision
import com.example.location.SessionRestorePolicy
import com.example.location.StopReason
import com.example.location.TrainTrackingService
import com.example.model.ActiveTrain
import com.example.model.AlertSeverity
import com.example.model.BroadcastSelection
import com.example.model.BroadcastTripOption
import com.example.model.CrowdingLevel
import com.example.model.CorridorExitPolicy
import com.example.model.CorridorExitState
import com.example.model.CrowdsourcedReport
import com.example.model.DelayLevel
import com.example.model.DestinationAlarm
import com.example.model.FavoriteStation
import com.example.model.LineAlert
import com.example.model.LiveGpsData
import com.example.model.MonitorBinding
import com.example.model.NearbyStationInfo
import com.example.model.Station
import com.example.model.StationInterchange
import com.example.model.SuburbLine
import com.example.model.TrainDirection
import com.example.model.TransitConnection
import com.example.model.TransitType
import com.example.model.VerificationStatus
import com.example.model.WaitingSelection
import com.example.notification.TrainNotificationHelper
import com.example.utils.GeoUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class TrackPosition(
    val lat: Double,
    val lon: Double,
    val prev: Station,
    val next: Station
)

class TrainViewModel private constructor(
    private val app: Application,
    private val enableLivePolling: Boolean,
    private val liveTrainRepository: LiveTrainRepository
) : AndroidViewModel(app) {
    constructor(app: Application) : this(
        app,
        true,
        LiveTrainRepository(context = app)
    )
    constructor(app: Application, enableLivePolling: Boolean) : this(app, enableLivePolling, LiveTrainRepository(context = app))

    private val locationTracker = LocationTrackingCoordinatorProvider.get(app)
    private val localPrefs = app.getSharedPreferences("winrah_local_state", Context.MODE_PRIVATE)
    private val monitorSessionStore = LocalMonitorSessionStore(app)

    val gpsData: StateFlow<LiveGpsData> = locationTracker.gpsData
    val isTrackingLocation: StateFlow<Boolean> = locationTracker.isTracking

    private val _isLiveDataLoading = MutableStateFlow(false)
    val isLiveDataLoading: StateFlow<Boolean> = _isLiveDataLoading.asStateFlow()

    private val _liveDataError = MutableStateFlow<String?>(null)
    val liveDataError: StateFlow<String?> = _liveDataError.asStateFlow()

    /** True while the first request of this process waits for the backend to wake up. */
    private val _serverWaking = MutableStateFlow(false)
    val serverWaking: StateFlow<Boolean> = _serverWaking.asStateFlow()
    private var serverWarmedUp = false

    /** Polling runs only while a screen of the app is visible. */
    private val appVisible = MutableStateFlow(true)

    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
        if (!visible && !_isOnboardActivationPending.value) {
            locationTracker.release(GpsOwner.UI)
        }
    }

    /** Anonymous device id shown in settings so users can request data deletion. */
    fun anonymousDeviceId(): String = liveTrainRepository.anonymousMonitorId()

    // Deferred local catalog entries are not shown as operational until the backend publishes trips.
    private val _suburbs = MutableStateFlow(
        TrainRepository.suburbLines.filterNot { it.id in setOf("airport_algiers", "thenia_tizi") }
    )
    val suburbs: StateFlow<List<SuburbLine>> = _suburbs.asStateFlow()

    private var publishedUiLineIds: Set<String> = emptySet()

    private val _selectedSuburb = MutableStateFlow(TrainRepository.suburbLines.first())
    val selectedSuburb: StateFlow<SuburbLine> = _selectedSuburb.asStateFlow()

    private val _selectedStation = MutableStateFlow<Station>(TrainRepository.suburbLines.first().stations[1])
    val selectedStation: StateFlow<Station> = _selectedStation.asStateFlow()

    // 4. DIRECTION & PLATFORM FILTERING
    private val _selectedDirectionFilter = MutableStateFlow(TrainDirection.BOTH)
    val selectedDirectionFilter: StateFlow<TrainDirection> = _selectedDirectionFilter.asStateFlow()

    // Broadcast selection is independent from the station used for passenger alerts.
    private val _broadcastSelection = MutableStateFlow<BroadcastSelection?>(null)
    val broadcastSelection: StateFlow<BroadcastSelection?> = _broadcastSelection.asStateFlow()

    private val _waitingSelection = MutableStateFlow(
        WaitingSelection(
            lineId = _selectedSuburb.value.id,
            stationId = _selectedStation.value.id,
        )
    )
    val waitingSelection: StateFlow<WaitingSelection> = _waitingSelection.asStateFlow()

    private val _broadcastLine = MutableStateFlow(_selectedSuburb.value)
    val broadcastLine: StateFlow<SuburbLine> = _broadcastLine.asStateFlow()

    private val _broadcastDirection = MutableStateFlow(TrainDirection.INBOUND)
    val broadcastDirection: StateFlow<TrainDirection> = _broadcastDirection.asStateFlow()

    private val _broadcastTrips = MutableStateFlow<List<BroadcastTripOption>>(emptyList())
    val broadcastTrips: StateFlow<List<BroadcastTripOption>> = _broadcastTrips.asStateFlow()

    private val _verificationStatus = MutableStateFlow(VerificationStatus.WAITING_GPS)
    val verificationStatus: StateFlow<VerificationStatus> = _verificationStatus.asStateFlow()

    private val _activeTrains = MutableStateFlow<List<ActiveTrain>>(emptyList())
    val activeTrains: StateFlow<List<ActiveTrain>> = _activeTrains.asStateFlow()

    private val _selectedTrain = MutableStateFlow<ActiveTrain?>(null)
    val selectedTrain: StateFlow<ActiveTrain?> = _selectedTrain.asStateFlow()

    /** Driven only by [BroadcastStatusRepository]; never set from UI code paths. */
    private val _isOnboardMode = MutableStateFlow(false)
    val isOnboardMode: StateFlow<Boolean> = _isOnboardMode.asStateFlow()
    val broadcastState: StateFlow<BroadcastState> = BroadcastStatusRepository.state

    /** Set when a recent unfinished broadcast exists and the user must decide. */
    private val _pendingRestore = MutableStateFlow<MonitorBinding?>(null)
    val pendingRestore: StateFlow<MonitorBinding?> = _pendingRestore.asStateFlow()
    private val _isOnboardActivationPending = MutableStateFlow(false)
    val isOnboardActivationPending: StateFlow<Boolean> = _isOnboardActivationPending.asStateFlow()

    private val _distanceToRailwayCorridorMeters = MutableStateFlow(0.0)
    val distanceToRailwayCorridorMeters: StateFlow<Double> = _distanceToRailwayCorridorMeters.asStateFlow()

    private val _approachingAlert = MutableStateFlow<String?>(null)
    val approachingAlert: StateFlow<String?> = _approachingAlert.asStateFlow()

    // 1. SMART DESTINATION GEOFENCE WAKE-UP ALARM
    private val _destinationAlarm = MutableStateFlow(DestinationAlarm())
    val destinationAlarm: StateFlow<DestinationAlarm> = _destinationAlarm.asStateFlow()

    // 2. CROWDSOURCED DELAY & CROWDING REPORTS
    private val _crowdReportsMap = MutableStateFlow<Map<String, CrowdsourcedReport>>(emptyMap())
    val crowdReportsMap: StateFlow<Map<String, CrowdsourcedReport>> = _crowdReportsMap.asStateFlow()
    private val _selectedCrowdingReport = MutableStateFlow<CrowdingLevel?>(null)
    val selectedCrowdingReport: StateFlow<CrowdingLevel?> = _selectedCrowdingReport.asStateFlow()
    private val _selectedDelayReport = MutableStateFlow<DelayLevel?>(null)
    val selectedDelayReport: StateFlow<DelayLevel?> = _selectedDelayReport.asStateFlow()

    // 3. FAVORITES AND QUICK WIDGETS
    private val _favoriteStations = MutableStateFlow<List<FavoriteStation>>(emptyList())
    val favoriteStations: StateFlow<List<FavoriteStation>> = _favoriteStations.asStateFlow()

    // 5. LINE DISRUPTIONS & WEATHER ALERTS TICKER
    private val _lineAlerts = MutableStateFlow<List<LineAlert>>(emptyList())
    val lineAlerts: StateFlow<List<LineAlert>> = _lineAlerts.asStateFlow()

    private val _trackGeometry = MutableStateFlow<TrackGeometry?>(null)
    val trackGeometry: StateFlow<TrackGeometry?> = _trackGeometry.asStateFlow()

    private val _trackGeometryLoading = MutableStateFlow(false)
    val trackGeometryLoading: StateFlow<Boolean> = _trackGeometryLoading.asStateFlow()

    private val _trackGeometryError = MutableStateFlow<String?>(null)
    val trackGeometryError: StateFlow<String?> = _trackGeometryError.asStateFlow()

    private val _selectedLineAlertModal = MutableStateFlow<LineAlert?>(null)

    fun refreshTrackGeometry(line: SuburbLine) {
        _trackGeometryLoading.value = true
        _trackGeometryError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { liveTrainRepository.getRailwayGeometryForLine(line) }
                .onSuccess { geometry -> _trackGeometry.value = geometry }
                .onFailure { error ->
                    _trackGeometry.value = null
                    _trackGeometryError.value = error.message ?: "map_geometry_unavailable"
                }
            _trackGeometryLoading.value = false
        }
    }
    val selectedLineAlertModal: StateFlow<LineAlert?> = _selectedLineAlertModal.asStateFlow()

    // 6. NEARBY STATIONS SMART RADAR & MULTI-MODAL INTERCHANGE
    private val _nearbyStations = MutableStateFlow<List<NearbyStationInfo>>(emptyList())
    val nearbyStations: StateFlow<List<NearbyStationInfo>> = _nearbyStations.asStateFlow()

    private val _selectedInterchange = MutableStateFlow<StationInterchange?>(null)
    val selectedInterchange: StateFlow<StationInterchange?> = _selectedInterchange.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    // Settings States
    private val _isWhistleSoundEnabled = MutableStateFlow(true)
    val isWhistleSoundEnabled: StateFlow<Boolean> = _isWhistleSoundEnabled.asStateFlow()

    private val _selectedSoundType = MutableStateFlow(TrainSoundType.STEAM_WHISTLE)
    val selectedSoundType: StateFlow<TrainSoundType> = _selectedSoundType.asStateFlow()

    private val _alertDistanceThresholdKm = MutableStateFlow(3.5f)
    val alertDistanceThresholdKm: StateFlow<Float> = _alertDistanceThresholdKm.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(true)
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    private val _isBackgroundNotificationEnabled = MutableStateFlow(true)
    val isBackgroundNotificationEnabled: StateFlow<Boolean> = _isBackgroundNotificationEnabled.asStateFlow()

    private val _selectedTheme = MutableStateFlow("dark")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private var lastWhistleAlertTime = 0L
    private var lastAlarmRingTime = 0L
    private val announcedArrivalKeys = mutableSetOf<String>()

    private var liveRefreshJob: Job? = null
    private var radarRefreshJob: Job? = null
    private var activationJob: Job? = null
    private var activationGeneration = 0
    private val radarRefreshMutex = Mutex()
    private val _monitorBinding = MutableStateFlow<MonitorBinding?>(null)
    val monitorBinding: StateFlow<MonitorBinding?> = _monitorBinding.asStateFlow()
    private val reportInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val _activeSessionReports = MutableStateFlow<List<ReportDto>>(emptyList())
    val activeSessionReports: StateFlow<List<ReportDto>> = _activeSessionReports.asStateFlow()
    private var corridorExitState = CorridorExitState()

    init {
        restoreLocalState()
        if (_broadcastSelection.value == null) {
            _broadcastSelection.value = BroadcastSelection(
                lineId = _broadcastLine.value.id,
                direction = _broadcastDirection.value,
            )
        }

        viewModelScope.launch {
            var initial = true
            BroadcastStatusRepository.state.collect { state ->
                _isOnboardMode.value = state.isActive
                // 6: a service redelivered after process death supersedes the restore prompt
                if (state.isActive) _pendingRestore.value = null
                val isNewEvent = !initial
                initial = false
                if (state is BroadcastState.Stopped && isNewEvent) {
                    _monitorBinding.value = null
                    _activeSessionReports.value = emptyList()
                    corridorExitState = CorridorExitState()
                    _verificationStatus.value = VerificationStatus.WAITING_GPS
                    stopMessageFor(state.reason)?.let { _userFeedbackMessage.value = it }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            restoreActiveMonitorSession()
        }

        // Initialize Android notification channels
        TrainNotificationHelper.initNotificationChannels(app)

        // Observe GPS updates and verify track alignment + Destination Alarm
        viewModelScope.launch {
            locationTracker.gpsData.collect { gps ->
                if (gps.isGpsActive) {
                    updateNearbyStations(gps.latitude, gps.longitude)
                    if (_isOnboardMode.value) {
                        // TrainTrackingService sends observations and owns the ongoing
                        // notification; the ViewModel only verifies the corridor for the UI.
                        verifyPassengerLocation(gps)
                    }
                    checkDestinationAlarm(gps.latitude, gps.longitude)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { liveTrainRepository.getPublishedUiLineIds() }
                .onSuccess { publishedIds ->
                    publishedUiLineIds = publishedIds
                    val published = TrainRepository.suburbLines.filter { it.id in publishedIds }
                    if (published.isNotEmpty()) {
                        _suburbs.value = published
                        if (_selectedSuburb.value.id !in publishedIds) {
                            _selectedSuburb.value = published.first()
                            _selectedStation.value = published.first().stations.getOrElse(1) { published.first().stations.first() }
                        }
                    }
                }
        }

        if (enableLivePolling) {
            // The polling loop performs the initial refresh itself; do not launch a second request.
            startLiveRefresh()
        }
    }

    fun selectSuburb(suburb: SuburbLine) {
        _selectedSuburb.value = suburb
        val defaultStation = suburb.stations.getOrElse(1) { suburb.stations.first() }
        _selectedStation.value = defaultStation
        _waitingSelection.value = WaitingSelection(
            lineId = suburb.id,
            stationId = defaultStation.id,
            trainId = _waitingSelection.value.trainId,
        )
        recalculateActiveTrains()
    }

    fun selectStation(station: Station) {
        _selectedStation.value = station
        _waitingSelection.value = _waitingSelection.value.copy(
            lineId = _selectedSuburb.value.id,
            stationId = station.id,
        )
        recalculateActiveTrains()
        checkApproachingTrain()
    }

    /** Selection used by the waiting/monitoring view only; it never changes broadcastSelection. */
    fun selectTrain(train: ActiveTrain?) {
        _selectedTrain.value = train
        _waitingSelection.value = _waitingSelection.value.copy(trainId = train?.id)
    }

    fun setBroadcastSelection(selection: BroadcastSelection?) {
        _broadcastSelection.value = selection
    }

    /** Changes only the route used for broadcasting; waiting station state remains untouched. */
    fun selectBroadcastSuburb(suburb: SuburbLine) {
        if (_isOnboardMode.value || _isOnboardActivationPending.value) {
            _userFeedbackMessage.value = "أوقف بث الموقع الحالي قبل تغيير مسار البث."
            return
        }
        if (_broadcastLine.value.id == suburb.id) return
        _broadcastLine.value = suburb
        _broadcastSelection.value = BroadcastSelection(
            lineId = suburb.id,
            direction = _broadcastDirection.value,
        )
        refreshBroadcastTrips()
    }

    fun selectBroadcastDirection(direction: TrainDirection) {
        if (_isOnboardMode.value || _isOnboardActivationPending.value) {
            _userFeedbackMessage.value = "أوقف بث الموقع الحالي قبل تغيير اتجاه البث."
            return
        }
        if (direction == TrainDirection.BOTH) return
        _broadcastDirection.value = direction
        _broadcastSelection.value = BroadcastSelection(
            lineId = _broadcastLine.value.id,
            direction = direction,
        )
        refreshBroadcastTrips()
    }

    fun selectBroadcastTrip(option: BroadcastTripOption?) {
        if (_isOnboardMode.value || _isOnboardActivationPending.value) {
            _userFeedbackMessage.value = "أوقف بث الموقع الحالي قبل تغيير الرحلة."
            return
        }
        _broadcastSelection.value = option?.let {
            BroadcastSelection(
                lineId = it.lineId,
                direction = it.direction,
                tripId = it.tripId,
                trainId = it.trainId,
            )
        } ?: BroadcastSelection(
            lineId = _broadcastLine.value.id,
            direction = _broadcastDirection.value,
        )
    }

    fun refreshBroadcastTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                liveTrainRepository.getBroadcastTripsForLine(
                    _broadcastLine.value,
                    _broadcastDirection.value,
                )
            }.onSuccess { trips ->
                _broadcastTrips.value = trips
                if (_broadcastSelection.value?.tripId !in trips.map { it.tripId }) {
                    _broadcastSelection.value = BroadcastSelection(
                        lineId = _broadcastLine.value.id,
                        direction = _broadcastDirection.value,
                    )
                }
            }.onFailure {
                _broadcastTrips.value = emptyList()
                _broadcastSelection.value = BroadcastSelection(
                    lineId = _broadcastLine.value.id,
                    direction = _broadcastDirection.value,
                )
                _userFeedbackMessage.value = "تعذر تحميل قائمة القطارات؛ يمكنك بدء بث المسار والاتجاه دون اختيار قطار."
            }
        }
    }

    fun clearBroadcastSelection() {
        _broadcastSelection.value = BroadcastSelection(
            lineId = _broadcastLine.value.id,
            direction = _broadcastDirection.value,
        )
    }

    fun setDirectionFilter(direction: TrainDirection) {
        _selectedDirectionFilter.value = direction
        recalculateActiveTrains()
    }

    fun toggleOnboardMode(enable: Boolean, reason: StopReason = StopReason.USER): Boolean {
        if (!enable) {
            activationGeneration++
            activationJob?.cancel()
            activationJob = null
            _monitorBinding.value = null
            _activeSessionReports.value = emptyList()
            clearPersistedReportSelections()
            corridorExitState = CorridorExitState()
            _isOnboardActivationPending.value = false
            // The service ends the session on the server, releases its GPS ownership
            // and publishes Stopped(reason); other GPS owners (alarm) keep running.
            requestServiceStop(reason)
            return true
        }

        if (_isOnboardActivationPending.value) return true
        if (!canPostNotifications()) {
            _userFeedbackMessage.value = "فعّل إشعارات التطبيق أولًا؛ إشعار المشاركة ضروري أثناء البث."
            return false
        }
        if (!enableLivePolling) {
            _isOnboardActivationPending.value = false
            _userFeedbackMessage.value = "وضع الاختبار المحلي لا يشغّل البث الشبكي."
            return false
        }
        activationJob?.cancel()
        val generation = ++activationGeneration
        _isOnboardActivationPending.value = true
        activationJob = viewModelScope.launch(Dispatchers.IO) {
            var createdSessionId: String? = null
            var serviceStarted = false
            try {
                val selection = _broadcastSelection.value
                    ?: BroadcastSelection(_broadcastLine.value.id, _broadcastDirection.value).also {
                        _broadcastSelection.value = it
                    }
                val requestedTripId = selection.tripId
                val requestedTrainId = selection.trainId
                PersistentAppLogger.write(
                    "SESSION_CREATE_BEGIN " +
                        "lineId=${selection.lineId} " +
                        "direction=${selection.direction.name} " +
                        "tripSelected=${requestedTripId != null} " +
                        "trainSelected=${requestedTrainId != null}"
                )
                val session = try {
                    liveTrainRepository.createMonitorSession(
                        lineId = selection.lineId,
                        direction = selection.direction,
                        tripId = requestedTripId,
                        trainId = requestedTrainId,
                    )
                } catch (error: Throwable) {
                    val diagnostic = describeSessionFailure(error)
                    PersistentAppLogger.write("SESSION_CREATE_FAILED detail=$diagnostic", error)
                    throw error
                }
                PersistentAppLogger.write(
                    "SESSION_CREATE_SUCCESS sessionId=${session.id} " +
                        "lineId=${session.lineId} direction=${session.direction}"
                )
                createdSessionId = session.id
                if (session.tripId != requestedTripId || session.trainId != requestedTrainId) {
                    throw IllegalStateException("session_binding_mismatch")
                }
                PersistentAppLogger.write("GPS_FIRST_FIX_WAIT_BEGIN timeoutSeconds=45")
                when (locationTracker.acquireAndAwaitFirstFix(GpsOwner.UI, 45_000L)) {
                    FirstFixResult.START_FAILED -> {
                        PersistentAppLogger.write("GPS_START_FAILED requestAccepted=false")
                        throw IllegalStateException("location_start_failed")
                    }
                    FirstFixResult.TIMEOUT -> {
                        PersistentAppLogger.write("GPS_FIRST_FIX_TIMEOUT realFix=false timeoutSeconds=45")
                        throw IllegalStateException("location_unavailable_timeout")
                    }
                    FirstFixResult.RECEIVED -> {
                        PersistentAppLogger.write(
                            "GPS_FIRST_FIX_RECEIVED accuracy=${locationTracker.gpsData.value.accuracyMeters} " +
                                "timestamp=${locationTracker.gpsData.value.timestamp}"
                        )
                    }
                }
                coroutineContext.ensureActive()
                if (generation != activationGeneration) return@launch
                val firstGps = locationTracker.gpsData.value
                val firstCorridorDistance = GeoUtils.findClosestRailwaySegmentDistanceMeters(
                    firstGps.latitude,
                    firstGps.longitude,
                    _broadcastLine.value.stations,
                )
                PersistentAppLogger.write(
                    "BROADCAST_CORRIDOR_CHECK distanceMeters=${"%.1f".format(Locale.US, firstCorridorDistance)} " +
                        "deadReckoning=${firstGps.isDeadReckoning}"
                )
                if (!firstGps.isDeadReckoning &&
                    firstCorridorDistance > CorridorExitPolicy.CORRIDOR_EXIT_DISTANCE_METERS
                ) {
                    throw IllegalStateException("outside_corridor")
                }
                _monitorBinding.value = MonitorBinding(
                    sessionId = session.id,
                    lineId = session.lineId,
                    direction = selection.direction,
                    tripId = session.tripId,
                    trainId = session.trainId,
                )
                restorePersistedReportSelections(session.id)
                _activeSessionReports.value = emptyList()
                // The service acquires its own GPS ownership; the UI hold is released
                // only after the service is started so updates never pause in between.
                refreshActiveSessionReports()
                BroadcastStatusRepository.update(BroadcastState.Starting)
                val started = startTrainTrackingService(
                    session.id,
                    session.lineId,
                    selection.direction,
                    session.tripId,
                    session.trainId,
                )
                if (!started) throw IllegalStateException("service_start_failed")
                serviceStarted = true
                if (generation != activationGeneration) {
                    runCatching { app.stopService(Intent(app, TrainTrackingService::class.java)) }
                    withContext(NonCancellable) {
                        runCatching { liveTrainRepository.endMonitorSession(createdSessionId) }
                    }
                    locationTracker.release(GpsOwner.UI)
                    return@launch
                }
                locationTracker.release(GpsOwner.UI)
                _isOnboardActivationPending.value = false
                _userFeedbackMessage.value = "بدأت مشاركة موقعك. يمكنك إيقافها من هنا أو من الإشعار."
            } catch (error: CancellationException) {
                if (serviceStarted) {
                    runCatching { app.stopService(Intent(app, TrainTrackingService::class.java)) }
                }
                if (createdSessionId != null) {
                    withContext(NonCancellable) {
                        runCatching { liveTrainRepository.endMonitorSession(createdSessionId) }
                    }
                }
                if (generation == activationGeneration) {
                    _monitorBinding.value = null
                    _activeSessionReports.value = emptyList()
                    clearPersistedReportSelections()
                    _isOnboardActivationPending.value = false
                    locationTracker.release(GpsOwner.UI)
                }
                throw error
            } catch (error: Exception) {
                if (createdSessionId != null) {
                    runCatching { liveTrainRepository.endMonitorSession(createdSessionId) }
                }
                if (generation != activationGeneration) return@launch
                _monitorBinding.value = null
                _activeSessionReports.value = emptyList()
                clearPersistedReportSelections()
                _isOnboardActivationPending.value = false
                locationTracker.release(GpsOwner.UI)
                runCatching { app.stopService(Intent(app, TrainTrackingService::class.java)) }
                BroadcastStatusRepository.update(BroadcastState.Stopped(StopReason.ERROR))
                _verificationStatus.value = VerificationStatus.WAITING_GPS
                PersistentAppLogger.write(
                    "BROADCAST_ACTIVATION_FAILED detail=${describeSessionFailure(error)}",
                    error,
                )
                _userFeedbackMessage.value = if (error.message == "location_start_failed") {
                    "تعذر تشغيل مستشعر GPS. فعّل الموقع على مستوى الجهاز وأوقف تقييد البطارية للتطبيق ثم حاول مجددًا."
                } else if (error.message == "location_unavailable" || error.message == "location_unavailable_timeout") {
                    "تم إنشاء جلسة البث، لكن لم تصل إشارة GPS حقيقية خلال 45 ثانية. تحرك إلى مكان مفتوح وتأكد من تشغيل الموقع ثم حاول مجددًا."
                } else if (error.message == "outside_corridor") {
                    "لا يمكن بدء البث: موقعك يبعد أكثر من 400 متر عن ممر السكة الحديدية. اقترب من السكة ثم حاول مجددًا."
                } else if (error.message == "no_live_trackable_train") {
                    "لا يوجد قطار حي موثق يمكن بدء المراقبة عليه الآن."
                } else if (error.message == "broadcast_selection_required") {
                    "اختر الضاحية والاتجاه قبل بدء البث. الرحلة والقطار اختياريان."
                } else if (error is HttpException && error.code() == 400) {
                    "الخادم رفض بيانات الجلسة (400). افتح شاشة التشخيص للتفاصيل."
                } else if (error is HttpException && error.code() == 403) {
                    "البث محجوب في نسخة الاختبار الحالية لحماية خادم Production."
                } else if (error.message == "service_start_failed") {
                    "تعذر تشغيل المشاركة في الخلفية. افتح التطبيق وحاول مجددًا."
                } else if (error.message == "session_binding_mismatch") {
                    "رفض الخادم جلسة لا تطابق القطار المطلوب؛ لم يبدأ البث."
                } else {
                    "تعذر إنشاء جلسة البث مع الخادم. حاول لاحقاً."
                }
            }
        }
        return true
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this.app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun describeSessionFailure(error: Throwable): String {
        return when (error) {
            is HttpException -> {
                val status = error.code()
                val body = runCatching {
                    error.response()?.errorBody()?.string().orEmpty()
                }.getOrDefault("")
                val serverMessage = runCatching {
                    val json = JSONObject(body)
                    val detail = json.opt("detail")
                    when (detail) {
                        is String -> detail
                        null -> json.optString("message")
                            .ifBlank { json.optString("error") }
                        else -> detail.toString()
                    }.ifBlank { "no-server-message" }
                }.getOrDefault("unparseable-server-response")
                "HTTP_$status: $serverMessage"
            }
            is IOException -> "NETWORK_ERROR: ${error.message ?: "connection-failed"}"
            else -> "${error.javaClass.simpleName}: ${error.message ?: "no-message"}"
        }
    }

    private fun startTrainTrackingService(
        sessionId: String,
        lineId: String,
        direction: TrainDirection,
        tripId: String?,
        trainId: String?,
    ): Boolean {
        val intent = Intent(app, TrainTrackingService::class.java).apply {
            action = TrainTrackingService.ACTION_START
            putExtra(TrainTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(TrainTrackingService.EXTRA_LINE_ID, lineId)
            putExtra(TrainTrackingService.EXTRA_DIRECTION, direction.name)
            tripId?.let { putExtra(TrainTrackingService.EXTRA_TRIP_ID, it) }
            trainId?.let { putExtra(TrainTrackingService.EXTRA_TRAIN_ID, it) }
        }
        return runCatching { ContextCompat.startForegroundService(app, intent) }
            .onFailure { PersistentAppLogger.write("SERVICE_START_FAILED", it) }
            .isSuccess
    }

    /** Asks the running service to finish; it ends the server session and releases GPS. */
    private fun requestServiceStop(reason: StopReason) {
        if (!BroadcastStatusRepository.state.value.isActive) {
            BroadcastStatusRepository.update(BroadcastState.Stopped(reason))
            return
        }
        val intent = Intent(app, TrainTrackingService::class.java).apply {
            action = TrainTrackingService.ACTION_STOP
            putExtra(TrainTrackingService.EXTRA_STOP_REASON, reason.name)
        }
        runCatching { app.startService(intent) }
            .onFailure { runCatching { app.stopService(Intent(app, TrainTrackingService::class.java)) } }
    }

    private fun stopMessageFor(reason: StopReason): String? = when (reason) {
        StopReason.USER -> null
        StopReason.SESSION_INVALID -> "انتهت جلسة المشاركة على الخادم؛ ابدأ مشاركة جديدة إن كنت ما زلت في القطار."
        StopReason.PERMISSION -> "توقفت المشاركة: صلاحية الموقع غير متاحة."
        StopReason.EXPIRED -> "توقفت المشاركة تلقائيًا لطول المدة أو لانقطاع الإشارة الطويل."
        StopReason.OUTSIDE_CORRIDOR -> "توقفت المشاركة تلقائيًا: ابتعد جهازك عن السكة الحديدية."
        StopReason.ERROR -> "توقفت المشاركة بسبب خطأ غير متوقع."
    }

    fun refreshActiveSessionReports() {
        val sessionId = _monitorBinding.value?.sessionId
        if (sessionId == null) {
            _activeSessionReports.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { liveTrainRepository.getReportsForSession(sessionId) }
                .onSuccess { reports ->
                    if (_monitorBinding.value?.sessionId == sessionId) {
                        _activeSessionReports.value = reports
                    }
                }
                .onFailure {
                    _userFeedbackMessage.value = "تعذر تحميل تقارير جلسة البث الحالية."
                }
        }
    }

    fun refreshGpsLocation() {
        locationTracker.acquire(GpsOwner.UI)
    }

    fun clearApproachingAlert() {
        _approachingAlert.value = null
    }

    fun setUserFeedback(message: String) {
        _userFeedbackMessage.value = message
    }

    fun clearUserFeedback() {
        _userFeedbackMessage.value = null
    }

    fun selectLineAlertModal(alert: LineAlert?) {
        _selectedLineAlertModal.value = alert
    }

    // ==========================================
    // 1. SMART DESTINATION GEOFENCE WAKE-UP ALARM
    // ==========================================
    fun setDestinationAlarm(station: Station, suburbId: String = _selectedSuburb.value.id, alertDistanceKm: Float = 1.5f) {
        _destinationAlarm.value = DestinationAlarm(
            isEnabled = true,
            targetStation = station,
            targetSuburbId = suburbId,
            alertDistanceKm = alertDistanceKm,
            isTriggered = false,
            remainingDistanceKm = 0.0f
        )
        locationTracker.acquire(GpsOwner.DESTINATION_ALARM)
        _userFeedbackMessage.value = "تم تفعيل منبه النزول لمحطة (${station.name}) - سنوقظك قبل ${alertDistanceKm} كم! ⏰"
    }

    fun dismissDestinationAlarm() {
        _destinationAlarm.value = _destinationAlarm.value.copy(isTriggered = false, isEnabled = false)
        locationTracker.release(GpsOwner.DESTINATION_ALARM)
        _userFeedbackMessage.value = "تم إيقاف المنبه بنجاح. رحلة موفقة! 🚆"
    }

    fun cancelDestinationAlarm() {
        _destinationAlarm.value = DestinationAlarm(isEnabled = false)
        locationTracker.release(GpsOwner.DESTINATION_ALARM)
        _userFeedbackMessage.value = "تم إلغاء منبه الوصول."
    }

    fun updateAlarmDistance(distanceKm: Float) {
        _destinationAlarm.value = _destinationAlarm.value.copy(alertDistanceKm = distanceKm)
    }

    private fun checkDestinationAlarm(currentLat: Double, currentLon: Double) {
        val alarm = _destinationAlarm.value
        if (!alarm.isEnabled || alarm.targetStation == null) return

        val distKm = GeoUtils.calculateDistanceKm(
            currentLat,
            currentLon,
            alarm.targetStation.latitude,
            alarm.targetStation.longitude
        ).toFloat()

        _destinationAlarm.value = alarm.copy(remainingDistanceKm = distKm)

        if (distKm <= alarm.alertDistanceKm) {
            _destinationAlarm.value = alarm.copy(isTriggered = true, remainingDistanceKm = distKm)
            val now = System.currentTimeMillis()
            if (now - lastAlarmRingTime > 12000L) {
                lastAlarmRingTime = now
                triggerSelectedSound(TrainSoundType.STATION_CHIME)
                triggerWakeUpVibration()

                if (_isBackgroundNotificationEnabled.value) {
                    TrainNotificationHelper.showDestinationAlarmNotification(
                        app,
                        alarm.targetStation,
                        distKm
                    )
                }
            }
        }
    }

    private fun triggerWakeUpVibration() {
        if (!_isVibrationEnabled.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // 2. CROWDSOURCED DELAY & CROWDING REPORTS
    // ==========================================
    fun submitCrowdingReport(trainId: String, crowding: CrowdingLevel) {
        if (_selectedCrowdingReport.value == crowding) return
        submitEvidenceReport(
            trainId = trainId,
            reportType = "OTHER",
            description = "إفادة مستخدم: ${crowding.titleAr}",
            onSuccess = { _selectedCrowdingReport.value = crowding },
        )
    }

    fun submitDelayReport(trainId: String, delay: DelayLevel) {
        if (_selectedDelayReport.value == delay) return
        submitEvidenceReport(
            trainId = trainId,
            reportType = "DELAYED",
            description = "إفادة مستخدم: ${delay.titleAr}",
            onSuccess = { _selectedDelayReport.value = delay },
        )
    }

    fun submitCrowdingReport(crowding: CrowdingLevel) {
        if (_selectedCrowdingReport.value == crowding) return
        submitBoundEvidenceReport(
            reportType = "OTHER",
            description = "إفادة مستخدم: ${crowding.titleAr}",
            onSuccess = { _selectedCrowdingReport.value = crowding },
        )
    }

    fun submitDelayReport(delay: DelayLevel) {
        if (_selectedDelayReport.value == delay) return
        submitBoundEvidenceReport(
            reportType = "DELAYED",
            description = "إفادة مستخدم: ${delay.titleAr}",
            onSuccess = { _selectedDelayReport.value = delay },
        )
    }

    private fun submitEvidenceReport(trainId: String, reportType: String, description: String, onSuccess: () -> Unit) {
        val tripId = _activeTrains.value.firstOrNull { it.id == trainId }?.tripId
        submitEvidenceReport(trainId, tripId, reportType, description, onSuccess = onSuccess)
    }

    private fun submitBoundEvidenceReport(reportType: String, description: String, onSuccess: () -> Unit) {
        val binding = _monitorBinding.value
        if (binding == null) {
            _userFeedbackMessage.value = "ابدأ بثًا حيًا قبل إرسال إفادة عن حالة القطار."
            PersistentAppLogger.write("REPORT_SUBMIT_BLOCKED reason=no_active_session type=$reportType")
            return
        }
        // Public corridor sessions may intentionally have no train identity.
        // The report remains bound to the active session, line, direction and station.
        val activeTrain = _activeTrains.value.firstOrNull()
        val trainId = binding.trainId ?: activeTrain?.id
        PersistentAppLogger.write(
            "REPORT_SUBMIT_BEGIN sessionId=${binding.sessionId} trainId=${trainId ?: "none"} type=$reportType"
        )
        submitEvidenceReport(
            trainId = trainId,
            tripId = binding.tripId ?: activeTrain?.tripId,
            reportType = reportType,
            description = description,
            sessionId = binding.sessionId,
            onSuccess = onSuccess,
        )
    }

    private fun submitEvidenceReport(
        trainId: String?,
        tripId: String?,
        reportType: String,
        description: String,
        sessionId: String? = null,
        onSuccess: () -> Unit = {},
    ) {
        if (!reportInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveTrainRepository.submitReport(
                    ReportRequest(
                        sessionId = sessionId,
                        trainId = trainId,
                        tripId = tripId,
                        stationId = liveTrainRepository.canonicalStationId(_selectedStation.value),
                        reportType = reportType,
                        description = description
                    )
                )
                onSuccess()
                refreshActiveSessionReports()
                if (sessionId != null) {
                    persistReportSelection(sessionId)
                }
                PersistentAppLogger.write(
                    "REPORT_SUBMIT_SUCCESS sessionId=${sessionId ?: "none"} trainId=${trainId ?: "none"} type=$reportType"
                )
                _userFeedbackMessage.value = "تم إرسال الإفادة إلى الخادم، شكرًا لمساهمتك."
            } catch (error: Exception) {
                PersistentAppLogger.write(
                    "REPORT_SUBMIT_FAILED sessionId=${sessionId ?: "none"} trainId=${trainId ?: "none"} " +
                        "type=$reportType detail=${error.message ?: error.javaClass.simpleName}",
                    error,
                )
                _userFeedbackMessage.value = "تعذر إرسال الإفادة الآن. تحقق من الاتصال وحاول مجددًا."
            } finally {
                reportInFlight.set(false)
            }
        }
    }

    // ==========================================
    // 3. FAVORITE STATIONS & QUICK WIDGETS
    // ==========================================
    fun isStationFavorite(stationId: String): Boolean {
        return _favoriteStations.value.any { it.station.id == stationId }
    }

    fun toggleFavoriteStation(station: Station, suburb: SuburbLine, tag: String = "محطة مفضلة", emoji: String = "⭐") {
        val current = _favoriteStations.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.station.id == station.id }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            _userFeedbackMessage.value = "تمت إزالة محطة (${station.name}) من المفضلة."
        } else {
            current.add(
                FavoriteStation(
                    station = station,
                    suburbId = suburb.id,
                    suburbName = suburb.name,
                    tag = tag,
                    tagEmoji = emoji
                )
            )
            _userFeedbackMessage.value = "تمت إضافة (${station.name}) إلى محطاتك المفضلة $emoji"
        }
        _favoriteStations.value = current
        persistFavorites()
    }

    fun selectFavorite(favorite: FavoriteStation) {
        val targetSuburb = _suburbs.value.find { it.id == favorite.suburbId }
        if (targetSuburb != null) {
            _selectedSuburb.value = targetSuburb
            val stationInLine = targetSuburb.stations.find { it.id == favorite.station.id } ?: favorite.station
            _selectedStation.value = stationInLine
            recalculateActiveTrains()
            _userFeedbackMessage.value = "تم التبديل السريع إلى: ${favorite.tagEmoji} ${favorite.station.name}"
        }
    }

    // ==============================================================
    // 6. NEARBY STATIONS SMART RADAR & MULTI-MODAL INTERCHANGE HUB
    // ==============================================================
    fun updateNearbyStations(userLat: Double, userLon: Double) {
        val nearest = TrainRepository.findNearestStations(userLat, userLon, limit = 5)
        _nearbyStations.value = nearest
    }

    fun selectNearbyStationAsDeparture(nearby: com.example.model.NearbyStationInfo) {
        _selectedSuburb.value = nearby.suburbLine
        _selectedStation.value = nearby.station
        recalculateActiveTrains()
        _userFeedbackMessage.value = "📍 تم تحديد أقرب محطة إليك (${nearby.station.name} - ${String.format("%.1f", nearby.distanceKm)} كم) كمحطة ركوب."
    }

    fun openInterchangeModal(stationCode: String) {
        val interchange = TrainRepository.getInterchangeForStation(stationCode)
        if (interchange != null) {
            _selectedInterchange.value = interchange
        } else {
            // Provide fallback interchange with general Algerian transit info
            val station = TrainRepository.suburbLines.flatMap { it.stations }.find { it.code == stationCode }
            val stationName = station?.name ?: "المحطة"
            _selectedInterchange.value = com.example.model.StationInterchange(
                stationCode = stationCode,
                stationName = stationName,
                mainHubTitle = "خدمات النقل والمواصلات المتاحة بمحطة $stationName",
                connections = listOf(
                    com.example.model.TransitConnection(
                        com.example.model.TransitType.TAXI_STATION,
                        "موقف سيارات الأجرة الحضرية",
                        "سيارات أجرة فردية وجماعية نحو وسط المدينة والأحياء المجاورة",
                        100,
                        1,
                        "متوفرة أمام مدخل المحطة"
                    ),
                    com.example.model.TransitConnection(
                        com.example.model.TransitType.ETUSA_BUS,
                        "محطة الحافلات المحلية",
                        "حافلات النقل الحضري والخاص نحو مختلف البلديات",
                        150,
                        2,
                        "حافلات منتظمة نهاراً"
                    )
                ),
                walkingTip = "اخرج مباشرة من الباب الرئيسي للمحطة للوصول إلى مواقف الحافلات وسيارات الأجرة.",
                landmark = "وسط مدينة $stationName"
            )
        }
    }

    fun closeInterchangeModal() {
        _selectedInterchange.value = null
    }

    // Settings Mutators
    fun setWhistleSoundEnabled(enabled: Boolean) {
        _isWhistleSoundEnabled.value = enabled
        persistSettings()
    }

    fun setSelectedSoundType(type: TrainSoundType) {
        _selectedSoundType.value = type
        persistSettings()
    }

    fun setAlertDistanceThreshold(km: Float) {
        _alertDistanceThresholdKm.value = km.coerceIn(0.5f, 20f)
        persistSettings()
        checkApproachingTrain()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        persistSettings()
    }

    fun setBackgroundNotificationEnabled(enabled: Boolean) {
        _isBackgroundNotificationEnabled.value = enabled
        persistSettings()
    }

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        persistSettings()
    }

    private fun persistSettings() {
        localPrefs.edit()
            .putBoolean("whistle_enabled", _isWhistleSoundEnabled.value)
            .putString("sound_type", _selectedSoundType.value.name)
            .putFloat("alert_distance_km", _alertDistanceThresholdKm.value)
            .putBoolean("vibration_enabled", _isVibrationEnabled.value)
            .putBoolean("background_notification_enabled", _isBackgroundNotificationEnabled.value)
            .putString("theme", _selectedTheme.value)
            .apply()
    }

    private fun persistFavorites() {
        val json = JSONArray().apply {
            _favoriteStations.value.forEach { favorite ->
                put(JSONObject().apply {
                    put("station_id", favorite.station.id)
                    put("suburb_id", favorite.suburbId)
                    put("tag", favorite.tag)
                    put("emoji", favorite.tagEmoji)
                })
            }
        }
        localPrefs.edit().putString("favorites", json.toString()).apply()
    }

    /**
     * Never restarts GPS on its own: a running service is re-attached, a recent
     * unfinished session is offered to the user, anything older is discarded.
     */
    private suspend fun restoreActiveMonitorSession() {
        val saved = runCatching { monitorSessionStore.load() }.getOrNull() ?: return
        val savedAtMs = runCatching { monitorSessionStore.savedAtMs() }.getOrNull() ?: 0L
        val decision = SessionRestorePolicy.decide(
            savedAtMs = savedAtMs,
            nowMs = System.currentTimeMillis(),
            serviceRunning = BroadcastStatusRepository.state.value.isActive,
        )
        PersistentAppLogger.write("SESSION_RESTORE_DECISION decision=$decision sessionId=${saved.sessionId}")
        when (decision) {
            RestoreDecision.ATTACH_TO_RUNNING -> withContext(Dispatchers.Main.immediate) {
                _monitorBinding.value = saved
                restorePersistedReportSelections(saved.sessionId)
                refreshActiveSessionReports()
            }
            RestoreDecision.ASK_USER -> _pendingRestore.value = saved
            RestoreDecision.DISCARD -> discardSavedSession(saved.sessionId)
        }
    }

    private suspend fun discardSavedSession(sessionId: String) {
        if (BroadcastStatusRepository.state.value.isActive) {
            withContext(Dispatchers.Main.immediate) { requestServiceStop(StopReason.USER) }
            return
        }
        runCatching { monitorSessionStore.clear() }
        withContext(Dispatchers.Main.immediate) { clearPersistedReportSelections() }
        runCatching { liveTrainRepository.endMonitorSession(sessionId) }
    }

    /** User answer to "resume your unfinished broadcast?". The service re-validates the session. */
    fun answerPendingRestore(resume: Boolean) {
        val saved = _pendingRestore.value ?: return
        _pendingRestore.value = null
        if (!resume) {
            viewModelScope.launch(Dispatchers.IO) { discardSavedSession(saved.sessionId) }
            return
        }
        _monitorBinding.value = saved
        restorePersistedReportSelections(saved.sessionId)
        BroadcastStatusRepository.update(BroadcastState.Starting)
        if (!startTrainTrackingService(saved.sessionId, saved.lineId, saved.direction, saved.tripId, saved.trainId)) {
            BroadcastStatusRepository.update(BroadcastState.Stopped(StopReason.ERROR))
        }
    }

    private fun restoreLocalState() {
        _isWhistleSoundEnabled.value = localPrefs.getBoolean("whistle_enabled", true)
        _selectedSoundType.value = runCatching {
            TrainSoundType.valueOf(localPrefs.getString("sound_type", TrainSoundType.STEAM_WHISTLE.name)!!)
        }.getOrDefault(TrainSoundType.STEAM_WHISTLE)
        _alertDistanceThresholdKm.value = localPrefs.getFloat("alert_distance_km", 3.5f).coerceIn(0.5f, 20f)
        _isVibrationEnabled.value = localPrefs.getBoolean("vibration_enabled", true)
        _isBackgroundNotificationEnabled.value = localPrefs.getBoolean("background_notification_enabled", true)
        _selectedTheme.value = localPrefs.getString("theme", "dark") ?: "dark"

        val raw = localPrefs.getString("favorites", null) ?: return
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val restored = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val suburb = TrainRepository.suburbLines.firstOrNull { it.id == item.optString("suburb_id") } ?: continue
                val station = suburb.stations.firstOrNull { it.id == item.optString("station_id") } ?: continue
                add(
                    FavoriteStation(
                        station = station,
                        suburbId = suburb.id,
                        suburbName = suburb.name,
                        tag = item.optString("tag", "محطة مفضلة"),
                        tagEmoji = item.optString("emoji", "⭐"),
                    )
                )
            }
        }
        _favoriteStations.value = restored
    }

    private fun persistReportSelection(sessionId: String) {
        localPrefs.edit()
            .putString("report_state_session_id", sessionId)
            .putString("report_crowding", _selectedCrowdingReport.value?.name)
            .putString("report_delay", _selectedDelayReport.value?.name)
            .apply()
    }

    private fun restorePersistedReportSelections(sessionId: String) {
        if (localPrefs.getString("report_state_session_id", null) != sessionId) {
            _selectedCrowdingReport.value = null
            _selectedDelayReport.value = null
            return
        }
        _selectedCrowdingReport.value = runCatching {
            localPrefs.getString("report_crowding", null)?.let(CrowdingLevel::valueOf)
        }.getOrNull()
        _selectedDelayReport.value = runCatching {
            localPrefs.getString("report_delay", null)?.let(DelayLevel::valueOf)
        }.getOrNull()
    }

    private fun clearPersistedReportSelections() {
        _selectedCrowdingReport.value = null
        _selectedDelayReport.value = null
        localPrefs.edit()
            .remove("report_state_session_id")
            .remove("report_crowding")
            .remove("report_delay")
            .apply()
    }

    fun triggerSelectedSound(soundType: TrainSoundType = _selectedSoundType.value) {
        viewModelScope.launch(Dispatchers.Default) {
            TrainSoundSynthesizer.playSound(soundType)
        }
        triggerVibration()
    }

    private fun triggerVibration() {
        if (!_isVibrationEnabled.value) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 250, 100, 400), -1)
            }
        } catch (_: Exception) {}
    }

    private fun verifyPassengerLocation(gps: LiveGpsData) {
        val stations = if (_isOnboardMode.value) {
            _broadcastLine.value.stations
        } else {
            _selectedSuburb.value.stations
        }
        val corridorDistMeters = GeoUtils.findClosestRailwaySegmentDistanceMeters(
            gps.latitude,
            gps.longitude,
            stations
        )
        _distanceToRailwayCorridorMeters.value = corridorDistMeters

        val decision = CorridorExitPolicy.evaluate(
            distanceMeters = corridorDistMeters,
            isDeadReckoning = gps.isDeadReckoning,
            isBroadcasting = _isOnboardMode.value,
            nowMs = System.currentTimeMillis(),
            previous = corridorExitState,
        )
                corridorExitState = decision.state
        PersistentAppLogger.write(
            "CORRIDOR_EXIT_SAMPLE distanceMeters=${"%.1f".format(Locale.US, corridorDistMeters)} " +
                "outsideSamples=${decision.state.outsideSampleCount} shouldStop=${decision.shouldStopBroadcast}"
        )
        when {

            gps.isDeadReckoning -> {
                _verificationStatus.value = VerificationStatus.TUNNEL_DEAD_RECKONING
            }
            corridorDistMeters <= CorridorExitPolicy.CORRIDOR_EXIT_DISTANCE_METERS -> {
                _verificationStatus.value = if (gps.speedKmh >= 12.0f) {
                    VerificationStatus.ON_TRACK_VERIFIED
                } else {
                    VerificationStatus.STATIONARY
                }
            }
            decision.shouldStopBroadcast -> {
                // The service enforces the stop (it also runs with the app closed).
                _verificationStatus.value = VerificationStatus.OUT_OF_CORRIDOR
            }
            _isOnboardMode.value -> {
                _verificationStatus.value = VerificationStatus.OUT_OF_CORRIDOR
            }
            else -> {
                _verificationStatus.value = VerificationStatus.WAITING_GPS
            }
        }
    }

    private fun startLiveRefresh() {
        liveRefreshJob?.cancel()
        liveRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                appVisible.first { it }
                ensureServerAwake()
                refreshLiveTrainsInternal()
                delay(15_000L)
            }
        }
    }

    fun refreshLiveTrains() {
        radarRefreshJob?.cancel()
        radarRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            refreshLiveTrainsInternal()
        }
    }

    private suspend fun ensureServerAwake() {
        if (serverWarmedUp || BuildConfig.USE_MOCK_LIVE_DATA) return
        _serverWaking.value = true
        try {
            liveTrainRepository.wakeServer()
            serverWarmedUp = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The refresh that follows reports the error to the user.
        } finally {
            _serverWaking.value = false
        }
    }

    private suspend fun refreshLiveTrainsInternal() = radarRefreshMutex.withLock {
        // Snapshot the selection so a slow response cannot be applied to a newer selection.
        val requestedLine = _selectedSuburb.value
        val requestedStation = _selectedStation.value
        val requestedDirection = _selectedDirectionFilter.value
        _isLiveDataLoading.value = true
        _liveDataError.value = null
        PersistentAppLogger.write(
            "RECEPTION_REFRESH_BEGIN lineId=${requestedLine.id} " +
                "station=${requestedStation.code} direction=$requestedDirection"
        )

        try {
            val trains = if (BuildConfig.USE_MOCK_LIVE_DATA) {
                PersistentAppLogger.write(
                    "RECEPTION_REFRESH_MOCK lineId=${requestedLine.id} " +
                        "station=${requestedStation.code}"
                )
                MockLiveTrainFactory.create(requestedLine, requestedStation)
            } else {
                liveTrainRepository.getLiveTrainsForLine(
                    requestedLine,
                    requestedStation
                )
            }.filter { train ->
                requestedDirection == TrainDirection.BOTH || train.direction == requestedDirection
            }

            if (!isCurrentRadarSelection(requestedLine.id, requestedStation.code, requestedDirection)) {
                PersistentAppLogger.write(
                    "RECEPTION_REFRESH_IGNORED_STALE " +
                        "lineId=${requestedLine.id} station=${requestedStation.code}"
                )
                return@withLock
            }

            _activeTrains.value = trains
            PersistentAppLogger.write(
                "RECEPTION_REFRESH_SUCCESS count=${trains.size} " +
                    "trains=${trains.take(10).joinToString(separator = ";") { train ->
                        "id=${train.id},speed=${train.speedKmh},eta=${train.etaToWaitingStationMinutes}," +
                            "status=${train.status},next=${train.nextStation?.code}"
                    }}"
            )
            if (trains.isEmpty()) {
                val trackableTrip = liveTrainRepository.getTrackableTripForLine(requestedLine)
                if (isCurrentRadarSelection(requestedLine.id, requestedStation.code, requestedDirection)) {
                    _liveDataError.value = if (trackableTrip == null) {
                        "لا توجد رحلات متاحة حاليًا لهذا المسار. غيّر الضاحية أو الاتجاه وحاول لاحقًا."
                    } else {
                        "الرحلة موجودة، لكن لا توجد بيانات موقع حي حاليًا. لا يمكن عرض قطار حتى تصل ملاحظة GPS حقيقية."
                    }
                }
            }
            checkApproachingTrain()
        } catch (error: CancellationException) {
            throw error // a newer selection replaced this request; not a failure
        } catch (error: Exception) {
            PersistentAppLogger.write(
                "RECEPTION_REFRESH_FAILED detail=${error.message ?: error.javaClass.simpleName}",
                error,
            )
            if (isCurrentRadarSelection(requestedLine.id, requestedStation.code, requestedDirection)) {
                _activeTrains.value = emptyList()
                _liveDataError.value = "تعذر جلب البث الحي من الخادم. تحقق من الاتصال وحاول مجدداً."
            }
        } finally {
            if (isCurrentRadarSelection(requestedLine.id, requestedStation.code, requestedDirection)) {
                _isLiveDataLoading.value = false
            }
        }
    }

    private fun isCurrentRadarSelection(
        lineId: String,
        stationCode: String,
        direction: TrainDirection,
    ): Boolean =
        _selectedSuburb.value.id == lineId &&
            _selectedStation.value.code == stationCode &&
            _selectedDirectionFilter.value == direction

    private fun recalculateActiveTrains() {
        refreshLiveTrains()
    }

    private fun checkTrainArrival(train: ActiveTrain, waitingStation: Station) {
        val distanceKm = train.distanceToWaitingStationKm ?: return
        val normalizedStatus = train.status?.trim()?.uppercase()
        val isAtSelectedStation = distanceKm <= 0.15f || normalizedStatus in setOf("ARRIVED", "AT_STATION", "ARRIVAL")
        val arrivalKey = "${train.tripId ?: train.id}:${waitingStation.code}"

        if (!isAtSelectedStation) {
            announcedArrivalKeys.remove(arrivalKey)
            return
        }
        if (arrivalKey in announcedArrivalKeys) return

        val didNotify = if (_isBackgroundNotificationEnabled.value) {
            TrainNotificationHelper.showTrainArrivalNotification(app, train.trainNumber, waitingStation.name)
        } else {
            false
        }
        if (didNotify) {
            announcedArrivalKeys += arrivalKey
            _approachingAlert.value = "وصل ${train.trainNumber} إلى محطة (${waitingStation.name})."
        }
    }

    /**
     * True only when the train's direction is known and its next stop has not yet
     * passed the waiting station along that direction. Trains moving away, on the
     * opposite track, or with unknown direction never trigger an approach alert.
     */
    private fun isHeadingToward(train: ActiveTrain, waitingStation: Station, line: SuburbLine): Boolean {
        val direction = train.direction ?: return false
        val next = train.nextStation ?: return false
        val nextIndex = line.stations.indexOfFirst { it.code == next.code }
        val waitIndex = line.stations.indexOfFirst { it.id == waitingStation.id }
        if (nextIndex < 0 || waitIndex < 0) return false
        val capitalAtEnd = line.stations.last().code in setOf("ALG", "AGH")
        val increasing = (direction == TrainDirection.INBOUND) == capitalAtEnd
        return if (increasing) nextIndex <= waitIndex else nextIndex >= waitIndex
    }

    private fun checkApproachingTrain() {
        val waitingStation = _selectedStation.value
        val thresholdKm = _alertDistanceThresholdKm.value

        _activeTrains.value.forEach { train ->
            checkTrainArrival(train, waitingStation)
        }

        val line = _selectedSuburb.value
        val approaching = _activeTrains.value.find { train ->
            isHeadingToward(train, waitingStation, line) && (
                (train.distanceToWaitingStationKm?.let { it <= thresholdKm } == true) ||
                    (train.etaToWaitingStationMinutes?.let { it <= 3 } == true)
                )
        }

        if (approaching != null) {
            val distanceText = approaching.distanceToWaitingStationKm?.let { "%.1f كم".format(it) } ?: "المسافة غير متوفرة"
            val etaText = approaching.etaToWaitingStationMinutes?.let { "$it دقائق" } ?: "الوقت غير متوفر"
            _approachingAlert.value = "تنبيه! ${approaching.trainNumber} على بُعد $distanceText من محطة (${waitingStation.name}) - الوصول: $etaText."

            val eta = approaching.etaToWaitingStationMinutes
            val distance = approaching.distanceToWaitingStationKm
            val now = System.currentTimeMillis()
            if (_isWhistleSoundEnabled.value && eta != null && distance != null && (now - lastWhistleAlertTime > 40000L)) {
                lastWhistleAlertTime = now
                triggerSelectedSound()
                if (_isBackgroundNotificationEnabled.value) {
                    TrainNotificationHelper.showApproachingNotification(
                        app,
                        approaching.trainNumber,
                        waitingStation.name,
                        eta,
                        distance
                    )
                }
            }
        } else {
            _approachingAlert.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // A running broadcast and an armed alarm outlive the screen; only the UI hold ends here.
        locationTracker.release(GpsOwner.UI)
        liveRefreshJob?.cancel()
        radarRefreshJob?.cancel()
    }
}
