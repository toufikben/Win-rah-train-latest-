package com.example.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.WinRahApplication
import com.example.feature.home.Heading
import dz.winrah.trainradar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android 15 forbids starting a dataSync foreground service from BOOT_COMPLETED, so a reboot
 * cannot resume silently. This posts a one-tap prompt; opening the app resumes on its own.
 */
class BootResumeReceiver : BroadcastReceiver() {
    companion object {
        const val NOTIF_RESUME = 2012
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as WinRahApplication
                val p = app.container.prefs.prefs.first()
                if (BootResumePolicy.action(p.alertStationId, p.alertHeading, notificationsGranted(context)) != BootResumeAction.PROMPT) {
                    return@launch
                }
                val stationId = p.alertStationId ?: return@launch
                val heading = p.alertHeading?.let { runCatching { Heading.valueOf(it) }.getOrNull() } ?: return@launch
                AlertChannels.ensure(context)
                NotificationManagerCompat.from(context).notify(NOTIF_RESUME, prompt(context, stationId, heading))
            } finally {
                pending.finish()
            }
        }
    }

    private fun prompt(context: Context, stationId: String, heading: Heading): android.app.Notification {
        val resumeFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val resumeIntent = ApproachAlertService.intentFor(context, stationId, heading)
        val resume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, 2, resumeIntent, resumeFlags)
        } else {
            PendingIntent.getService(context, 2, resumeIntent, resumeFlags)
        }
        val cancel = PendingIntent.getService(context, 3, ApproachAlertService.stopIntent(context), resumeFlags)
        return NotificationCompat.Builder(context, AlertChannels.BOOT)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setContentTitle("تنبيه الاقتراب جاهز")
            .setContentText("اضغط لاستئناف التنبيه الذي كان مفعّلًا قبل إعادة التشغيل.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(resume)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(0, "استئناف", resume)
            .addAction(0, "إلغاء", cancel)
            .build()
    }

    private fun notificationsGranted(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
