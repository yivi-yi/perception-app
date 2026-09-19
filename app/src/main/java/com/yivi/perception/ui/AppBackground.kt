package com.yivi.perception.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.yivi.perception.PerceptionApp
import com.yivi.perception.ui.common.LocalBlurredWallpaper
import com.yivi.perception.ui.theme.LocalPalette
import java.io.File
import kotlin.math.max

/**
 * 背景：没设壁纸就是主题渐变，设了就铺壁纸，上面盖一层很淡的蒙层让字看得清。
 * 顺便把壁纸的缩小版提供给 GlassCard，卡片就有了毛玻璃。
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val bgPath by PerceptionApp.instance.settings.bgUri.collectAsState()

    // 解码放到 IO 里做，壁纸大也不会卡首屏
    val full by produceState<ImageBitmap?>(null, bgPath) {
        value = withContext(Dispatchers.IO) { decodeWallpaper(bgPath, 1800) }
    }
    val small by produceState<ImageBitmap?>(null, bgPath, palette.isDark, palette.accent) {
        // 没壁纸的时候自己造一张柔光底（小图，放大后就是糊的），卡片才有东西可以糊
        value = withContext(Dispatchers.IO) {
            decodeWallpaper(bgPath, 48) ?: makeSoftBackdrop(palette)
        }
    }
    val bgBmp = full ?: small

    CompositionLocalProvider(LocalBlurredWallpaper provides small) {
        Box(Modifier.fillMaxSize()) {
            if (bgBmp != null) {
                Image(
                    bitmap = bgBmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(palette.bgGradient)))
            }

            val scrim = if (palette.isDark) Color.Black else Color.White
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            scrim.copy(alpha = if (palette.isDark) 0.16f else 0.22f),
                            scrim.copy(alpha = if (palette.isDark) 0.38f else 0.40f)
                        )
                    )
                )
            )

            content()
        }
    }
}

/** 没壁纸时的默认底：主题色渐变 + 几团柔光，小图放大刚好是毛玻璃那种糊 */
private fun makeSoftBackdrop(palette: com.yivi.perception.ui.theme.Palette): ImageBitmap? = try {
    val w = 54
    val h = 108
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(palette.bgTop.toArgb(), palette.bgBottom.toArgb()),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    fun blob(cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(
            cx, cy, r,
            (color and 0x00FFFFFF) or (alpha shl 24),
            (color and 0x00FFFFFF),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
    }
    blob(w * 0.15f, h * 0.12f, w * 1.4f, palette.accent.toArgb(), 0x66)
    blob(w * 0.95f, h * 0.42f, w * 1.2f, palette.textPrimary.toArgb(), 0x1F)
    blob(w * 0.5f, h * 0.92f, w * 1.6f, palette.accent.toArgb(), 0x4D)
    bmp.asImageBitmap()
} catch (e: Exception) {
    null
}

private fun decodeWallpaper(path: String, maxDim: Int): ImageBitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
