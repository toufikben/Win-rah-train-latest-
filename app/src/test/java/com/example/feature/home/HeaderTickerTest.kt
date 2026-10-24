package com.example.feature.home

import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeaderTickerTest {

    @Test
    fun clockAdvancesAfterOneTick() = runTest {
        val zone = ZoneOffset.ofHours(1)
        var instant = Instant.parse("2026-10-24T06:00:00Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = zone
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = instant
        }
        val seen = mutableListOf<LocalTime>()
        val job = launch {
            HeaderTicker(clock, tickMillis = 30_000L).times().take(2).toList(seen)
        }
        runCurrent()
        assertEquals(LocalTime.of(7, 0), seen.single())

        instant = Instant.parse("2026-10-24T06:01:00Z")
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(listOf(LocalTime.of(7, 0), LocalTime.of(7, 1)), seen)
        job.cancel()
    }
}
