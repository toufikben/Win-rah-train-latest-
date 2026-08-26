package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.model.CrowdingLevel
import com.example.model.DelayLevel
import com.example.model.Station
import com.example.model.VerificationStatus
import com.example.viewmodel.TrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardScreen(viewModel: TrainViewModel) {
    val isOnboard by viewModel.isOnboardMode.collectAsState()
    val gpsData by viewModel.gpsData.collectAsState()
    val verificationStatus by viewModel.verificationStatus.collectAsState()
    val corridorDistMeters by viewModel.distanceToRailwayCorridorMeters.collectAsState()
    val selectedSuburb by viewModel.selectedSuburb.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val feedbackMsg by viewModel.userFeedbackMessage.collectAsState()
    val selectedInterchange by viewModel.selectedInterchange.collectAsState()
    val destinationAlarm by viewModel.destinationAlarm.collectAsState()
    val scrollState = rememberScrollState()

    var permissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        permissionGranted = fineGranted || coarseGranted
        if (permissionGranted) {
            viewModel.toggleOnboardMode(true)
        } else {
            viewModel.setUserFeedback("لم يتم تفعيل المستشعر: يجب السماح بصلاحية الموقع أولًا.")
        }
    }

    var selectedAlarmStation by remember {
        mutableStateOf(destinationAlarm.targetStation ?: selectedSuburb.stations.lastOrNull() ?: selectedSuburb.stations.first())
    }
    var alarmDistanceKm by remember { mutableFloatStateOf(destinationAlarm.alertDistanceKm) }
    var stationMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "وضع الراكب والخدمات الذكية",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = "تتبع GPS الحقيقي، التحقق من مسار السكة، ومنبه الوصول الذكي",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // TRANSIT GUIDE MOVED FROM RADAR: presentation only; ViewModel logic is unchanged.
        val stationInterchange = com.example.data.TrainRepository.getInterchangeForStation(selectedStation.code)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1B4B).copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4338CA)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openInterchangeModal(selectedStation.code) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = "🚊🚇🚌🚕", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (stationInterchange != null) "مواصلات وربط: ${selectedStation.name}" else "دليل النقل والربط: ${selectedStation.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5B4FC)
                        )
                        Text(
                            text = if (stationInterchange != null) "${stationInterchange.connections.size} وسائط ربط" else "استكشف وسائل النقل المتاحة عند النزول",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC7D2FE),
                            fontSize = 10.sp
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF4F46E5)) {
                    Text(
                        text = "دليل المواصلات ❯",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }
        }

        feedbackMsg?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12324A)),
                onClick = { viewModel.clearUserFeedback() }
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. MAIN GPS ONBOARD BROADCAST CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnboard) Color(0xFF065F46) else Color(0xFF0F172A)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isOnboard) Color(0xFF10B981) else Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (isOnboard) Color(0xFF10B981) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SatelliteAlt,
                        contentDescription = null,
                        tint = if (isOnboard) Color.White else Color(0xFF38BDF8),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isOnboard) "بث GPS الحي والخدمة في الخلفية نشطان 🛰️" else "بث موقع الراكب متوقف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "عند ركوبك القطار، يساهم تشغيل البث في إفادة المسافرين في المحطات القادمة بالوقت الدقيق والموقع المباشر للقطار على السكة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnboard) Color(0xFFD1FAE5) else Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفعيل بث المستشعر المباشر",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Switch(
                        checked = isOnboard,
                        onCheckedChange = { enable ->
                            if (enable) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.toggleOnboardMode(false)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The app does not simulate tunnel movement. Missing GPS is shown as unavailable.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "GPS", fontSize = 18.sp, color = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (gpsData.isGpsActive) "الموقع الحقيقي متاح؛ لا يوجد تقدير اصطناعي عند فقدان الإشارة." else "في انتظار الموقع الحقيقي من الجهاز.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFCBD5E1)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // VERIFICATION STATUS BADGE
        val (badgeBg, badgeColor, statusIcon) = when (verificationStatus) {
            VerificationStatus.ON_TRACK_VERIFIED -> Triple(Color(0xFF065F46), Color(0xFF34D399), Icons.Default.CheckCircle)
            VerificationStatus.STATIONARY -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), Icons.Default.Info)
            VerificationStatus.OUT_OF_CORRIDOR -> Triple(Color(0xFF7F1D1D), Color(0xFFF87171), Icons.Default.Warning)
            VerificationStatus.WAITING_GPS -> Triple(Color(0xFF1E293B), Color(0xFF94A3B8), Icons.Default.LocationSearching)
            VerificationStatus.TUNNEL_DEAD_RECKONING -> Triple(Color(0xFF78350F), Color(0xFFFDE68A), Icons.Default.CheckCircle)
            VerificationStatus.MANUAL_TRACKING -> Triple(Color(0xFF1E3A8A), Color(0xFF60A5FA), Icons.Default.Security)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = badgeBg)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "التحقق من التواجد على السكة الحديدية:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = verificationStatus.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. SMART DESTINATION GEOFENCE WAKE-UP ALARM CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (destinationAlarm.isEnabled) Color(0xFF3B82F6) else Color(0xFF1E293B)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (destinationAlarm.isEnabled) Icons.Default.AlarmOn else Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (destinationAlarm.isEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "منبه النزول الذكي (Smart Wake-Up)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (destinationAlarm.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E3A8A)
                        ) {
                            Text(
                                text = "مُفعل ⏰",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "حدد محطة نزولك وسيقوم التطبيق بإصدار رنين واهتزاز متواصل لتنبيهك وإيقاظك قبل المحطة بمسافة الأمان المحددة حتى لو كان الهاتف في جيبك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Station Dropdown
                Text(
                    text = "محطة النزول المستهدفة:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = stationMenuExpanded,
                    onExpandedChange = { stationMenuExpanded = !stationMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAlarmStation.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stationMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = stationMenuExpanded,
                        onDismissRequest = { stationMenuExpanded = false }
                    ) {
                        selectedSuburb.stations.forEach { station ->
                            DropdownMenuItem(
                                text = { Text(station.name) },
                                onClick = {
                                    selectedAlarmStation = station
                                    stationMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Distance Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مسافة التنبيه المسبقة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = String.format("%.1f كم قبل المحطة", alarmDistanceKm),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                Slider(
                    value = alarmDistanceKm,
                    onValueChange = { alarmDistanceKm = it },
                    valueRange = 0.8f..4.0f,
                    steps = 6
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (destinationAlarm.isEnabled) {
                        Button(
                            onClick = { viewModel.cancelDestinationAlarm() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إلغاء المنبه", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.setDestinationAlarm(selectedAlarmStation, selectedSuburb.id, alarmDistanceKm)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تفعيل منبه النزول ⏰", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.triggerSelectedSound() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تجربة الرنين", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. LIVE CROWDSOURCING SUBMISSION CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.RateReview,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شارك حالة القطار الآن مع الركاب 👥",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "بنقرة واحدة، ساهم في إخبار المسافرين على المحطات بمدى توفر الأماكن أو وجود تأخير:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "مدى الاكتظاظ في عربتك:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
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
                                    viewModel.submitCrowdingReport(level)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = level.emoji, fontSize = 18.sp)
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "دقة الموعد الحالي للقطار:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
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
                                    viewModel.submitDelayReport(delayLevel)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = delayLevel.emoji, fontSize = 16.sp)
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. GPS TELEMETRY STATUS
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "بيانات مستشعر GPS الحي",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (gpsData.isDeadReckoning) "تقدير نفق 🚇" else if (gpsData.isGpsActive) "إشارة متصلة 🟢" else "غير متصل ⚪",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gpsData.isDeadReckoning) Color(0xFFFBBF24) else if (gpsData.isGpsActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "خط العرض:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(text = if (gpsData.latitude != 0.0) String.format("%.5f° N", gpsData.latitude) else "--", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(text = "خط الطول:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(text = if (gpsData.longitude != 0.0) String.format("%.5f° E", gpsData.longitude) else "--", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "السرعة الحالية:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(text = "${gpsData.speedKmh.toInt()} كم/سا", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                    Column {
                        Text(text = "المسافة لأقرب سكة:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(text = if (gpsData.isGpsActive) "${corridorDistMeters.toInt()} متر" else "--", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    selectedInterchange?.let { interchange ->
        StationInterchangeModalDialog(
            interchange = interchange,
            onSetAlarm = {
                val station = com.example.data.TrainRepository.suburbLines
                    .flatMap { it.stations }
                    .find { it.code == interchange.stationCode }
                if (station != null) viewModel.setDestinationAlarm(station, alertDistanceKm = 1.5f)
                viewModel.closeInterchangeModal()
            },
            onDismiss = { viewModel.closeInterchangeModal() }
        )
    }
}
