package com.example.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.WinRahApplication
import com.example.data.remote.dto.DepartureDto
import com.example.design.LineColors
import dz.winrah.trainradar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Waiting-at-a-station alert: follows the next train towards the rider's station and
 * notifies when it is [lead] minutes away. Needs only the network.
 */
class ApproachAlertService : Service() {
    companion object {
        private const val EXTRA_STATION = "station"
        private const val ACTION_STOP = "com.example.alerts.STOP"
        private const val NOTIF_ONGOING = 2001
        private const val NOTIF_APPROACH = 2011
        private const val WAITING_CAP_MS = 90 * 60_000L

        fun start(context: Context, stationId: String) {
            val i = Intent(context, ApproachAlertService::class.java).putExtra(EXTRA_STATION, stationId)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ApproachAlertService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val container by lazy { (application as WinRahApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        val stationId = intent?.getStringExtra(EXTRA_STATION) ?: return START_NOT_STICKY.also { stopSelf() }
        AlertChannels.ensure(this)
        ServiceCompat.startForeground(
            this,
            NOTIF_ONGOING,
            liveNotification("جارٍ التحضير…", null, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        job?.cancel()
        job = scope.launch { runWaiting(stationId) }
        return START_REDELIVER_INTENT
    }

    private suspend fun runWaiting(stationId: String) {
        val started = System.currentTimeMillis()
        val lead = container.prefs.prefs.first().alertLeadMinutes
        var notified = false
        var trip: String? = null
        while (scope.isActive && System.currentTimeMillis() - started < WAITING_CAP_MS) {
            val station = runCatching {
                container.network.loadReference()
                container.network.station(stationId)
            }.getOrNull()
            val departures: List<DepartureDto> = runCatching {
                container.network.departures(stationId, 6)
            }.getOrDefault(emptyList())
            val next = if (trip == null) departures.firstOrNull() else departures.firstOrNull { it.tripId == trip }
            if (next == null) {
                if (trip != null) break
                delay(20_000)
                continue
            }
            if (station != null) {
                trip = next.tripId
                val eta = next.eta?.min
                val title = "${next.trainNumber ?: "القطار"} نحو ${next.destinationNameAr ?: ""}"
                val text = when {
                    eta == null -> "مجدول ${next.scheduledTime} · لا أحد يشاركه بعد"
                    eta <= 0 -> "يصل الآن إلى ${station.nameAr}"
                    else -> "يصل إلى ${station.nameAr} خلال $eta د"
                }
                val progress = eta?.let { (100 - it.coerceIn(0, 15) * 100 / 15) }
                notify(NOTIF_ONGOING, liveNotification(text, title, progress, LineColors.forId(next.lineId).hashCode()))
                if (!notified && eta != null && eta <= lead) {
                    notified = true
                    notify(
                        NOTIF_APPROACH,
                        NotificationCompat.Builder(this, AlertChannels.LIVE)
                            .setSmallIcon(R.drawable.ic_stat_train)
                            .setContentTitle("قطارك يقترب")
                            .setContentText(text)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setContentIntent(openApp())
                            .setAutoCancel(true)
                            .build(),
                    )
                    playSound()
                }
                if (eta != null && eta <= 0 && notified) break
            }
            delay(20_000)
        }
        shutdown()
    }

    private fun liveNotification(
        text: String,
        title: String?,
        progress: Int?,
        @Suppress("UNUSED_PARAMETER") colorKey: Int? = null,
    ): Notification {
        val b = NotificationCompat.Builder(this, AlertChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle(title ?: "وين راح")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .addAction(
                0,
                "إيقاف",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ApproachAlertService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setRequestPromotedOngoing(true)
        if (progress != null) {
            b.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(
                        listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(0xFFE8A317.toInt())),
                    )
                    .setStyledByProgress(true)
                    .setProgress(progress),
            )
        }
        return b.build()
    }

    private suspend fun playSound() {
        val p = container.prefs.prefs.first()
        val type = com.example.audio.trainSoundById(p.soundId)
        com.example.audio.TrainSoundSynthesizer.playSound(type, p.soundVolume / 100f, false)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(id, n)
    }

    private fun shutdown() {
        job?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
