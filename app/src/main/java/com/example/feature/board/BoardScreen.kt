package com.example.feature.board

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.WinRahApplication
import com.example.alerts.ApproachAlertService
import com.example.design.LiveDot
import com.example.design.PrimaryButton
import com.example.design.SecondaryButton
import com.example.design.Segmented
import com.example.design.TrainGlyph
import com.example.design.Wr
import com.example.design.WrCard
import com.example.design.WrType
import com.example.location.BroadcastState
import com.example.model.TrainDirection
import com.example.viewmodel.TrainViewModel

/**
 * "I'm on the train": the rider picks only the line and direction (riders don't know train
 * numbers; the server matches the trip), then optionally a stop to be woken before.
 */
@Composable
fun BoardScreen(vm: TrainViewModel) {
    val context = LocalContext.current
    val c = Wr.colors
    val lines by vm.suburbs.collectAsState()
    val line by vm.broadcastLine.collectAsState()
    val direction by vm.broadcastDirection.collectAsState()
    val onboard by vm.isOnboardMode.collectAsState()
    val pending by vm.isOnboardActivationPending.collectAsState()
    val state by vm.broadcastState.collectAsState()
    val feedback by vm.userFeedbackMessage.collectAsState()
    val network = remember { (context.applicationContext as WinRahApplication).container.network }
    val stations by network.stations.collectAsState()
    var wakeAt by rememberSaveable { mutableStateOf<String?>(null) }
    var pickWake by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.toggleOnboardMode(true)
    }
    fun start() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) vm.toggleOnboardMode(true)
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(Modifier.fillMaxSize().background(c.ground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("أنا في القطار", style = WrType.title.copy(fontSize = 26.sp), color = c.ink)

        if (onboard || pending) {
            SharingCard(state, line.name, direction)
            SecondaryButton("نزلت من القطار", onClick = { vm.toggleOnboardMode(false); ApproachAlertService.stop(context) }, color = c.danger)
        } else {
            Text("اختر الخط والاتجاه فقط. نتعرّف على القطار تلقائيًا من موقعه.", style = WrType.body, color = c.inkMuted)
            Text("الخط", style = WrType.section.copy(color = c.inkMuted))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { l ->
                    val on = l.id == line.id
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) c.ink else c.card).clickable { vm.selectBroadcastSuburb(l) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(l.name, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = if (on) c.ground else c.ink)
                    }
                }
            }
            Text("الاتجاه", style = WrType.section.copy(color = c.inkMuted))
            val dirs = listOf(TrainDirection.INBOUND, TrainDirection.OUTBOUND)
            Segmented(listOf("نحو ${line.inboundTerminus}", "نحو ${line.outboundTerminus}"), dirs.indexOf(direction).coerceAtLeast(0),
                { vm.selectBroadcastDirection(dirs[it]) })
            PrimaryButton("ابدأ المشاركة", onClick = { start() }, leading = { Icon(TrainGlyph, null, tint = c.onSignal) })
        }

        // Wake-up alarm before the rider's stop; works with or without sharing.
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text("منبّه النزول", style = WrType.bodyStrong, color = c.ink)
            Text(
                wakeAt?.let { id -> "سنوقظك قبل ${stations.firstOrNull { it.id == id }?.nameAr ?: ""}" }
                    ?: "نم مطمئنًا: نرن قبل محطتك بدقائق، حتى والهاتف مقفل.",
                style = WrType.small, color = c.inkMuted,
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wakeAt == null) {
                    SecondaryButton("اختر محطة النزول", onClick = { pickWake = !pickWake })
                } else {
                    SecondaryButton("إلغاء المنبّه", onClick = { wakeAt = null; ApproachAlertService.stop(context) }, color = c.danger)
                }
            }
            if (pickWake) {
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cardMuted)) {
                    stations.sortedBy { it.nameAr }.forEach { st ->
                        Text(st.nameAr, style = WrType.body, color = c.ink, modifier = Modifier.fillMaxWidth().clickable {
                            wakeAt = st.id; pickWake = false
                            val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (ok) ApproachAlertService.start(context, ApproachAlertService.MODE_ONBOARD, st.id)
                            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }

        BatteryHint()
        feedback?.let { Text(it, style = WrType.small, color = c.estimatedText) }
    }
}

@Composable
private fun SharingCard(state: BroadcastState, lineName: String, direction: TrainDirection) {
    val c = Wr.colors
    val (title, sub) = when (state) {
        is BroadcastState.Sending -> "تشارك موقع القطار الآن" to "شكرًا! الركاب في المحطات يرون قطارك."
        BroadcastState.Starting -> "جارٍ البدء…" to "نتحقق من الموقع والاتصال."
        is BroadcastState.Rejected -> "الموقع غير مقبول حاليًا" to "تأكد أنك داخل القطار وأن GPS مفعّل."
        is BroadcastState.ServerUnavailable -> "الخادم لا يرد" to "نعيد المحاولة تلقائيًا، لا تقلق."
        is BroadcastState.Stopped, BroadcastState.Idle -> "متوقف" to ""
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.hero).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.heroLine), contentAlignment = Alignment.Center) {
                if (state is BroadcastState.Sending) LiveDot(size = 12.dp) else Icon(TrainGlyph, null, tint = c.signal)
            }
            Column {
                Text(title, style = WrType.title.copy(fontSize = 18.sp), color = c.heroText)
                Text("$lineName · ${direction.titleAr}", style = WrType.small, color = c.heroMuted)
            }
        }
        if (sub.isNotEmpty()) Text(sub, style = WrType.small, color = c.heroMuted)
        Text("تتوقف المشاركة تلقائيًا عندما تبتعد عن السكة.", style = WrType.small.copy(fontSize = 12.sp), color = c.heroMuted.copy(alpha = 0.8f))
    }
}

/** Some phones kill background work aggressively; offer the one setting that fixes it. */
@Composable
private fun BatteryHint() {
    val context = LocalContext.current
    val c = Wr.colors
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.signalSoft).clickable {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("اجعل التنبيهات موثوقة", style = WrType.bodyStrong, color = c.ink)
        Text("بعض الهواتف توقف التطبيقات في الخلفية. اسمح لـ وين راح بالعمل دون قيود البطارية حتى لا يفوتك المنبّه.",
            style = WrType.small, color = c.ink)
    }
}
