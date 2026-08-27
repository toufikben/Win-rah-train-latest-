package com.example.data

import android.content.Context
import com.example.data.remote.BackendService
import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.MonitorSessionRequest
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportDto
import com.example.data.remote.dto.ReportRequest
import com.example.data.remote.dto.ResumeMonitorSessionRequest
import com.example.data.remote.dto.StationDto
import com.example.data.remote.dto.TripDto
import com.example.model.ActiveTrain
import com.example.model.BroadcastTripOption
import com.example.model.Station
import com.example.model.SuburbLine
import com.example.model.TrainDirection
import com.example.utils.GeoUtils
import java.util.UUID

data class TrackGeometry(
    val coordinates: List<List<Double>>,
    val sourceKind: String
)

class LiveTrainRepository(
    private val api: com.example.data.remote.TrainApi = BackendService.api,
    private val context: Context? = null,
) {
    private var stationsById: Map<String, StationDto> = emptyMap()
    private var tripsById: Map<String, TripDto> = emptyMap()

    suspend fun refreshReferenceData() {
        val stations = api.getStations()
        val trips = api.getTrips()
        stationsById = stations.associateBy { it.id }
        tripsById = trips.associateBy { it.id }
    }

    suspend fun getTrackableTripForLine(line: SuburbLine): Pair<String, String>? {
        if (tripsById.isEmpty()) refreshReferenceData()
        return tripsById.values
            .firstOrNull { matchesWinRahLine(line.id, it.lineId) && it.trainId != null }
            ?.let { trip -> trip.id to trip.trainId!! }
    }

    suspend fun getBroadcastTripsForLine(
        line: SuburbLine,
        direction: TrainDirection,
    ): List<BroadcastTripOption> {
        if (tripsById.isEmpty()) refreshReferenceData()
        return tripsById.values
            .filter { trip ->
                matchesWinRahLine(line.id, trip.lineId) &&
                    trip.trainId != null &&
                    directionMatches(direction, trip.direction)
            }
            .sortedWith(compareBy({ it.scheduledDeparture == null }, { it.scheduledDeparture ?: "" }, { it.id }))
            .map { trip ->
                BroadcastTripOption(
                    tripId = trip.id,
                    trainId = trip.trainId!!,
                    lineId = line.id,
                    direction = parseDirection(trip.direction)!!,
                    status = trip.status,
                    scheduledDeparture = trip.scheduledDeparture,
                    scheduledArrival = trip.scheduledArrival,
                )
            }
    }

    private fun directionMatches(selected: TrainDirection, backendDirection: String?): Boolean {
        if (selected == TrainDirection.BOTH) return true
        return parseDirection(backendDirection) == selected
    }

    private fun parseDirection(value: String?): TrainDirection? = when (value?.uppercase()) {
        "INBOUND" -> TrainDirection.INBOUND
        "OUTBOUND" -> TrainDirection.OUTBOUND
        else -> null
    }

    suspend fun getLiveTrainsForLine(line: SuburbLine, waitingStation: Station): List<ActiveTrain> {
        if (tripsById.isEmpty()) refreshReferenceData()
        val live = api.getLiveTrains()
        return live.mapNotNull { dto ->
            val backendLineId = dto.lineId ?: dto.tripId?.let { tripsById[it]?.lineId }
            if (!matchesWinRahLine(line.id, backendLineId)) return@mapNotNull null
            toActiveTrain(dto, line, waitingStation)
        }
    }

    suspend fun getPublishedUiLineIds(): Set<String> {
        if (tripsById.isEmpty()) refreshReferenceData()
        return backendLineIdsByUiLine
            .filterValues { acceptedIds -> tripsById.values.any { it.lineId in acceptedIds } }
            .keys
    }

    suspend fun getRailwayGeometryForLine(line: SuburbLine): TrackGeometry? {
        val response = api.getRailwaySegments()
        val acceptedIds = backendLineIdsByUiLine[line.id].orEmpty()
        val feature = response.features.firstOrNull { candidate ->
            val geometry = candidate.geometry
            candidate.properties?.lineId in acceptedIds &&
                geometry?.type == "LineString" &&
                !geometry.coordinates.isNullOrEmpty()
        } ?: return null
        val geometry = feature.geometry ?: return null
        return TrackGeometry(
            coordinates = geometry.coordinates.orEmpty(),
            sourceKind = feature.properties?.sourceKind ?: "UNKNOWN",
        )
    }

    suspend fun createMonitorSession(tripId: String, trainId: String): MonitorSessionDto {
        return api.createMonitorSession(
            MonitorSessionRequest(
                tripId = tripId,
                trainId = trainId,
                anonymousMonitorId = getAnonymousMonitorId()
            )
        )
    }

    suspend fun resumeMonitorSession(sessionId: String, tripId: String, trainId: String): MonitorSessionDto =
        api.resumeMonitorSession(
            sessionId,
            ResumeMonitorSessionRequest(
                tripId = tripId,
                trainId = trainId,
                anonymousMonitorId = getAnonymousMonitorId(),
            ),
        )

    suspend fun endMonitorSession(sessionId: String): MonitorSessionDto =
        api.endMonitorSession(sessionId)

    suspend fun getReportsForSession(sessionId: String): List<ReportDto> =
        api.getReportsForSession(sessionId)

    suspend fun submitObservation(request: ObservationRequest): Map<String, Any?> {
        return api.submitObservation(request)
    }

    suspend fun submitReport(request: ReportRequest) {
        api.submitReport(request)
    }

    /** Resolve a local UI station to the canonical UUID returned by the backend. */
    suspend fun canonicalStationId(station: Station): String? {
        if (stationsById.isEmpty()) refreshReferenceData()
        stationsById[station.id]?.let { return it.id }

        stationsById.values.firstOrNull { server ->
            listOf(server.nameAr, server.nameFr, server.nameEn).any { name ->
                normalize(name) == normalize(station.name) || normalize(name) == normalize(station.code)
            }
        }?.let { return it.id }

        // Reference coordinates differ slightly between the two catalogs. Use a
        // conservative proximity fallback, never an arbitrary station ID.
        return stationsById.values
            .map { server ->
                server to GeoUtils.calculateDistanceKm(
                    station.latitude,
                    station.longitude,
                    server.latitude,
                    server.longitude,
                )
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= 2.0 }
            ?.first
            ?.id
    }

    private fun toActiveTrain(
        dto: LiveTrainDto,
        line: SuburbLine,
        waitingStation: Station
    ): ActiveTrain? {
        val position = dto.lastObservedPosition ?: dto.estimatedPosition ?: return null
        val id = dto.trainId ?: dto.id ?: return null
        val nextStation = dto.nextStation?.let(::toUiStation)
        val distanceKm = GeoUtils.calculateDistanceKm(
            position.latitude,
            position.longitude,
            waitingStation.latitude,
            waitingStation.longitude
        ).toFloat()
        val etaMinutes = dto.eta
            ?.takeIf { nextStation != null && sameStation(it.stationId, nextStation) }
            ?.estimatedArrivalMin
        val direction = dto.direction?.let {
            when (it.uppercase()) {
                "INBOUND" -> TrainDirection.INBOUND
                "OUTBOUND" -> TrainDirection.OUTBOUND
                else -> null
            }
        }
        val sourceCount = dto.sourceCount ?: dto.confidence?.sourceCount ?: 0
        val truth = dto.truth?.uppercase()
        val status = when (dto.status?.uppercase()) {
            "RUNNING" -> "يعمل الآن — بث من ركاب/مراقبين"
            "UNKNOWN" -> "الحالة غير مؤكدة"
            null -> null
            else -> dto.status
        }
        return ActiveTrain(
            id = id,
            tripId = dto.tripId,
            trainNumber = id,
            suburbId = line.id,
            latitude = position.latitude,
            longitude = position.longitude,
            speedKmh = dto.speedMps?.times(3.6f),
            nextStation = nextStation,
            prevStation = null,
            distanceToWaitingStationKm = distanceKm,
            etaToWaitingStationMinutes = etaMinutes,
            isCrowdsourced = sourceCount > 0 || truth == "OBSERVED" || truth == "ESTIMATED",
            broadcasterCount = sourceCount,
            status = status,
            direction = direction,
            destinationName = when (direction) {
                TrainDirection.INBOUND -> line.inboundTerminus
                TrainDirection.OUTBOUND -> line.outboundTerminus
                else -> null
            },
            platformTrack = null,
            // The Android client never performs tunnel/dead-reckoning simulation.
            isTunnelEstimate = false,
            crowdReport = null,
            lastUpdated = dto.lastUpdate,
            truth = dto.truth
        )
    }

    private fun toUiStation(dto: StationDto): Station {
        val known = TrainRepository.suburbLines
            .flatMap { it.stations }
            .firstOrNull { station ->
                normalize(station.name) == normalize(dto.nameAr)
                    || normalize(station.code) == normalize(dto.nameEn)
                    || normalize(station.code) == normalize(dto.nameFr)
            }
        return known ?: Station(
            id = dto.id,
            name = dto.nameAr ?: dto.nameFr ?: dto.nameEn ?: "غير متوفر",
            code = dto.nameEn ?: dto.id,
            latitude = dto.latitude,
            longitude = dto.longitude,
            order = 0
        )
    }

    private fun sameStation(serverStationId: String, uiStation: Station): Boolean {
        if (serverStationId == uiStation.id) return true
        val server = stationsById[serverStationId] ?: return false
        return normalize(server.nameAr) == normalize(uiStation.name)
    }

    /**
     * Canonical backend line identities.
     *
     * The reference seed stores UUIDs generated from the stable `line:<code>` key,
     * while older in-memory fixtures use the readable line code. Accepting both
     * keeps local tests compatible without allowing a name-only match.
     * Deferred lines are mapped proactively, but remain unavailable until the
     * backend publishes verified trips for their canonical UUIDs.
     */
    private val backendLineIdsByUiLine: Map<String, Set<String>> = mapOf(
        "thnia_algiers" to setOf(
            "line-suburb-thenia",
            "bd566e47-d079-561d-84d1-f66283653b14",
        ),
        "zeralda_algiers" to setOf(
            "line-suburb-zeralda",
            "e9b9c544-c906-5bc2-83ee-2bf46d97cef9",
        ),
        "algiers_affroun" to setOf(
            "line-suburb-elaffroun",
            "bd458a9a-5dab-5ee0-8c02-063626e8b0f2",
        ),
        "airport_algiers" to setOf(
            "line-suburb-airport-algiers",
            "38655abf-3a8c-5053-9c9e-fad27fa779dd",
        ),
        "thenia_tizi" to setOf(
            "line-suburb-thenia-tizi",
            "4955565d-df27-5d12-93da-3bc351899cfd",
        ),
    )

    private fun matchesWinRahLine(uiLineId: String, backendLineId: String?): Boolean {
        return backendLineId != null && backendLineId in backendLineIdsByUiLine[uiLineId].orEmpty()
    }

    private fun normalize(value: String?): String =
        value.orEmpty()
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ـ", "")
            .replace(" ", "")

    private fun getAnonymousMonitorId(): String {
        val preferences = context?.getSharedPreferences("winrah_identity", Context.MODE_PRIVATE)
        val existing = preferences?.getString("anonymous_monitor_id", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "android-${UUID.randomUUID()}"
        preferences?.edit()?.putString("anonymous_monitor_id", generated)?.apply()
        return generated
    }
}
