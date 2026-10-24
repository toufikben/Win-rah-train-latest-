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
        val title = "${trainNumber ?: "القطار"} نحو ${destinationNameAr ?: ""}"
        val text = when {
            liveEtaMin == null && etaMin != null -> "مجدول $scheduledTime · التنبيه حسب الجدول"
            etaMin == null -> "مجدول $scheduledTime · لا أحد يشاركه بعد"
            etaMin <= 0 -> "يصل الآن إلى $stationName"
            else -> "يصل إلى $stationName خلال $etaMin د"
        }
        return AlertCopy(title = title, text = text, isScheduled = liveEtaMin == null)
    }

    fun approach(waiting: AlertCopy): AlertCopy = waiting.copy(title = "قطارك يقترب")
}
