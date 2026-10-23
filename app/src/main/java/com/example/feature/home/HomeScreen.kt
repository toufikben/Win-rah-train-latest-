package com.example.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.design.Chevron
import com.example.design.LineColors
import com.example.design.ListRow
import com.example.design.LiveDot
import com.example.design.MiniTrack
import com.example.design.OneLine
import com.example.design.PrimaryButton
import com.example.design.SectionHeader
import com.example.design.Segmented
import com.example.design.TrainGlyph
import com.example.design.Truth
import com.example.design.TruthTag
import com.example.design.WrCard
import com.example.design.WrType
import com.example.design.Wr
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenStation: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onBoard: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val s by vm.state.collectAsState()
    val c = Wr.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Header(onOpenNotifications)
            FavoritesRow(s, onSelect = vm::selectStation, onAdd = onOpenFavorites)
            Segmented(
                options = listOf("نحو الجزائر", "نحو الضواحي"),
                selected = if (s.heading == Heading.TO_ALGIERS) 0 else 1,
                onSelect = { vm.setHeading(if (it == 0) Heading.TO_ALGIERS else Heading.TO_SUBURBS) },
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
            when {
                s.serverWaking -> WakingCard()
                s.hero != null -> HeroCard(s, onToggleAlert = { vm.toggleAlert() })
                !s.loading -> EmptyHero(s)
            }
            s.error?.let {
                Text(it, style = WrType.small, color = c.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            s.alertMessage?.let {
                Text(it, style = WrType.small, color = c.estimatedText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            s.nearest?.let { (st, d) ->
                WrCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    ListRow(divider = false, onClick = { onOpenStation(st.id) }) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.liveSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GpsFixed, null, tint = c.liveText, modifier = Modifier.size(18.dp))
                        }
                        Text("أقرب محطة إليك: ${st.nameAr} · ${walk(d)}", style = WrType.body.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f))
                        Chevron()
                    }
                }
            }
            if (s.next.isNotEmpty()) {
                SectionHeader("بعده", Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp), action = "كل القطارات",
                    onAction = { s.station?.let { onOpenStation(it.id) } })
                WrCard(Modifier.padding(horizontal = 20.dp)) {
                    s.next.forEachIndexed { i, r -> TrainLine(r, divider = i < s.next.lastIndex) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            "أنا في القطار", onClick = onBoard,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            leading = { Icon(TrainGlyph, null, tint = c.onSignal, modifier = Modifier.size(22.dp)) },
        )
    }
}

private fun walk(m: Double): String {
    val minutes = (m / 80).toInt().coerceAtLeast(1)
    return if (minutes <= 25) "$minutes د مشيًا" else "${"%.1f".format(m / 1000)} كم"
}

