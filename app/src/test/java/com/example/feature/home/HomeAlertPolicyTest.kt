package com.example.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAlertPolicyTest {

    private val harrach = "harrach"
    private val rouiba = "rouiba"
    private val names = mapOf(harrach to "الحراش", rouiba to "الرويبة")

    @Test
    fun noArmedAlertShowsNoBanner() {
        assertNull(HomeAlertPolicy.banner(null, harrach, Heading.TO_ALGIERS) { names[it] })
    }

    @Test
    fun matchingViewBannerFollowsSelection() {
        val banner = HomeAlertPolicy.banner(
            ArmedAlert(harrach, Heading.TO_ALGIERS),
            currentStationId = harrach,
            currentHeading = Heading.TO_ALGIERS,
            stationName = { names[it] },
        )
        assertEquals(true, banner?.followsCurrentView)
        assertEquals("التنبيه مفعّل · الحراش · نحو الجزائر", HomeAlertPolicy.bannerText(banner!!))
    }

    @Test
    fun otherHeadingWarnsThatTheAlertDoesNotFollow() {
        val banner = HomeAlertPolicy.banner(
            ArmedAlert(harrach, Heading.TO_ALGIERS),
            currentStationId = harrach,
            currentHeading = Heading.TO_SUBURBS,
            stationName = { names[it] },
        )
        assertFalse(banner!!.followsCurrentView)
        assertEquals(
            "التنبيه مفعّل على الحراش · نحو الجزائر — لا يتبع اختيارك الحالي",
            HomeAlertPolicy.bannerText(banner),
        )
    }

    @Test
    fun otherStationBannerNamesTheArmedStation() {
        val banner = HomeAlertPolicy.banner(
            ArmedAlert(harrach, Heading.TO_SUBURBS),
            currentStationId = rouiba,
            currentHeading = Heading.TO_SUBURBS,
            stationName = { names[it] },
        )
        assertFalse(banner!!.followsCurrentView)
        assertEquals("الحراش", banner.stationName)
        assertEquals("نحو الضواحي", banner.headingLabel)
    }

    @Test
    fun missingStationUsesTheStoredLabel() {
        val banner = HomeAlertPolicy.banner(
            ArmedAlert("gone", Heading.TO_ALGIERS),
            currentStationId = harrach,
            currentHeading = Heading.TO_ALGIERS,
            stationName = { names[it] },
        )
        assertEquals(HomeAlertPolicy.UNKNOWN_STATION, banner!!.stationName)
        assertTrue(HomeAlertPolicy.bannerText(banner).contains("محطة محفوظة"))
    }

    @Test
    fun tappingTheArmedPairCancelsIt() {
        val tap = HomeAlertPolicy.tap(ArmedAlert(harrach, Heading.TO_ALGIERS), harrach, Heading.TO_ALGIERS)
        assertEquals(AlertTap.Cancel, tap)
    }

    @Test
    fun tappingWithoutAnAlertArmsTheCurrentPair() {
        val tap = HomeAlertPolicy.tap(null, harrach, Heading.TO_SUBURBS)
        assertEquals(AlertTap.Arm(harrach, Heading.TO_SUBURBS), tap)
    }

    @Test
    fun tappingAnotherPairReplacesAndReportsThePreviousOne() {
        val tap = HomeAlertPolicy.tap(ArmedAlert(harrach, Heading.TO_ALGIERS), rouiba, Heading.TO_SUBURBS)
        assertEquals(
            AlertTap.Replace(
                from = ArmedAlert(harrach, Heading.TO_ALGIERS),
                to = ArmedAlert(rouiba, Heading.TO_SUBURBS),
            ),
            tap,
        )
    }

    @Test
    fun storedValuesRoundTripAndInvalidHeadingIsIgnored() {
        assertEquals(ArmedAlert(harrach, Heading.TO_SUBURBS), HomeAlertPolicy.armed(harrach, "TO_SUBURBS"))
        assertNull(HomeAlertPolicy.armed(null, "TO_ALGIERS"))
        assertNull(HomeAlertPolicy.armed(harrach, null))
        assertNull(HomeAlertPolicy.armed(harrach, "SIDEWAYS"))
    }
}
