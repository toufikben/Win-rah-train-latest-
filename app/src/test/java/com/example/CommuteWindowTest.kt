package com.example

import com.example.data.CommuteWindow
import com.example.data.Line
import com.example.data.LineStation
import com.example.data.prefs.Commute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteWindowTest {
    private val line = Line(
        id = "line",
        code = "L",
        nameAr = "خط",
        stations = listOf(
            LineStation("a", "أ", 0.0, true),
            LineStation("b", "ب", 100.0, true),
        ),
        active = true,
    )

    @Test
    fun windowWrapsAcrossMidnight() {
        assertTrue(CommuteWindow.isWithin(1430, 10, 90, 120))
        assertTrue(CommuteWindow.isWithin(5, 10, 90, 120))
        assertFalse(CommuteWindow.isWithin(200, 10, 90, 120))
    }

    @Test
    fun commuteLegUsesCircularWindows() {
        val commute = Commute("a", "b", outboundMinute = 10, returnMinute = 17 * 60)
        assertEquals("a", CommuteWindow.legNow(commute, 5)?.fromStationId)
        assertEquals("b", CommuteWindow.legNow(commute, 1430)?.fromStationId)
        assertNull(CommuteWindow.legNow(commute, 12 * 60))
    }

    @Test
    fun delayWrapsAcrossMidnight() {
        assertEquals(10, CommuteWindow.circularDelayMinutes(5, 1435))
        assertEquals(-10, CommuteWindow.circularDelayMinutes(1435, 5))
        assertEquals(0, CommuteWindow.circularDelayMinutes(1435, 1435))
    }

    @Test
    fun expectedDirectionFollowsChainage() {
        assertEquals("OUTBOUND", CommuteWindow.expectedDirection(listOf(line), "a", "b"))
        assertEquals("INBOUND", CommuteWindow.expectedDirection(listOf(line), "b", "a"))
        assertNull(CommuteWindow.expectedDirection(listOf(line), "a", "missing"))
    }
}