@Composable
private fun Header(onOpenNotifications: () -> Unit) {
    val c = Wr.colors
    val now = LocalTime.now(ZoneOffset.ofHours(1))
    val greeting = if (now.hour < 12) "صباح الخير" else "مساء الخير"
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$greeting · ${now.hour}:${"%02d".format(now.minute)}", style = WrType.small, color = c.inkMuted)
            Text("وين راه التران", style = WrType.title, color = c.ink)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.card)
                .clickable(role = Role.Button, onClick = onOpenNotifications)
                .semantics { contentDescription = "الإشعارات" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Notifications, null, tint = c.ink, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun FavoritesRow(s: HomeState, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val c = Wr.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shown = (listOfNotNull(s.station) + s.favorites).distinctBy { it.id }
        shown.forEach { st ->
            val on = st.id == s.station?.id
            Row(
                Modifier.clip(CircleShape).background(if (on) c.ink else c.card)
                    .clickable(role = Role.Button) { onSelect(st.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (on) Icon(Icons.Rounded.Star, null, tint = c.signal, modifier = Modifier.size(14.dp))
                Text(st.nameAr, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = if (on) c.card else c.ink)
            }
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, c.inkFaint, CircleShape)
                .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "إضافة محطة" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = c.inkMuted, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun HeroCard(s: HomeState, onToggleAlert: () -> Unit) {
    val c = Wr.colors
    val h = s.hero ?: return
    val r = h.row
    val lineColor = LineColors.forCode(r.lineCode)
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.hero)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.lineName.ifBlank { "القطار القادم" }, style = WrType.tag.copy(fontSize = 12.sp), color = Color.White,
                modifier = Modifier.clip(CircleShape).background(lineColor).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            when (r.truth) {
                Truth.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(size = 8.dp)
                    Text(if (r.riders > 1) "مباشر · ${r.riders} ركاب" else "مباشر · راكب واحد", style = WrType.small, color = Color(0xFFB9F2D7))
                }
                Truth.ESTIMATED -> Text("تقديري", style = WrType.small, color = Color(0xFFF2C66B))
                Truth.SCHEDULED -> Text("مجدول · لا أحد يشاركه بعد", style = WrType.small, color = c.heroMuted)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                OneLine("قطار ${r.title}", WrType.bodyStrong.copy(fontSize = 14.sp), c.heroText)
                val sub = if (r.etaMin != null) "يصل ${s.station?.nameAr ?: ""} بين ${r.etaMin} و${r.etaMax} دقائق" else "الموعد ${r.time}"
                Text(sub, style = WrType.small, color = c.heroMuted)
            }
            AnimatedContent(targetState = r.etaMin?.let { (it + (r.etaMax ?: it)) / 2 }, label = "eta") { eta ->
                if (eta != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$eta", style = WrType.hero, color = c.heroText)
                        Text(" د", style = WrType.title.copy(fontSize = 16.sp), color = c.heroText)
                    }
                } else {
                    Text(r.time, style = WrType.hero.copy(fontSize = 44.sp), color = c.heroText)
                }
            }
        }
        MiniTrack(
            progress = h.progress, lineColor = lineColor, fromName = h.fromName, midName = h.midName,
            toName = s.station?.nameAr ?: "", description = "القطار قبل ${s.station?.nameAr ?: "محطتك"}",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الموعد ${r.time}", style = WrType.small, color = c.heroMuted)
            r.delayMin?.let { d ->
                Text(if (d <= 1) "في موعده" else "+$d د", style = WrType.tag.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = c.onSignal, modifier = Modifier.clip(CircleShape).background(c.signal).padding(horizontal = 9.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (s.alertOn) c.signal else c.heroLine)
                    .clickable(role = Role.Switch, onClick = onToggleAlert).padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (s.alertOn) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications, null,
                    tint = if (s.alertOn) c.onSignal else c.heroText, modifier = Modifier.size(16.dp))
                Text(if (s.alertOn) "سننبهك" else "نبّهني", style = WrType.bodyStrong.copy(fontSize = 13.sp),
                    color = if (s.alertOn) c.onSignal else c.heroText)
            }
        }
    }
}

@Composable
private fun EmptyHero(s: HomeState) {
    val c = Wr.colors
    WrCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text("لا قطارات أخرى اليوم في هذا الاتجاه", style = WrType.bodyStrong, color = c.ink)
        Text("من ${s.station?.nameAr ?: "محطتك"}. جرّب الاتجاه الآخر أو محطة أخرى.", style = WrType.small, color = c.inkMuted)
    }
}

@Composable
private fun WakingCard() {
    val c = Wr.colors
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.hero).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("نوقظ الخادم…", style = WrType.bodyStrong, color = c.heroText)
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = c.signal, trackColor = c.heroLine,
        )
        Text("قد يستغرق نصف دقيقة في أول فتح صباحًا.", style = WrType.small, color = c.heroMuted)
    }
}

@Composable
fun TrainLine(r: TrainRow, divider: Boolean) {
    val c = Wr.colors
    ListRow(divider = divider) {
        Text(r.time, style = WrType.time, color = c.ink, modifier = Modifier.width(48.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(LineColors.forCode(r.lineCode)))
        Column(Modifier.weight(1f)) {
            OneLine(r.title, WrType.bodyStrong.copy(fontSize = 14.sp), c.ink)
            OneLine(if (r.etaMin != null) "يصل بعد ${r.etaMin}–${r.etaMax} د" else r.note, WrType.small.copy(fontSize = 12.sp), c.inkMuted)
        }
        TruthTag(r.truth)
    }
}
