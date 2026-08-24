package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.Station

object TrainNotificationHelper {

    const val CHANNEL_DESTINATION_ALARM = "train_destination_alarm_channel"
    const val CHANNEL_APPROACHING = "train_approaching_channel"
    const val CHANNEL_ONGOING_TRIP = "train_ongoing_trip_channel"

    const val NOTIFICATION_ID_ALARM = 1001
    const val NOTIFICATION_ID_APPROACHING = 1002
    const val NOTIFICATION_ID_ONGOING = 1003

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Destination Alarm Channel (High Importance, Alarm sound + Vibration)
            val alarmChannel = NotificationChannel(
                CHANNEL_DESTINATION_ALARM,
                "منبه الوصول والنزول الذكي ⏰",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات إيقاظ الراكب عند الاقتراب من محطة النزول المحددة"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 2. Approaching Train Channel
            val approachingChannel = NotificationChannel(
                CHANNEL_APPROACHING,
                "تنبيهات اقتراب القطارات 🚆",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات باقتراب القطار من محطة الانتظار"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }

            // 3. Ongoing Trip Status
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_TRIP,
                "حالة الرحلة المباشرة 📍",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عرض السرعة الحالية والمحطة القادمة في شريط الإشعارات"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(approachingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    fun showDestinationAlarmNotification(context: Context, station: Station, remainingKm: Float) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_DESTINATION_ALARM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⏰ استيقظ! محطة النزول اقتربت (${station.name})")
                .setContentText("المسافة المتبقية: ${String.format("%.1f", remainingKm)} كم فقط. جهز أمتعتك للنزول!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))

            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID_ALARM, builder.build())
            }
        } catch (_: Exception) {}
    }

    fun showApproachingNotification(context: Context, trainTitle: String, stationName: String, etaMinutes: Int, distKm: Float) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_APPROACHING)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("🚆 $trainTitle يقترب من (${stationName})")
                .setContentText("الوقت المتوقع: $etaMinutes دقيقة (على بُعد ${String.format("%.1f", distKm)} كم)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID_APPROACHING, builder.build())
            }
        } catch (_: Exception) {}
    }

    fun showOngoingTripNotification(context: Context, currentSpeedKmh: Float, nextStationName: String, isTunnel: Boolean) {
        try {
            val statusText = if (isTunnel) "إشارة GPS ضعيفة؛ الموقع غير مؤكد" else "السرعة: ${currentSpeedKmh.toInt()} كم/سا"
            val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING_TRIP)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("رحلة قطار نشطة • القادمة: $nextStationName")
                .setContentText(statusText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)

            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID_ONGOING, builder.build())
            }
        } catch (_: Exception) {}
    }

    fun clearOngoingNotification(context: Context) {
        try {
            with(NotificationManagerCompat.from(context)) {
                cancel(NOTIFICATION_ID_ONGOING)
            }
        } catch (_: Exception) {}
    }
}
