package com.example.alerts

import com.example.feature.home.Heading

enum class BootResumeAction { NONE, PROMPT }

/** Decides what the app may do with an armed approach alert after a device reboot. */
object BootResumePolicy {
    fun action(
        alertStationId: String?,
        alertHeading: String?,
        notificationsGranted: Boolean,
    ): BootResumeAction {
        if (alertStationId.isNullOrBlank() || !notificationsGranted) return BootResumeAction.NONE
        alertHeading?.let { name -> runCatching { Heading.valueOf(name) }.getOrNull() }
            ?: return BootResumeAction.NONE
        return BootResumeAction.PROMPT
    }
}
