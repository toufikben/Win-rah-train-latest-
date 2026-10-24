package com.example.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotificationTextTest {

    private fun waiting(liveEtaMin: Int?, etaMin: Int?) = AlertNotificationText.waiting(
        trainNumber = "1508",
        destinationNameAr = "الجزائر",
        stationName = "باب الزوار",
        scheduledTime = "10:35",
        liveEtaMin = liveEtaMin,
        etaMin = etaMin,
    )

    @Test
    fun scheduledWaitingTextCarriesTheScheduleLabel() {
        val copy = waiting(liveEtaMin = null, etaMin = 4)
        assertTrue(copy.text.startsWith("جدولي"))
        assertTrue(copy.text.contains("10:35"))
        assertTrue(copy.isScheduled)
    }

    @Test
    fun scheduledTrainDueNowDoesNotClaimALiveArrival() {
        val copy = waiting(liveEtaMin = null, etaMin = 0)
        assertTrue(copy.text.startsWith("جدولي"))
        assertFalse(copy.text.contains("يصل الآن"))
    }

    @Test
    fun liveEtaKeepsTheLiveCopy() {
        val copy = waiting(liveEtaMin = 4, etaMin = 4)
        assertEquals("يصل إلى باب الزوار خلال 4 د", copy.text)
        assertFalse(copy.text.contains("جدولي"))
        assertFalse(copy.isScheduled)
    }

    @Test
    fun approachTitleMarksAScheduledAlert() {
        val scheduled = AlertNotificationText.approach(waiting(liveEtaMin = null, etaMin = 4))
        assertTrue(scheduled.title.contains("قطارك يقترب"))
        assertTrue(scheduled.title.contains("جدولي"))

        val live = AlertNotificationText.approach(waiting(liveEtaMin = 4, etaMin = 4))
        assertEquals("قطارك يقترب", live.title)
    }
}
