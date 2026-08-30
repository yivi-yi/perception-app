package com.yivi.perception.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val accent: Color,
    val accentSoft: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardViolet: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val lineViolet: Color,
    val isDark: Boolean
) {
    val bgGradient = listOf(bgTop, bgBottom)
    val cardGradient = listOf(cardTop, cardBottom)

    companion object {
        fun of(accentKind: String, dark: Boolean): Palette {
            val accentP = if (dark) Color(0xFFFF8FA3) else Color(0xFFE06B82)
            val accentG = if (dark) Color(0xFFBFC7D4) else Color(0xFF8E96A6)
            val accent = if (accentKind == "gray") accentG else accentP
            val accentSoft = accent.copy(alpha = 0.25f)

            if (dark) return Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFF0E0A12), bgBottom = Color(0xFF171020),
                cardTop = Color(0xFF241C30), cardBottom = Color(0xFF180D24),
                cardViolet = Color(0xFF3A2A4A),
                textPrimary = Color(0xFFF6EAF0), textSecondary = Color(0xFFB8A7B5),
                lineViolet = Color(0xFF4A3A5E), isDark = true
            )
            return Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFFF3EDF2), bgBottom = Color(0xFFE8E0EC),
                cardTop = Color(0xFFFFFFFF), cardBottom = Color(0xFFF0E8F2),
                cardViolet = Color(0xFFE4D8EA),
                textPrimary = Color(0xFF241C30), textSecondary = Color(0xFF6B5F75),
                lineViolet = Color(0xFFD0C0D8), isDark = false
            )
        }
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.of("pink", true) }
