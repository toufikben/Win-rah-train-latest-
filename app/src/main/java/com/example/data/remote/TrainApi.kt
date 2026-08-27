package com.example.data.remote

import com.example.data.remote.dto.LiveTrainDto
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.MonitorSessionRequest
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportDto
import com.example.data.remote.dto.ResumeMonitorSessionRequest
import com.example.data.remote.dto.ReportRequest
import com.example.data.remote.dto.StationDto
import com.example.data.remote.dto.TripDto
import com.example.data.remote.dto.TripStopDto
import com.example.data.remote.dto.RailwaySegmentsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrainApi {
    @GET("stations")
    suspend fun getStations(): List<StationDto>

    @GET("trips")
    suspend fun getTrips(@Query("line_id") lineId: String? = null): List<TripDto>

    @GET("trips/{trip_id}/stops")
    suspend fun getTripStops(@Path("trip_id") tripId: String): List<TripStopDto>

    @GET("trains")
    suspend fun getLiveTrains(): List<LiveTrainDto>

    @GET("map/railway-segments.geojson")
    suspend fun getRailwaySegments(): RailwaySegmentsResponse

    @GET("nearby-trains")
    suspend fun getNearbyTrains(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("radius") radiusMeters: Double = 5000.0
    ): List<LiveTrainDto>

    @POST("monitor-sessions")
    suspend fun createMonitorSession(@Body request: MonitorSessionRequest): MonitorSessionDto

    @POST("monitor-sessions/{session_id}/resume")
    suspend fun resumeMonitorSession(
        @Path("session_id") sessionId: String,
        @Body request: ResumeMonitorSessionRequest,
    ): MonitorSessionDto

    @POST("monitor-sessions/{session_id}/end")
    suspend fun endMonitorSession(@Path("session_id") sessionId: String): MonitorSessionDto

    @POST("observations")
    suspend fun submitObservation(@Body request: ObservationRequest): Map<String, Any?>

    @POST("reports")
    suspend fun submitReport(@Body request: ReportRequest): Map<String, Any?>

    @GET("reports/session/{session_id}")
    suspend fun getReportsForSession(@Path("session_id") sessionId: String): List<ReportDto>
}
