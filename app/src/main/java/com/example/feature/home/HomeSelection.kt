package com.example.feature.home

import com.example.data.prefs.Prefs

/** Decides when the first-run screen may adopt the station nearest to the rider. */
object HomeSelection {
    fun shouldAdoptNearest(manualStationId: String?, prefs: Prefs): Boolean =
        manualStationId == null && prefs.commute == null && prefs.favoriteStationIds.isEmpty()
}
