package com.example.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.WinRahApplication
import com.example.data.NetworkRepository
import com.example.data.Station
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
 * Background alerts that must keep working with the screen off:
 *
 *  - [MODE_WAITING]: the rider waits at a station; we follow the next train towards it and
 *    notify when it is [lead] minutes away. A Live Update (Android 16 ProgressStyle) shows
 *    the train approaching. Needs only the network.
 *  - [MODE_ONBOARD]: the rider is on a train and may fall asleep; we watch the phone's own
 *    location and ring a full-screen alarm before the destination. Needs location.
 *
 * Both stop by themselves (train arrived, alarm rang, or a time cap) so they never drain a battery.
 */
class ApproachAlertService : Service() {
    companion object {
        const val MODE_WAITING = "waiting"
        const val MODE_ONBOARD = "onboard"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_STATION = "station"
        private const val ACTION_STOP = "com.example.alerts.STOP"
        const val CHANNEL_LIVE = "winrah_live_alert"
        const val CHANNEL_ALARM = "winrah_wake_alarm"
        private const val NOTIF_LIVE = 2001
        const val NOTIF_ALARM = 2002
        private const val WAITING_CAP_MS = 90 * 60_000L
        private const val ONBOARD_CAP_MS = 3 * 60 * 60_000L

        fun start(context: Context, mode: String, stationId: String) {
            val i = Intent(context, ApproachAlertService::class.java).putExtra(EXTRA_MODE, mode).putExtra(EXTRA_STATION, stationId)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ApproachAlertService::class.java).setAction(ACTION_STOP))
        }

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_LIVE, "قطارك يقترب", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تتبع القطار القادم إلى محطتك وتنبيه قبل وصوله"
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALARM, "منبّه محطة النزول", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "يوقظك قبل الوصول إلى محطتك"
                setSound(null, null) // the alarm activity plays its own synthesized sound
                enableVibration(true)
            })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var locationListener: LocationListener? = null
    private val container by lazy { (application as WinRahApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: return START_NOT_STICKY.also { stopSelf() }
        val stationId = intent.getStringExtra(EXTRA_STATION) ?: return START_NOT_STICKY.also { stopSelf() }
        ensureChannels(this)
        val onboard = mode == MODE_ONBOARD
        if (onboard && !hasLocation()) { stopSelf(); return START_NOT_STICKY }
        val type = if (onboard) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ServiceCompat.startForeground(this, NOTIF_LIVE, liveNotification("جارٍ التحضير…", null, null, null), type)
        job?.cancel()
        job = scope.launch { if (onboard) runOnboard(stationId) else runWaiting(stationId) }
        return START_REDELIVER_INTENT
    }

    // ------------------------------------------------------------ waiting at a station

    private suspend fun runWaiting(stationId: String) {
        val started = System.currentTimeMillis()
        val lead = container.prefs.prefs.first().alertLeadMinutes
        var notified = false
        var trip: String? = null
        while (scope.isActive && System.currentTimeMillis() - started < WAITING_CAP_MS) {
            val station = runCatching { container.network.loadReference(); container.network.station(stationId) }.getOrNull()
            val next: DepartureDto? = runCatching { container.network.departures(stationId, 6) }.getOrNull()
                ?.let { list -> list.firstOrNull { it.tripId == trip } ?: list.firstOrNull() }
            if (next != null && station != null) {
                trip = next.tripId
                val eta = next.eta?.min
                val title = "${next.trainNumber ?: "القطار"} نحو ${next.destinationNameAr ?: ""}"
                val text = when {
                    eta == null -> "مجدول ${next.scheduledTime} · لا أحد يشاركه بعد"
                    eta <= 0 -> "يصل الآن إلى ${station.nameAr}"
                    else -> "يصل إلى ${station.nameAr} خلال $eta د"
                }
                // Progress over the last 15 minutes of the approach.
                val progress = eta?.let { (100 - it.coerceIn(0, 15) * 100 / 15) }
                notify(NOTIF_LIVE, liveNotification(text, title, progress, LineColors.forId(next.lineId).hashCode()))
                if (!notified && eta != null && eta <= lead) {
                    notified = true
                    notify(NOTIF_LIVE + 1, NotificationCompat.Builder(this, CHANNEL_LIVE)
                        .setSmallIcon(R.drawable.ic_stat_train)
                        .setContentTitle("قطارك يقترب")
                        .setContentText(text)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .build())
                    playSound(alarm = false)
                }
                if (eta != null && eta <= 0 && notified) break
            }
            delay(20_000)
        }
        shutdown()
    }

    private fun liveNotification(text: String, title: String?, progress: Int?, @Suppress("UNUSED_PARAMETER") colorKey: Int?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle(title ?: "وين راح")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .addAction(0, "إيقاف", PendingIntent.getService(this, 1,
                Intent(this, ApproachAlertService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .setRequestPromotedOngoing(true)
        if (progress != null) {
            b.setStyle(NotificationCompat.ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(0xFFE8A317.toInt())))
                .setStyledByProgress(true)
                .setProgress(progress))
        }
        return b.build()
    }

    // ------------------------------------------------------------ asleep on board

    @SuppressLint("MissingPermission")
    private suspend fun runOnboard(stationId: String) {
        container.network.runCatching { loadReference() }
        val dest: Station = container.network.station(stationId) ?: return shutdown()
        val lead = container.prefs.prefs.first().alertLeadMinutes
        val lm = getSystemService(LocationManager::class.java)
        var rung = false
        val listener = LocationListener { loc: Location ->
            if (rung) return@LocationListener
            val d = NetworkRepository.haversine(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
            // Suburban trains average ~12 m/s between stops; never ring later than 1.5 km out.
            val speed = if (loc.hasSpeed() && loc.speed > 3f) loc.speed.toDouble() else 12.0
            val threshold = maxOf(1_500.0, speed * lead * 60)
            val minutes = (d / speed / 60).toInt()
            notify(NOTIF_LIVE, liveNotification("${dest.nameAr} بعد ${"%.1f".format(d / 1000)} كم (~$minutes د)", "منبّه النزول مفعّل",
                (100 - (d / 20_000.0 * 100).toInt()).coerceIn(0, 100), null))
            if (d <= threshold) { rung = true; ringAlarm(dest) }
        }
        locationListener = listener
        lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER.takeIf { Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(it) }
            ?: LocationManager.GPS_PROVIDER, 15_000L, 50f, listener, Looper.getMainLooper())
        val started = System.currentTimeMillis()
        while (scope.isActive && !rung && System.currentTimeMillis() - started < ONBOARD_CAP_MS) delay(10_000)
        shutdown()
    }

    private fun ringAlarm(dest: Station) {
        val full = PendingIntent.getActivity(this, 2,
            Intent(this, WakeAlarmActivity::class.java).putExtra(WakeAlarmActivity.EXTRA_STATION, dest.nameAr)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nm = getSystemService(NotificationManager::class.java)
        val b = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle("استيقظ! اقتربت من ${dest.nameAr}")
            .setContentText("محطتك القادمة. استعد للنزول.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(full)
            .setAutoCancel(true)
        // Android 14+ lets the user revoke full-screen intents; fall back to a heads-up + sound.
        if (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()) b.setFullScreenIntent(full, true)
        else scope.launch { playSound(alarm = true) }
        notify(NOTIF_ALARM, b.build())
    }

    // ------------------------------------------------------------ helpers

    private suspend fun playSound(alarm: Boolean) {
        val p = container.prefs.prefs.first()
        val type = com.example.audio.trainSoundById(if (alarm) p.alarmSoundId else p.soundId)
        com.example.audio.TrainSoundSynthesizer.playSound(type, p.soundVolume / 100f, alarm)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(this).notify(id, n)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun shutdown() {
        locationListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        locationListener = null
        job?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        scope.cancel()
        super.onDestroy()
    }
}
