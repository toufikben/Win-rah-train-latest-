package com.example.data

import com.example.data.remote.TrainApi
import com.example.data.remote.dto.DepartureDto
import com.example.data.remote.dto.LineDto
import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.StationDto
import com.example.design.LineColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A station as the new screens use it. */
data class Station(
    val id: String,
    val nameAr: String,
    val nameFr: String,
    val latitude: Double,
    val longitude: Double,
    val lineCodes: List<String> = emptyList(),
)

data class Line(
    val id: String,
    val code: String?,
    val nameAr: String,
    val stations: List<LineStation>,
    val active: Boolean,
)

data class LineStation(val id: String, val nameAr: String, val chainageM: Double, val served: Boolean)

/** Reference data (stations, lines) is cached for the session; live data is fetched on demand. */
class NetworkRepository(private val api: TrainApi) {
    private val mutex = Mutex()
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()
    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    suspend fun loadReference(force: Boolean = false) = mutex.withLock {
        if (!force && _stations.value.isNotEmpty() && _lines.value.isNotEmpty()) return@withLock
        val lineDtos = api.getLines()
        val lines = lineDtos.map { it.toLine() }
        val codesByStation = mutableMapOf<String, MutableList<String>>()
        for (l in lines) for (s in l.stations) if (s.served && l.code != null) {
            codesByStation.getOrPut(s.id) { mutableListOf() }.add(l.code)
        }
        _lines.value = lines
        _stations.value = api.getStations().map { it.toStation(codesByStation[it.id].orEmpty()) }
    }

    suspend fun departures(stationId: String, limit: Int = 12): List<DepartureDto> = api.getDepartures(stationId, limit)

    suspend fun liveTrains(): List<LiveTrainDto> = api.getLiveTrains()

    fun station(id: String?): Station? = id?.let { sid -> _stations.value.firstOrNull { it.id == sid } }

    /** Nearest stations to a point, with straight-line distance in metres. */
    fun nearest(lat: Double, lon: Double, count: Int = 3): List<Pair<Station, Double>> =
        _stations.value.map { it to haversine(lat, lon, it.latitude, it.longitude) }
            .sortedBy { it.second }.take(count)

    private fun LineDto.toLine() = Line(
        id = id,
        code = code ?: LineColors.codeForId(id),
        nameAr = nameAr ?: "",
        stations = stations.map { LineStation(it.id, it.nameAr ?: "", it.chainageM, it.served) },
        active = status != "deferred",
    )

    private fun StationDto.toStation(codes: List<String>) = Station(
        id = id, nameAr = nameAr ?: nameFr ?: id, nameFr = nameFr ?: "",
        latitude = latitude, longitude = longitude, lineCodes = codes.distinct(),
    )

    companion object {
        fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat)
            val dp = Math.toRadians(bLat - aLat); val dl = Math.toRadians(bLon - aLon)
            val x = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * r * atan2(sqrt(x), sqrt(1 - x))
        }
    }
}
