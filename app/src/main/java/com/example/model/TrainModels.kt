package com.example.model

data class Station(
    val id: String,
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int,
    val defaultPlatform: String = "رصيف 1"
)

data class SuburbLine(
    val id: String,
    val name: String,
    val description: String,
    val stations: List<Station>,
    val inboundTerminus: String = "الجزائر (آغا)",
    val outboundTerminus: String = "الثنية"
)

data class LiveGpsData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Float = 0.0f,
    val accuracyMeters: Float = 0.0f,
    val altitudeMeters: Double = 0.0,
    val timestamp: Long = 0L,
    val isGpsActive: Boolean = false,
    val isDeadReckoning: Boolean = false, // True when operating in tunnel/blind spot estimation
    val deadReckoningDurationSec: Int = 0
)

enum class VerificationStatus(val label: String, val isVerified: Boolean) {
    WAITING_GPS("في انتظار إشارة GPS الحقيقية...", false),
    OUT_OF_CORRIDOR("الموقع الحالي خارج مسار السكة الحديدية", false),
    STATIONARY("متوقف في المحطة (السرعة أقل من 10 كم/سا)", true),
    ON_TRACK_VERIFIED("تم التحقق بنجاح: الراكب على متن القطار", true),
    TUNNEL_DEAD_RECKONING("في النفق: تقدير ذكي بالقصور الذاتي (Dead-Reckoning)", true),
    MANUAL_TRACKING("متابعة القطار عبر نظام الإحداثيات الحقيقية", true)
}

enum class CrowdingLevel(val titleAr: String, val emoji: String, val colorHex: Long) {
    LOW("أماكن شاغرة متوفرة", "🟢", 0xFF10B981),
    MODERATE("مقاعد ممتلئة / وقوف خفيف", "🟡", 0xFFF59E0B),
    HIGH("مزدحم جداً / اكتظاظ كبير", "🔴", 0xFFEF4444)
}

enum class DelayLevel(val titleAr: String, val delayMinutes: Int, val emoji: String) {
    ON_TIME("في الموعد المحدد", 0, "⏱️"),
    DELAY_5("تأخير 5 دقائق", 5, "⏳"),
    DELAY_15("تأخير 15 دقيقة", 15, "⚠️"),
    DELAY_30("تأخير 30 دقيقة+", 30, "🚨")
}

data class CrowdsourcedReport(
    val crowding: CrowdingLevel = CrowdingLevel.LOW,
    val delay: DelayLevel = DelayLevel.ON_TIME,
    val reportCount: Int = 12,
    val lastUpdatedMinutesAgo: Int = 2
)

data class FavoriteStation(
    val station: Station,
    val suburbId: String,
    val suburbName: String,
    val tag: String = "مفضلة",
    val tagEmoji: String = "⭐"
)

data class DestinationAlarm(
    val isEnabled: Boolean = false,
    val targetStation: Station? = null,
    val targetSuburbId: String? = null,
    val alertDistanceKm: Float = 1.5f,
    val isTriggered: Boolean = false,
    val remainingDistanceKm: Float = 0.0f
)

enum class TrainDirection(val titleAr: String, val symbol: String) {
    BOTH("كلا الاتجاهين", "🔄"),
    INBOUND("نحو العاصمة (آغا)", "⬅️"),
    OUTBOUND("نحو الضواحي والولايات", "➡️")
}

enum class AlertSeverity(val titleAr: String, val colorHex: Long, val badgeEmoji: String) {
    NORMAL("خدمة عادية منتظمة", 0xFF10B981, "🟢"),
    INFO("تنبيه خدمي / طقس", 0xFF0284C7, "ℹ️"),
    WARNING("أشغال صيانة / بطء", 0xFFF59E0B, "⚠️"),
    CRITICAL("انقطاع أو تأخير استثنائي", 0xFFEF4444, "🚨")
}

data class LineAlert(
    val id: String,
    val lineId: String,
    val lineName: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val timeAgo: String,
    val weatherTemperature: String = "24°C",
    val weatherCondition: String = "صافي ومعتدل"
)

data class ActiveTrain(
    val id: String,
    val tripId: String?,
    val trainNumber: String,
    val suburbId: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float?,
    val nextStation: Station?,
    val prevStation: Station?,
    val distanceToWaitingStationKm: Float?,
    val etaToWaitingStationMinutes: Int?,
    val isCrowdsourced: Boolean,
    val broadcasterCount: Int,
    val status: String?,
    val direction: TrainDirection?,
    val destinationName: String?,
    val platformTrack: String?,
    val isTunnelEstimate: Boolean = false,
    val crowdReport: CrowdsourcedReport? = null,
    val lastUpdated: String? = null,
    val truth: String? = null
)

enum class TransitType(val titleAr: String, val emoji: String, val badgeColorHex: Long) {
    METRO("مترو الجزائر", "🚇", 0xFF9333EA),
    TRAMWAY("ترامواي العاصمة", "🚊", 0xFF0284C7),
    ETUSA_BUS("حافلات إيتوزا والنقل الحضري", "🚌", 0xFF059669),
    TAXI_STATION("محطة سيارات الأجرة / طاكسي", "🚕", 0xFFF59E0B),
    AIRPORT_SHUTTLE("مكوك المطار والمحطة الجوية", "✈️", 0xFF2563EB),
    CABLE_CAR("المصعد الهوائي / تيليفيريك", "🚡", 0xFFEC4899)
}

data class TransitConnection(
    val type: TransitType,
    val nameAr: String,
    val lineOrDestination: String,
    val walkingDistanceMeters: Int,
    val walkingTimeMinutes: Int,
    val frequencyOrNotes: String
)

data class StationInterchange(
    val stationCode: String,
    val stationName: String,
    val mainHubTitle: String,
    val connections: List<TransitConnection>,
    val walkingTip: String,
    val landmark: String
)

data class NearbyStationInfo(
    val station: Station,
    val suburbLine: SuburbLine,
    val distanceKm: Double,
    val walkingMinutes: Int,
    val drivingMinutes: Int,
    val connectionsCount: Int = 0
)

