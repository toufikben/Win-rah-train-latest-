package com.example.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class BootResumePolicyTest {

    @Test
    fun promptsWhenAnArmedAlertCanBeResumed() {
        assertEquals(
            BootResumeAction.PROMPT,
            BootResumePolicy.action("station-1", "TO_ALGIERS", notificationsGranted = true),
        )
    }

    @Test
    fun staysSilentWithoutAnArmedStation() {
        assertEquals(
            BootResumeAction.NONE,
            BootResumePolicy.action(null, "TO_ALGIERS", notificationsGranted = true),
        )
        assertEquals(
            BootResumeAction.NONE,
            BootResumePolicy.action("  ", "TO_ALGIERS", notificationsGranted = true),
        )
    }

    @Test
    fun staysSilentWithoutAValidHeading() {
        assertEquals(
            BootResumeAction.NONE,
            BootResumePolicy.action("station-1", null, notificationsGranted = true),
        )
        assertEquals(
            BootResumeAction.NONE,
            BootResumePolicy.action("station-1", "SIDEWAYS", notificationsGranted = true),
        )
    }

    @Test
    fun staysSilentWhenNotificationsAreDenied() {
        assertEquals(
            BootResumeAction.NONE,
            BootResumePolicy.action("station-1", "TO_SUBURBS", notificationsGranted = false),
        )
    }
}
