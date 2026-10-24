package com.example.alerts

import com.example.feature.home.Heading

/** Decides whether an armed approach alert can be resumed after the app process died. */
object AlertResumePolicy {
    fun headingToResume(
        stationId: String?,
        headingName: String?,
        notificationsGranted: Boolean,
    ): Heading? = null
}
