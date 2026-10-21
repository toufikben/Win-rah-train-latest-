package com.example.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens of the WinRah design system (see the design canvas). */
@Immutable
data class WrColors(
    val ground: Color,        // screen background
    val card: Color,          // raised surfaces
    val cardMuted: Color,     // inset fields, segmented track
    val ink: Color,           // primary text and the dark hero
    val inkMuted: Color,      // secondary text
    val inkFaint: Color,      // tertiary text, dividers' companion
    val divider: Color,
    val hero: Color,          // the dark "next train" card
    val heroText: Color,
    val heroMuted: Color,
    val heroLine: Color,
    val signal: Color,        // railway-signal amber: the one accent
    val onSignal: Color,
    val signalSoft: Color,
    val live: Color,          // pulse of a live train
    val liveText: Color,
    val liveSoft: Color,
    val estimatedText: Color,
    val estimatedSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val isDark: Boolean,
)

val LightWr = WrColors(
    ground = Color(0xFFEEF1F0), card = Color(0xFFFFFFFF), cardMuted = Color(0xFFDFE4E2),
    ink = Color(0xFF0F1A17), inkMuted = Color(0xFF56635E), inkFaint = Color(0xFF8A9792),
    divider = Color(0xFFEDF0EF),
    hero = Color(0xFF0F1A17), heroText = Color(0xFFFFFFFF), heroMuted = Color(0xFFB7C3BF), heroLine = Color(0xFF24322D),
    signal = Color(0xFFE8A317), onSignal = Color(0xFF0F1A17), signalSoft = Color(0xFFFBEFD2),
    live = Color(0xFF3DDC97), liveText = Color(0xFF0F6B45), liveSoft = Color(0xFFDCF6EA),
    estimatedText = Color(0xFF8A5A00), estimatedSoft = Color(0xFFFBEFD2),
    danger = Color(0xFF9B2C22), dangerSoft = Color(0xFFFDE8E6),
    isDark = false,
)

val DarkWr = WrColors(
    ground = Color(0xFF0B1311), card = Color(0xFF16211E), cardMuted = Color(0xFF1F2B27),
    ink = Color(0xFFEEF3F1), inkMuted = Color(0xFF94A39E), inkFaint = Color(0xFF6E7C77),
    divider = Color(0xFF24332E),
    hero = Color(0xFF16211E), heroText = Color(0xFFEEF3F1), heroMuted = Color(0xFF94A39E), heroLine = Color(0xFF24332E),
    signal = Color(0xFFE8A317), onSignal = Color(0xFF0B1311), signalSoft = Color(0xFF3A2F17),
    live = Color(0xFF3DDC97), liveText = Color(0xFF8FE8C0), liveSoft = Color(0xFF1B3A2B),
    estimatedText = Color(0xFFF2C66B), estimatedSoft = Color(0xFF3A2F17),
    danger = Color(0xFFFF8A7E), dangerSoft = Color(0xFF3B1E1A),
    isDark = true,
)

val LocalWrColors = staticCompositionLocalOf { LightWr }

/** Each line keeps one colour everywhere: chips, diagram, map. */
object LineColors {
    val Thenia = Color(0xFF2A5D9F)
    val ElAffroun = Color(0xFFB5651D)
    val Zeralda = Color(0xFF1F7A5C)
    val Airport = Color(0xFF6B5CA5)
    val OuedAissi = Color(0xFF0F8A8A)
    val Unknown = Color(0xFF8A9792)

    fun forCode(code: String?): Color = when (code) {
        "line-suburb-thenia" -> Thenia
        "line-suburb-elaffroun", "line-suburb-elaffroun-thenia" -> ElAffroun
        "line-suburb-zeralda", "line-suburb-zeralda-thenia" -> Zeralda
        "line-suburb-airport" -> Airport
        else -> Unknown
    }

    /** Canonical line UUIDs (uuid5 of the line code, as in the backend). */
    private val byId = mapOf(
        "bd566e47-d079-561d-84d1-f66283653b14" to "line-suburb-thenia",
        "bd458a9a-5dab-5ee0-8c02-063626e8b0f2" to "line-suburb-elaffroun",
        "e9b9c544-c906-5bc2-83ee-2bf46d97cef9" to "line-suburb-zeralda",
        "cfa133f9-0de9-5fe9-99f2-8299d574c3cb" to "line-suburb-elaffroun-thenia",
        "d4b79a9a-35d3-5a50-b17e-17e7c86f7ae2" to "line-suburb-zeralda-thenia",
        "38655abf-3a8c-5053-9c9e-fad27fa779dd" to "line-suburb-airport",
    )

    fun codeForId(id: String?): String? = id?.let { byId[it] ?: it.takeIf { c -> c.startsWith("line-") } }
    fun forId(id: String?): Color = forCode(codeForId(id))
}
