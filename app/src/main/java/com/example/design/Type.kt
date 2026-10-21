package com.example.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dz.winrah.trainradar.R

/** Body and UI text: IBM Plex Sans Arabic (OFL), Arabic and Latin. */
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/** Headlines and every number riders read (times, minutes): Readex Pro, variable (OFL). */
val Readex = FontFamily(
    Font(R.font.readex_pro, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.readex_pro, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.readex_pro, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.readex_pro, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

object WrType {
    val hero = TextStyle(fontFamily = Readex, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 56.sp)
    val title = TextStyle(fontFamily = Readex, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp)
    val section = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp)
    val time = TextStyle(fontFamily = Readex, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val bodyStrong = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, lineHeightStyle = Trim)
    val small = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val tag = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp)
    val button = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)
}

val WrMaterialTypography = Typography(
    displayLarge = WrType.hero,
    headlineSmall = WrType.title,
    titleLarge = WrType.title,
    titleMedium = WrType.bodyStrong,
    bodyLarge = WrType.body,
    bodyMedium = WrType.body.copy(fontSize = 14.sp),
    bodySmall = WrType.small,
    labelLarge = WrType.bodyStrong.copy(fontSize = 14.sp),
    labelMedium = WrType.small,
    labelSmall = WrType.tag,
)
