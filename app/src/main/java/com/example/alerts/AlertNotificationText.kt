package com.example.alerts

data class AlertCopy(
    val title: String,
    val text: String,
    val isScheduled: Boolean,
)

/** Copy shared by the ongoing waiting notification and the approach heads-up. */
object AlertNotificationText {
    fun waiting(
        trainNumber: String?,
        destinationNameAr: String?,
        stationName: String,
        scheduledTime: String,
        liveEtaMin: Int?,
        etaMin: Int?,
    ): AlertCopy {
        val isScheduled = liveEtaMin == null
        val title = "${trainNumber ?: "القطار"} نحو ${destinationNameAr ?: ""}"
        val text = if (isScheduled) {
            when {
                etaMin == null -> "جدولي · $scheduledTime · لا أحد يشاركه بعد"
                etaMin <= 0 -> "جدولي · $scheduledTime · حسب الجدول"
                else -> "جدولي · $scheduledTime · التنبيه حسب الجدول"
            }
        } else {
            when {
                etaMin == null -> "الموعد $scheduledTime"
                etaMin <= 0 -> "يصل الآن إلى $stationName"
                else -> "يصل إلى $stationName خلال $etaMin د"
            }
        }
        return AlertCopy(title = title, text = text, isScheduled = isScheduled)
    }

    fun approach(waiting: AlertCopy): AlertCopy =
        if (waiting.isScheduled) {
            waiting.copy(title = "قطارك يقترب · جدولي")
        } else {
            waiting.copy(title = "قطارك يقترب")
        }
}
