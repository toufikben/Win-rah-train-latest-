package com.example.model

/** Pure, deterministic policy for deciding when an active broadcast must stop. */
data class CorridorExitState(
    val outsideSampleCount: Int = 0,
    val outsideSinceMs: Long? = null,
)

data class CorridorExitDecision(
    val state: CorridorExitState,
    val shouldStopBroadcast: Boolean,
)

object CorridorExitPolicy {
    const val CORRIDOR_EXIT_DISTANCE_METERS = 400.0
    const val REQUIRED_OUT_OF_CORRIDOR_SAMPLES = 3
    const val MIN_OUT_OF_CORRIDOR_DURATION_MS = 30_000L

    fun evaluate(
        distanceMeters: Double,
        isDeadReckoning: Boolean,
        isBroadcasting: Boolean,
        nowMs: Long,
        previous: CorridorExitState,
    ): CorridorExitDecision {
        if (isDeadReckoning || !isBroadcasting || distanceMeters <= CORRIDOR_EXIT_DISTANCE_METERS) {
            return CorridorExitDecision(CorridorExitState(), shouldStopBroadcast = false)
        }

        val since = previous.outsideSinceMs ?: nowMs
        val next = CorridorExitState(
            outsideSampleCount = previous.outsideSampleCount + 1,
            outsideSinceMs = since,
        )
        val shouldStop = next.outsideSampleCount >= REQUIRED_OUT_OF_CORRIDOR_SAMPLES &&
            nowMs - since >= MIN_OUT_OF_CORRIDOR_DURATION_MS
        return CorridorExitDecision(
            state = if (shouldStop) CorridorExitState() else next,
            shouldStopBroadcast = shouldStop,
        )
    }
}

