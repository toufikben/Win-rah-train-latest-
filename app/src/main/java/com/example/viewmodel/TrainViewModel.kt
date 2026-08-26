package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.TrainSoundSynthesizer
import com.example.audio.TrainSoundType
import com.example.data.LiveTrainRepository
import dz.winrah.trainradar.BuildConfig
import com.example.data.TrackGeometry
import com.example.data.TrainRepository
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportRequest
import com.example.location.LocationTracker
import com.example.model.ActiveTrain
import com.example.model.AlertSeverity
import com.example.model.CrowdingLevel
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
import com.example.notification.TrainNotificationHelper
import com.example.simulation.LocalTrainSimulation
import com.example.utils.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        BuildConfig.WINRAH_API_ENVIRONMENT != "local",
        LiveTrainRepository()
    )
    constructor(app: Application, enableLivePolling: Boolean) : this(app, enableLivePolling, LiveTrainRepository())

    private val locationTracker = LocationTracker(app)
    private val localPrefs = app.getSharedPreferences("winrah_local_state", Context.MODE_PRIVATE)

    val gpsData: StateFlow<LiveGpsData> = locationTracker.gpsData
    val isTrackingLocation: StateFlow<Boolean> = locationTracker.isTracking

    private val _isLiveDataLoading = MutableStateFlow(false)
    val isLiveDataLoading: StateFlow<Boolean> = _isLiveDataLoading.asStateFlow()

    private val _liveDataError = MutableStateFlow<String?>(null)
    val liveDataError: StateFlow<String?> = _liveDataError.asStateFlow()

    // Deferred local catalog entries are not shown as operational until the backend publishes trips.
    private val _suburbs = MutableStateFlow(
        TrainRepository.suburbLines.filterNot { it.id in setOf("airport_algiers", "thenia_tizi") }
    )
    val suburbs: StateFlow<List<SuburbLine>> = _suburbs.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()
    private var publishedUiLineIds: Set<String> = emptySet()

    private val _selectedSuburb = MutableStateFlow(TrainRepository.suburbLines.first())
    val selectedSuburb: StateFlow<SuburbLine> = _selectedSuburb.asStateFlow()

    private val _selectedStation = MutableStateFlow<Station>(TrainRepository.suburbLines.first().stations[1])
    val selectedStation: StateFlow<Station> = _selectedStation.asStateFlow()

    // 4. DIRECTION & PLATFORM FILTERING
    private val _selectedDirectionFilter = MutableStateFlow(TrainDirection.BOTH)
    val selectedDirectionFilter: StateFlow<TrainDirection> = _selectedDirectionFilter.asStateFlow()

    private val _verificationStatus = MutableStateFlow(VerificationStatus.WAITING_GPS)
    val verificationStatus: StateFlow<VerificationStatus> = _verificationStatus.asStateFlow()

    private val _activeTrains = MutableStateFlow<List<ActiveTrain>>(emptyList())
    val activeTrains: StateFlow<List<ActiveTrain>> = _activeTrains.asStateFlow()

    private val _selectedTrain = MutableStateFlow<ActiveTrain?>(null)
    val selectedTrain: StateFlow<ActiveTrain?> = _selectedTrain.asStateFlow()

    private val _isOnboardMode = MutableStateFlow(false)
    val isOnboardMode: StateFlow<Boolean> = _isOnboardMode.asStateFlow()

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
    private var simulationStartedAtSeconds = System.currentTimeMillis() / 1000L
    private val _monitorBinding = MutableStateFlow<MonitorBinding?>(null)
    val monitorBinding: StateFlow<MonitorBinding?> = _monitorBinding.asStateFlow()
    private var lastObservationSentAt = 0L

    init {
        restoreLocalState()

        // Initialize Android notification channels
        TrainNotificationHelper.initNotificationChannels(app)

        // Observe GPS updates and verify track alignment + Destination Alarm
        viewModelScope.launch {
            locationTracker.gpsData.collect { gps ->
                if (gps.isGpsActive) {
                    updateNearbyStations(gps.latitude, gps.longitude)
                    if (_isOnboardMode.value) {
                        verifyPassengerLocation(gps)
                        sendObservationIfEligible(gps)
                        if (_isBackgroundNotificationEnabled.value) {
                            val nextStName = _activeTrains.value.firstOrNull()?.nextStation?.name ?: "المحطة القادمة"
                            TrainNotificationHelper.showOngoingTripNotification(
                                app,
                                gps.speedKmh,
                                nextStName,
                                gps.isDeadReckoning
                            )
                        }
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
            refreshLiveTrains()
            startLiveRefresh()
        }
    }

    fun selectSuburb(suburb: SuburbLine) {
        _selectedSuburb.value = suburb
        _selectedStation.value = suburb.stations.getOrElse(1) { suburb.stations.first() }
        recalculateActiveTrains()
    }

    fun sendSimulationNotificationNow() {
        if (!_isSimulationMode.value) {
            _userFeedbackMessage.value = "شغّل المحاكاة أولاً لإرسال إشعار تجريبي."
            return
        }
        val sample = _activeTrains.value.firstOrNull() ?: return
        TrainNotificationHelper.showSimulationNotification(
            app,
            _selectedSuburb.value.name,
            _selectedStation.value.name,
            sample.etaToWaitingStationMinutes ?: 0,
            sample.speedKmh ?: 0f,
            sample.distanceToWaitingStationKm ?: 0f
        )
        _userFeedbackMessage.value = "تم إطلاق إشعار محاكاة محلي؛ لا يمثل قطارًا حيًا."
    }

    fun setSimulationMode(enabled: Boolean) {
        if (_isSimulationMode.value == enabled) return
        _isSimulationMode.value = enabled
        if (enabled) {
            simulationStartedAtSeconds = System.currentTimeMillis() / 1000L
            _suburbs.value = TrainRepository.suburbLines
            liveRefreshJob?.cancel()
            updateSimulatedTrains()
            val sample = _activeTrains.value.firstOrNull()
            if (sample != null) {
                TrainNotificationHelper.showSimulationNotification(
                    app,
                    _selectedSuburb.value.name,
                    _selectedStation.value.name,
                    sample.etaToWaitingStationMinutes ?: 0,
                    sample.speedKmh ?: 0f,
                    sample.distanceToWaitingStationKm ?: 0f
                )
            }
            startLiveRefresh()
            _userFeedbackMessage.value = "تم تشغيل محاكاة محلية: لا توجد بيانات حية ولا إرسال إلى الخادم."
        } else {
            _isSimulationMode.value = false
            _suburbs.value = if (publishedUiLineIds.isNotEmpty()) {
                TrainRepository.suburbLines.filter { it.id in publishedUiLineIds }
            } else {
                TrainRepository.suburbLines.filterNot { it.id in setOf("airport_algiers", "thenia_tizi") }
            }
            if (_selectedSuburb.value.id !in _suburbs.value.map { it.id }) {
                _selectedSuburb.value = _suburbs.value.first()
                _selectedStation.value = _selectedSuburb.value.stations.getOrElse(1) { _selectedSuburb.value.stations.first() }
            }
            _activeTrains.value = emptyList()
            liveRefreshJob?.cancel()
            if (enableLivePolling) {
                refreshLiveTrains()
                startLiveRefresh()
            }
            _userFeedbackMessage.value = "تم إيقاف المحاكاة والعودة إلى البيانات الحية/القراءة العادية."
        }
    }

    fun selectStation(station: Station) {
        _selectedStation.value = station
        recalculateActiveTrains()
        checkApproachingTrain()
    }

    fun selectTrain(train: ActiveTrain?) {
        _selectedTrain.value = train
    }

    fun setDirectionFilter(direction: TrainDirection) {
        _selectedDirectionFilter.value = direction
        recalculateActiveTrains()
    }

    fun toggleOnboardMode(enable: Boolean): Boolean {
        if (!enable) {
            val binding = _monitorBinding.value
            _monitorBinding.value = null
            locationTracker.stopLocationUpdates()
            TrainNotificationHelper.clearOngoingNotification(app)
            _isOnboardMode.value = false
            _verificationStatus.value = VerificationStatus.WAITING_GPS
            if (binding != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { liveTrainRepository.endMonitorSession(binding.sessionId) }
                }
            }
            return true
        }

        if (!enableLivePolling) {
            _userFeedbackMessage.value = "وضع الاختبار المحلي لا يشغّل البث الشبكي."
            return false
        }
        val liveTrain = _activeTrains.value.firstOrNull {
            !it.id.isBlank() && !it.tripId.isNullOrBlank()
        }
        viewModelScope.launch(Dispatchers.IO) {
            var createdSessionId: String? = null
            try {
                val train = liveTrain ?: throw IllegalStateException("no_live_trackable_train")
                val requestedTripId = train.tripId!!
                val requestedTrainId = train.id
                val session = liveTrainRepository.createMonitorSession(
                    tripId = requestedTripId,
                    trainId = requestedTrainId
                )
                createdSessionId = session.id
                if (session.tripId != requestedTripId || session.trainId != requestedTrainId) {
                    throw IllegalStateException("session_binding_mismatch")
                }
                if (!locationTracker.startLocationUpdates()) {
                    throw IllegalStateException("location_unavailable")
                }
                _monitorBinding.value = MonitorBinding(
                    sessionId = session.id,
                    tripId = session.tripId,
                    trainId = session.trainId
                )
                _isOnboardMode.value = true
                _userFeedbackMessage.value = "تم بدء البث الحقيقي من جهازك بعد إنشاء جلسة المراقبة."
            } catch (error: Exception) {
                if (createdSessionId != null) {
                    runCatching { liveTrainRepository.endMonitorSession(createdSessionId) }
                }
                _monitorBinding.value = null
                _isOnboardMode.value = false
                locationTracker.stopLocationUpdates()
                _verificationStatus.value = VerificationStatus.WAITING_GPS
                _userFeedbackMessage.value = if (error.message == "location_unavailable") {
                    "تعذر بدء الموقع. تحقق من صلاحية GPS وإشارة الجهاز."
                } else if (error.message == "no_live_trackable_train") {
                    "لا يوجد قطار حي موثق يمكن بدء المراقبة عليه الآن."
                } else if (error.message == "session_binding_mismatch") {
                    "رفض الخادم جلسة لا تطابق القطار المطلوب؛ لم يبدأ البث."
                } else {
                    "تعذر إنشاء جلسة البث مع الخادم. حاول لاحقاً."
                }
            }
        }
        return true
    }

    fun refreshGpsLocation() {
        locationTracker.startLocationUpdates()
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
        locationTracker.startLocationUpdates()
        _userFeedbackMessage.value = "تم تفعيل منبه النزول لمحطة (${station.name}) - سنوقظك قبل ${alertDistanceKm} كم! ⏰"
    }

    fun dismissDestinationAlarm() {
        _destinationAlarm.value = _destinationAlarm.value.copy(isTriggered = false, isEnabled = false)
        _userFeedbackMessage.value = "تم إيقاف المنبه بنجاح. رحلة موفقة! 🚆"
    }

    fun cancelDestinationAlarm() {
        _destinationAlarm.value = DestinationAlarm(isEnabled = false)
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
        submitEvidenceReport(
            trainId = trainId,
            reportType = "OTHER",
            description = "إفادة مستخدم: ${crowding.titleAr}"
        )
    }

    fun submitDelayReport(trainId: String, delay: DelayLevel) {
        submitEvidenceReport(
            trainId = trainId,
            reportType = "DELAYED",
            description = "إفادة مستخدم: ${delay.titleAr}"
        )
    }

    fun submitCrowdingReport(crowding: CrowdingLevel) {
        submitBoundEvidenceReport(
            reportType = "OTHER",
            description = "إفادة مستخدم: ${crowding.titleAr}"
        )
    }

    fun submitDelayReport(delay: DelayLevel) {
        submitBoundEvidenceReport(
            reportType = "DELAYED",
            description = "إفادة مستخدم: ${delay.titleAr}"
        )
    }

    private fun submitEvidenceReport(trainId: String, reportType: String, description: String) {
        val tripId = _activeTrains.value.firstOrNull { it.id == trainId }?.tripId
        submitEvidenceReport(trainId, tripId, reportType, description)
    }

    private fun submitBoundEvidenceReport(reportType: String, description: String) {
        val binding = _monitorBinding.value
        if (binding == null) {
            _userFeedbackMessage.value = "ابدأ بثًا حيًا موثقًا قبل إرسال إفادة من وضع الراكب."
            return
        }
        submitEvidenceReport(binding.trainId, binding.tripId, reportType, description)
    }

    private fun submitEvidenceReport(
        trainId: String,
        tripId: String?,
        reportType: String,
        description: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveTrainRepository.submitReport(
                    ReportRequest(
                        trainId = trainId,
                        tripId = tripId,
                        stationId = liveTrainRepository.canonicalStationId(_selectedStation.value),
                        reportType = reportType,
                        description = description
                    )
                )
                _userFeedbackMessage.value = "تم إرسال الإفادة إلى الخادم كبيان من مستخدمين، شكرًا لمساهمتك."
            } catch (_: Exception) {
                _userFeedbackMessage.value = "تعذر إرسال الإفادة الآن. تحقق من الاتصال وحاول مجددًا."
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
        val stations = _selectedSuburb.value.stations
        val corridorDistMeters = GeoUtils.findClosestRailwaySegmentDistanceMeters(
            gps.latitude,
            gps.longitude,
            stations
        )
        _distanceToRailwayCorridorMeters.value = corridorDistMeters

        if (gps.isDeadReckoning) {
            _verificationStatus.value = VerificationStatus.TUNNEL_DEAD_RECKONING
        } else if (corridorDistMeters <= 400.0) {
            if (gps.speedKmh >= 12.0f) {
                _verificationStatus.value = VerificationStatus.ON_TRACK_VERIFIED
            } else {
                _verificationStatus.value = VerificationStatus.STATIONARY
            }
        } else {
            _verificationStatus.value = VerificationStatus.OUT_OF_CORRIDOR
        }
    }

    private fun sendObservationIfEligible(gps: LiveGpsData) {
        val binding = _monitorBinding.value ?: return
        if (_verificationStatus.value != VerificationStatus.ON_TRACK_VERIFIED &&
            _verificationStatus.value != VerificationStatus.STATIONARY
        ) return
        val now = System.currentTimeMillis()
        if (now - lastObservationSentAt < 10_000L) return
        lastObservationSentAt = now
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                liveTrainRepository.submitObservation(
                    ObservationRequest(
                        sessionId = binding.sessionId,
                        tripId = binding.tripId,
                        trainId = binding.trainId,
                        latitude = gps.latitude,
                        longitude = gps.longitude,
                        accuracy = gps.accuracyMeters,
                        speed = gps.speedKmh / 3.6f,
                        heading = null,
                        timestamp = if (gps.timestamp > 0L) gps.timestamp else now
                    )
                )
            }.onFailure {
                _userFeedbackMessage.value = "تعذر إرسال آخر ملاحظة للمصدر الحي؛ ستستمر المحاولة تلقائياً."
            }
        }
    }

    private fun startLiveRefresh() {
        liveRefreshJob?.cancel()
        liveRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_isSimulationMode.value) {
                    updateSimulatedTrains()
                    delay(1_000L)
                } else {
                    refreshLiveTrains()
                    delay(15_000L)
                }
            }
        }
    }

    fun refreshLiveTrains() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLiveDataLoading.value = true
            _liveDataError.value = null
            if (_isSimulationMode.value) {
                updateSimulatedTrains()
                _isLiveDataLoading.value = false
                return@launch
            }
            try {
                val trains = liveTrainRepository.getLiveTrainsForLine(
                    _selectedSuburb.value,
                    _selectedStation.value
                ).filter { train ->
                    _selectedDirectionFilter.value == TrainDirection.BOTH ||
                        train.direction == null ||
                        train.direction == _selectedDirectionFilter.value
                }
                _activeTrains.value = trains
                checkApproachingTrain()
            } catch (_: Exception) {
                _activeTrains.value = emptyList()
                _liveDataError.value = "تعذر جلب البث الحي من الخادم. تحقق من الاتصال وحاول مجدداً."
            } finally {
                _isLiveDataLoading.value = false
            }
        }
    }

    private fun recalculateActiveTrains() {
        refreshLiveTrains()
    }

    private fun updateSimulatedTrains() {
        val elapsed = (System.currentTimeMillis() / 1000L - simulationStartedAtSeconds).coerceAtLeast(0L)
        _activeTrains.value = LocalTrainSimulation.trainsFor(
            _selectedSuburb.value,
            _selectedStation.value,
            elapsed
        ).filter { train ->
            _selectedDirectionFilter.value == TrainDirection.BOTH || train.direction == _selectedDirectionFilter.value
        }
        _liveDataError.value = null
        checkApproachingTrain()
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

    private fun checkApproachingTrain() {
        val waitingStation = _selectedStation.value
        val thresholdKm = _alertDistanceThresholdKm.value

        _activeTrains.value.forEach { train ->
            checkTrainArrival(train, waitingStation)
        }

        val approaching = _activeTrains.value.find { train ->
            (train.distanceToWaitingStationKm?.let { it <= thresholdKm } == true) ||
                (train.etaToWaitingStationMinutes?.let { it <= 3 } == true)
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
        locationTracker.stopLocationUpdates()
        TrainNotificationHelper.clearOngoingNotification(app)
        liveRefreshJob?.cancel()
    }
}
