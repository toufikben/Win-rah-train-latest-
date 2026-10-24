package com.example.feature.home

import java.time.Clock
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Emits the Algeria wall clock on a fixed cadence. */
class HeaderTicker(
    private val clock: Clock = Clock.system(ZoneOffset.ofHours(1)),
    @Suppress("UNUSED_PARAMETER") private val tickMillis: Long = 30_000L,
) {
    fun times(): Flow<LocalTime> = flowOf(LocalTime.now(clock))
}
