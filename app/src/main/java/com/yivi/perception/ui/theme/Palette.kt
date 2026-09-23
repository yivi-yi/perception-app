package com.yivi.perception.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 全局配色。底色走中性灰，不偏紫。
 * 三套主题色：pink 粉 / blue 灰蓝 / gray 灰（跟小家灰渡界一个调）。
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
    val danger: Color,
    val isDark: Boolean
) {
    val text: Color get() = textPrimary
    val textLight: Color get() = textSecondary
    val textDim: Color get() = textSecondary.copy(alpha = 0.72f)
    val background: Color get() = bgBottom
    val surface: Color get() = if (isDark) Color(0xFF1C1C20) else Color(0xFFFFFFFF)
    val chipBg: Color get() = cardViolet
    val chipBorder: Color get() = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.85f)

    val bgGradient: List<Color> get() = listOf(bgTop, bgBottom)
    val cardGradient: List<Color> get() = listOf(cardTop, cardBottom)

    /** 毛玻璃卡片里的半透明底：alpha 压低，雾化才透得出来 */
    val glassTint: Color
        get() = if (isDark) Color(0xFF17171B).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.42f)

    /** 弹窗底色：跟主题色，稍微沾一点主色（暗色下沾多了会发红，压到很低） */
    val dialogTint: Color
        get() = lerp(surface, accent, if (isDark) 0.04f else 0.05f)

    val titleBrush: Brush get() = Brush.linearGradient(listOf(textPrimary, accent, textPrimary))

    companion object {
        fun of(accentKind: String, dark: Boolean): Palette {
            val pink = if (dark) Color(0xFFFF9FB0) else Color(0xFFC2556F)
            val blue = if (dark) Color(0xFFAFC2D8) else Color(0xFF5F7691)
            val gray = if (dark) Color(0xFFB9B9B9) else Color(0xFF6B6B6B)
            val accent = when (accentKind) {
                "blue" -> blue
                "gray" -> gray
                else -> pink
            }
            val accentSoft = accent.copy(alpha = 0.25f)

            return if (dark) Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFF121216), bgBottom = Color(0xFF17171C),
                cardTop = Color(0xFF232329), cardBottom = Color(0xFF17171B),
                cardViolet = Color(0xFF2B2B31),
                textPrimary = Color(0xFFF2F2F4), textSecondary = Color(0xFFB4B4BC),
                lineViolet = Color(0xFF3B3B42), danger = Color(0xFFFF8095), isDark = true
            ) else Palette(
                accent = accent, accentSoft = accentSoft,
                bgTop = Color(0xFFF7F7F8), bgBottom = Color(0xFFEFEFF1),
                cardTop = Color(0xFFFFFFFF), cardBottom = Color(0xFFF2F2F4),
                cardViolet = Color(0xFFE6E6E9),
                textPrimary = Color(0xFF26262A), textSecondary = Color(0xFF6C6C74),
                lineViolet = Color(0xFFD3D3D8), danger = Color(0xFFBE3F58), isDark = false
            )
        }
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.of("pink", true) }
