package com.example.data

import com.example.model.ActiveTrain
import com.example.model.Station
import com.example.model.SuburbLine
import com.example.model.TrainDirection
import com.example.utils.GeoUtils
import kotlin.math.max

/**
 * Test-only radar data. This class has no network or database access and must
 * only be enabled through BuildConfig.USE_MOCK_LIVE_DATA in a test APK.
 */
object MockLiveTrainFactory {
    fun create(line: SuburbLine, waitingStation: Station): List<ActiveTrain> {
        val stationIndex = line.stations.indexOfFirst { it.id == waitingStation.id }
            .takeIf { it >= 0 } ?: 0
        val nextIndex = (stationIndex + 1).coerceAtMost(line.stations.lastIndex)
        val next = line.stations[nextIndex]
        val previous = line.stations[(stationIndex - 1).coerceAtLeast(0)]
        val progress = ((System.currentTimeMillis() / 10_000L) % 100L) / 100.0
        val latitude = previous.latitude + (next.latitude - previous.latitude) * progress
        val longitude = previous.longitude + (next.longitude - previous.longitude) * progress
        val distanceKm = GeoUtils.calculateDistanceKm(
            latitude, longitude, waitingStation.latitude, waitingStation.longitude
        ).toFloat()
        val eta = max(1, (distanceKm / 0.75f * 60f).toInt())

        return listOf(
            ActiveTrain(
                id = "mock-train-01",
                tripId = "mock-trip-${line.id}",
                trainNumber = "WR-MOCK-01",
                suburbId = line.id,
                latitude = latitude,
                longitude = longitude,
                speedKmh = 45f,
                nextStation = next,
                prevStation = previous,
                distanceToWaitingStationKm = distanceKm,
                etaToWaitingStationMinutes = eta,
                isCrowdsourced = false,
                broadcasterCount = 0,
                status = "RUNNING",
                direction = TrainDirection.INBOUND,
                destinationName = line.inboundTerminus,
                platformTrack = waitingStation.defaultPlatform,
                lastUpdated = "اختبار محلي",
                truth = "MOCK"
            )
        )
    }
}
