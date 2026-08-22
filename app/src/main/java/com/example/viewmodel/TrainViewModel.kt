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
import com.example.data.TrainRepository
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

class TrainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val locationTracker = LocationTracker(app)

    val gpsData: StateFlow<LiveGpsData> = locationTracker.gpsData
    val isTrackingLocation: StateFlow<Boolean> = locationTracker.isTracking
    val isTunnelSimulationMode: StateFlow<Boolean> = locationTracker.isTunnelSimulationMode

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
    private val _crowdReportsMap = MutableStateFlow<Map<String, CrowdsourcedReport>>(
        mapOf(
            "tr_thnia_algiers_1" to CrowdsourcedReport(CrowdingLevel.MODERATE, DelayLevel.ON_TIME, 18, 1),
            "tr_thnia_algiers_2" to CrowdsourcedReport(CrowdingLevel.LOW, DelayLevel.DELAY_5, 7, 3),
            "tr_zeralda_algiers_1" to CrowdsourcedReport(CrowdingLevel.HIGH, DelayLevel.DELAY_5, 24, 2),
            "tr_algiers_affroun_1" to CrowdsourcedReport(CrowdingLevel.MODERATE, DelayLevel.ON_TIME, 15, 4),
            "tr_airport_algiers_1" to CrowdsourcedReport(CrowdingLevel.LOW, DelayLevel.ON_TIME, 9, 1),
            "tr_thenia_tizi_1" to CrowdsourcedReport(CrowdingLevel.MODERATE, DelayLevel.DELAY_5, 11, 5)
        )
    )
    val crowdReportsMap: StateFlow<Map<String, CrowdsourcedReport>> = _crowdReportsMap.asStateFlow()

    // 3. FAVORITES AND QUICK WIDGETS
    private val _favoriteStations = MutableStateFlow<List<FavoriteStation>>(
        listOf(
            FavoriteStation(
                station = TrainRepository.suburbLines[0].stations[15], // الجزائر المركزية
                suburbId = TrainRepository.suburbLines[0].id,
                suburbName = TrainRepository.suburbLines[0].name,
                tag = "العمل / العاصمة",
                tagEmoji = "🏢"
            ),
            FavoriteStation(
                station = TrainRepository.suburbLines[0].stations[2], // بومرداس
                suburbId = TrainRepository.suburbLines[0].id,
                suburbName = TrainRepository.suburbLines[0].name,
                tag = "المنزل",
                tagEmoji = "🏠"
            ),
            FavoriteStation(
                station = TrainRepository.suburbLines[0].stations[9], // باب الزوار
                suburbId = TrainRepository.suburbLines[0].id,
                suburbName = TrainRepository.suburbLines[0].name,
                tag = "الجامعة / USTHB",
                tagEmoji = "🎓"
            )
        )
    )
    val favoriteStations: StateFlow<List<FavoriteStation>> = _favoriteStations.asStateFlow()

    // 5. LINE DISRUPTIONS & WEATHER ALERTS TICKER
    private val _lineAlerts = MutableStateFlow<List<LineAlert>>(TrainRepository.initialLineAlerts)
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

    // Live continuous train track progression
    private var trainProgressA = 0.28f
    private var trainProgressB = 0.65f
    private var trainUpdateJob: Job? = null

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

        startRealTimeTrackEngine()
        updateNearbyStations(36.7538, 3.0588) // Initialize with Algiers center coordinates
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

    fun toggleTunnelSimulation(enable: Boolean) {
        locationTracker.toggleTunnelSimulation(enable)
        val msg = if (enable) "تم تفعيل محاكاة النفق: نظام القصور الذاتي (Dead-Reckoning) يعمل الآن 🚇" else "تمت استعادة إشارة GPS الطبيعية 🛰️"
        _userFeedbackMessage.value = msg
    }

    fun toggleOnboardMode(enable: Boolean): Boolean {
        _isOnboardMode.value = enable
        if (enable) {
            val started = locationTracker.startLocationUpdates()
            if (!started) {
                _verificationStatus.value = VerificationStatus.WAITING_GPS
            }
            return started
        } else {
            locationTracker.stopLocationUpdates()
            TrainNotificationHelper.clearOngoingNotification(app)
            _verificationStatus.value = VerificationStatus.WAITING_GPS
            return true
        }
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
        val current = _crowdReportsMap.value[trainId] ?: CrowdsourcedReport()
        val updated = current.copy(
            crowding = crowding,
            reportCount = current.reportCount + 1,
            lastUpdatedMinutesAgo = 0
        )
        val map = _crowdReportsMap.value.toMutableMap()
        map[trainId] = updated
        _crowdReportsMap.value = map

        recalculateActiveTrains()
        _userFeedbackMessage.value = "شكراً لمشاركتك! تم تسجيل حالة الاكتظاظ (${crowding.titleAr}) لمساعدة بقية الركاب 👥"
    }

    fun submitDelayReport(trainId: String, delay: DelayLevel) {
        val current = _crowdReportsMap.value[trainId] ?: CrowdsourcedReport()
        val updated = current.copy(
            delay = delay,
            reportCount = current.reportCount + 1,
            lastUpdatedMinutesAgo = 0
        )
        val map = _crowdReportsMap.value.toMutableMap()
        map[trainId] = updated
        _crowdReportsMap.value = map

        recalculateActiveTrains()
        _userFeedbackMessage.value = "شكراً لك! تم تحديث تقرير التأخير (${delay.titleAr}) على الرادار ⏱️"
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
        recalculateActiveTrains()
    }

    private fun startRealTimeTrackEngine() {
        trainUpdateJob?.cancel()
        trainUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2500L) // Smooth 2.5s position refresh along the rail track
                trainProgressA = (trainProgressA + 0.007f) % 1.0f
                trainProgressB = (trainProgressB + 0.005f) % 1.0f
                recalculateActiveTrains()
            }
        }
    }

    private fun recalculateActiveTrains() {
        val suburb = _selectedSuburb.value
        val stations = suburb.stations
        val waitingStation = _selectedStation.value
        val filter = _selectedDirectionFilter.value

        if (stations.size < 2) return

        val trains = mutableListOf<ActiveTrain>()

        // Train 1: Live movement Inbound (towards Capital / Terminus A)
        val pos1 = interpolateTrackPosition(stations, trainProgressA)
        val dist1 = GeoUtils.calculateDistanceKm(pos1.lat, pos1.lon, waitingStation.latitude, waitingStation.longitude).toFloat()
        val speed1 = 82.0f
        val eta1 = GeoUtils.calculateEtaMinutes(dist1, speed1)

        val isPassengerBroadcasting = _isOnboardMode.value && (_verificationStatus.value == VerificationStatus.ON_TRACK_VERIFIED || _verificationStatus.value == VerificationStatus.TUNNEL_DEAD_RECKONING)
        val broadcasterCount = if (isPassengerBroadcasting) 3 else 1

        val currentLat1 = if (isPassengerBroadcasting && gpsData.value.isGpsActive) gpsData.value.latitude else pos1.lat
        val currentLon1 = if (isPassengerBroadcasting && gpsData.value.isGpsActive) gpsData.value.longitude else pos1.lon
        val currentSpeed1 = if (isPassengerBroadcasting && gpsData.value.speedKmh > 5f) gpsData.value.speedKmh else speed1

        val report1 = _crowdReportsMap.value["tr_${suburb.id}_1"] ?: CrowdsourcedReport(
            crowding = CrowdingLevel.LOW,
            delay = DelayLevel.ON_TIME,
            reportCount = 8,
            lastUpdatedMinutesAgo = 2
        )

        val train1 = ActiveTrain(
            id = "tr_${suburb.id}_1",
            trainNumber = "قطار المسافرين المباشر 🚆",
            suburbId = suburb.id,
            latitude = currentLat1,
            longitude = currentLon1,
            speedKmh = currentSpeed1,
            nextStation = pos1.next,
            prevStation = pos1.prev,
            distanceToWaitingStationKm = String.format("%.1f", dist1).toFloat(),
            etaToWaitingStationMinutes = eta1,
            isCrowdsourced = isPassengerBroadcasting,
            broadcasterCount = broadcasterCount,
            status = if (eta1 <= 3) "يقترب الآن من المحطة" else "في المسار المباشر",
            direction = TrainDirection.INBOUND,
            destinationName = suburb.inboundTerminus,
            platformTrack = waitingStation.defaultPlatform,
            isTunnelEstimate = gpsData.value.isDeadReckoning,
            crowdReport = report1
        )

        // Train 2: Outbound Train (towards Outer Suburbs)
        val pos2 = interpolateTrackPosition(stations, 1.0f - trainProgressB)
        val dist2 = GeoUtils.calculateDistanceKm(pos2.lat, pos2.lon, waitingStation.latitude, waitingStation.longitude).toFloat()
        val speed2 = 70.0f
        val eta2 = GeoUtils.calculateEtaMinutes(dist2, speed2)

        val report2 = _crowdReportsMap.value["tr_${suburb.id}_2"] ?: CrowdsourcedReport(
            crowding = CrowdingLevel.MODERATE,
            delay = DelayLevel.DELAY_5,
            reportCount = 14,
            lastUpdatedMinutesAgo = 1
        )

        val train2 = ActiveTrain(
            id = "tr_${suburb.id}_2",
            trainNumber = "قطار الضواحي العادي 🚆",
            suburbId = suburb.id,
            latitude = pos2.lat,
            longitude = pos2.lon,
            speedKmh = speed2,
            nextStation = pos2.prev,
            prevStation = pos2.next,
            distanceToWaitingStationKm = String.format("%.1f", dist2).toFloat(),
            etaToWaitingStationMinutes = eta2,
            isCrowdsourced = false,
            broadcasterCount = 2,
            status = if (eta2 <= 3) "يقترب الآن من المحطة" else "في الموعد",
            direction = TrainDirection.OUTBOUND,
            destinationName = suburb.outboundTerminus,
            platformTrack = if (waitingStation.defaultPlatform == "رصيف 1") "رصيف 2" else "رصيف 1",
            isTunnelEstimate = false,
            crowdReport = report2
        )

        // Filter based on selected direction
        if (filter == TrainDirection.BOTH || filter == TrainDirection.INBOUND) {
            trains.add(train1)
        }
        if (filter == TrainDirection.BOTH || filter == TrainDirection.OUTBOUND) {
            trains.add(train2)
        }

        _activeTrains.value = trains
        checkApproachingTrain()

        if (!_isOnboardMode.value && _destinationAlarm.value.isEnabled) {
            checkDestinationAlarm(pos1.lat, pos1.lon)
        }
    }

    private fun interpolateTrackPosition(stations: List<Station>, progress: Float): TrackPosition {
        val totalSegments = stations.size - 1
        val scaled = (progress * totalSegments).coerceIn(0.0f, totalSegments.toFloat())
        val index = scaled.toInt().coerceIn(0, totalSegments - 1)
        val frac = (scaled - index).toDouble()

        val s1 = stations[index]
        val s2 = stations[index + 1]

        val lat = s1.latitude + frac * (s2.latitude - s1.latitude)
        val lon = s1.longitude + frac * (s2.longitude - s1.longitude)
        return TrackPosition(lat, lon, s1, s2)
    }

    private fun checkApproachingTrain() {
        val waitingStation = _selectedStation.value
        val thresholdKm = _alertDistanceThresholdKm.value

        val approaching = _activeTrains.value.find {
            it.distanceToWaitingStationKm <= thresholdKm || it.etaToWaitingStationMinutes <= 3
        }

        if (approaching != null) {
            val alertText = "تنبيه! ${approaching.trainNumber} على بُعد ${approaching.distanceToWaitingStationKm} كم من محطة (${waitingStation.name}) - الوصول: ${approaching.etaToWaitingStationMinutes} دقائق."
            _approachingAlert.value = alertText

            val now = System.currentTimeMillis()
            if (_isWhistleSoundEnabled.value && (now - lastWhistleAlertTime > 40000L)) {
                lastWhistleAlertTime = now
                triggerSelectedSound()

                if (_isBackgroundNotificationEnabled.value) {
                    TrainNotificationHelper.showApproachingNotification(
                        app,
                        approaching.trainNumber,
                        waitingStation.name,
                        approaching.etaToWaitingStationMinutes,
                        approaching.distanceToWaitingStationKm
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopLocationUpdates()
        TrainNotificationHelper.clearOngoingNotification(app)
        trainUpdateJob?.cancel()
    }
}
