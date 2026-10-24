package com.example.feature.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.alerts.AlertResumePolicy
import com.example.alerts.ApproachAlertService
import com.example.data.CommuteLeg
import com.example.data.CommuteWindow
import com.example.data.Line
import com.example.data.Station
import com.example.data.prefs.Prefs
import com.example.data.remote.dto.DepartureDto
import com.example.design.Truth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneOffset

enum class Heading { TO_ALGIERS, TO_SUBURBS }

/** One train as the home and station screens show it. */
data class TrainRow(
    val tripId: String,
    val number: String?,
    val lineCode: String?,
    val lineName: String,
    val title: String,           // "22 · من الثنية"
    val note: String,            // day rule or express note
    val time: String,            // scheduled "H:MM"
    val truth: Truth,
    val etaMin: Int?,
    val etaMax: Int?,
    val delayMin: Int?,          // minutes late vs schedule, when live
    val riders: Int,
    val heading: Heading,
    val destination: String,
    val origin: String,
)

data class Hero(
    val row: TrainRow,
    val fromName: String,
    val midName: String?,
    val progress: Float,
)

data class HomeState(
    val loading: Boolean = true,
    val serverWaking: Boolean = false,
    val error: String? = null,
    val station: Station? = null,
    val favorites: List<Station> = emptyList(),
    val needsStation: Boolean = false,
    val heading: Heading = Heading.TO_ALGIERS,
    val commuteMode: String? = null,      // "ذهاب" / "عودة" when the commute picked the view
    val hero: Hero? = null,
    val next: List<TrainRow> = emptyList(),
    val nearest: Pair<Station, Double>? = null,
    val alertOn: Boolean = false,
    val alertMessage: String? = null,
    val armedAlert: ArmedAlert? = null,
    val armedBanner: ArmedBanner? = null,
    val switchNotice: SwitchNotice? = null,
)

/** "The alert moved to X"; the undo target is the pair it came from. */
data class SwitchNotice(val message: String, val undo: ArmedAlert)

