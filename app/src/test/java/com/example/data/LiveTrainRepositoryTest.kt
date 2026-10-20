package com.example.data

import com.example.data.remote.TrainApi
import com.example.data.remote.dto.EtaDto
import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.PositionDto
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.MonitorSessionRequest
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportDto
import com.example.data.remote.dto.ReportRequest
import com.example.data.remote.dto.ResumeMonitorSessionRequest
import com.example.data.remote.dto.RailwaySegmentsResponse
import com.example.data.remote.dto.StationDto
import com.example.data.remote.dto.TripDto
import com.example.data.remote.dto.TripStopDto
import com.example.model.SuburbLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTrainRepositoryTest {
    private val theniaLine = SuburbLine(
        id = "thnia_algiers",
        name = "Thenia",
        description = "test",
        stations = emptyList(),
    )

    @Test
    fun tripWithoutRealTrainIdIsNotTrackable() = runTest {
        val repository = LiveTrainRepository(
            FakeTrainApi(
                listOf(
                    TripDto(
                        id = "trip-uuid",
                        trainId = null,
                        lineId = "line-suburb-thenia",
                        direction = null,
                        scheduledDeparture = null,
                        scheduledArrival = null,
                        status = null,
                    ),
                ),
            ),
        )

        assertNull(repository.getTrackableTripForLine(theniaLine))
    }

    @Test
    fun deterministicBackendUuidMatchesUiLine() = runTest {
        val repository = LiveTrainRepository(
            FakeTrainApi(
                listOf(
                    TripDto(
                        id = "trip-uuid",
                        trainId = "train-uuid",
                        lineId = "bd566e47-d079-561d-84d1-f66283653b14",
                        direction = null,
                        scheduledDeparture = null,
                        scheduledArrival = null,
                        status = null,
                    ),
                ),
            ),
        )

        assertEquals("trip-uuid" to "train-uuid", repository.getTrackableTripForLine(theniaLine))
    }

    @Test
    fun trackableTripUsesBackendTrainIdExactly() = runTest {
        val repository = LiveTrainRepository(
            FakeTrainApi(
                listOf(
                    TripDto(
                        id = "trip-uuid",
                        trainId = "train-uuid",
                        lineId = "line-suburb-thenia",
                        direction = null,
                        scheduledDeparture = null,
                        scheduledArrival = null,
                        status = null,
                    ),
                ),
            ),
        )

        assertEquals("trip-uuid" to "train-uuid", repository.getTrackableTripForLine(theniaLine))
    }

    @Test
    fun localStationResolvesToCanonicalBackendUuid() = runTest {
        val repository = LiveTrainRepository(
            FakeTrainApi(
                trips = emptyList(),
                stations = listOf(
                    StationDto(
                        id = "station-uuid",
                        nameAr = "الثنية",
                        nameFr = "Thenia",
                        nameEn = "Thenia",
                        latitude = 36.725,
                        longitude = 3.557,
                    ),
                ),
            ),
        )

        assertEquals("station-uuid", repository.canonicalStationId(TrainRepository.suburbLines.first().stations.first()))
    }

    @Test
    fun uuidNeverShownAsTrainNumber() = runTest {
        val repository = LiveTrainRepository(FakeTrainApi(trips = listOf(thenaTrip), live = listOf(liveTrain())))
        val waiting = TrainRepository.suburbLines.first().stations[5]

        val train = repository.getLiveTrainsForLine(TrainRepository.suburbLines.first(), waiting).single()

        assertEquals("قطار ← الجزائر (آغا)", train.trainNumber)
    }

    @Test
    fun etaOnlyForWaitingStation() = runTest {
        val line = TrainRepository.suburbLines.first()
        val next = line.stations[3]
        val stations = listOf(StationDto("next-uuid", next.name, null, null, next.latitude, next.longitude))
        val live = liveTrain(eta = EtaDto("next-uuid", 4, 6, "MEDIUM"))
        val repository = LiveTrainRepository(FakeTrainApi(trips = listOf(thenaTrip), stations = stations, live = listOf(live)))

        val atNext = repository.getLiveTrainsForLine(line, next).single()
        val elsewhere = repository.getLiveTrainsForLine(line, line.stations[8]).single()

        assertEquals(4, atNext.etaToWaitingStationMinutes)
        assertNull(elsewhere.etaToWaitingStationMinutes)
    }

    private val thenaTrip = TripDto(
        id = "trip-uuid",
        trainId = "34f9c796-095b-5b03-854a-13df562dddf1",
        lineId = "bd566e47-d079-561d-84d1-f66283653b14",
        direction = "INBOUND",
        scheduledDeparture = null,
        scheduledArrival = null,
        status = "SCHEDULED",
    )

    private fun liveTrain(eta: EtaDto? = null) = LiveTrainDto(
        id = "34f9c796-095b-5b03-854a-13df562dddf1",
        tripId = "trip-uuid",
        trainId = "34f9c796-095b-5b03-854a-13df562dddf1",
        lastObservedPosition = PositionDto(36.73, 3.40),
        direction = "INBOUND",
        eta = eta,
    )

    private class FakeTrainApi(
        private val trips: List<TripDto>,
        private val stations: List<StationDto> = emptyList(),
        private val live: List<LiveTrainDto> = emptyList(),
    ) : TrainApi {
        override suspend fun health(): retrofit2.Response<okhttp3.ResponseBody> =
            retrofit2.Response.success(null)
        override suspend fun getStations(): List<StationDto> = stations
        override suspend fun getTrips(lineId: String?): List<TripDto> = trips
        override suspend fun getTripStops(tripId: String): List<TripStopDto> = emptyList()
        override suspend fun getLiveTrains(): List<LiveTrainDto> = live
        override suspend fun getRailwaySegments(): RailwaySegmentsResponse = RailwaySegmentsResponse()
        override suspend fun getNearbyTrains(
            latitude: Double,
            longitude: Double,
            radiusMeters: Double,
        ): List<LiveTrainDto> = emptyList()

        override suspend fun createMonitorSession(request: MonitorSessionRequest): MonitorSessionDto =
            error("not used")

        override suspend fun resumeMonitorSession(
            sessionId: String,
            request: ResumeMonitorSessionRequest,
        ): MonitorSessionDto = error("not used")

        override suspend fun endMonitorSession(sessionId: String): MonitorSessionDto =
            error("not used")

        override suspend fun submitObservation(request: ObservationRequest): Map<String, Any?> =
            error("not used")

        override suspend fun submitReport(request: ReportRequest): Map<String, Any?> =
            error("not used")

        override suspend fun getReportsForSession(sessionId: String): List<ReportDto> = emptyList()
    }
}
