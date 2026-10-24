package com.example.feature.home

import com.example.data.CommuteWindow
import com.example.data.remote.dto.DepartureDto
import java.time.LocalTime
import java.time.ZoneOffset

/** Pure departure rules shared by the home screen and the approach alert. */
object HomeDepartures {
    const val PAST_GRACE_MIN = 1
    private val ALGERIA: ZoneOffset = ZoneOffset.ofHours(1)

    fun headingOf(destinationNameAr: String?): Heading =
        if (destinationNameAr == "الجزائر" || destinationNameAr == "آغا") {
            Heading.TO_ALGIERS
        } else {
            Heading.TO_SUBURBS
        }

    /** Minutes from [nowMinute] to the scheduled time; negative when it already passed. */
    fun minutesUntil(departure: DepartureDto, nowMinute: Int): Int =
        -CommuteWindow.circularDelayMinutes(nowMinute, departure.scheduledMinute)

    /** Live trains stay visible; a scheduled train past the grace period is gone. */
    fun isPast(departure: DepartureDto, nowMinute: Int): Boolean =
        departure.eta == null && minutesUntil(departure, nowMinute) < -PAST_GRACE_MIN

    fun visible(departures: List<DepartureDto>, heading: Heading, nowMinute: Int): List<DepartureDto> =
        departures.filter { headingOf(it.destinationNameAr) == heading && !isPast(it, nowMinute) }

    fun nextForAlert(
        departures: List<DepartureDto>,
        heading: Heading,
        nowMinute: Int,
        tripId: String? = null,
    ): DepartureDto? {
        val pool = departures.filter { headingOf(it.destinationNameAr) == heading }
        if (tripId != null) {
            pool.firstOrNull { it.tripId == tripId && !isPast(it, nowMinute) }?.let { return it }
        }
        return pool.firstOrNull { !isPast(it, nowMinute) }
    }

    /** Live ETA when shared, else the scheduled countdown; null when that train is gone. */
    fun etaMinutes(departure: DepartureDto, nowMinute: Int): Int? {
        departure.eta?.let { return it.min }
        return minutesUntil(departure, nowMinute).takeIf { it >= -PAST_GRACE_MIN }
    }

    fun nowMinute(): Int = LocalTime.now(ALGERIA).let { it.hour * 60 + it.minute }
}
