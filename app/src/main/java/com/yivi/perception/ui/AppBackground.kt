package com.yivi.perception.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

    val full = remember(bgPath) { decodeWallpaper(bgPath, 1800) }
    val small = remember(bgPath) { decodeWallpaper(bgPath, 90) }

    CompositionLocalProvider(LocalBlurredWallpaper provides small) {
        Box(Modifier.fillMaxSize()) {
            if (full != null) {
                Image(
                    bitmap = full,
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
                            scrim.copy(alpha = if (palette.isDark) 0.20f else 0.26f),
                            scrim.copy(alpha = if (palette.isDark) 0.46f else 0.46f)
                        )
                    )
                )
            )

            content()
        }
    }
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
