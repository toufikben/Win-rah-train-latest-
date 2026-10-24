package com.example.alerts

enum class BootResumeAction { NONE, PROMPT }

/** Decides what the app may do with an armed approach alert after a device reboot. */
object BootResumePolicy {
    fun action(
        alertStationId: String?,
        alertHeading: String?,
        @Suppress("UNUSED_PARAMETER") notificationsGranted: Boolean,
    ): BootResumeAction = BootResumeAction.NONE
}
