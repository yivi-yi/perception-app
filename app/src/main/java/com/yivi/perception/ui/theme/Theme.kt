package com.yivi.perception.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import com.yivi.perception.PerceptionApp

@Composable
fun PerceptionTheme(content: @Composable () -> Unit) {
    val settings = PerceptionApp.instance.settings
    val accent by settings.accent.collectAsState()
    val dark by settings.dark.collectAsState()
    val palette = remember(accent, dark) { Palette.of(accent, dark) }

    val cs = if (dark) darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.bgBottom,
        secondary = palette.accent,
        background = palette.bgBottom,
        onBackground = palette.textPrimary,
        surface = palette.cardBottom,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.cardViolet,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.lineViolet
    ) else lightColorScheme(
        primary = palette.accent,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = palette.accent,
        background = palette.bgBottom,
        onBackground = palette.textPrimary,
        surface = palette.cardBottom,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.cardViolet,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.lineViolet
    )

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = cs, typography = PerceptionTypography, content = content)
    }
}
