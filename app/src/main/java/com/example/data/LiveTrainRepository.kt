package com.example.data

import com.example.data.remote.BackendService
import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.MonitorSessionRequest
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportRequest
import com.example.data.remote.dto.StationDto
import com.example.data.remote.dto.TripDto
import com.example.model.ActiveTrain
import com.example.model.Station
import com.example.model.SuburbLine
import com.example.model.TrainDirection
import com.example.utils.GeoUtils
import java.util.UUID

class LiveTrainRepository(
    private val api: com.example.data.remote.TrainApi = BackendService.api
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

    suspend fun getLiveTrainsForLine(line: SuburbLine, waitingStation: Station): List<ActiveTrain> {
        if (tripsById.isEmpty()) refreshReferenceData()
        val live = api.getLiveTrains()
        return live.mapNotNull { dto ->
            val backendLineId = dto.lineId ?: dto.tripId?.let { tripsById[it]?.lineId }
            if (!matchesWinRahLine(line.id, backendLineId)) return@mapNotNull null
            toActiveTrain(dto, line, waitingStation)
        }
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

    suspend fun endMonitorSession(sessionId: String): MonitorSessionDto =
        api.endMonitorSession(sessionId)

    suspend fun submitObservation(request: ObservationRequest) {
        api.submitObservation(request)
    }

    suspend fun submitReport(request: ReportRequest) {
        api.submitReport(request)
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

    private fun matchesWinRahLine(uiLineId: String, backendLineId: String?): Boolean {
        val accepted = when (uiLineId) {
            "thnia_algiers" -> setOf("line-suburb-thenia")
            "zeralda_algiers" -> setOf("line-suburb-zeralda")
            "algiers_affroun" -> setOf("line-suburb-elaffroun")
            // The current backend reference data exposes no verified IDs for these two UI lines.
            // They therefore remain empty until the server publishes matching line IDs.
            "airport_algiers", "thenia_tizi" -> emptySet()
            else -> emptySet()
        }
        return backendLineId != null && backendLineId in accepted
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

    private fun getAnonymousMonitorId(): String =
        "android-${UUID.randomUUID()}"
}
