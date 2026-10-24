package com.example.feature.line

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.data.Line
import com.example.data.NetworkRepository
import com.example.design.LineColors
import com.example.design.LiveDot
import com.example.design.TrainGlyph
import com.example.design.Truth
import com.example.design.TruthTag
import com.example.design.Wr
import com.example.design.WrType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A live train pinned to the station it is at or approaching on the diagram. */
data class DiagramTrain(val label: String, val stationIndex: Int, val towardsStart: Boolean?, val truth: Truth, val riders: Int)

data class LineState(
    val lines: List<Line> = emptyList(),
    val selected: Int = 0,
    val trains: List<DiagramTrain> = emptyList(),
    val error: String? = null,
)

/** The four lines a rider picks between; through services are drawn on their radial line. */
private val RADIAL = listOf("line-suburb-thenia", "line-suburb-elaffroun", "line-suburb-zeralda", "line-suburb-airport")

class LineViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    val state = MutableStateFlow(LineState())

    init {
        viewModelScope.launch {
            while (isActive) { refresh(); delay(15_000) }
        }
    }

    fun select(i: Int) { state.value = state.value.copy(selected = i, trains = emptyList()); viewModelScope.launch { refresh() } }

    private suspend fun refresh() {
        try {
            c.network.loadReference()
            val lines = RADIAL.mapNotNull { code -> c.network.lines.value.firstOrNull { it.code == code && it.active } }
            val line = lines.getOrNull(state.value.selected)
            val trains = if (line == null) emptyList() else place(line)
            state.value = state.value.copy(lines = lines, trains = trains, error = null)
        } catch (e: Exception) {
            state.value = state.value.copy(error = "تعذر تحميل القطارات الحية.")
        }
    }

    private suspend fun place(line: Line): List<DiagramTrain> {
        val served = line.stations.filter { it.served }
        val coords = served.map { c.network.station(it.id) }
        return c.network.liveTrains().mapNotNull { t ->
            val code = LineColors.codeForId(t.lineId)
            // A through train (e.g. El Affroun–Thénia) shows on every radial line it runs on.
            if (code != line.code && !(code ?: "").contains(line.code!!.removePrefix("line-suburb-"))) return@mapNotNull null
            val pos = t.estimatedPosition ?: t.lastObservedPosition ?: return@mapNotNull null
            val idx = coords.withIndex().filter { it.value != null }
                .minByOrNull { NetworkRepository.haversine(pos.latitude, pos.longitude, it.value!!.latitude, it.value!!.longitude) }
                ?.index ?: return@mapNotNull null
            val towardsStart = t.destinationNameAr?.let { d -> d == served.first().nameAr || d == "الجزائر" || d == "آغا" }
            DiagramTrain(
                label = t.trainNumber ?: "قطار",
                stationIndex = idx,
                towardsStart = towardsStart,
                truth = if (t.truth == "live") Truth.LIVE else Truth.ESTIMATED,
                riders = t.sourceCount ?: 0,
            )
        }
    }
}

@Composable
fun LineScreen(vm: LineViewModel, onOpenStation: (String) -> Unit) {
    val s by vm.state.collectAsState()
    val c = Wr.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        Text("الخطوط", style = WrType.title.copy(fontSize = 26.sp), color = c.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.lines.forEachIndexed { i, l ->
                val col = LineColors.forCode(l.code)
                val on = i == s.selected
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) col else c.card).clickable { vm.select(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(l.nameAr, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = if (on) Color.White else c.ink) }
            }
        }
        val line = s.lines.getOrNull(s.selected)
        if (line == null) {
            Text(s.error ?: "جارٍ التحميل…", style = WrType.body, color = c.inkMuted, modifier = Modifier.padding(20.dp))
            return@Column
        }
        val col = LineColors.forCode(line.code)
        val stations = line.stations.filter { it.served }
        val live = s.trains.count { it.truth == Truth.LIVE }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live > 0) LiveDot()
            Text(
                if (s.trains.isEmpty()) "لا أحد يشارك قطارًا على هذا الخط الآن" else "${s.trains.size} قطار على الخط · $live مباشر",
                style = WrType.small, color = c.inkMuted,
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            itemsIndexed(stations, key = { _, st -> st.id }) { i, st ->
                val here = s.trains.filter { it.stationIndex == i }
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable { onOpenStation(st.id) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val x = size.width / 2
                            val top = if (i == 0) size.height / 2 else 0f
                            val bottom = if (i == stations.lastIndex) size.height / 2 else size.height
                            drawLine(col, Offset(x, top), Offset(x, bottom), strokeWidth = 6.dp.toPx())
                            val terminus = i == 0 || i == stations.lastIndex
                            drawCircle(Color.White, radius = (if (terminus) 8 else 6).dp.toPx(), center = Offset(x, size.height / 2))
                            drawCircle(col, radius = (if (terminus) 8 else 6).dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(st.nameAr, style = if (i == 0 || i == stations.lastIndex) WrType.bodyStrong else WrType.body, color = c.ink, modifier = Modifier.weight(1f))
                    here.forEach { t ->
                        Row(
                            Modifier.padding(start = 6.dp).clip(RoundedCornerShape(12.dp)).background(if (t.truth == Truth.LIVE) c.liveSoft else c.estimatedSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(TrainGlyph, null, tint = if (t.truth == Truth.LIVE) c.liveText else c.estimatedText, modifier = Modifier.size(14.dp))
                            Text(
                                t.label + when (t.towardsStart) { true -> " ↑"; false -> " ↓"; null -> "" },
                                style = WrType.tag, color = if (t.truth == Truth.LIVE) c.liveText else c.estimatedText,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TruthTag(Truth.LIVE); TruthTag(Truth.ESTIMATED)
                }
            }
        }
    }
}
