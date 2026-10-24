package com.example.feature.home

/** The alert the rider armed, independent of what the home screen currently shows. */
data class ArmedAlert(val stationId: String, val heading: Heading)

/** What the alert button does for the station and heading on screen. */
sealed interface AlertTap {
    data class Arm(val stationId: String, val heading: Heading) : AlertTap
    data object Cancel : AlertTap
    data class Replace(val from: ArmedAlert, val to: ArmedAlert) : AlertTap
}

/** Always-visible description of the armed alert, so changing the view cannot hide it. */
data class ArmedBanner(
    val stationName: String,
    val headingLabel: String,
    val followsCurrentView: Boolean,
)

object HomeAlertPolicy {
    const val UNKNOWN_STATION = "محطة محفوظة"

    fun armed(stationId: String?, heading: String?): ArmedAlert? = null

    fun tap(armed: ArmedAlert?, stationId: String, heading: Heading): AlertTap =
        AlertTap.Arm(stationId, heading)

    fun banner(
        armed: ArmedAlert?,
        currentStationId: String?,
        currentHeading: Heading?,
        stationName: (String) -> String?,
    ): ArmedBanner? = null

    fun bannerText(banner: ArmedBanner): String = ""

    fun headingLabel(heading: Heading): String = ""
}
