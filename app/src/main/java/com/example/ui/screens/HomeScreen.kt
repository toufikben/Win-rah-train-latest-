package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrainRepository
import com.example.model.ActiveTrain
import com.example.model.CrowdingLevel
import com.example.model.DelayLevel
import com.example.model.FavoriteStation
import com.example.model.LineAlert
import com.example.model.NearbyStationInfo
import com.example.model.Station
import com.example.model.StationInterchange
import com.example.model.TrainDirection
import com.example.model.TransitConnection
import com.example.model.TransitType
import com.example.viewmodel.TrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TrainViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToOnboard: () -> Unit
) {
    val suburbs by viewModel.suburbs.collectAsState()
    val selectedSuburb by viewModel.selectedSuburb.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val activeTrains by viewModel.activeTrains.collectAsState()
    val isLiveDataLoading by viewModel.isLiveDataLoading.collectAsState()
    val liveDataError by viewModel.liveDataError.collectAsState()
    val alert by viewModel.approachingAlert.collectAsState()
    val isOnboard by viewModel.isOnboardMode.collectAsState()
    val destinationAlarm by viewModel.destinationAlarm.collectAsState()
    val favorites by viewModel.favoriteStations.collectAsState()
    val feedbackMsg by viewModel.userFeedbackMessage.collectAsState()
    val directionFilter by viewModel.selectedDirectionFilter.collectAsState()
    val lineAlerts by viewModel.lineAlerts.collectAsState()
    val selectedLineAlertModal by viewModel.selectedLineAlertModal.collectAsState()
    val nearbyStations by viewModel.nearbyStations.collectAsState()
    val selectedInterchange by viewModel.selectedInterchange.collectAsState()

    var reportingTrain by remember { mutableStateOf<ActiveTrain?>(null) }
    var showSetAlarmDialog by remember { mutableStateOf(false) }
    var showNearbyRadarDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP HEADER BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Win rah train 🚂",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🇩🇿",
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = "رادار قطارات الجزائر الحي • مواقيت دقيقة ومنبه وصول",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isOnboard) Color(0xFF059669) else Color(0xFF1E293B),
                modifier = Modifier.clickable { onNavigateToOnboard() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isOnboard) Color.White else Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnboard) "بث مباشر" else "وضع الراكب",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnboard) Color.White else Color(0xFF38BDF8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. WEATHER & LINE DISRUPTIONS TICKER (FEATURE 5)
        val activeLineAlert = lineAlerts.find { it.lineId == selectedSuburb.id } ?: lineAlerts.firstOrNull()
        if (activeLineAlert != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectLineAlertModal(activeLineAlert) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = activeLineAlert.severity.badgeEmoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeLineAlert.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(activeLineAlert.severity.colorHex)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = activeLineAlert.weatherTemperature,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // FEEDBACK BANNER
        if (feedbackMsg != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF047857),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackMsg!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearUserFeedback() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }


        // 1. SMART DESTINATION ALARM BANNER (FEATURE 2)
        if (destinationAlarm.isEnabled && destinationAlarm.targetStation != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (destinationAlarm.isTriggered) Color(0xFFDC2626) else Color(0xFF1E3A8A),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (destinationAlarm.isTriggered) Color(0xFFEF4444) else Color(0xFF3B82F6)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (destinationAlarm.isTriggered) Color.White.copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (destinationAlarm.isTriggered) Icons.Default.AlarmOn else Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (destinationAlarm.isTriggered) Color(0xFFFEE2E2) else Color(0xFF93C5FD),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (destinationAlarm.isTriggered) "⏰ استيقظ! وصلت لمحطة النزول!" else "منبه النزول الذكي نشط ⏰",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "محطة النزول: ${destinationAlarm.targetStation!!.name} (التنبيه قبل ${destinationAlarm.alertDistanceKm} كم)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (destinationAlarm.isTriggered) Color(0xFFFEE2E2) else Color(0xFFBFDBFE)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (destinationAlarm.isTriggered) {
                                viewModel.dismissDestinationAlarm()
                            } else {
                                viewModel.cancelDestinationAlarm()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destinationAlarm.isTriggered) Color.White else Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (destinationAlarm.isTriggered) "إيقاف" else "إلغاء",
                            color = if (destinationAlarm.isTriggered) Color(0xFFDC2626) else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // APPROACHING ALERT BANNER
        if (alert != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "تنبيه",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "اقتراب القطار من محطتك!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = alert!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFECACA)
                        )
                    }
                    Text(
                        text = "إغلاق",
                        modifier = Modifier
                            .clickable { viewModel.clearApproachingAlert() }
                            .padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FAVORITES QUICK WIDGETS BAR
        if (favorites.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(favorites) { fav ->
                    val isCurrent = selectedStation.id == fav.station.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) Color(0xFF065F46) else Color(0xFF1E293B),
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)) else null,
                        modifier = Modifier.clickable { viewModel.selectFavorite(fav) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = fav.tagEmoji, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = fav.station.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 6. NEARBY STATIONS SMART RADAR QUICK WIDGET
        val nearestStation = nearbyStations.firstOrNull()
        if (nearestStation != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF064E3B).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNearbyRadarDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "أقرب محطة إليك: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA7F3D0)
                                )
                                Text(
                                    text = nearestStation.station.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "تبعد ${String.format("%.1f", nearestStation.distanceKm)} كم (${nearestStation.walkingMinutes} د مشياً) • ${nearestStation.suburbLine.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6EE7B7),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF059669)
                    ) {
                        Text(
                            text = "رادار المحطات 🛰️",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // SUBURB LINE SELECTOR
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(suburbs) { suburb ->
                val isSelected = suburb.id == selectedSuburb.id
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectSuburb(suburb) },
                    label = { Text(suburb.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // WAITING STATION SELECTOR + STAR FAVORITE BUTTON
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "محطة الركوب:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val isFav = viewModel.isStationFavorite(selectedStation.id)
                IconButton(
                    onClick = {
                        viewModel.toggleFavoriteStation(selectedStation, selectedSuburb)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "المفضلة",
                        tint = if (isFav) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Button to set destination wake-up alarm for this station
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E3A8A),
                modifier = Modifier.clickable { showSetAlarmDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "منبه النزول ⏰",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(selectedSuburb.stations) { station ->
                val isSelected = station.id == selectedStation.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF059669) else Color(0xFF1E293B),
                    modifier = Modifier.clickable { viewModel.selectStation(station) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) Color.White else Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                            Text(
                                text = station.defaultPlatform,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFFD1FAE5) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. DIRECTION FILTER BAR (FEATURE 4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrainDirection.values().forEach { dir ->
                val isSelected = dir == directionFilter
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setDirectionFilter(dir) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = dir.symbol, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dir.titleAr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ACTIVE TRAINS HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "القطارات القادمة لمحطة (${selectedStation.name})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "فتح الخريطة 🗺️",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToMap() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ACTIVE TRAINS LIST
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLiveDataLoading && activeTrains.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                }
            } else if (liveDataError != null && activeTrains.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(liveDataError ?: "تعذر جلب البيانات", color = Color(0xFFF87171), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshLiveTrains() }) { Text("إعادة المحاولة") }
                    }
                }
                                } else if (activeTrains.isEmpty()) {
                        item {
                            Text(
                                text = "لا يوجد بث حي على هذا الخط حاليًا. فعّل وضع على متن القطار للمساهمة.",

                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(activeTrains) { train ->
                    EnhancedTrainCard(
                        train = train,
                        waitingStation = selectedStation,
                        onInspectClicked = {
                            viewModel.selectTrain(train)
                            onNavigateToMap()
                        },
                        onReportClicked = {
                            reportingTrain = train
                        }
                    )
                }
            }
        }
    }

    // LINE ALERT MODAL DIALOG
    if (selectedLineAlertModal != null) {
        val alertInfo = selectedLineAlertModal!!
        AlertDialog(
            onDismissRequest = { viewModel.selectLineAlertModal(null) },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = alertInfo.severity.badgeEmoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alertInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "الخط: ${alertInfo.lineName}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF38BDF8))
                            Text(text = alertInfo.timeAgo, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = alertInfo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "الطقس وحالة السكة: ${alertInfo.weatherTemperature} - ${alertInfo.weatherCondition}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.selectLineAlertModal(null) }) {
                    Text("تم الفهم", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // CROWDSOURCED REPORTING DIALOG
    if (reportingTrain != null) {
        val train = reportingTrain!!
        AlertDialog(
            onDismissRequest = { reportingTrain = null },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.RateReview,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تقرير حالة القطار التشاركي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ساعد بقية الركاب في معرفة نسبة الاكتظاظ والتأخير لـ ${train.trainNumber}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "1. نسبة الاكتظاظ الحالية:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CrowdingLevel.values().forEach { level ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(level.colorHex)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.submitCrowdingReport(train.id, level)
                                        reportingTrain = null
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = level.emoji, fontSize = 20.sp)
                                    Text(
                                        text = level.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "2. حالة موعد القطار والتأخير:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DelayLevel.values().forEach { delayLevel ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.submitDelayReport(train.id, delayLevel)
                                        reportingTrain = null
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = delayLevel.emoji, fontSize = 18.sp)
                                    Text(
                                        text = delayLevel.titleAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCBD5E1),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reportingTrain = null }) {
                    Text("إغلاق", color = Color(0xFF38BDF8))
                }
            }
        )
    }

    // DESTINATION ALARM SET DIALOG
    if (showSetAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showSetAlarmDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ضبط منبه النزول الذكي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "هل تريد تفعيل منبه الوصول عند اقترابك من محطة (${selectedStation.name})؟",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "سيقوم الهاتف بإصدار رنين واهتزاز قوي لإيقاظك قبل الوصول بـ 1.5 كم لتستعد للنزول بأمان، حتى لو كانت الشاشة مقفلة!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDestinationAlarm(selectedStation, selectedSuburb.id, 1.5f)
                        showSetAlarmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("تفعيل المنبه ⏰", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    val interchange = com.example.data.TrainRepository.getInterchangeForStation(selectedStation.code)
                    if (interchange != null) {
                        TextButton(onClick = {
                            showSetAlarmDialog = false
                            viewModel.openInterchangeModal(selectedStation.code)
                        }) {
                            Text("دليل النقل 🚊", color = Color(0xFF38BDF8))
                        }
                    }
                    TextButton(onClick = { showSetAlarmDialog = false }) {
                        Text("إلغاء", color = Color(0xFF94A3B8))
                    }
                }
            }
        )
    }

    // 6. NEARBY STATIONS SMART RADAR DIALOG
    if (showNearbyRadarDialog) {
        NearbyStationsRadarDialog(
            nearbyStations = nearbyStations,
            onSelectDeparture = { nearby ->
                viewModel.selectNearbyStationAsDeparture(nearby)
                showNearbyRadarDialog = false
            },
            onOpenInterchange = { stationCode ->
                showNearbyRadarDialog = false
                viewModel.openInterchangeModal(stationCode)
            },
            onDismiss = { showNearbyRadarDialog = false }
        )
    }

    // 6. MULTI-MODAL INTERCHANGE & TRANSIT HUB MODAL DIALOG
    val currentInterchange = selectedInterchange
    if (currentInterchange != null) {
        StationInterchangeModalDialog(
            interchange = currentInterchange,
            onSetAlarm = {
                val station = TrainRepository.suburbLines.flatMap { it.stations }.find { it.code == currentInterchange.stationCode }
                if (station != null) {
                    viewModel.setDestinationAlarm(station, alertDistanceKm = 1.5f)
                }
                viewModel.closeInterchangeModal()
            },
            onDismiss = { viewModel.closeInterchangeModal() }
        )
    }
}

