package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StationDto(
    @Json(name = "id") val id: String,
    @Json(name = "name_ar") val nameAr: String?,
    @Json(name = "name_fr") val nameFr: String?,
    @Json(name = "name_en") val nameEn: String?,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "railway_line_ids") val railwayLineIds: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TripDto(
    @Json(name = "id") val id: String,
    @Json(name = "train_id") val trainId: String?,
    @Json(name = "line_id") val lineId: String?,
    @Json(name = "direction") val direction: String?,
    @Json(name = "scheduled_departure") val scheduledDeparture: String?,
    @Json(name = "scheduled_arrival") val scheduledArrival: String?,
    @Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class TripStopDto(
    @Json(name = "station_id") val stationId: String,
    @Json(name = "station_name") val stationName: String?,
    @Json(name = "sequence") val sequence: Int,
    @Json(name = "scheduled_arrival") val scheduledArrival: String?,
    @Json(name = "scheduled_departure") val scheduledDeparture: String?
)

@JsonClass(generateAdapter = true)
data class PositionDto(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double
)

@JsonClass(generateAdapter = true)
data class ConfidenceDto(
    @Json(name = "level") val level: String?,
    @Json(name = "source_count") val sourceCount: Int = 0,
    @Json(name = "last_updated") val lastUpdated: String?
)

@JsonClass(generateAdapter = true)
data class EtaDto(
    @Json(name = "station_id") val stationId: String,
    @Json(name = "estimated_arrival_min") val estimatedArrivalMin: Int?,
    @Json(name = "estimated_arrival_max") val estimatedArrivalMax: Int?,
    @Json(name = "confidence") val confidence: String?
)

/** Public aggregate returned by /trains and /trips/{id}/live. */
@JsonClass(generateAdapter = true)
data class LiveTrainDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "train_id") val trainId: String? = null,
    @Json(name = "last_observed_position") val lastObservedPosition: PositionDto? = null,
    @Json(name = "estimated_position") val estimatedPosition: PositionDto? = null,
    @Json(name = "direction") val direction: String? = null,
    @Json(name = "speed") val speedMps: Float? = null,
    @Json(name = "heading") val heading: Float? = null,
    @Json(name = "confidence") val confidence: ConfidenceDto? = null,
    @Json(name = "eta") val eta: EtaDto? = null,
    @Json(name = "last_update") val lastUpdate: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "next_station") val nextStation: StationDto? = null,
    @Json(name = "source_count") val sourceCount: Int? = null,
    @Json(name = "truth") val truth: String? = null,
    @Json(name = "freshness") val freshness: String? = null,
    @Json(name = "line_order") val lineOrder: Int? = null,
    @Json(name = "line_id") val lineId: String? = null,
    // Train labels (backend phase 1): matched trains carry their timetable number.
    @Json(name = "matched") val matched: Boolean? = null,
    @Json(name = "train_number") val trainNumber: String? = null,
    @Json(name = "origin_name_ar") val originNameAr: String? = null,
    @Json(name = "destination_name_ar") val destinationNameAr: String? = null,
    @Json(name = "station_event") val stationEvent: String? = null,
)

@JsonClass(generateAdapter = true)
data class LineStationDto(
    @Json(name = "id") val id: String,
    @Json(name = "name_ar") val nameAr: String?,
    @Json(name = "name_fr") val nameFr: String?,
    @Json(name = "chainage_m") val chainageM: Double,
    @Json(name = "served") val served: Boolean = true,
)

