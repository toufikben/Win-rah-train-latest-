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

class WakeAlarmService : Service() {
    companion object {
        private const val EXTRA_STATION = "station"
        private const val ACTION_STOP = "com.example.alerts.WAKE_STOP"
        private const val NOTIF_ONGOING = 2010
        const val NOTIF_ALARM = 2002
        private const val CAP_MS = 3 * 60 * 60_000L

        fun start(context: Context, stationId: String) {
            val i = Intent(context, WakeAlarmService::class.java).putExtra(EXTRA_STATION, stationId)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var locationListener: LocationListener? = null
    private val container by lazy { (application as WinRahApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown(clearStation = true)
            return START_NOT_STICKY
        }
        val stationId = intent?.getStringExtra(EXTRA_STATION) ?: return START_NOT_STICKY.also { stopSelf() }
        if (!hasLocation()) {
            stopSelf()
            return START_NOT_STICKY
        }
        AlertChannels.ensure(this)
        ServiceCompat.startForeground(
            this,
            NOTIF_ONGOING,
            liveNotification("جارٍ التحضير…", "منبّه النزول مفعّل"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        job?.cancel()
        job = scope.launch { runOnboard(stationId) }
        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    private suspend fun runOnboard(stationId: String) {
        container.network.runCatching { loadReference() }
        val dest: Station = container.network.station(stationId) ?: return shutdown(clearStation = true)
        val lead = container.prefs.prefs.first().alertLeadMinutes
        val lm = getSystemService(LocationManager::class.java)
        var rung = false
        val listener = LocationListener { loc: Location ->
            if (rung) return@LocationListener
            val d = NetworkRepository.haversine(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
            val speed = if (loc.hasSpeed() && loc.speed > 3f) loc.speed.toDouble() else 12.0
            val threshold = WakeAlarmPolicy.thresholdMeters(speed, lead)
            val minutes = (d / speed / 60).toInt()
            notify(
                NOTIF_ONGOING,
                liveNotification(
                    "${dest.nameAr} بعد ${"%.1f".format(d / 1000)} كم (~$minutes د)",
                    "منبّه النزول مفعّل",
                ),
            )
            if (d <= threshold) {
                rung = true
                locationListener?.let { lm.removeUpdates(it) }
                locationListener = null
                scope.launch { ringAlarm(dest) }
            }
        }
        locationListener = listener
        lm.requestLocationUpdates(
            LocationManager.FUSED_PROVIDER.takeIf {
                Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(it)
            } ?: LocationManager.GPS_PROVIDER,
            15_000L,
            50f,
            listener,
            Looper.getMainLooper(),
        )
        val started = System.currentTimeMillis()
        while (scope.isActive && !rung && System.currentTimeMillis() - started < CAP_MS) delay(10_000)
        if (rung) delay(4_000)
        shutdown(clearStation = !rung)
    }

    private suspend fun ringAlarm(dest: Station) {
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, WakeAlarmActivity::class.java)
                .putExtra(WakeAlarmActivity.EXTRA_STATION, dest.nameAr)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            12,
            Intent(this, WakeAlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, AlertChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle("استيقظ! اقتربت من ${dest.nameAr}")
            .setContentText("محطتك القادمة. استعد للنزول.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .addAction(0, "إيقاف", stop)
            .setDeleteIntent(stop)
            .setAutoCancel(true)
        notify(NOTIF_ALARM, b.build())
        playSound()
        container.prefs.setWakeStation(null)
    }

    private suspend fun playSound() {
        val p = container.prefs.prefs.first()
        val type = com.example.audio.trainSoundById(p.alarmSoundId)
        com.example.audio.TrainSoundSynthesizer.playSound(type, p.soundVolume / 100f, true)
    }

    private fun liveNotification(text: String, title: String): Notification =
        NotificationCompat.Builder(this, AlertChannels.LIVE)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle(title)
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
                    11,
                    Intent(this, WakeAlarmService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

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

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun shutdown(clearStation: Boolean) {
        locationListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        locationListener = null
        job?.cancel()
        if (clearStation) {
            CoroutineScope(Dispatchers.IO).launch { runCatching { container.prefs.setWakeStation(null) } }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        scope.cancel()
        super.onDestroy()
    }
}