@Composable
fun NearbyStationsRadarDialog(
    nearbyStations: List<com.example.model.NearbyStationInfo>,
    onSelectDeparture: (com.example.model.NearbyStationInfo) -> Unit,
    onOpenInterchange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "رادار المحطات الأقرب إليك بالـ GPS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "ترتيب حي حسب المسافة من موقعك الحالي",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nearbyStations) { nearby ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = nearby.station.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", nearby.distanceKm)} كم",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF7DD3FC),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🛤️ ${nearby.suburbLine.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DirectionsWalk,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${nearby.walkingMinutes} د مشياً",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.LocalTaxi,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${nearby.drivingMinutes} د بالسيارة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { onSelectDeparture(nearby) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                ) {
                                    Text("ركوب من هنا 📍", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Button(
                                    onClick = { onOpenInterchange(nearby.station.code) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA))
                                ) {
                                    Text("المواصلات 🚊", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
fun StationInterchangeModalDialog(
    interchange: com.example.model.StationInterchange,
    onSetAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚊", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "دليل النقل والربط: ${interchange.stationName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = interchange.mainHubTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA5B4FC)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tip and Landmark Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DirectionsWalk,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "إرشادات الخروج والمشي للراجلين:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = interchange.walkingTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 المعالم القريبة: ${interchange.landmark}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Connections List
                items(interchange.connections) { connection ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(connection.type.badgeColorHex).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = connection.type.emoji, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = connection.type.titleAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(connection.type.badgeColorHex)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F172A)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsWalk,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${connection.walkingDistanceMeters}م • ${connection.walkingTimeMinutes}د",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = connection.nameAr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = connection.lineOrDestination,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "ℹ️ ${connection.frequencyOrNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSetAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ضبط منبه نزول هنا ⏰", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xFF94A3B8))
            }
        }
    )
}


@Composable
fun EnhancedTrainCard(
    train: ActiveTrain,
    waitingStation: Station,
    onInspectClicked: () -> Unit,
    onReportClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DirectionsRailway,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = train.trainNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${train.direction?.symbol ?: "•"} باتجاه: ${train.destinationName ?: "غير متوفر"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Platform Track Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF047857)
                ) {
                    Text(
                        text = train.platformTrack ?: "الرصيف غير متوفر",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub Status: Next Station & Tunnel estimation if applicable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "المحطة التالية: ${train.nextStation?.name ?: "غير متوفر"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                if (train.truth?.uppercase() == "ESTIMATED") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFB45309).copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Text(
                            text = "تقدير من الخادم — ليس GPS مباشر",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFDE68A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CROWDSOURCING STATUS ROW
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = train.crowdReport?.crowding?.emoji ?: "•", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = train.crowdReport?.crowding?.titleAr ?: "تقارير الاكتظاظ غير متوفرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = train.crowdReport?.crowding?.colorHex?.let(::Color) ?: Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = train.crowdReport?.delay?.emoji ?: "•", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = train.crowdReport?.delay?.titleAr ?: "تقارير التأخير غير متوفرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = if ((train.crowdReport?.delay?.delayMinutes ?: 0) > 0) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }

                    // Report/Vote Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF334155),
                        modifier = Modifier.clickable { onReportClicked() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RateReview,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "تحديث",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics: Distance, Speed, ETA
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Distance
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "المسافة المتبقية",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = train.distanceToWaitingStationKm?.let { "%.1f كم".format(it) } ?: "غير متوفر",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Speed
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "السرعة الحالية",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF34D399))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = train.speedKmh?.let { "${it.toInt()} كم/سا" } ?: "غير متوفر",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // ETA
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "الوصول المقدر",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFF87171))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = train.etaToWaitingStationMinutes?.let { "$it دقيقة" } ?: "غير متوفر",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF87171)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onInspectClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تتبع مسار القطار على الخريطة الحية",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
