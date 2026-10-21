package com.example.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.alerts.ApproachAlertService
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
    val heading: Heading = Heading.TO_ALGIERS,
    val commuteMode: String? = null,      // "ذهاب" / "عودة" when the commute picked the view
    val hero: Hero? = null,
    val next: List<TrainRow> = emptyList(),
    val nearest: Pair<Station, Double>? = null,
    val alertOn: Boolean = false,
)

/** Algeria is UTC+1 all year. */
private val ALGERIA: ZoneOffset = ZoneOffset.ofHours(1)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    val prefs: StateFlow<Prefs> = c.prefs.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    private var manualStationId: String? = null
    private var manualHeading: Heading? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch { c.prefs.prefs.collect { recompute() } }
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
        val p = prefs.value
        val st = _state.value.station ?: return@launch
        val on = p.alertStationId != st.id
        c.prefs.setAlert(if (on) st.id else null, p.alertLeadMinutes)
        // The alert must survive the app being closed, so a foreground service follows the train.
        val ctx = getApplication<Application>()
        if (on) ApproachAlertService.start(ctx, ApproachAlertService.MODE_WAITING, st.id) else ApproachAlertService.stop(ctx)
    }

    fun setNearest(lat: Double, lon: Double) {
        _state.value = _state.value.copy(nearest = c.network.nearest(lat, lon, 1).firstOrNull())
    }

    private var lastDepartures: List<DepartureDto> = emptyList()

    private suspend fun refresh() {
        try {
            if (c.network.stations.value.isEmpty()) _state.value = _state.value.copy(serverWaking = true)
            c.network.loadReference()
            val stationId = chooseStation(prefs.value) ?: c.network.stations.value.firstOrNull()?.id ?: return
            lastDepartures = c.network.departures(stationId, 16)
            _state.value = _state.value.copy(loading = false, serverWaking = false, error = null)
            recompute()
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, serverWaking = false,
                error = "تعذر الاتصال. نعيد المحاولة تلقائيًا.")
        }
    }

    private fun nowMinute(): Int = LocalTime.now(ALGERIA).let { it.hour * 60 + it.minute }

    /** Commute window: an hour and a half around each commute time picks the view. */
    private fun commuteLeg(p: Prefs): Pair<String, String>? {
        val cm = p.commute ?: return null
        val now = nowMinute()
        return when {
            now in (cm.outboundMinute - 90)..(cm.outboundMinute + 120) -> cm.fromStationId to cm.toStationId
            now in (cm.returnMinute - 90)..(cm.returnMinute + 180) -> cm.toStationId to cm.fromStationId
            else -> null
        }
    }

    private fun chooseStation(p: Prefs): String? =
        manualStationId ?: commuteLeg(p)?.first ?: p.favoriteStationIds.firstOrNull() ?: p.commute?.fromStationId

    private fun recompute() {
        val p = prefs.value
        val lines = c.network.lines.value
        val stationId = chooseStation(p)
        val station = c.network.station(stationId)
        val leg = commuteLeg(p)?.takeIf { manualStationId == null || manualStationId == it.first }
        val legHeading = leg?.let { headingBetween(lines, it.first, it.second) }
        val heading = manualHeading ?: legHeading ?: _state.value.heading
        val rows = lastDepartures.map { it.toRow(lines) }
            .filter { it.heading == heading }
            .filter { row -> leg == null || reaches(lines, row.lineCode, leg.second) }
        val hero = rows.firstOrNull()?.let { heroFor(it, lines, station) }
        _state.value = _state.value.copy(
            station = station,
            favorites = p.favoriteStationIds.mapNotNull { c.network.station(it) },
            heading = heading,
            commuteMode = when {
                leg == null -> null
                leg.first == p.commute?.fromStationId -> "ذهاب"
                else -> "عودة"
            },
            hero = hero,
            next = rows.drop(1).take(4),
            alertOn = station != null && p.alertStationId == station.id,
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
        val toAlgiers = dest == "الجزائر" || dest == "آغا"
        val truth = when (truth) { "live" -> Truth.LIVE; "estimated" -> Truth.ESTIMATED; else -> Truth.SCHEDULED }
        val delay = eta?.let { e ->
            val expected = nowMinute() + e.min
            (expected - scheduledMinute).takeIf { it in -5..120 }
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
            title = if (toAlgiers) "${number ?: ""} · من ${originNameAr ?: ""}" else "${number ?: ""} · إلى $dest",
            note = note, time = scheduledTime, truth = truth, etaMin = eta?.min, etaMax = eta?.max,
            delayMin = delay, riders = sourceCount,
            heading = if (toAlgiers) Heading.TO_ALGIERS else Heading.TO_SUBURBS,
            destination = dest, origin = originNameAr ?: "",
        )
    }
}
