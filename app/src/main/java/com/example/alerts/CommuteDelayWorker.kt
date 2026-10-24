package com.example.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.WinRahApplication
import com.example.data.CommuteLeg
import com.example.data.CommuteWindow
import com.example.data.prefs.Commute
import dz.winrah.trainradar.R
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Supporter feature: every 15 minutes, only around the rider's commute times, check the next
 * train from their station and warn once if it runs late. Cheap: one small request per run,
 * and outside the commute windows the worker returns immediately.
 */
class CommuteDelayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val NAME = "commute-delay"
        private const val NOTIF_DELAY = 2003
        private const val CHANNEL = AlertChannels.LIVE
        const val DELAY_THRESHOLD_MIN = 5

        /** Schedules or cancels the check to match the current commute and supporter state. */
        fun sync(context: Context, commute: Commute?, supporter: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (commute == null || !supporter) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<CommuteDelayWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Which leg to check now, or null outside the windows (40 min before to 10 min after). */
        fun legNow(c: Commute, nowMinute: Int): CommuteLeg? = when {
            CommuteWindow.isWithin(nowMinute, c.outboundMinute, 40, 10) ->
                CommuteLeg(c.fromStationId, c.toStationId)
            CommuteWindow.isWithin(nowMinute, c.returnMinute, 40, 10) ->
                CommuteLeg(c.toStationId, c.fromStationId)
            else -> null
        }
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as WinRahApplication).container
        val p = container.prefs.prefs.first()
        val cm = p.commute ?: return Result.success()
        if (!p.isSupporter) return Result.success()
        val now = LocalTime.now(ZoneOffset.ofHours(1)).let { it.hour * 60 + it.minute }
        val leg = legNow(cm, now) ?: return Result.success()
        val stationId = leg.fromStationId
        val otherEnd = leg.toStationId
        return try {
            container.network.loadReference()
            val lines = container.network.lines.value
            val expected = CommuteWindow.expectedDirection(lines, stationId, otherEnd)
            val next = container.network.departures(stationId, 8).firstOrNull { d ->
                val serves = lines.firstOrNull { it.id == d.lineId }
                    ?.stations?.any { it.id == otherEnd && it.served } ?: false
                serves && (expected == null || d.direction.equals(expected, ignoreCase = true))
            } ?: return Result.success()
            val eta = next.eta?.min ?: return Result.success()
            val late = CommuteWindow.circularDelayMinutes(now + eta, next.scheduledMinute)
            val key = "${next.tripId}@${java.time.LocalDate.now()}"
            val prefs = applicationContext.getSharedPreferences("commute_delay", Context.MODE_PRIVATE)
            if (late >= DELAY_THRESHOLD_MIN && prefs.getString("last", null) != key) {
                prefs.edit().putString("last", key).apply()
                notify("قطارك متأخر $late د",
                    "قطار ${next.scheduledTime} (${next.trainNumber ?: ""}) يصل إلى ${container.network.station(stationId)?.nameAr ?: ""} خلال $eta د تقريبًا.")
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        AlertChannels.ensure(applicationContext)
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_DELAY,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_train)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build())
    }
}
