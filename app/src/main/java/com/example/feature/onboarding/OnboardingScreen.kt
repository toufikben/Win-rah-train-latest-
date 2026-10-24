package com.example.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.design.LineColors
import com.example.design.LiveDot
import com.example.design.MiniTrack
import com.example.design.PrimaryButton
import com.example.design.TrainGlyph
import com.example.design.Truth
import com.example.design.TruthTag
import com.example.design.Wr
import com.example.design.WrType

private data class Page(val title: String, val body: String)

private val PAGES = listOf(
    Page("أين قطاري الآن؟", "وين راح يريك موقع قطار الضاحية الحقيقي، من ركاب داخله، لا من جدول ورقي فقط."),
    Page("الركاب يصنعون الخريطة", "اضغط «أنا في القطار» عندما تركب. هاتفك يشارك موقع القطار بشكل مجهول، ويتوقف عند نزولك."),
    Page("نم مطمئنًا", "اختر محطتك وسننبهك قبل الوصول بدقائق، حتى لو كان الهاتف في جيبك."),
)

/** Three short pages; the shell then asks for notification and location permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val c = Wr.colors
    Column(Modifier.fillMaxSize().background(c.hero).safeDrawingPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("تخطي", color = c.heroMuted) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(page, label = "onboarding") { i -> Illustration(i) }
        }
        AnimatedContent(page, label = "onboarding-text") { i ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(PAGES[i].title, style = WrType.hero.copy(fontSize = 30.sp), color = c.heroText)
                Text(PAGES[i].body, style = WrType.body.copy(fontSize = 17.sp), color = c.heroMuted)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAGES.indices.forEach { i ->
                    Box(Modifier.height(6.dp).width(if (i == page) 22.dp else 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i == page) c.signal else c.heroLine))
                }
            }
            PrimaryButton(if (page < PAGES.lastIndex) "التالي" else "ابدأ", onClick = { if (page < PAGES.lastIndex) page++ else onDone() }, modifier = Modifier.width(150.dp))
        }
    }
}

@Composable
private fun Illustration(i: Int) {
    val c = Wr.colors
    when (i) {
        0 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(size = 10.dp); Text("يصل خلال 4 دقائق", style = WrType.title, color = c.heroText)
            }
            MiniTrack(progress = 0.6f, lineColor = LineColors.Thenia, fromName = "رويبة", midName = "الرويبة المنطقة الصناعية", toName = "رغاية",
                modifier = Modifier.width(280.dp), description = "قطار بين رويبة ورغاية")
        }
        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(60.dp)).background(c.heroLine), contentAlignment = Alignment.Center) {
                Icon(TrainGlyph, null, tint = c.signal, modifier = Modifier.size(56.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TruthTag(Truth.LIVE); TruthTag(Truth.ESTIMATED); TruthTag(Truth.SCHEDULED) }
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏰", fontSize = 72.sp)
            Text("قبل محطتك بـ 5 دقائق", style = WrType.title, color = c.heroText, textAlign = TextAlign.Center)
        }
    }
}
