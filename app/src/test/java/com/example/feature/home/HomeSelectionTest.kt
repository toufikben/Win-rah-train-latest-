package com.example.feature.home

import com.example.data.prefs.Commute
import com.example.data.prefs.Prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSelectionTest {
    private val commute = Commute("a", "b", outboundMinute = 7 * 60, returnMinute = 17 * 60)

    @Test
    fun `first run with no choices adopts the nearest station`() {
        assertTrue(HomeSelection.shouldAdoptNearest(null, Prefs()))
    }

    @Test
    fun `an explicit selection wins over the nearest station`() {
        assertFalse(HomeSelection.shouldAdoptNearest("a", Prefs()))
    }

    @Test
    fun `a saved favourite wins over the nearest station`() {
        assertFalse(HomeSelection.shouldAdoptNearest(null, Prefs(favoriteStationIds = listOf("a"))))
    }

    @Test
    fun `a saved commute wins over the nearest station`() {
        assertFalse(HomeSelection.shouldAdoptNearest(null, Prefs(commute = commute)))
    }
}
