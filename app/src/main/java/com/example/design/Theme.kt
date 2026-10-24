package com.example.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The app theme. Material 3 underneath; screens read [Wr.colors] for the design tokens. */
@Composable
fun WinRahTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val wr = if (darkTheme) DarkWr else LightWr
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = wr.signal, onPrimary = wr.onSignal, background = wr.ground, onBackground = wr.ink,
            surface = wr.card, onSurface = wr.ink, surfaceVariant = wr.cardMuted, onSurfaceVariant = wr.inkMuted,
            outline = wr.inkFaint, error = wr.danger,
        )
    } else {
        lightColorScheme(
            primary = wr.ink, onPrimary = wr.card, secondary = wr.signal, onSecondary = wr.onSignal,
            background = wr.ground, onBackground = wr.ink, surface = wr.card, onSurface = wr.ink,
            surfaceVariant = wr.cardMuted, onSurfaceVariant = wr.inkMuted, outline = wr.inkFaint, error = wr.danger,
        )
    }
    CompositionLocalProvider(LocalWrColors provides wr) {
        MaterialTheme(colorScheme = scheme, typography = WrMaterialTypography, content = content)
    }
}

object Wr {
    val colors: WrColors
        @Composable @ReadOnlyComposable get() = LocalWrColors.current
}
