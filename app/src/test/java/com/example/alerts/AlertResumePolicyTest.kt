package com.example.alerts

import com.example.feature.home.Heading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertResumePolicyTest {
    @Test
    fun `resumes an armed alert with its saved heading`() {
        assertEquals(Heading.TO_ALGIERS, AlertResumePolicy.headingToResume("station", "TO_ALGIERS", true))
        assertEquals(Heading.TO_SUBURBS, AlertResumePolicy.headingToResume("station", "TO_SUBURBS", true))
    }

    @Test
    fun `does not resume without notification permission`() {
        assertNull(AlertResumePolicy.headingToResume("station", "TO_ALGIERS", false))
    }

    @Test
    fun `does not resume a disarmed or incomplete alert`() {
        assertNull(AlertResumePolicy.headingToResume(null, "TO_ALGIERS", true))
        assertNull(AlertResumePolicy.headingToResume("station", null, true))
        assertNull(AlertResumePolicy.headingToResume("station", "BROKEN", true))
    }
}
