package com.yivi.perception.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.yivi.perception.PerceptionApp
import com.yivi.perception.ui.theme.LocalPalette
import java.io.InputStream

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings
    val bgUri by settings.bgUri.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (bgUri.isNotBlank()) {
            val bitmap = remember(bgUri) { loadBitmap(context, Uri.parse(bgUri)) }
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(palette.bgGradient)))
            }
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(palette.bgGradient)))
        }
        content()
    }
}

private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { ins: InputStream ->
            BitmapFactory.decodeStream(ins)
        }
    } catch (e: Exception) {
        null
    }
}
