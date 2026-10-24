package com.example.feature.home

import com.example.data.remote.dto.DepartureDto
import com.example.data.remote.dto.DepartureEtaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDeparturesTest {
    private fun dep(
        tripId: String,
        destination: String?,
        minute: Int,
        eta: Int? = null,
    ) = DepartureDto(
        tripId = tripId,
        trainNumber = tripId,
        lineId = "line",
        direction = null,
        originNameAr = "الجزائر",
        destinationNameAr = destination,
        scheduledTime = "%d:%02d".format(minute / 60, minute % 60),
        scheduledMinute = minute,
        eta = eta?.let { DepartureEtaDto(it, it + 2) },
    )

    @Test
    fun `heading follows the destination shown on screen`() {
        assertEquals(Heading.TO_ALGIERS, HomeDepartures.headingOf("الجزائر"))
        assertEquals(Heading.TO_ALGIERS, HomeDepartures.headingOf("آغا"))
        assertEquals(Heading.TO_SUBURBS, HomeDepartures.headingOf("الثنية"))
        assertEquals(Heading.TO_SUBURBS, HomeDepartures.headingOf(null))
    }

    @Test
    fun `alert picks the next train in the selected heading`() {
        val departures = listOf(
            dep("35", "الثنية", 9 * 60 + 6),
            dep("62", "الجزائر", 15 * 60 + 58),
        )

        assertEquals("62", HomeDepartures.nextForAlert(departures, Heading.TO_ALGIERS, 9 * 60)?.tripId)
        assertEquals("35", HomeDepartures.nextForAlert(departures, Heading.TO_SUBURBS, 9 * 60)?.tripId)
    }

    @Test
    fun `alert skips a scheduled departure that already left`() {
        val departures = listOf(
            dep("past", "الجزائر", 8 * 60 + 28),
            dep("next", "الجزائر", 8 * 60 + 38),
        )

        assertEquals("next", HomeDepartures.nextForAlert(departures, Heading.TO_ALGIERS, 8 * 60 + 31)?.tripId)
    }

    @Test
    fun `alert ignores the other direction when none is left in this heading`() {
        val departures = listOf(dep("35", "الثنية", 9 * 60 + 6))

        assertNull(HomeDepartures.nextForAlert(departures, Heading.TO_ALGIERS, 8 * 60))
    }

    @Test
    fun `scheduled departures in the past are hidden`() {
        val departures = listOf(
            dep("past", "الجزائر", 8 * 60 + 28),
            dep("next", "الجزائر", 8 * 60 + 38),
        )

        assertEquals(
            listOf("next"),
            HomeDepartures.visible(departures, Heading.TO_ALGIERS, 8 * 60 + 31).map { it.tripId },
        )
    }

    @Test
    fun `a departure one minute late is still shown`() {
        val departures = listOf(dep("late", "الجزائر", 8 * 60 + 28))

        assertEquals(
            listOf("late"),
            HomeDepartures.visible(departures, Heading.TO_ALGIERS, 8 * 60 + 29).map { it.tripId },
        )
    }

    @Test
    fun `a delayed live train stays visible after its scheduled time`() {
        val departures = listOf(dep("late", "الجزائر", 8 * 60 + 28, eta = 7))

        assertEquals(
            listOf("late"),
            HomeDepartures.visible(departures, Heading.TO_ALGIERS, 8 * 60 + 31).map { it.tripId },
        )
    }

    @Test
    fun `eta falls back to the scheduled time when nobody shares the train`() {
        val departure = dep("sched", "الجزائر", 8 * 60 + 38)

        assertEquals(7, HomeDepartures.etaMinutes(departure, 8 * 60 + 31))
    }

    @Test
    fun `live eta wins over the schedule`() {
        val departure = dep("live", "الجزائر", 8 * 60 + 38, eta = 3)

        assertEquals(3, HomeDepartures.etaMinutes(departure, 8 * 60 + 31))
    }

    @Test
    fun `schedule fallback wraps midnight`() {
        val departure = dep("midnight", "الجزائر", 3)

        assertEquals(5, HomeDepartures.etaMinutes(departure, 23 * 60 + 58))
    }

    @Test
    fun `schedule fallback rejects a departure that is already gone`() {
        val departure = dep("gone", "الجزائر", 8 * 60 + 20)

        assertNull(HomeDepartures.etaMinutes(departure, 8 * 60 + 31))
    }
}
