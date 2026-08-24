package com.example.simulation

import com.example.data.TrainRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrainSimulationTest {
    @Test
    fun generatesTwoClearlySimulatedTrainsForEveryLocalLine() {
        TrainRepository.suburbLines.forEach { line ->
            val trains = LocalTrainSimulation.trainsFor(line, line.stations[line.stations.size / 2], 60L)
            assertEquals("line=${line.id}", 2, trains.size)
            assertTrue(trains.all { it.id.startsWith("SIM-") })
            assertTrue(trains.all { it.tripId?.startsWith("SIM-") == true })
            assertTrue(trains.all { it.truth == "SIMULATED_LOCAL" })
            assertTrue(trains.all { it.speedKmh != null && it.speedKmh!! >= 0f })
            assertTrue(trains.all { it.distanceToWaitingStationKm != null && it.distanceToWaitingStationKm!! >= 0f })
            assertTrue(trains.all { it.etaToWaitingStationMinutes != null && it.etaToWaitingStationMinutes!! >= 0 })
        }
    }

    @Test
    fun simulatedPositionStaysInsideSelectedLineBounds() {
        val line = TrainRepository.suburbLines.first()
        val waiting = line.stations[5]
        val trains = LocalTrainSimulation.trainsFor(line, waiting, 123L)
        val minLat = line.stations.minOf { it.latitude }
        val maxLat = line.stations.maxOf { it.latitude }
        val minLon = line.stations.minOf { it.longitude }
        val maxLon = line.stations.maxOf { it.longitude }

        trains.forEach { train ->
            assertTrue(train.latitude in minLat..maxLat)
            assertTrue(train.longitude in minLon..maxLon)
        }
    }

    @Test
    fun changingElapsedTimeMovesOrChangesSimulationState() {
        val line = TrainRepository.suburbLines.first()
        val waiting = line.stations[3]
        val first = LocalTrainSimulation.trainsFor(line, waiting, 10L)
        val later = LocalTrainSimulation.trainsFor(line, waiting, 80L)
        assertFalse(first.zip(later).all { (a, b) -> a.latitude == b.latitude && a.longitude == b.longitude })
    }
}
