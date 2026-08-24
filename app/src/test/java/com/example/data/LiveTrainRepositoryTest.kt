package com.example.data

import com.example.data.remote.TrainApi
import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.MonitorSessionRequest
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportRequest
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

    private class FakeTrainApi(private val trips: List<TripDto>) : TrainApi {
        override suspend fun getStations(): List<StationDto> = emptyList()
        override suspend fun getTrips(lineId: String?): List<TripDto> = trips
        override suspend fun getTripStops(tripId: String): List<TripStopDto> = emptyList()
        override suspend fun getLiveTrains(): List<LiveTrainDto> = emptyList()
        override suspend fun getNearbyTrains(
            latitude: Double,
            longitude: Double,
            radiusMeters: Double,
        ): List<LiveTrainDto> = emptyList()

        override suspend fun createMonitorSession(request: MonitorSessionRequest): MonitorSessionDto =
            error("not used")

        override suspend fun endMonitorSession(sessionId: String): MonitorSessionDto =
            error("not used")

        override suspend fun submitObservation(request: ObservationRequest): Map<String, Any?> =
            error("not used")

        override suspend fun submitReport(request: ReportRequest): Map<String, Any?> =
            error("not used")
    }
}
