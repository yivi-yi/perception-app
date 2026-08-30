package com.yivi.perception.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PerceptionColorScheme = darkColorScheme(
    primary = AccentPink,
    onPrimary = DeepBg,
    secondary = SoftAmber,
    onSecondary = DeepBg,
    tertiary = AccentPinkDim,
    background = DeepBg,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = CardViolet,
    onSurfaceVariant = TextSecondary,
    outline = LineViolet
)

@Composable
fun PerceptionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PerceptionColorScheme,
        typography = PerceptionTypography,
        content = content
    )
}
