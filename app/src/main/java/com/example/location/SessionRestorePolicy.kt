package com.example.location

/** What to do with a locally saved broadcast session when the app opens. */
enum class RestoreDecision { ATTACH_TO_RUNNING, ASK_USER, DISCARD }

object SessionRestorePolicy {
    /** A broadcast older than this is never resumed; riders do not stay on a suburban train for 3 h. */
    const val MAX_AGE_MS = 3 * 60 * 60 * 1000L

    /** The service ends a session that has had no accepted observation for this long. */
    const val MAX_SILENCE_MS = 30 * 60 * 1000L

    fun decide(savedAtMs: Long, nowMs: Long, serviceRunning: Boolean): RestoreDecision = when {
        serviceRunning -> RestoreDecision.ATTACH_TO_RUNNING
        savedAtMs <= 0L || nowMs - savedAtMs > MAX_AGE_MS || nowMs < savedAtMs -> RestoreDecision.DISCARD
        else -> RestoreDecision.ASK_USER
    }

    /** True when a running session must be ended by the service. */
    fun isExpired(startedAtMs: Long, lastAcceptedAtMs: Long?, nowMs: Long): Boolean =
        nowMs - startedAtMs > MAX_AGE_MS ||
            nowMs - (lastAcceptedAtMs ?: startedAtMs) > MAX_SILENCE_MS
}
