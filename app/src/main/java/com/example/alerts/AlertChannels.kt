package com.example.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AlertChannels {
    const val LIVE = "winrah_live_alert"
    const val ALARM = "winrah_wake_alarm"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(LIVE, "قطارك يقترب", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تتبع القطار القادم إلى محطتك وتنبيه قبل وصوله"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(ALARM, "منبّه محطة النزول", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "يوقظك قبل الوصول إلى محطتك"
                setSound(null, null)
                enableVibration(true)
            },
        )
    }
}
