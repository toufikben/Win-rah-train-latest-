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

    fun armed(stationId: String?, heading: String?): ArmedAlert? {
        if (stationId == null || heading == null) return null
        val parsed = Heading.entries.firstOrNull { it.name == heading } ?: return null
        return ArmedAlert(stationId, parsed)
    }

    fun tap(armed: ArmedAlert?, stationId: String, heading: Heading): AlertTap = when {
        armed == null -> AlertTap.Arm(stationId, heading)
        armed.stationId == stationId && armed.heading == heading -> AlertTap.Cancel
        else -> AlertTap.Replace(armed, ArmedAlert(stationId, heading))
    }

    fun banner(
        armed: ArmedAlert?,
        currentStationId: String?,
        currentHeading: Heading?,
        stationName: (String) -> String?,
    ): ArmedBanner? {
        if (armed == null) return null
        return ArmedBanner(
            stationName = stationName(armed.stationId) ?: UNKNOWN_STATION,
            headingLabel = headingLabel(armed.heading),
            followsCurrentView = armed.stationId == currentStationId && armed.heading == currentHeading,
        )
    }

    fun bannerText(banner: ArmedBanner): String =
        if (banner.followsCurrentView) {
            "التنبيه مفعّل · ${banner.stationName} · ${banner.headingLabel}"
        } else {
            "التنبيه مفعّل على ${banner.stationName} · ${banner.headingLabel} — لا يتبع اختيارك الحالي"
        }

    fun headingLabel(heading: Heading): String = when (heading) {
        Heading.TO_ALGIERS -> "نحو الجزائر"
        Heading.TO_SUBURBS -> "نحو الضواحي"
    }
}
