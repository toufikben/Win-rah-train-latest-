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
import com.example.utils.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    constructor(app: Application) : this(app, true, LiveTrainRepository())
    constructor(app: Application, enableLivePolling: Boolean) : this(app, enableLivePolling, LiveTrainRepository())

    private val locationTracker = LocationTracker(app)

    val gpsData: StateFlow<LiveGpsData> = locationTracker.gpsData
    val isTrackingLocation: StateFlow<Boolean> = locationTracker.isTracking

    private val _isLiveDataLoading = MutableStateFlow(false)
    val isLiveDataLoading: StateFlow<Boolean> = _isLiveDataLoading.asStateFlow()

    private val _liveDataError = MutableStateFlow<String?>(null)
    val liveDataError: StateFlow<String?> = _liveDataError.asStateFlow()

    private val _suburbs = MutableStateFlow(TrainRepository.suburbLines)
    val suburbs: StateFlow<List<SuburbLine>> = _suburbs.asStateFlow()

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

    private val _selectedLineAlertModal = MutableStateFlow<LineAlert?>(null)
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

    private var liveRefreshJob: Job? = null
    private val _monitorBinding = MutableStateFlow<MonitorBinding?>(null)
    val monitorBinding: StateFlow<MonitorBinding?> = _monitorBinding.asStateFlow()
    private var lastObservationSentAt = 0L

    init {
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
            reportType = "CROWDING",
            description = "إفادة مستخدم: ${crowding.titleAr}"
        )
    }

    fun submitDelayReport(trainId: String, delay: DelayLevel) {
        submitEvidenceReport(
            trainId = trainId,
            reportType = "DELAY",
            description = "إفادة مستخدم: ${delay.titleAr}"
        )
    }

    fun submitCrowdingReport(crowding: CrowdingLevel) {
        submitBoundEvidenceReport(
            reportType = "CROWDING",
            description = "إفادة مستخدم: ${crowding.titleAr}"
        )
    }

    fun submitDelayReport(delay: DelayLevel) {
        submitBoundEvidenceReport(
            reportType = "DELAY",
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
    }

    fun setSelectedSoundType(type: TrainSoundType) {
        _selectedSoundType.value = type
    }

    fun setAlertDistanceThreshold(km: Float) {
        _alertDistanceThresholdKm.value = km
        checkApproachingTrain()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
    }

    fun setBackgroundNotificationEnabled(enabled: Boolean) {
        _isBackgroundNotificationEnabled.value = enabled
    }

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
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
                refreshLiveTrains()
                delay(15_000L)
            }
        }
    }

    fun refreshLiveTrains() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLiveDataLoading.value = true
            _liveDataError.value = null
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

    private fun checkApproachingTrain() {
        val waitingStation = _selectedStation.value
        val thresholdKm = _alertDistanceThresholdKm.value

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
