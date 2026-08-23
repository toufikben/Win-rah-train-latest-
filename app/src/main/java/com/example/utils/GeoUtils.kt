package com.example.utils

import com.example.model.Station
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates geodesic distance in Kilometers between two GPS coordinates using Haversine formula
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculates distance in meters between two GPS coordinates
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Calculates distance in meters from a GPS point to a line segment between two stations
     */
    fun distanceToSegmentMeters(
        pointLat: Double, pointLon: Double,
        segStartLat: Double, segStartLon: Double,
        segEndLat: Double, segEndLon: Double
    ): Double {
        val segmentLengthMeters = calculateDistanceMeters(segStartLat, segStartLon, segEndLat, segEndLon)
        if (segmentLengthMeters < 1.0) {
            return calculateDistanceMeters(pointLat, pointLon, segStartLat, segStartLon)
        }

        // Vector projection in equirectangular approximation for local distances
        val x = (pointLon - segStartLon) * cos(Math.toRadians((segStartLat + segEndLat) / 2.0))
        val y = pointLat - segStartLat
        val dx = (segEndLon - segStartLon) * cos(Math.toRadians((segStartLat + segEndLat) / 2.0))
        val dy = segEndLat - segStartLat

        val dot = x * dx + y * dy
        val lenSq = dx * dx + dy * dy
        val param = if (lenSq != 0.0) (dot / lenSq).coerceIn(0.0, 1.0) else -1.0

        val projLat = segStartLat + param * (segEndLat - segStartLat)
        val projLon = segStartLon + param * (segEndLon - segStartLon)

        return calculateDistanceMeters(pointLat, pointLon, projLat, projLon)
    }

    /**
     * Checks if a GPS coordinate is within the railway corridor (e.g. within 350 meters of any line segment)
     */
    fun findClosestRailwaySegmentDistanceMeters(lat: Double, lon: Double, stations: List<Station>): Double {
        if (stations.size < 2) return Double.MAX_VALUE
        var minDistance = Double.MAX_VALUE

        for (i in 0 until stations.size - 1) {
            val s1 = stations[i]
            val s2 = stations[i + 1]
            val dist = distanceToSegmentMeters(lat, lon, s1.latitude, s1.longitude, s2.latitude, s2.longitude)
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

}
