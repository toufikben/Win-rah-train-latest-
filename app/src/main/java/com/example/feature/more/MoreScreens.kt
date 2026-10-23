package com.example.feature.more

import android.app.Activity
import androidx.activity.compose.LocalActivity
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WinRahApplication
import com.example.audio.TrainSoundSynthesizer
import com.example.audio.TrainSoundType
import com.example.billing.Plan
import com.example.billing.SupportOffer
import com.example.data.Station
import com.example.data.prefs.Commute
import com.example.data.prefs.Prefs
import com.example.design.Chevron
import com.example.design.ListRow
import com.example.design.PrimaryButton
import com.example.design.SecondaryButton
import com.example.design.Segmented
import com.example.design.Wr
import com.example.design.WrCard
import com.example.design.WrType
import com.example.feature.guide.StationGuides
import dz.winrah.trainradar.BuildConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoreViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WinRahApplication).container
    val prefs: StateFlow<Prefs> = c.prefs.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    val stations = c.network.stations
    val offers = c.billing.offers
    val billingMessage = c.billing.message

    init { viewModelScope.launch { runCatching { c.network.loadReference() } } }

    fun refreshBilling() { c.billing.refresh() }
    fun buy(activity: Activity, offer: SupportOffer) = c.billing.buy(activity, offer)
    fun restore() = viewModelScope.launch { c.billing.restore() }
    fun clearBillingMessage() = c.billing.clearMessage()

    fun toggleFavorite(id: String) = viewModelScope.launch {
        val p = prefs.value
        val favs = p.favoriteStationIds
        if (id in favs) c.prefs.setFavorites(favs - id)
        else if (favs.size < p.maxFavorites) c.prefs.setFavorites(favs + id)
    }
    fun setSound(id: String) = viewModelScope.launch { c.prefs.setSound(id) }
    fun setAlarmSound(id: String) = viewModelScope.launch { c.prefs.setAlarmSound(id) }
    fun setVolume(v: Int) = viewModelScope.launch { c.prefs.setVolume(v) }
    fun setVibrate(v: Boolean) = viewModelScope.launch { c.prefs.setVibrate(v) }
    fun setLead(m: Int) = viewModelScope.launch { c.prefs.setAlert(prefs.value.alertStationId, m) }
    fun setTheme(t: String) = viewModelScope.launch { c.prefs.setTheme(t) }
    fun setCommute(cm: Commute?) = viewModelScope.launch { c.prefs.setCommute(cm) }
    fun preview(type: TrainSoundType) = viewModelScope.launch { TrainSoundSynthesizer.playSound(type, prefs.value.soundVolume / 100f) }
}

// ---------------------------------------------------------------- hub

@Composable
fun MoreScreen(vm: MoreViewModel, open: (String) -> Unit) {
    val p by vm.prefs.collectAsState()
    val c = Wr.colors
    Column(Modifier.fillMaxSize().background(c.ground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("المزيد", style = WrType.title.copy(fontSize = 26.sp), color = c.ink)
        SupportBanner(p, onClick = { open("support") })
        WrCard {
            MoreRow(Icons.Rounded.Star, "محطاتي", "${p.favoriteStationIds.size} محطة") { open("favorites") }
            MoreRow(Icons.Rounded.Route, "رحلتي اليومية", p.commute?.let { "ذهاب ${hm(it.outboundMinute)} · عودة ${hm(it.returnMinute)}" } ?: "لم تُضبط بعد") { open("commute") }
            MoreRow(Icons.Rounded.MusicNote, "التنبيهات والأصوات", "قبل الوصول بـ ${p.alertLeadMinutes} دقائق") { open("sounds") }
            MoreRow(Icons.Rounded.Palette, "المظهر", when (p.theme) { "light" -> "فاتح"; "dark" -> "داكن"; else -> "حسب الهاتف" }, last = true) { open("appearance") }
        }
        WrCard {
            MoreRow(Icons.Rounded.Info, "كيف يعمل وين راح", "مباشر، تقديري، مجدول") { open("howto") }
            MoreRow(Icons.Rounded.Favorite, "عن التطبيق والإهداء", "الإصدار ${BuildConfig.VERSION_NAME}") { open("about") }
            MoreRow(Icons.Rounded.Tune, "التشخيص", "حالة الاتصال والموقع", last = true) { open("diagnostics") }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, last: Boolean = false, onClick: () -> Unit) {
    val c = Wr.colors
    ListRow(divider = !last, onClick = onClick) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.cardMuted), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = WrType.bodyStrong.copy(fontSize = 15.sp), color = c.ink)
            Text(sub, style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
        }
        Chevron()
    }
}

