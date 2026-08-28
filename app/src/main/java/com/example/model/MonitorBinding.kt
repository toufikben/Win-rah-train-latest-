package com.example.model

/**
 * Immutable identity of the train monitored by one backend monitor session.
 * The IDs are returned by the backend and must not be inferred from UI lists.
 */
data class MonitorBinding(
    val sessionId: String,
    val lineId: String = "legacy",
    val direction: TrainDirection = TrainDirection.OUTBOUND,
    val tripId: String? = null,
    val trainId: String? = null,
) {
    fun matches(tripId: String?, trainId: String?): Boolean =
        this.tripId == tripId && this.trainId == trainId
}
