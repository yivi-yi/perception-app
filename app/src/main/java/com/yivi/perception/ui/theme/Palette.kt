package com.yivi.perception.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 全局配色。分暗/亮两套，主色可选粉或灰。
 * 后面的短别名（text / textDim / surface…）是给界面代码用的，跟小家那边一致的叫法。
 */
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
    val text: Color get() = textPrimary
    val textLight: Color get() = textSecondary
    val textDim: Color get() = textSecondary.copy(alpha = 0.72f)
    val background: Color get() = bgBottom
    val surface: Color get() = if (isDark) Color(0xFF1D1626) else Color(0xFFFFFFFF)
    val chipBg: Color get() = cardViolet
    val chipBorder: Color get() = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)

    val bgGradient: List<Color> get() = listOf(bgTop, bgBottom)
    val cardGradient: List<Color> get() = listOf(cardTop, cardBottom)

    /** 毛玻璃卡片里的那层半透明底 */
    val glassTint: Color
        get() = if (isDark) Color(0xFF1B1425).copy(alpha = 0.46f) else Color.White.copy(alpha = 0.52f)

    /** 玻璃上的一点点提亮，亮色主题更明显 */
    val glassSheen: Color
        get() = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.30f)

    /** 花体标题用的渐变刷 */
    val titleBrush: Brush get() = Brush.linearGradient(listOf(textPrimary, accent, textPrimary))

    companion object {
        fun of(accentKind: String, dark: Boolean): Palette {
            val accentP = if (dark) Color(0xFFFF9FB0) else Color(0xFFE0708A)
            val accentG = if (dark) Color(0xFFBFC7D4) else Color(0xFF8E96A6)
            val accent = if (accentKind == "gray") accentG else accentP
            val accentSoft = accent.copy(alpha = 0.25f)

            return if (dark) Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFF0E0A12), bgBottom = Color(0xFF191122),
                cardTop = Color(0xFF241C30), cardBottom = Color(0xFF180D24),
                cardViolet = Color(0xFF3A2A4A),
                textPrimary = Color(0xFFF6EAF0), textSecondary = Color(0xFFB8A7B5),
                lineViolet = Color(0xFF4A3A5E), isDark = true
            ) else Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFFF7F1F6), bgBottom = Color(0xFFEDE4F0),
                cardTop = Color(0xFFFFFFFF), cardBottom = Color(0xFFF3EBF5),
                cardViolet = Color(0xFFE4D8EA),
                textPrimary = Color(0xFF2A2133), textSecondary = Color(0xFF6B5F75),
                lineViolet = Color(0xFFD0C0D8), isDark = false
            )
        }
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.of("pink", true) }
