package com.example.feature.home

import com.example.data.remote.dto.DepartureDto

/** Pure departure rules shared by the home screen and the approach alert. */
object HomeDepartures {
    const val PAST_GRACE_MIN = 1

    fun headingOf(destinationNameAr: String?): Heading = Heading.TO_SUBURBS

    fun minutesUntil(departure: DepartureDto, nowMinute: Int): Int = 0

    fun isPast(departure: DepartureDto, nowMinute: Int): Boolean = false

    fun visible(departures: List<DepartureDto>, heading: Heading, nowMinute: Int): List<DepartureDto> = departures

    fun nextForAlert(
        departures: List<DepartureDto>,
        heading: Heading,
        nowMinute: Int,
        tripId: String? = null,
    ): DepartureDto? = null

    fun etaMinutes(departure: DepartureDto, nowMinute: Int): Int? = null

    fun nowMinute(): Int = 0
}