/** Algeria is UTC+1 all year. */
private val ALGERIA: ZoneOffset = ZoneOffset.ofHours(1)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    val prefs: StateFlow<Prefs> = c.prefs.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    private var manualStationId: String? = null
    private var manualHeading: Heading? = null
    private var currentPrefs: Prefs? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            c.prefs.prefs.collect {
                currentPrefs = it
                recompute(it)
            }
        }
        viewModelScope.launch { resumeArmedAlert() }
        start()
    }

    fun start() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(20_000)
            }
        }
    }

    fun selectStation(id: String) { manualStationId = id; viewModelScope.launch { refresh() } }
    fun setHeading(h: Heading) { manualHeading = h; recompute() }

    fun toggleAlert() = viewModelScope.launch {
        val st = _state.value.station
        if (st == null) {
            _state.value = _state.value.copy(alertMessage = "اختر محطة أولًا لتفعيل تنبيه الاقتراب.")
            return@launch
        }
        val heading = _state.value.heading
        val armedNow = _state.value.armedAlert
        when (val tap = HomeAlertPolicy.tap(armedNow, st.id, heading)) {
            AlertTap.Cancel -> {
                disarm()
                _state.value = _state.value.copy(alertMessage = null, switchNotice = null)
            }
            is AlertTap.Arm -> armPair(tap.stationId, tap.heading)
            is AlertTap.Replace -> {
                if (armPair(tap.to.stationId, tap.to.heading)) {
                    val toName = c.network.station(tap.to.stationId)?.nameAr ?: HomeAlertPolicy.UNKNOWN_STATION
                    _state.value = _state.value.copy(
                        switchNotice = SwitchNotice(
                            "نُقل التنبيه إلى $toName · ${HomeAlertPolicy.headingLabel(tap.to.heading)}",
                            tap.from,
                        ),
                    )
                }
            }
        }
    }

    /** Cancels whatever is armed, even when the banner is showing a pair the view left behind. */
    fun cancelAlert() = viewModelScope.launch {
        val p = currentPrefs ?: prefs.value
        if (p.alertStationId == null) return@launch
        disarm()
        _state.value = _state.value.copy(alertMessage = null, switchNotice = null)
    }

    /** Jumps the view to the armed pair so the hero and the alert describe the same train. */
    fun showArmedAlert() {
        val a = _state.value.armedAlert ?: return
        manualStationId = a.stationId
        manualHeading = a.heading
        viewModelScope.launch { refresh() }
    }

    fun undoSwitch() = viewModelScope.launch {
        val notice = _state.value.switchNotice ?: return@launch
        _state.value = _state.value.copy(switchNotice = null)
        armPair(notice.undo.stationId, notice.undo.heading)
    }

    fun dismissSwitchNotice() {
        _state.value = _state.value.copy(switchNotice = null)
    }

    private suspend fun armPair(stationId: String, heading: Heading): Boolean {
        val p = currentPrefs ?: prefs.value
        if (!canPostNotifications()) {
            _state.value = _state.value.copy(alertMessage = "فعّل إشعارات التطبيق أولًا؛ التنبيه لا يعمل بدونها.")
            return false
        }
        val upcoming = HomeDepartures.visible(lastDepartures, heading, nowMinute())
        if (upcoming.isEmpty()) {
            _state.value = _state.value.copy(alertMessage = "لا توجد رحلات قادمة في هذا الاتجاه الآن؛ لم يُفعَّل التنبيه.")
            return false
        }
        c.prefs.setAlert(stationId, p.alertLeadMinutes, heading.name)
        // The alert must survive the app being closed, so a foreground service follows the train.
        ApproachAlertService.start(getApplication<Application>(), stationId, heading)
        _state.value = _state.value.copy(
            armedAlert = ArmedAlert(stationId, heading),
            alertMessage = if (upcoming.none { it.eta != null }) {
                "لا توجد بيانات مباشرة الآن؛ التنبيه جدولي (حسب التوقيت)."
            } else {
                null
            },
        )
        return true
    }

    private suspend fun disarm() {
        val p = currentPrefs ?: prefs.value
        c.prefs.setAlert(null, p.alertLeadMinutes, null)
        ApproachAlertService.stop(getApplication())
        _state.value = _state.value.copy(armedAlert = null, armedBanner = null, alertOn = false)
    }

    /** Re-arms the tracker after a process death or force-stop; otherwise the armed label would lie. */
    private suspend fun resumeArmedAlert() {
        val p = c.prefs.prefs.first()
        val stationId = p.alertStationId ?: return
        val heading = AlertResumePolicy.headingToResume(stationId, p.alertHeading, canPostNotifications())
        if (heading == null) {
            c.prefs.setAlert(null, p.alertLeadMinutes, null)
            if (!canPostNotifications()) {
                _state.value = _state.value.copy(alertMessage = "فعّل إشعارات التطبيق أولًا؛ التنبيه لا يعمل بدونها.")
            }
            return
        }
        ApproachAlertService.start(getApplication<Application>(), stationId, heading)
    }

    fun clearAlertMessage() {
        _state.value = _state.value.copy(alertMessage = null)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun setNearest(lat: Double, lon: Double) = viewModelScope.launch {
        val nearest = c.network.nearest(lat, lon, 1).firstOrNull() ?: return@launch
        _state.value = _state.value.copy(nearest = nearest)
        val p = c.prefs.prefs.first()
        if (HomeSelection.shouldAdoptNearest(manualStationId, p)) {
            manualStationId = nearest.first.id
            refresh()
        }
    }

    private var lastDepartures: List<DepartureDto> = emptyList()

    private suspend fun refresh() {
        try {
            if (c.network.stations.value.isEmpty()) _state.value = _state.value.copy(serverWaking = true)
            c.network.loadReference()
            val p = c.prefs.prefs.first()
            val stationId = chooseStation(p)
            if (stationId == null) {
                lastDepartures = emptyList()
                _state.value = _state.value.copy(loading = false, serverWaking = false, error = null)
                recompute(p)
                return
            }
            lastDepartures = c.network.departures(stationId, 16)
            _state.value = _state.value.copy(loading = false, serverWaking = false, error = null)
            recompute(p)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, serverWaking = false,
                error = "تعذر الاتصال. نعيد المحاولة تلقائيًا.")
        }
    }

    private fun nowMinute(): Int = LocalTime.now(ALGERIA).let { it.hour * 60 + it.minute }

    /** Commute window: an hour and a half around each commute time picks the view. */
    private fun commuteLeg(p: Prefs): CommuteLeg? {
        val commute = p.commute ?: return null
        return CommuteWindow.legNow(commute, nowMinute())
    }

    private fun chooseStation(p: Prefs): String? =
        manualStationId ?: commuteLeg(p)?.fromStationId ?: p.favoriteStationIds.firstOrNull() ?: p.commute?.fromStationId

    private fun recompute(p: Prefs = currentPrefs ?: prefs.value) {
        val lines = c.network.lines.value
        val stationId = chooseStation(p)
        val station = c.network.station(stationId)
        val leg = commuteLeg(p)?.takeIf { manualStationId == null || manualStationId == it.fromStationId }
        val legHeading = leg?.let { headingBetween(lines, it.fromStationId, it.toStationId) }
        val heading = manualHeading ?: legHeading ?: _state.value.heading
        val rows = HomeDepartures.visible(lastDepartures, heading, nowMinute())
            .map { it.toRow(lines) }
            .filter { row -> leg == null || reaches(lines, row.lineCode, leg.toStationId) }
        val hero = rows.firstOrNull()?.let { heroFor(it, lines, station) }
        val armedNow = HomeAlertPolicy.armed(p.alertStationId, p.alertHeading)
        _state.value = _state.value.copy(
            station = station,
            favorites = p.favoriteStationIds.mapNotNull { c.network.station(it) },
            heading = heading,
            commuteMode = when {
                leg == null -> null
                leg.fromStationId == p.commute?.fromStationId -> "ذهاب"
                else -> "عودة"
            },
            hero = hero,
            next = rows.drop(1).take(4),
            needsStation = !_state.value.loading && stationId == null,
            alertOn = station != null && p.alertStationId == station.id && p.alertHeading == heading.name,
            armedAlert = armedNow,
            armedBanner = HomeAlertPolicy.banner(armedNow, stationId, heading) { c.network.station(it)?.nameAr },
        )
    }

    private fun reaches(lines: List<Line>, lineCode: String?, stationId: String): Boolean =
        lines.firstOrNull { it.code == lineCode }?.stations?.any { it.id == stationId && it.served } ?: true

    private fun headingBetween(lines: List<Line>, from: String, to: String): Heading? {
        for (l in lines) {
            val a = l.stations.firstOrNull { it.id == from } ?: continue
            val b = l.stations.firstOrNull { it.id == to } ?: continue
            // Radial lines run from Alger outward: a lower chainage is closer to Algiers.
            return if (b.chainageM < a.chainageM) Heading.TO_ALGIERS else Heading.TO_SUBURBS
        }
        return null
    }

    private fun heroFor(row: TrainRow, lines: List<Line>, station: Station?): Hero {
        val line = lines.firstOrNull { it.code == row.lineCode }
        val here = line?.stations?.indexOfFirst { it.id == station?.id } ?: -1
        val step = if (row.heading == Heading.TO_ALGIERS) 1 else -1
        val before = { k: Int -> line?.stations?.getOrNull(here + step * k)?.nameAr }
        val eta = row.etaMin
        val progress = when {
            eta == null -> 0.15f
            else -> (1f - eta / 10f).coerceIn(0.05f, 0.95f)
        }
        return Hero(row, fromName = before(2) ?: row.origin, midName = before(1), progress = progress)
    }

    private fun DepartureDto.toRow(lines: List<Line>): TrainRow {
        val line = lines.firstOrNull { it.id == lineId }
        val dest = destinationNameAr ?: ""
        val heading = HomeDepartures.headingOf(dest)
        val truth = when (truth) { "live" -> Truth.LIVE; "estimated" -> Truth.ESTIMATED; else -> Truth.SCHEDULED }
        val delay = eta?.let { e ->
            val expected = nowMinute() + e.min
            CommuteWindow.circularDelayMinutes(expected, scheduledMinute).takeIf { it in -5..120 }
        }
        val note = when {
            express -> "سريع: لا يقف في $skipsCount محطات"
            serviceDays == "not_friday" -> "لا يسير يوم الجمعة"
            serviceDays == "friday_only" -> "يسير يوم الجمعة فقط"
            continuesTo != null -> "يواصل إلى $continuesTo"
            else -> "يقف في كل المحطات"
        }
        val number = trainNumber
        return TrainRow(
            tripId = tripId, number = number, lineCode = line?.code, lineName = line?.nameAr ?: "",
            title = if (heading == Heading.TO_ALGIERS) "${number ?: ""} · من ${originNameAr ?: ""}" else "${number ?: ""} · إلى $dest",
            note = note, time = scheduledTime, truth = truth, etaMin = eta?.min, etaMax = eta?.max,
            delayMin = delay, riders = sourceCount,
            heading = heading,
            destination = dest, origin = originNameAr ?: "",
        )
    }
}