@Composable
private fun SupportBanner(p: Prefs, onClick: () -> Unit) {
    val c = Wr.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (p.isSupporter) "شكرًا لأنك تدعم وين راح" else "ادعم وين راح", style = WrType.title.copy(fontSize = 20.sp), color = c.heroText)
        Text(
            if (p.lifetimeSupporter) "داعم مدى الحياة. كل المزايا مفعّلة."
            else if (p.isSupporter) "كل المزايا مفعّلة."
            else "بلا إعلانات أبدًا. دعمك يبقي الخادم يعمل ويضيف مزايا مثل محطات غير محدودة.",
            style = WrType.small, color = c.heroMuted,
        )
    }
}

private fun hm(m: Int) = "%d:%02d".format(m / 60, m % 60)

@Composable
fun SubScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Wr.colors
    Column(Modifier.fillMaxSize().background(c.ground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = WrType.title.copy(fontSize = 24.sp), color = c.ink)
        content()
    }
}

// ---------------------------------------------------------------- favorites

@Composable
fun FavoritesScreen(vm: MoreViewModel, onOpenSupport: () -> Unit) {
    val p by vm.prefs.collectAsState()
    val all by vm.stations.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val c = Wr.colors
    val nq = StationGuides.normalize(q)
    val list = all.filter { nq.isBlank() || it.nameAr.contains(q.trim()) || StationGuides.normalize(it.nameFr).contains(nq) }
        .sortedWith(compareByDescending<Station> { it.id in p.favoriteStationIds }.thenBy { it.nameAr })
    val full = !p.isSupporter && p.favoriteStationIds.size >= Prefs.FREE_FAVORITES
    Column(Modifier.fillMaxSize().background(c.ground)) {
        Text("محطاتي", style = WrType.title.copy(fontSize = 24.sp), color = c.ink, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp))
        Text(
            if (p.isSupporter) "أضف ما تشاء من المحطات." else "${p.favoriteStationIds.size} من ${Prefs.FREE_FAVORITES} محطات مجانية",
            style = WrType.small, color = c.inkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        TextField(
            q, { q = it }, singleLine = true, placeholder = { Text("ابحث عن محطة") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.card, unfocusedContainerColor = c.card,
                focusedIndicatorColor = c.card, unfocusedIndicatorColor = c.card,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (full) {
            Text(
                "وصلت إلى حد المحطات المجانية. ادعم التطبيق لمحطات غير محدودة.",
                style = WrType.small, color = c.estimatedText,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(c.signalSoft).clickable(onClick = onOpenSupport).padding(12.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(list, key = { it.id }) { st ->
                val on = st.id in p.favoriteStationIds
                ListRow(Modifier.padding(horizontal = 6.dp), onClick = { if (on || !full) vm.toggleFavorite(st.id) else onOpenSupport() }) {
                    Column(Modifier.weight(1f)) {
                        Text(st.nameAr, style = WrType.bodyStrong.copy(fontSize = 15.sp), color = c.ink)
                        Text(st.nameFr, style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
                    }
                    Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (on) "مفضلة" else "غير مفضلة",
                        tint = if (on) c.signal else c.inkFaint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- commute

@Composable
fun CommuteScreen(vm: MoreViewModel) {
    val p by vm.prefs.collectAsState()
    val all by vm.stations.collectAsState()
    val c = Wr.colors
    val cm = p.commute ?: Commute(p.favoriteStationIds.firstOrNull() ?: "", "", 7 * 60 + 30, 17 * 60)
    SubScreen("رحلتي اليومية") {
        Text("نعرض قطار الذهاب صباحًا وقطار العودة مساءً تلقائيًا، وننبهك إن تأخر.", style = WrType.body, color = c.inkMuted)
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            StationPicker("من", cm.fromStationId, all) { vm.setCommute(cm.copy(fromStationId = it)) }
            StationPicker("إلى", cm.toStationId, all) { vm.setCommute(cm.copy(toStationId = it)) }
            TimeStepper("الذهاب", cm.outboundMinute) { vm.setCommute(cm.copy(outboundMinute = it)) }
            TimeStepper("العودة", cm.returnMinute) { vm.setCommute(cm.copy(returnMinute = it)) }
        }
        if (p.commute != null) SecondaryButton("إلغاء الرحلة اليومية", onClick = { vm.setCommute(null) }, color = c.danger)
    }
}

@Composable
private fun StationPicker(label: String, selected: String, all: List<Station>, onPick: (String) -> Unit) {
    val c = Wr.colors
    var open by remember { mutableStateOf(false) }
    val name = all.firstOrNull { it.id == selected }?.nameAr ?: "اختر محطة"
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = WrType.small, color = c.inkMuted, modifier = Modifier.padding(end = 12.dp))
            Text(name, style = WrType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Chevron()
        }
        if (open) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cardMuted)) {
                all.sortedBy { it.nameAr }.forEach { st ->
                    Text(st.nameAr, style = WrType.body, color = if (st.id == selected) c.signal else c.ink,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(st.id); open = false }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun TimeStepper(label: String, minute: Int, onChange: (Int) -> Unit) {
    val c = Wr.colors
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = WrType.small, color = c.inkMuted, modifier = Modifier.weight(1f))
        Step("−") { onChange((minute - 5 + 1440) % 1440) }
        Text(hm(minute), style = WrType.time.copy(fontSize = 22.sp), color = c.ink, modifier = Modifier.padding(horizontal = 14.dp))
        Step("+") { onChange((minute + 5) % 1440) }
    }
}

@Composable
private fun Step(t: String, onClick: () -> Unit) {
    val c = Wr.colors
    Box(Modifier.size(40.dp).clip(CircleShape).background(c.cardMuted).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, style = WrType.title, color = c.ink)
    }
}

// ---------------------------------------------------------------- sounds

@Composable
fun SoundsScreen(vm: MoreViewModel) {
    val p by vm.prefs.collectAsState()
    val c = Wr.colors
    var vol by remember(p.soundVolume) { mutableFloatStateOf(p.soundVolume.toFloat()) }
    SubScreen("التنبيهات والأصوات") {
        Text("نبهني قبل الوصول بـ", style = WrType.section.copy(color = c.inkMuted))
        val leads = listOf(2, 5, 10)
        Segmented(leads.map { "$it د" }, leads.indexOf(p.alertLeadMinutes).coerceAtLeast(0), { vm.setLead(leads[it]) })
        Text("صوت التنبيه", style = WrType.section.copy(color = c.inkMuted))
        SoundList(p.soundId, vm::setSound, vm::preview)
        Text("صوت المنبّه (عند النوم في القطار)", style = WrType.section.copy(color = c.inkMuted))
        SoundList(p.alarmSoundId, vm::setAlarmSound, vm::preview)
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text("مستوى الصوت ${vol.toInt()}٪", style = WrType.bodyStrong, color = c.ink)
            Slider(vol, { vol = it }, valueRange = 0f..100f, onValueChangeFinished = { vm.setVolume(vol.toInt()) },
                colors = SliderDefaults.colors(thumbColor = c.signal, activeTrackColor = c.signal))
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الاهتزاز", style = WrType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                Switch(p.vibrate, vm::setVibrate, colors = SwitchDefaults.colors(checkedTrackColor = c.signal))
            }
        }
        Text("الأصوات مولّدة داخل التطبيق ومستوحاة من أصوات القطارات، وليست تسجيلات لقطارات SNTF.",
            style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
    }
}

@Composable
private fun SoundList(selected: String, onPick: (String) -> Unit, onPreview: (TrainSoundType) -> Unit) {
    val c = Wr.colors
    WrCard {
        TrainSoundType.entries.forEachIndexed { i, s ->
            val on = s.id == selected
            ListRow(divider = i < TrainSoundType.entries.lastIndex, onClick = { onPick(s.id) }) {
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) c.signal else c.inkFaint, CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.signal))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.titleAr, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = c.ink)
                    Text(s.descriptionAr, style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.cardMuted).clickable { onPreview(s) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "استماع", tint = c.ink)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- appearance

@Composable
fun AppearanceScreen(vm: MoreViewModel) {
    val p by vm.prefs.collectAsState()
    val themes = listOf("system", "light", "dark")
    SubScreen("المظهر") {
        Segmented(listOf("حسب الهاتف", "فاتح", "داكن"), themes.indexOf(p.theme).coerceAtLeast(0), { vm.setTheme(themes[it]) })
    }
}

// ---------------------------------------------------------------- support

@Composable
fun SupportScreen(vm: MoreViewModel) {
    val p by vm.prefs.collectAsState()
    val offers by vm.offers.collectAsState()
    val msg by vm.billingMessage.collectAsState()
    val activity = LocalActivity.current
    val c = Wr.colors
    LaunchedEffect(Unit) { vm.refreshBilling() }
    SubScreen("ادعم وين راح") {
        Text("وين راح بلا إعلانات وسيبقى كذلك. الدعم اختياري، والمعلومات الأساسية مجانية للجميع دائمًا.", style = WrType.body, color = c.inkMuted)
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text("مجانًا", style = WrType.section.copy(color = c.inkMuted))
            Text("• كل القطارات الحية والجدول\n• 3 محطات مفضلة\n• تنبيه اقتراب واحد", style = WrType.body, color = c.ink)
            Text("مع الدعم", style = WrType.section.copy(color = c.signal), modifier = Modifier.padding(top = 10.dp))
            Text("• محطات مفضلة بلا حد\n• تنبيهات تأخر رحلتي اليومية\n• سجل التأخر لكل قطار\n• شارة الداعم", style = WrType.body, color = c.ink)
        }
        if (p.lifetimeSupporter) {
            Text("أنت داعم مدى الحياة. شكرًا!", style = WrType.bodyStrong, color = c.liveText)
        } else if (offers.isEmpty()) {
            Text("جارٍ تحميل الأسعار من Google Play… إن لم تظهر، تأكد أن التطبيق مثبت من المتجر.", style = WrType.small, color = c.inkMuted)
        } else offers.forEach { o -> OfferCard(o, best = o.plan == Plan.YEARLY) { activity?.let { vm.buy(it, o) } } }
        msg?.let {
            Text(it, style = WrType.small, color = c.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(c.cardMuted).clickable { vm.clearBillingMessage() }.padding(12.dp))
        }
        SecondaryButton("استعادة مشترياتي", onClick = { vm.restore() })
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text("أيام الدعم المجانية", style = WrType.bodyStrong, color = c.ink)
            Text("قريبًا: كل رحلتين تشارك فيهما موقع القطار تمنحانك يوم دعم مجاني (حتى 8 أيام في الشهر).",
                style = WrType.small, color = c.inkMuted)
        }
        Text("الدفع عبر Google Play. يمكنك إلغاء الاشتراك في أي وقت من المتجر.", style = WrType.small.copy(fontSize = 12.sp),
            color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfferCard(o: SupportOffer, best: Boolean, onBuy: () -> Unit) {
    val c = Wr.colors
    val (title, sub) = when (o.plan) {
        Plan.MONTHLY -> "شهري" to "يتجدد كل شهر"
        Plan.YEARLY -> "سنوي" to "أوفر من 12 شهرًا منفصلة"
        Plan.LIFETIME -> "مدى الحياة" to "دفعة واحدة، بلا تجديد"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
            .border(if (best) 2.dp else 1.dp, if (best) c.signal else c.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onBuy).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = WrType.title.copy(fontSize = 18.sp), color = c.ink, modifier = Modifier.weight(1f))
            if (best) Text("الأفضل قيمة", style = WrType.tag, color = c.onSignal,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.signal).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(o.price, style = WrType.time.copy(fontSize = 24.sp), color = c.ink)
        Text(sub, style = WrType.small, color = c.inkMuted)
    }
}

// ---------------------------------------------------------------- how it works

@Composable
fun HowToScreen() {
    val c = Wr.colors
    SubScreen("كيف يعمل وين راح") {
        listOf(
            "الركاب هم المصدر" to "عندما تضغط «أنا في القطار» يشارك هاتفك موقع القطار بشكل مجهول. لا نعرف من أنت، ولا نحفظ موقعك بعد انتهاء الرحلة.",
            "مباشر" to "راكب واحد على الأقل في القطار يشارك موقعه الآن.",
            "تقديري" to "انقطعت المشاركة قبل قليل، فنحسب الموقع من آخر نقطة معروفة وسرعة القطار.",
            "مجدول" to "لا أحد يشارك هذا القطار، فنعرض وقته من جدول SNTF الرسمي.",
            "البطارية" to "المشاركة تعمل في الخلفية وتتوقف تلقائيًا عند نزولك. إن كثر المشاركون في قطار واحد، نطلب من هاتفك الإرسال أقل لتوفير البطارية.",
        ).forEach { (t, d) ->
            WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Text(t, style = WrType.bodyStrong, color = c.ink)
                Text(d, style = WrType.body, color = c.inkMuted)
            }
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
fun AboutScreen() {
    val c = Wr.colors
    SubScreen("عن وين راح") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Photo placeholder until the owner provides one.
            Box(Modifier.size(88.dp).clip(CircleShape).background(c.hero), contentAlignment = Alignment.Center) {
                Text("ت ب", style = WrType.title.copy(fontSize = 28.sp), color = c.signal)
            }
            Text("توفيق بن جداه", style = WrType.title.copy(fontSize = 20.sp), color = c.ink)
            Text("مطوّر وين راح", style = WrType.small, color = c.inkMuted)
        }
        WrCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text("صُمم هذا التطبيق حبًا في خدمة مسافري وركاب السكك الحديدية بالجزائر، بلا أي إعلانات. نرحب بكل فكرة أو مساعدة لتحسين الخدمة لكافة مستعملي القطار.",
                style = WrType.body, color = c.ink)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.signalSoft).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("إهداء خاص وتقدير", style = WrType.title.copy(fontSize = 18.sp), color = c.ink)
            Text("نهدي هذا العمل إلى كافة ركاب ومسافري السكك الحديدية الجزائرية وإلى كل من يسعى لتسهيل تنقل المواطنين وتوفير الوقت والأمان.",
                style = WrType.body, color = c.ink, textAlign = TextAlign.Center)
            Text("«نسأل الله التوفيق والبركة في السفر والوصول الآمن لجميع إخواننا في كل محطات الوطن الحبيب الجزائر»",
                style = WrType.body, color = c.estimatedText, textAlign = TextAlign.Center)
        }
        Text("مواعيد القطارات من جدول SNTF ابتداءً من 19 سبتمبر 2026. وين راح تطبيق مستقل غير تابع لـ SNTF.",
            style = WrType.small.copy(fontSize = 12.sp), color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("الإصدار ${BuildConfig.VERSION_NAME}", style = WrType.small.copy(fontSize = 12.sp), color = c.inkFaint,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