/** A line with its stations in running order (GET /lines). */
@JsonClass(generateAdapter = true)
data class LineDto(
    @Json(name = "id") val id: String,
    @Json(name = "code") val code: String?,
    @Json(name = "name_ar") val nameAr: String?,
    @Json(name = "name_fr") val nameFr: String?,
    @Json(name = "status") val status: String? = "active",
    @Json(name = "length_m") val lengthM: Double = 0.0,
    @Json(name = "stations") val stations: List<LineStationDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DepartureEtaDto(
    @Json(name = "min") val min: Int,
    @Json(name = "max") val max: Int,
)

/** A train calling at a station today (GET /stations/{id}/departures). */
@JsonClass(generateAdapter = true)
data class DepartureDto(
    @Json(name = "trip_id") val tripId: String,
    @Json(name = "train_number") val trainNumber: String?,
    @Json(name = "line_id") val lineId: String?,
    @Json(name = "direction") val direction: String?,
    @Json(name = "origin_name_ar") val originNameAr: String?,
    @Json(name = "destination_name_ar") val destinationNameAr: String?,
    @Json(name = "continues_to") val continuesTo: String? = null,
    @Json(name = "service_days") val serviceDays: String? = null,
    @Json(name = "scheduled_time") val scheduledTime: String,
    @Json(name = "scheduled_minute") val scheduledMinute: Int,
    @Json(name = "express") val express: Boolean = false,
    @Json(name = "skips_count") val skipsCount: Int = 0,
    @Json(name = "truth") val truth: String = "scheduled",
    @Json(name = "eta") val eta: DepartureEtaDto? = null,
    @Json(name = "source_count") val sourceCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MonitorSessionRequest(
    @Json(name = "line_id") val lineId: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "train_id") val trainId: String? = null,
    @Json(name = "anonymous_monitor_id") val anonymousMonitorId: String
)

@JsonClass(generateAdapter = true)
data class ResumeMonitorSessionRequest(
    @Json(name = "line_id") val lineId: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "train_id") val trainId: String? = null,
    @Json(name = "anonymous_monitor_id") val anonymousMonitorId: String,
)

@JsonClass(generateAdapter = true)
data class MonitorSessionDto(
    @Json(name = "id") val id: String,
    @Json(name = "line_id") val lineId: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "trip_id") val tripId: String?,
    @Json(name = "train_id") val trainId: String?,
    @Json(name = "status") val status: String,
    @Json(name = "started_at") val startedAt: String?,
    @Json(name = "ended_at") val endedAt: String?,
    @Json(name = "last_observation_at") val lastObservationAt: String?
)

@JsonClass(generateAdapter = true)
data class ObservationRequest(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "line_id") val lineId: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "trip_id") val tripId: String? = null,
    @Json(name = "train_id") val trainId: String? = null,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "accuracy") val accuracy: Float?,
    @Json(name = "speed") val speed: Float?,
    @Json(name = "heading") val heading: Float?,
    @Json(name = "timestamp") val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class ReportRequest(
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "train_id") val trainId: String? = null,
    @Json(name = "trip_id") val tripId: String?,
    @Json(name = "station_id") val stationId: String?,
    @Json(name = "report_type") val reportType: String,
    @Json(name = "description") val description: String?
)

@JsonClass(generateAdapter = true)
data class ReportDto(
    @Json(name = "id") val id: String,
    @Json(name = "session_id") val sessionId: String?,
    @Json(name = "train_id") val trainId: String?,
    @Json(name = "trip_id") val tripId: String?,
    @Json(name = "station_id") val stationId: String?,
    @Json(name = "report_type") val reportType: String,
    @Json(name = "description") val description: String?,
    @Json(name = "created_at") val createdAt: String?,
)

@JsonClass(generateAdapter = true)
data class RailwaySegmentsResponse(
    @Json(name = "features") val features: List<RailwaySegmentFeature> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RailwaySegmentFeature(
    @Json(name = "geometry") val geometry: RailwaySegmentGeometry?,
    @Json(name = "properties") val properties: RailwaySegmentProperties?
)

@JsonClass(generateAdapter = true)
data class RailwaySegmentGeometry(
    @Json(name = "type") val type: String?,
    @Json(name = "coordinates") val coordinates: List<List<Double>>?
)

@JsonClass(generateAdapter = true)
data class RailwaySegmentProperties(
    @Json(name = "line_id") val lineId: String?,
    @Json(name = "source_kind") val sourceKind: String?
)
