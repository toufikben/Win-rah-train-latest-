package com.example.data

import com.example.data.prefs.Commute

data class CommuteLeg(val fromStationId: String, val toStationId: String)

object CommuteWindow {
    const val MINUTES_PER_DAY = 24 * 60

    fun isWithin(nowMinute: Int, targetMinute: Int, beforeMinutes: Int, afterMinutes: Int): Boolean {
        val now = Math.floorMod(nowMinute, MINUTES_PER_DAY)
        val start = Math.floorMod(targetMinute - beforeMinutes, MINUTES_PER_DAY)
        val end = Math.floorMod(targetMinute + afterMinutes, MINUTES_PER_DAY)
        return if (start <= end) now in start..end else now >= start || now <= end
    }

    fun legNow(commute: Commute, nowMinute: Int): CommuteLeg? = when {
        isWithin(nowMinute, commute.outboundMinute, 90, 120) ->
            CommuteLeg(commute.fromStationId, commute.toStationId)
        isWithin(nowMinute, commute.returnMinute, 90, 180) ->
            CommuteLeg(commute.toStationId, commute.fromStationId)
        else -> null
    }

    fun circularDelayMinutes(expectedMinute: Int, scheduledMinute: Int): Int {
        val raw = Math.floorMod(expectedMinute - scheduledMinute, MINUTES_PER_DAY)
        return if (raw > MINUTES_PER_DAY / 2) raw - MINUTES_PER_DAY else raw
    }

    fun expectedDirection(lines: List<Line>, fromStationId: String, toStationId: String): String? {
        val line = lines.firstOrNull { l ->
            l.stations.any { it.id == fromStationId && it.served } &&
                l.stations.any { it.id == toStationId && it.served }
        } ?: return null
        val from = line.stations.firstOrNull { it.id == fromStationId } ?: return null
        val to = line.stations.firstOrNull { it.id == toStationId } ?: return null
        return if (to.chainageM < from.chainageM) "INBOUND" else "OUTBOUND"
    }
}
