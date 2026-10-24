package com.example.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Station
import com.example.data.TrainRepository
import com.example.design.Wr
import com.example.design.WrCard
import com.example.design.WrType
import com.example.model.StationInterchange
import com.example.model.TransitType
import java.text.Normalizer

/** Maps the backend's French station names onto the hand-written interchange guides. */
object StationGuides {
    private val codes = mapOf(
        "alger" to "ALG", "agha" to "AGH", "alger agha" to "AGH", "el harrach" to "HRH",
        "caroubier" to "CRB", "hussein dey" to "HSD", "bab ezzouar" to "BBZ",
        "aeroport houari boumediene" to "AIR", "ain naadja" to "ANN", "boumerdes" to "BMR",
        "blida" to "BLD", "zeralda" to "ZRD", "birtouta" to "BRT",
    )

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    fun forStation(nameFr: String?): StationInterchange? =
        nameFr?.let { codes[normalize(it)] }?.let { TrainRepository.stationInterchanges[it] }
}

@Composable
fun GuideScreen(station: Station?) {
    val c = Wr.colors
    val g = StationGuides.forStation(station?.nameFr)
    Column(Modifier.fillMaxSize().background(c.ground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(station?.nameAr ?: "", style = WrType.title.copy(fontSize = 26.sp), color = c.ink)
        if (g == null) {
            Text("لا يوجد دليل لهذه المحطة بعد.", style = WrType.body, color = c.inkMuted)
            return@Column
        }
        Text(g.mainHubTitle, style = WrType.body, color = c.inkMuted)
        WrCard {
            g.connections.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(badge(t.type)), contentAlignment = Alignment.Center) {
                        Text(glyph(t.type), style = WrType.bodyStrong, color = Color.White)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.nameAr, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = c.ink)
                        Text(t.lineOrDestination, style = WrType.small, color = c.inkMuted)
                        val walk = if (t.walkingTimeMinutes > 0) "${t.walkingTimeMinutes} د مشيًا · ${t.walkingDistanceMeters} م" else null
                        Text(listOfNotNull(walk, t.frequencyOrNotes).joinToString(" · "), style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
                    }
                }
                if (i < g.connections.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.divider)
            }
        }
        WrCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("كيف تخرج", style = WrType.section.copy(color = c.inkMuted))
                Text(g.walkingTip, style = WrType.body, color = c.ink)
                Text("بالقرب: ${g.landmark}", style = WrType.small, color = c.inkMuted)
            }
        }
        Text("معلومات جمعها مستخدمون وقد تتغير. أبلغنا إن وجدت خطأ.", style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
    }
}

private fun glyph(t: TransitType) = when (t) {
    TransitType.METRO -> "M"; TransitType.TRAMWAY -> "T"; TransitType.ETUSA_BUS -> "B"
    TransitType.TAXI_STATION -> "TX"; TransitType.AIRPORT_SHUTTLE -> "✈"; TransitType.CABLE_CAR -> "C"
}

private fun badge(t: TransitType) = when (t) {
    TransitType.METRO -> Color(0xFFC8102E); TransitType.TRAMWAY -> Color(0xFF0E7C86)
    TransitType.ETUSA_BUS -> Color(0xFF1F7A5C); TransitType.TAXI_STATION -> Color(0xFFB7791F)
    TransitType.AIRPORT_SHUTTLE -> Color(0xFF6B5CA5); TransitType.CABLE_CAR -> Color(0xFF8A4F9E)
}
