package com.example.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the broadcast service is actually doing right now. The UI renders this, never a local flag. */
sealed interface BroadcastState {
    data object Idle : BroadcastState
    data object Starting : BroadcastState
    data class Sending(val lastAcceptedAtMs: Long?, val lastAttemptAtMs: Long?) : BroadcastState
    data class Rejected(val reason: String, val atMs: Long) : BroadcastState
    data class ServerUnavailable(val atMs: Long, val retryInMs: Long) : BroadcastState
    data class Stopped(val reason: StopReason) : BroadcastState

    /** True while the foreground service owns a live session (including transient failures). */
    val isActive: Boolean
        get() = this is Starting || this is Sending || this is Rejected || this is ServerUnavailable
}

enum class StopReason { USER, SESSION_INVALID, PERMISSION, EXPIRED, OUTSIDE_CORRIDOR, ERROR }

/** Process-wide single source of truth shared by TrainTrackingService and the UI. */
object BroadcastStatusRepository {
    private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    fun update(state: BroadcastState) {
        _state.value = state
    }
}

/**
 * Limits observation sends to one attempt per [minIntervalMs], whether the
 * previous attempt succeeded or not. A rejected attempt must not unlock
 * an immediate retry on the next GPS fix.
 */
class ObservationThrottle(private val minIntervalMs: Long = 10_000L) {
    private var lastAttemptAtMs: Long? = null

    fun shouldAttempt(nowMs: Long): Boolean {
        val last = lastAttemptAtMs ?: return true
        return nowMs - last >= minIntervalMs
    }

    fun onAttempt(nowMs: Long) {
        lastAttemptAtMs = nowMs
    }
}

/** Exponential backoff for an unreachable server: 5 s, 10 s, 20 s, 40 s, then 60 s. */
class ServerBackoff(
    private val initialMs: Long = 5_000L,
    private val maxMs: Long = 60_000L,
) {
    private var failures = 0

    fun nextDelayMs(): Long {
        val delay = (initialMs shl failures.coerceAtMost(10)).coerceAtMost(maxMs)
        failures++
        return delay
    }

    fun reset() {
        failures = 0
    }
}
