package com.yivi.perception.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.ToolCatalog
import com.yivi.perception.service.NetworkUtils
import com.yivi.perception.service.ServerService
import com.yivi.perception.ui.theme.LocalPalette

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings

    val accent by settings.accent.collectAsState()
    val dark by settings.dark.collectAsState()
    val bgUri by settings.bgUri.collectAsState()
    val logs by settings.logs.collectAsState()

    var running by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    val notifPermission = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) settings.setBgUri(uri.toString())
    }

    fun startServer() {
        if (!notifPermission && Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START }
        ContextCompat.startForegroundService(context, intent)
        running = true
    }
    fun stopServer() {
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
        context.startService(intent)
        running = false
    }

    val lanIp = NetworkUtils.localIp()
    val port = ServerService.PORT

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("设置", color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            PanelCard {
                Text("主题", color = palette.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeSwatch("粉色", accent == "pink", listOf(Color(0xFFFF8FA3), Color(0xFFE06B82))) { settings.setAccent("pink") }
                    ThemeSwatch("灰色", accent == "gray", listOf(Color(0xFFBFC7D4), Color(0xFF8E96A6))) { settings.setAccent("gray") }
                }
                Spacer(Modifier.height(16.dp))
                Text("模式", color = palette.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                SegmentRow(
                    options = listOf("暗色", "亮色"),
                    selected = if (dark) 0 else 1,
                    onSelect = { settings.setDark(it == 0) }
                )
            }

            PanelCard {
                Text("自定义背景", color = palette.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.cardViolet)
                    ) {
                        if (bgUri.isNotBlank()) {
                            val bmp = remember(bgUri) { loadBg(context.applicationContext, Uri.parse(bgUri)) }
                            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    TextButton(onClick = { pickLauncher.launch("image/*") }) { Text("选择图片", color = palette.accent) }
                    Spacer(Modifier.width(6.dp))
                    if (bgUri.isNotBlank()) TextButton(onClick = { settings.setBgUri("") }) { Text("清除", color = palette.textSecondary) }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(palette.cardGradient))
                    .border(1.dp, palette.lineViolet, RoundedCornerShape(22.dp))
                    .clickable { showTools = true }
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.List, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("工具盒", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("${ToolCatalog.tools.size} 个工具", color = palette.textSecondary, fontSize = 12.sp)
                }
            }

            PanelCard {
                Text("MCP 地址", color = palette.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("本机: ", color = palette.textSecondary, fontSize = 13.sp)
                    Text("http://127.0.0.1:$port/mcp", color = palette.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("局域网: ", color = palette.textSecondary, fontSize = 13.sp)
                    Text("http://$lanIp:$port/mcp", color = palette.accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日志", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showLogs = true }) { Text("查看日志", color = palette.accent) }
                }
            }
        }

        StartButton(
            running = running,
            onClick = { if (running) stopServer() else startServer() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 26.dp)
        )
    }

    if (showTools) {
        AlertDialog(
            onDismissRequest = { showTools = false },
            containerColor = palette.cardBottom,
            shape = RoundedCornerShape(24.dp),
            title = { Text("工具盒 · ${ToolCatalog.tools.size} 个", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ToolCatalog.tools.forEach { (name, desc) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(name, color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(desc, color = palette.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTools = false }) { Text("关闭", color = palette.accent) } }
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            containerColor = palette.cardBottom,
            shape = RoundedCornerShape(24.dp),
            title = { Text("运行日志", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (logs.isEmpty()) Text("暂无日志", color = palette.textSecondary)
                    logs.reversed().forEach { Text(it, color = palette.textSecondary, fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text("关闭", color = palette.accent) } }
        )
    }
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(palette.cardGradient))
            .border(1.dp, palette.lineViolet, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        content()
    }
}

@Composable
private fun ThemeSwatch(label: String, active: Boolean, colors: List<Color>, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(colors))
                .then(if (active) Modifier.border(3.dp, palette.accent, RoundedCornerShape(20.dp)) else Modifier)
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (active) palette.textPrimary else palette.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardViolet.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (active) palette.textPrimary else palette.textSecondary, fontSize = 14.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun StartButton(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(palette.cardGradient))
            .border(1.dp, if (running) palette.accent else palette.lineViolet, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (running) "停止" else "启动",
            tint = if (running) palette.accent else palette.textPrimary,
            modifier = Modifier.size(30.dp)
        )
    }
}

private fun loadBg(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }
}
