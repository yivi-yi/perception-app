@file:OptIn(ExperimentalMaterial3Api::class)

package com.yivi.perception.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.Tools
import com.yivi.perception.service.NetworkUtils
import com.yivi.perception.service.ServerService
import com.yivi.perception.ui.common.ActionPill
import com.yivi.perception.ui.common.ConfirmDialog
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.common.SectionLabel
import com.yivi.perception.ui.common.SegRow
import com.yivi.perception.ui.common.StatementDialog
import com.yivi.perception.ui.common.TextInputDialog
import com.yivi.perception.ui.common.UsageDialog
import com.yivi.perception.ui.common.ThinDivider
import com.yivi.perception.ui.theme.LocalPalette
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings

    val accent by settings.accent.collectAsState()
    val dark by settings.dark.collectAsState()
    val bgPath by settings.bgUri.collectAsState()
    val logs by settings.logs.collectAsState()

    var running by remember { mutableStateOf(PerceptionApp.instance.mcpServer.isRunning) }
    // 服务到底起没起，以真身为准（端口被占之类起不来时，界面不会骗你）
    LaunchedEffect(Unit) {
        while (true) {
            running = PerceptionApp.instance.mcpServer.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }
    var showTools by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var clearStep by remember { mutableStateOf(0) }
    var selfTestRunning by remember { mutableStateOf(false) }
    var selfTestResult by remember { mutableStateOf<String?>(null) }
    var editCity by remember { mutableStateOf(false) }
    val weatherCity by settings.weatherCity.collectAsState()
    var showUsage by remember { mutableStateOf(false) }
    var showStatement by remember { mutableStateOf(false) }
    val bootStart by settings.bootStart.collectAsState()
    val scope = rememberCoroutineScope()
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }
    var dndOk by remember { mutableStateOf(isDndGranted(context)) }
    var overlayOk by remember { mutableStateOf(canDrawOverlays(context)) }
    val versionName = remember { appVersion(context) }

    var notifPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val multiPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyWallpaper(context, uri)?.let { settings.setBgUri(it) }
    }

    fun startServer() {
        // 服务要用到的权限顺手一起要：通知（服务常驻）+ 定位（工具盒定位/WiFi 名）+ 活动识别（步数）
        val ask = buildList {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ask.isNotEmpty()) multiPermLauncher.launch(ask.toTypedArray())
        if (!notifPermission) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START }
        ContextCompat.startForegroundService(context, intent)
        running = PerceptionApp.instance.mcpServer.isRunning
    }

    fun stopServer() {
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
        context.startService(intent)
        running = PerceptionApp.instance.mcpServer.isRunning
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val accEnabled = remember { mutableStateOf(isAccessibilityEnabled(context)) }
    val notifEnabled = remember { mutableStateOf(isNotifEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                accEnabled.value = isAccessibilityEnabled(context)
                notifEnabled.value = isNotifEnabled(context)
                batteryOk = isIgnoringBattery(context)
                dndOk = isDndGranted(context)
                overlayOk = canDrawOverlays(context)
                notifPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val lanIps = remember { NetworkUtils.allIps() }
    val port = ServerService.PORT

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "𝒮ℯ𝓉𝓉𝒾𝓃ℊ",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = palette.text,
            style = MaterialTheme.typography.titleMedium.copy(brush = palette.titleBrush)
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("ℳ𝒞𝒫 𝒮ℯ𝓇𝓋ℯ𝓇", "服务")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (running) "服务运行中 · 局域网的设备都能连" else "服务没开，工具盒现在连不上",
                    color = if (running) palette.accent else palette.textDim,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                ActionPill(
                    text = if (running) "停止服务" else "启动服务",
                    onClick = { if (running) stopServer() else startServer() },
                    modifier = Modifier.fillMaxWidth(),
                    danger = running
                )
                Spacer(Modifier.height(14.dp))
                AddressRow("本机", "http://127.0.0.1:$port/mcp") {
                    copyText(context, "http://127.0.0.1:$port/mcp")
                }
                val ips = lanIps
                if (ips.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    AddressRow("局域网", "读不到局域网 IP，先连上 WiFi 或热点")
                } else {
                    ips.forEach { ip ->
                        Spacer(Modifier.height(6.dp))
                        AddressRow("局域网", "http://$ip:$port/mcp") {
                            copyText(context, "http://$ip:$port/mcp")
                        }
                    }
                }
                if (!notifPermission) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "没给「通知」权限，通知栏不会显示服务通知（服务照样在跑）",
                        color = palette.textDim,
                        fontSize = 10.5.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    ActionPill(
                        text = "去给通知权限",
                        onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val startError = PerceptionApp.instance.mcpServer.lastError
                if (!running && !startError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("上次没起来：$startError", color = palette.textDim, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(10.dp))
                ActionPill(
                    text = if (selfTestRunning) "自测中…" else "自测（本机连自己试试）",
                    onClick = {
                        if (!selfTestRunning) {
                            selfTestRunning = true
                            selfTestResult = null
                            scope.launch {
                                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    selfTest(port)
                                }
                                selfTestResult = r
                                selfTestRunning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                selfTestResult?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = palette.textDim, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(12.dp))
                ThinDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { showLogs = true }.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("运行日志", color = palette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${logs.size} 条", color = palette.textDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("𝒯𝒽ℯ𝓂ℯ", "主题")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeSwatch("粉", accent == "pink", listOf(Color(0xFFFF9FB0), Color(0xFFD96E86))) { settings.setAccent("pink") }
                    ThemeSwatch("灰蓝", accent == "blue", listOf(Color(0xFFAFC2D8), Color(0xFF7C8FA6))) { settings.setAccent("blue") }
                    ThemeSwatch("灰", accent == "gray", listOf(Color(0xFFB9B9B9), Color(0xFF828282))) { settings.setAccent("gray") }
                }
                Spacer(Modifier.height(14.dp))
                ThinDivider()
                Spacer(Modifier.height(14.dp))
                SegRow(
                    options = listOf("暗色", "亮色"),
                    selected = if (dark) 0 else 1,
                    onSelect = { settings.setDark(it == 0) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("ℬ𝒶𝒸𝓀ℊ𝓇ℴ𝓊𝓃𝒹", "背景")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.chipBg.copy(alpha = 0.5f))
                ) {
                    val bmp = remember(bgPath) { loadBg(bgPath) }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("换一张壁纸", color = palette.text, fontSize = 14.sp)
                    Text(
                        if (bgPath.isBlank()) "现在用的是主题渐变" else "卡片会跟着糊成毛玻璃",
                        color = palette.textDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                TextButton(onClick = { pickLauncher.launch("image/*") }) {
                    Text("选图", color = MaterialTheme.colorScheme.primary)
                }
                if (bgPath.isNotBlank()) {
                    TextButton(onClick = { settings.setBgUri("") }) {
                        Text("清除", color = palette.textDim)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒲ℯ𝒶𝓉𝒽ℯ𝓇", "天气")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { editCity = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("默认城市", color = palette.text, fontSize = 14.sp)
                        Text(
                            if (weatherCity.isBlank()) "不填就用手机定位（要定位权限）" else weatherCity,
                            color = palette.textDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text(if (weatherCity.isBlank()) "去填" else "改", color = palette.accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒯ℴℴ𝓁𝓈", "工具盒")
        Spacer(Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth().clickable { showTools = true },
            shape = RoundedCornerShape(24.dp),
            showHighlight = false
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { showTools = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("工具盒", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("给别的 AI 调的本机工具", color = palette.textDim, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text("${Tools.all.size} 个", color = palette.accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒫ℯ𝓇𝓂𝒾𝓈𝓈𝒾ℴ𝓃", "权限")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PermissionRow("无障碍", accEnabled.value) { openAccessibility(context) }
                ThinDivider()
                PermissionRow("通知监听", notifEnabled.value) { openNotificationAccess(context) }
                ThinDivider()
                PermissionRow("勿扰权限（改勿扰用）", dndOk) { openDndAccess(context) }
                ThinDivider()
                PermissionRow("悬浮窗（开着服务更稳）", overlayOk) { openOverlayAccess(context) }
                Spacer(Modifier.height(4.dp))
                Text("· 应用时间线：系统设置 → 特殊应用权限 → 使用情况访问", color = palette.textDim, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒦ℯℯ𝓅 𝒜𝓁𝒾𝓋ℯ", "保活")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("开机自启动", color = palette.text, fontSize = 14.sp)
                        Text("重启后自动把服务和闹钟接回来", color = palette.textDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(
                        checked = bootStart,
                        onCheckedChange = { settings.setBootStart(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
                ThinDivider()
                JumpRow(
                    title = "忽略电池优化",
                    value = if (batteryOk) "已放行" else "去放行",
                    valueColor = if (batteryOk) palette.accent else palette.textDim,
                    onClick = { requestIgnoreBattery(context) }
                )
                ThinDivider()
                JumpRow(
                    title = "自启动 / 后台管理",
                    value = "各家手机不一样，去给个权限",
                    valueColor = palette.textDim,
                    onClick = { openAutoStartSettings(context) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "· 想让闹钟准点响、服务常驻，上面两个都放行最稳",
                    color = palette.textDim,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒜𝒷ℴ𝓊𝓉", "关于")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                JumpRow(
                    title = "使用说明",
                    value = "怎么用、权限干嘛的",
                    valueColor = palette.textDim,
                    onClick = { showUsage = true }
                )
                ThinDivider()
                JumpRow(
                    title = "使用声明",
                    value = "再读一遍",
                    valueColor = palette.textDim,
                    onClick = { showStatement = true }
                )
                ThinDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("版本", color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(versionName, color = palette.textDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒟𝒶𝓉𝒶", "数据")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { clearStep = 1 }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("清空全部数据", color = Color(0xFFFF8095), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("日程 · 闹钟 · 设置", color = palette.textDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    if (editCity) {
        TextInputDialog(
            title = "默认城市",
            initial = weatherCity,
            placeholder = "广州",
            onDismiss = { editCity = false },
            onSave = { settings.setWeatherCity(it); editCity = false }
        )
    }

    if (showUsage) {
        UsageDialog(onDismiss = { showUsage = false })
    }

    if (showStatement) {
        StatementDialog(onAgree = { showStatement = false }, onDismiss = { showStatement = false })
    }

    if (clearStep == 1) {
        ConfirmDialog(
            title = "清空全部数据",
            text = "日程、闹钟和所有设置都会被清掉，确定要开始吗？",
            confirmText = "继续",
            onConfirm = { clearStep = 2 },
            onDismiss = { clearStep = 0 }
        )
    }

    if (clearStep == 2) {
        ConfirmDialog(
            title = "真的要清空？",
            text = "删了就找不回来了，最后一次确认。",
            confirmText = "清空",
            onConfirm = {
                clearStep = 0
                scope.launch {
                    try {
                        val stop = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
                        context.startService(stop)
                    } catch (_: Exception) {
                    }
                    running = false
                    runCatching { com.yivi.perception.alarm.AlarmScheduler.cancelAll(context) }
                    PerceptionApp.instance.repository.clearAll()
                    File(context.filesDir, "wallpaper").listFiles()?.forEach { it.delete() }
                    settings.clearAll()
                }
            },
            onDismiss = { clearStep = 0 }
        )
    }

    if (showTools) {
        AlertDialog(
            onDismissRequest = { showTools = false },
            containerColor = palette.dialogTint,
            shape = RoundedCornerShape(22.dp),
            title = { Text("工具盒 · ${Tools.all.size} 个", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Tools.all.forEach { spec ->
                        Column(Modifier.padding(bottom = 12.dp)) {
                            Text(spec.name, color = palette.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Text(spec.desc, color = palette.textLight, fontSize = 11.sp, lineHeight = 16.sp)
                            if (spec.params.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                spec.params.forEach { p ->
                                    Text(
                                        "· ${p.name} (${p.type}${if (p.required) "，必填" else ""})：${p.desc}",
                                        color = palette.textDim,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTools = false }) { Text("关闭", color = MaterialTheme.colorScheme.primary) } }
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            containerColor = palette.dialogTint,
            shape = RoundedCornerShape(22.dp),
            title = { Text("运行日志", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (logs.isEmpty()) Text("暂无日志", color = palette.textLight, fontSize = 12.sp)
                    logs.reversed().forEach { Text(it, color = palette.textLight, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text("关闭", color = MaterialTheme.colorScheme.primary) } }
        )
    }
}

@Composable
private fun ThemeSwatch(label: String, active: Boolean, colors: List<Color>, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(colors))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (active) palette.text else palette.textDim, fontSize = 12.sp)
    }
}

@Composable
private fun JumpRow(title: String, value: String, valueColor: Color, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.sp)
        Spacer(Modifier.width(2.dp))
        Text("›", color = palette.textDim, fontSize = 15.sp)
    }
}

@Composable
private fun AddressRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textLight, fontSize = 12.sp, modifier = Modifier.width(46.dp))
        Text(value, color = palette.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (onCopy != null) {
            Text("复制", color = palette.textDim, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onCopy))
        }
    }
}

/** 本机连自己一次：能分清是"服务没起来"还是"外面连不进来" */
private fun selfTest(port: Int): String = try {
    fun call(body: String): Pair<Int, String> {
        val conn = java.net.URL("http://127.0.0.1:$port/mcp").openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 4000
        conn.readTimeout = 10000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json, text/event-stream")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    val init = call(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
            "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"selftest\",\"version\":\"1\"}}}"
    )
    if (init.first !in 200..299) {
        "没通：initialize 返回 HTTP ${init.first} · ${init.second.take(80)}"
    } else {
        val listed = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}")
        val text = listed.second
        val count = Regex("\"name\":").findAll(text).count()
        if (listed.first in 200..299 && text.contains("\"tools\"")) {
            "通了：initialize 200，tools/list 拿到 $count 个工具"
        } else {
            "initialize 通了，但 tools/list 不对劲：HTTP ${listed.first} · ${text.take(80)}"
        }
    }
} catch (e: Exception) {
    "没通：${e.message ?: e.javaClass.simpleName}（服务没起？或者端口被占）"
}

private fun copyText(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("mcp", text))
    } catch (_: Exception) {
    }
}

@Composable
private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    ) {
        Text(label, color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (enabled) palette.accent else palette.chipBg.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (enabled) "已开启" else "去开启",
            color = if (enabled) palette.accent else palette.textDim,
            fontSize = 12.sp
        )
        Spacer(Modifier.width(2.dp))
        Text("›", color = palette.textDim, fontSize = 15.sp)
    }
}

private fun copyWallpaper(context: Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "wallpaper")
    dir.mkdirs()
    val target = File(dir, "bg_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { ins ->
        target.outputStream().use { out -> ins.copyTo(out) }
    }
    // 旧的壁纸删掉，省地方
    dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
    if (target.exists() && target.length() > 0) target.absolutePath else null
} catch (e: Exception) {
    null
}

private fun loadBg(path: String) = try {
    if (path.isBlank()) null
    else {
        val f = File(path)
        if (!f.exists()) null
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 400) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (e: Exception) {
    null
}

private fun isIgnoringBattery(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}

/** 各家手机的"自启动管理"页面，能跳就跳，跳不过去就开应用详情页让用户自己找 */
private fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.activity.PowerSavingActivityList")
    )
    for (cn in candidates) {
        try {
            context.startActivity(
                Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

private fun appVersion(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "v${info.versionName}"
} catch (e: Exception) {
    "v1.0"
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return services.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

private fun isNotifEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun openAccessibility(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}

private fun isDndGranted(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
    return nm?.isNotificationPolicyAccessGranted ?: false
}

private fun canDrawOverlays(context: Context): Boolean =
    android.provider.Settings.canDrawOverlays(context)

private fun openOverlayAccess(context: Context) {
    try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

private fun openDndAccess(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

private fun openNotificationAccess(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}
