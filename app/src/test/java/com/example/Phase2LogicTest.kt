package com.example

import com.example.alerts.CommuteDelayWorker
import com.example.data.prefs.Commute
import com.example.data.prefs.Prefs
import com.example.feature.guide.StationGuides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2LogicTest {
    private val commute = Commute("from", "to", outboundMinute = 7 * 60 + 30, returnMinute = 17 * 60)

    @Test
    fun delayCheckRunsOnlyAroundCommuteTimes() {
        assertEquals("from", CommuteDelayWorker.legNow(commute, 6 * 60 + 50)?.fromStationId)   // 40 min before
        assertEquals("from", CommuteDelayWorker.legNow(commute, 7 * 60 + 40)?.fromStationId)   // 10 min after
        assertNull(CommuteDelayWorker.legNow(commute, 7 * 60 + 41))
        assertEquals("to", CommuteDelayWorker.legNow(commute, 16 * 60 + 30)?.fromStationId)
        assertNull(CommuteDelayWorker.legNow(commute, 12 * 60))
    }

    @Test
    fun guidesMatchBackendFrenchNamesIgnoringAccents() {
        assertNotNull(StationGuides.forStation("Aéroport Houari Boumédiène"))
        assertNotNull(StationGuides.forStation("Aïn Naâdja"))
        assertNotNull(StationGuides.forStation("Zéralda"))
        assertNotNull(StationGuides.forStation("Boumerdès"))
        // Similar names must not borrow another station's guide.
        assertNull(StationGuides.forStation("Rouiba Zone Industrielle"))
        assertNull(StationGuides.forStation(null))
    }

    @Test
    fun freeTierAllowsThreeFavoritesAndSupportersUnlimited() {
        assertEquals(3, Prefs().maxFavorites)
        assertFalse(Prefs().isSupporter)
        assertTrue(Prefs(lifetimeSupporter = true).isSupporter)
        assertTrue(Prefs(supporterUntilMs = System.currentTimeMillis() + 60_000).maxFavorites > 100)
        assertFalse(Prefs(supporterUntilMs = System.currentTimeMillis() - 1).isSupporter)
    }
}
