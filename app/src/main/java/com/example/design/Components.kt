package com.example.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** What a time or position is based on; every number in the app carries one. */
enum class Truth { LIVE, ESTIMATED, SCHEDULED }

/** A green dot that pulses: something is live right now. */
@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 8.dp, color: Color = Wr.colors.live) {
    val t = rememberInfiniteTransition(label = "live")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)), label = "ring")
    Canvas(modifier.size(size * 2.4f)) {
        val c = center
        val r = size.toPx() / 2
        drawCircle(color.copy(alpha = 0.55f * (1f - ring)), radius = r + r * 1.6f * ring, center = c)
        drawCircle(color, radius = r, center = c)
    }
}

@Composable
fun TruthTag(truth: Truth, modifier: Modifier = Modifier, text: String? = null) {
    val c = Wr.colors
    val (label, fg, bg, border) = when (truth) {
        Truth.LIVE -> Quad("مباشر", c.liveText, c.liveSoft, null)
        Truth.ESTIMATED -> Quad("تقديري", c.estimatedText, c.estimatedSoft, null)
        Truth.SCHEDULED -> Quad("مجدول", c.inkMuted, Color.Transparent, c.inkFaint)
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (truth == Truth.LIVE) LiveDot(size = 5.dp, color = c.live)
        Text(text ?: label, style = WrType.tag, color = fg)
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: Color?)

@Composable
fun LineChip(name: String, color: Color, modifier: Modifier = Modifier, onDark: Boolean = false) {
    Row(
        modifier.clip(CircleShape).background(if (onDark) color else Wr.colors.card)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!onDark) Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(name, style = WrType.tag.copy(fontSize = 12.sp), color = if (onDark) Color.White else Wr.colors.ink)
    }
}


/** Front view of the suburban electric train, drawn on a 24-unit grid. */
val TrainGlyph: ImageVector by lazy {
    ImageVector.Builder("train", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathFillType = PathFillType.NonZero) {
            moveTo(7f, 3f); lineTo(17f, 3f)
            arcTo(3f, 3f, 0f, false, true, 20f, 6f); lineTo(20f, 15f)
            arcTo(3f, 3f, 0f, false, true, 17f, 18f); lineTo(7f, 18f)
            arcTo(3f, 3f, 0f, false, true, 4f, 15f); lineTo(4f, 6f)
            arcTo(3f, 3f, 0f, false, true, 7f, 3f); close()
            moveTo(4f, 10f); lineTo(20f, 10f)
            moveTo(8f, 18f); lineTo(6f, 21f)
            moveTo(16f, 18f); lineTo(18f, 21f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.1f, 14f); arcTo(0.6f, 0.6f, 0f, true, true, 7.9f, 14f); arcTo(0.6f, 0.6f, 0f, true, true, 9.1f, 14f)
            moveTo(16.1f, 14f); arcTo(0.6f, 0.6f, 0f, true, true, 14.9f, 14f); arcTo(0.6f, 0.6f, 0f, true, true, 16.1f, 14f)
        }
    }.build()
}

/**
 * The little track in the hero card: two stations before mine, the train
 * gliding between them, my station highlighted. [progress] is 0 at the far
 * station and 1 at mine (layout is right-to-left, the train moves toward the left end).
 */
@Composable
fun MiniTrack(
    progress: Float,
    lineColor: Color,
    fromName: String,
    midName: String?,
    toName: String,
    modifier: Modifier = Modifier,
    description: String,
) {
    val c = Wr.colors
    val t = rememberInfiniteTransition(label = "glide")
    val wobble by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "w")
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description }) {
        val w = maxWidth
        val p = progress.coerceIn(0f, 1f)
        Box(Modifier.align(Alignment.TopStart).offset(y = 15.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(c.heroLine))
        Box(Modifier.align(Alignment.TopStart).offset(y = 15.dp).width(w * p).height(4.dp).clip(CircleShape).background(lineColor))
        Box(Modifier.offset(y = 10.dp).size(14.dp).clip(CircleShape).background(c.hero).border(3.dp, lineColor, CircleShape))
        if (midName != null) {
            Box(Modifier.offset(x = w / 2 - 7.dp, y = 10.dp).size(14.dp).clip(CircleShape).background(c.hero).border(3.dp, c.heroMuted, CircleShape))
        }
        Box(Modifier.offset(x = w - 20.dp, y = 7.dp).size(20.dp).clip(CircleShape).background(c.signal).border(4.dp, c.hero, CircleShape))
        Box(
            Modifier.offset(x = (w - 30.dp) * p + (wobble * 2).dp, y = 3.dp).size(30.dp, 26.dp)
                .clip(RoundedCornerShape(8.dp)).background(c.heroText),
            contentAlignment = Alignment.Center,
        ) { Icon(TrainGlyph, contentDescription = null, tint = c.hero, modifier = Modifier.size(18.dp)) }
        Text(fromName, style = WrType.tag, color = c.heroMuted, modifier = Modifier.offset(y = 30.dp))
        if (midName != null) Text(midName, style = WrType.tag, color = c.heroMuted, modifier = Modifier.offset(x = w / 2 - 20.dp, y = 30.dp))
        Text(toName, style = WrType.tag, color = c.heroText, modifier = Modifier.align(Alignment.TopEnd).offset(y = 30.dp))
    }
}

@Composable
fun WrCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Wr.colors.card).padding(padding), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = WrType.section, color = Wr.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = WrType.small.copy(fontWeight = FontWeight.SemiBold),
                color = if (Wr.colors.isDark) Color(0xFF8CC0FF) else Color(0xFF1D5FA8),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val c = Wr.colors
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(c.signal)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, style = WrType.button, color = c.onSignal)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Wr.colors.ink) {
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = WrType.button.copy(fontSize = 16.sp), color = color) }
}

/** Two or three options in a pill track; the chosen one is a raised white pill. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Wr.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.cardMuted).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.card else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) }).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = WrType.bodyStrong.copy(fontSize = 14.sp), color = if (on) c.ink else c.inkMuted) }
        }
    }
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Wr.colors
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind { if (divider) drawLine(c.divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Wr.colors.inkFaint, modifier = Modifier.size(16.dp))
}

@Composable
fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
