package com.example.feature.station

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.data.Station
import com.example.data.prefs.Prefs
import com.example.design.Chevron
import com.example.design.LineChip
import com.example.design.LineColors
import com.example.design.ListRow
import com.example.design.Wr
import com.example.design.WrCard
import com.example.design.WrType
import com.example.feature.guide.StationGuides
import com.example.feature.home.Heading
import com.example.feature.home.TrainLine
import com.example.feature.home.TrainRow
import com.example.design.Truth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StationState(
    val station: Station? = null,
    val toAlgiers: List<TrainRow> = emptyList(),
    val toSuburbs: List<TrainRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class StationViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    val state = MutableStateFlow(StationState())
    val prefs: StateFlow<Prefs> = c.prefs.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    private var stationId: String? = null

    fun open(id: String) {
        if (id == stationId) return
        stationId = id
        viewModelScope.launch {
            while (isActive && stationId == id) {
                load(id)
                delay(20_000)
            }
        }
    }

    private suspend fun load(id: String) {
        try {
            c.network.loadReference()
            val lines = c.network.lines.value
            val rows = c.network.departures(id, 30).map { d ->
                val dest = d.destinationNameAr ?: ""
                val toAlg = dest == "الجزائر" || dest == "آغا"
                val line = lines.firstOrNull { it.id == d.lineId }
                TrainRow(
                    tripId = d.tripId, number = d.trainNumber, lineCode = line?.code, lineName = line?.nameAr ?: "",
                    title = if (toAlg) "${d.trainNumber ?: ""} · من ${d.originNameAr ?: ""}" else "${d.trainNumber ?: ""} · إلى $dest",
                    note = when {
                        d.express -> "سريع: لا يقف في ${d.skipsCount} محطات"
                        d.serviceDays == "not_friday" -> "لا يسير يوم الجمعة"
                        d.serviceDays == "friday_only" -> "يسير يوم الجمعة فقط"
                        else -> "يسير كل يوم"
                    },
                    time = d.scheduledTime,
                    truth = when (d.truth) { "live" -> Truth.LIVE; "estimated" -> Truth.ESTIMATED; else -> Truth.SCHEDULED },
                    etaMin = d.eta?.min, etaMax = d.eta?.max, delayMin = null, riders = d.sourceCount,
                    heading = if (toAlg) Heading.TO_ALGIERS else Heading.TO_SUBURBS,
                    destination = dest, origin = d.originNameAr ?: "",
                )
            }
            state.value = StationState(
                station = c.network.station(id),
                toAlgiers = rows.filter { it.heading == Heading.TO_ALGIERS }.take(5),
                toSuburbs = rows.filter { it.heading == Heading.TO_SUBURBS }.take(5),
                loading = false,
            )
        } catch (e: Exception) {
            state.value = state.value.copy(loading = false, error = "تعذر تحميل القطارات. نعيد المحاولة.")
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val id = stationId ?: return@launch
        val favs = prefs.value.favoriteStationIds
        c.prefs.setFavorites(if (id in favs) favs - id else (favs + id).take(prefs.value.maxFavorites))
    }
}

@Composable
fun StationScreen(vm: StationViewModel, stationId: String, onOpenGuide: (String) -> Unit) {
    LaunchedEffect(stationId) { vm.open(stationId) }
    val s by vm.state.collectAsState()
    val p by vm.prefs.collectAsState()
    val c = Wr.colors
    val fav = stationId in p.favoriteStationIds
    Column(Modifier.fillMaxSize().background(c.ground).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.station?.nameAr ?: "", style = WrType.title.copy(fontSize = 26.sp), color = c.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
                    .clickable(role = Role.Switch) { vm.toggleFavorite() }
                    .semantics { contentDescription = if (fav) "إزالة من محطاتي" else "إضافة إلى محطاتي" },
                contentAlignment = Alignment.Center,
            ) { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, tint = if (fav) c.signal else c.inkMuted) }
        }
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.station?.lineCodes?.forEach { code -> LineChip(lineShortName(code), LineColors.forCode(code)) }
        }
        Section("نحو الجزائر", s.toAlgiers, empty = "لا قطارات أخرى اليوم نحو الجزائر")
        Section("نحو الضواحي", s.toSuburbs, empty = "لا قطارات أخرى اليوم نحو الضواحي")
        s.error?.let { Text(it, style = WrType.small, color = c.danger, modifier = Modifier.padding(20.dp)) }
        if (StationGuides.forStation(s.station?.nameFr) != null) {
            WrCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                ListRow(divider = false, onClick = { onOpenGuide(stationId) }) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFC8102E)), contentAlignment = Alignment.Center) {
                        Text("M", style = WrType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("دليل المحطة والمواصلات", style = WrType.bodyStrong.copy(fontSize = 14.sp), color = c.ink)
                        Text("مترو، حافلات، طاكسي، وما بالقرب", style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
                    }
                    Chevron()
                }
            }
        }
        TruthLegend(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp))
    }
}

@Composable
private fun Section(title: String, rows: List<TrainRow>, empty: String) {
    val c = Wr.colors
    Text(title, style = WrType.section.copy(color = c.inkMuted), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
    WrCard(Modifier.padding(horizontal = 20.dp)) {
        if (rows.isEmpty()) {
            Text(empty, style = WrType.small, color = c.inkMuted, modifier = Modifier.padding(14.dp))
        } else rows.forEachIndexed { i, r -> TrainLine(r, divider = i < rows.lastIndex) }
    }
}

@Composable
fun TruthLegend(modifier: Modifier = Modifier) {
    val c = Wr.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.cardMuted).padding(PaddingValues(14.dp, 12.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("مباشر: ركاب في القطار يشاركون موقعه الآن.", style = WrType.small, color = c.ink)
        Text("تقديري: محسوب من آخر موقع معروف وسرعة القطار.", style = WrType.small, color = c.ink)
        Text("مجدول: من جدول SNTF ابتداءً من 19 سبتمبر 2026، لا أحد يشاركه بعد.", style = WrType.small, color = c.ink)
    }
    Spacer(Modifier.height(4.dp))
}

fun lineShortName(code: String?): String = when (code) {
    "line-suburb-thenia" -> "الثنية"
    "line-suburb-elaffroun" -> "العفرون"
    "line-suburb-zeralda" -> "زرالدة"
    "line-suburb-airport" -> "المطار"
    "line-suburb-elaffroun-thenia" -> "العفرون–الثنية"
    "line-suburb-zeralda-thenia" -> "زرالدة–الثنية"
    else -> code ?: ""
}
