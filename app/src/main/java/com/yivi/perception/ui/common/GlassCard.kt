package com.yivi.perception.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yivi.perception.ui.theme.LocalPalette
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 背景壁纸的「缩小版」。卡片把它放大着画在自己底下，就成了毛玻璃——
 * 不用 Modifier.blur，安卓 8 到 15 都是一个效果。
 */
val LocalBlurredWallpaper = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * 毛玻璃卡片：透明底 + 描边 + 顶部高光 + 一点颗粒。
 * 有壁纸时卡片里会透出自己那块背景的模糊版本。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
    showBorder: Boolean = true,
    showHighlight: Boolean = true,
    grain: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = LocalPalette.current
    val fill = tint ?: palette.glassTint
    val blur = LocalBlurredWallpaper.current
    val grainDots = remember { List(64) { i -> (i * 37) % 97 to (i * 61) % 89 } }

    Box(modifier.clip(shape)) {
        // 卡片那块背景的模糊版，按窗口坐标对齐放大画进来
        if (blur != null) {
            val cfg = LocalConfiguration.current
            val winW = cfg.screenWidthDp.dp
            val winH = cfg.screenHeightDp.dp
            var pos by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .clipToBounds()
                    .onGloballyPositioned { pos = it.positionInWindow() }
                    .drawBehind {
                        val img = blur
                        val w = winW.toPx()
                        val h = winH.toPx()
                        val scale = max(w / img.width, h / img.height)
                        val dw = img.width * scale
                        val dh = img.height * scale
                        val offX = (w - dw) / 2f
                        val offY = (h - dh) / 2f
                        drawImage(
                            image = img,
                            dstOffset = IntOffset((offX - pos.x).roundToInt(), (offY - pos.y).roundToInt()),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            filterQuality = FilterQuality.Low
                        )
                    }
            )
        }

        // 半透明底
        Box(Modifier.matchParentSize().background(fill, shape))

        // 颗粒（固定的点，避免重组闪）
        if (grain) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .drawBehind {
                        grainDots.forEach { (gx, gy) ->
                            drawCircle(
                                Color.White.copy(alpha = 0.045f),
                                radius = 1.dp.toPx(),
                                center = Offset(size.width * gx / 97f, size.height * gy / 89f)
                            )
                        }
                    }
            )
        }

        if (showBorder) {
            Box(Modifier.matchParentSize().border(1.dp, palette.chipBorder, shape))
        }

        // 顶部内高光：有模糊底就不加了，不然糊成一片
        if (showHighlight && blur == null) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (palette.isDark) 0.06f else 0.20f),
                                1f to Color.White.copy(alpha = 0f)
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, 3.dp.toPx())
                        )
                    }
            )
        }

        content()
    }
}
