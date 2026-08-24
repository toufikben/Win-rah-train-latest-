package com.example.simulation

import com.example.model.ActiveTrain
import com.example.model.Station
import com.example.model.SuburbLine
import com.example.model.TrainDirection
import com.example.utils.GeoUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic, local-only train simulation. Every generated identifier is prefixed with SIM
 * and must never be sent to the production API.
 */
object LocalTrainSimulation {
    private const val CYCLE_SECONDS = 420L
    private const val BASE_SPEED_KMH = 48.0

    fun trainsFor(line: SuburbLine, waitingStation: Station, elapsedSeconds: Long): List<ActiveTrain> {
        if (line.stations.size < 2) return emptyList()
        val path = Path(line.stations)
        return listOf(
            buildTrain(line, waitingStation, path, elapsedSeconds, TrainDirection.OUTBOUND, 0L),
            buildTrain(line, waitingStation, path, elapsedSeconds, TrainDirection.INBOUND, CYCLE_SECONDS / 2)
        )
    }

    private fun buildTrain(
        line: SuburbLine,
        waitingStation: Station,
        path: Path,
        elapsedSeconds: Long,
        direction: TrainDirection,
        offsetSeconds: Long
    ): ActiveTrain {
        val phase = Math.floorMod(elapsedSeconds + offsetSeconds, CYCLE_SECONDS)
        val directedPositionKm = path.totalKm * phase.toDouble() / CYCLE_SECONDS.toDouble()
        val forwardPositionKm = if (direction == TrainDirection.OUTBOUND) {
            directedPositionKm
        } else {
            path.totalKm - directedPositionKm
        }
        val position = path.positionAt(forwardPositionKm)
        val waitingPositionKm = path.distanceToStationKm(waitingStation)
        val directedWaitingKm = if (direction == TrainDirection.OUTBOUND) {
            waitingPositionKm
        } else {
            path.totalKm - waitingPositionKm
        }
        val distanceAheadKm = Math.floorMod(
            ((directedWaitingKm - directedPositionKm) * 1000.0).toLong(),
            (path.totalKm * 1000.0).toLong().coerceAtLeast(1L)
        ) / 1000.0
        val atStation = distanceAheadKm < 0.12 || path.distanceToNearestStationKm(forwardPositionKm) < 0.12
        val speedKmh = if (atStation) 0f else (BASE_SPEED_KMH + 8.0 * (0.5 + 0.5 * kotlin.math.sin(phase / 28.0))).toFloat()
        val etaMinutes = if (atStation) 0 else ceil(distanceAheadKm / max(speedKmh.toDouble(), 1.0) * 60.0).toInt()
        val stationIndex = path.nearestStationIndex(forwardPositionKm)
        val nextIndex = if (direction == TrainDirection.OUTBOUND) {
            min(stationIndex + 1, line.stations.lastIndex)
        } else {
            max(stationIndex - 1, 0)
        }
        val prevIndex = if (direction == TrainDirection.OUTBOUND) {
            max(stationIndex, 0)
        } else {
            min(stationIndex, line.stations.lastIndex)
        }

        return ActiveTrain(
            id = "SIM-${line.id}-${direction.name}",
            tripId = "SIM-TRIP-${line.id}-${direction.name}",
            trainNumber = "محاكاة ${line.id.uppercase()} ${if (direction == TrainDirection.OUTBOUND) "ذهاب" else "إياب"}",
            suburbId = line.id,
            latitude = position.first,
            longitude = position.second,
            speedKmh = speedKmh,
            nextStation = line.stations[nextIndex],
            prevStation = line.stations[prevIndex],
            distanceToWaitingStationKm = distanceAheadKm.toFloat(),
            etaToWaitingStationMinutes = etaMinutes,
            isCrowdsourced = false,
            broadcasterCount = 0,
            status = if (atStation) "SIMULATED_STOP" else "SIMULATED_MOVING",
            direction = direction,
            destinationName = if (direction == TrainDirection.OUTBOUND) line.outboundTerminus else line.inboundTerminus,
            platformTrack = positionStationPlatform(line, stationIndex),
            isTunnelEstimate = false,
            crowdReport = null,
            lastUpdated = "محاكاة محلية • الزمن ${phase}s",
            truth = "SIMULATED_LOCAL"
        )
    }

    private fun positionStationPlatform(line: SuburbLine, stationIndex: Int): String =
        line.stations.getOrNull(stationIndex)?.defaultPlatform ?: "رصيف تجريبي"

    private class Path(private val stations: List<Station>) {
        private val cumulativeKm: List<Double> = buildList {
            add(0.0)
            for (index in 1 until stations.size) {
                add(last() + GeoUtils.calculateDistanceKm(
                    stations[index - 1].latitude,
                    stations[index - 1].longitude,
                    stations[index].latitude,
                    stations[index].longitude
                ))
            }
        }

        val totalKm: Double = cumulativeKm.last().coerceAtLeast(0.001)

        fun distanceToStationKm(station: Station): Double {
            val index = stations.indexOfFirst { it.id == station.id }.takeIf { it >= 0 } ?: 0
            return cumulativeKm[index]
        }

        fun positionAt(distanceKm: Double): Pair<Double, Double> {
            val distance = distanceKm.coerceIn(0.0, totalKm)
            val segment = (0 until stations.lastIndex).firstOrNull { distance <= cumulativeKm[it + 1] }
                ?: stations.lastIndex - 1
            val start = stations[segment]
            val end = stations[segment + 1]
            val segmentKm = (cumulativeKm[segment + 1] - cumulativeKm[segment]).coerceAtLeast(0.001)
            val ratio = ((distance - cumulativeKm[segment]) / segmentKm).coerceIn(0.0, 1.0)
            return Pair(
                start.latitude + (end.latitude - start.latitude) * ratio,
                start.longitude + (end.longitude - start.longitude) * ratio
            )
        }

        fun nearestStationIndex(distanceKm: Double): Int =
            cumulativeKm.indices.minByOrNull { kotlin.math.abs(itValue(it) - distanceKm) } ?: 0

        fun distanceToNearestStationKm(distanceKm: Double): Double =
            cumulativeKm.minOf { kotlin.math.abs(it - distanceKm) }

        private fun itValue(index: Int): Double = cumulativeKm[index]
    }
}
