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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.ToolCatalog
import com.yivi.perception.service.NetworkUtils
import com.yivi.perception.service.ServerService
import com.yivi.perception.ui.common.ActionPill
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.common.SectionLabel
import com.yivi.perception.ui.common.SegRow
import com.yivi.perception.ui.common.StatementDialog
import com.yivi.perception.ui.common.TextInputDialog
import com.yivi.perception.ui.common.UsageDialog
import com.yivi.perception.ui.common.ThinDivider
import com.yivi.perception.ui.theme.LocalPalette
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings

    val accent by settings.accent.collectAsState()
    val dark by settings.dark.collectAsState()
    val bgPath by settings.bgUri.collectAsState()
    val logs by settings.logs.collectAsState()
    val annivText by settings.annivText.collectAsState()
    val annivType by settings.annivType.collectAsState()
    val annivDate by settings.annivDate.collectAsState()

    var running by remember { mutableStateOf(PerceptionApp.instance.mcpServer.isRunning) }
    var showTools by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var pickAnnivDate by remember { mutableStateOf(false) }
    var clearStep by remember { mutableStateOf(0) }
    var editNcm by remember { mutableStateOf(false) }
    val ncmBase by settings.ncmBase.collectAsState()
    var showUsage by remember { mutableStateOf(false) }
    var showStatement by remember { mutableStateOf(false) }
    val bootStart by settings.bootStart.collectAsState()
    val scope = rememberCoroutineScope()
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }
    val versionName = remember { appVersion(context) }

    val notifPermission = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val multiPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyWallpaper(context, uri)?.let { settings.setBgUri(it) }
    }

    fun startServer() {
        // 服务要用到的权限顺手一起要：通知（服务常驻）+ 定位（工具盒定位/WiFi 名）+ 活动识别（步数）
        val ask = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ask.isNotEmpty()) multiPermLauncher.launch(ask.toTypedArray())
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

    val lifecycleOwner = LocalLifecycleOwner.current
    val accEnabled = remember { mutableStateOf(isAccessibilityEnabled(context)) }
    val notifEnabled = remember { mutableStateOf(isNotifEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                accEnabled.value = isAccessibilityEnabled(context)
                notifEnabled.value = isNotifEnabled(context)
                batteryOk = isIgnoringBattery(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val lanIp = NetworkUtils.localIp()
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

        Spacer(Modifier.height(16.dp))
        SectionLabel("𝒯𝒽ℯ𝓂ℯ")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeSwatch("粉色", accent != "gray", listOf(Color(0xFFFF9FB0), Color(0xFFE0708A))) { settings.setAccent("pink") }
                    ThemeSwatch("灰色", accent == "gray", listOf(Color(0xFFBFC7D4), Color(0xFF8E96A6))) { settings.setAccent("gray") }
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
        SectionLabel("ℬ𝒶𝒸𝓀ℊ𝓇ℴ𝓊𝓃𝒹")
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
        SectionLabel("𝒜𝓃𝓃𝒾𝓋ℯ𝓇𝓈𝒶𝓇𝓎")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("名字", color = palette.textLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        annivText.ifBlank { "纪念日" },
                        color = palette.text,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { editName = true }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日期", color = palette.textLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (annivDate > 0) {
                            Instant.ofEpochMilli(annivDate).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
                        } else "未设置",
                        color = palette.text,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { pickAnnivDate = true }
                    )
                }
                Spacer(Modifier.height(14.dp))
                SegRow(
                    options = listOf("正数", "倒数"),
                    selected = if (annivType == "倒数") 1 else 0,
                    onSelect = { settings.setAnnivType(if (it == 1) "倒数" else "正数") }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒯ℴℴ𝓁𝓈")
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
                    Text("${ToolCatalog.tools.size} 个", color = palette.accent, fontSize = 12.sp)
                }
                ThinDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { editNcm = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("网易云 API", color = palette.text, fontSize = 14.sp)
                        Text(
                            if (ncmBase.isBlank()) "不填就只有「跳网易云点歌」这个工具用不了"
                            else ncmBase,
                            color = palette.textDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text(if (ncmBase.isBlank()) "去填" else "改", color = palette.accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒫ℯ𝓇𝓂𝒾𝓈𝓈𝒾ℴ𝓃")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), showHighlight = false) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PermissionRow("无障碍", accEnabled.value) { openAccessibility(context) }
                ThinDivider()
                PermissionRow("通知监听", notifEnabled.value) { openNotificationAccess(context) }
                Spacer(Modifier.height(4.dp))
                Text("· 应用时间线：系统设置 → 特殊应用权限 → 使用情况访问", color = palette.textDim, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("ℳ𝒞𝒫 𝒮ℯ𝓇𝓋ℯ𝓇")
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
                AddressRow("本机", "http://127.0.0.1:$port/mcp")
                Spacer(Modifier.height(6.dp))
                AddressRow("局域网", "http://$lanIp:$port/mcp")
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

        Spacer(Modifier.height(18.dp))
        SectionLabel("𝒦ℯℯ𝓅 𝒜𝓁𝒾𝓋ℯ")
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
        SectionLabel("𝒜𝒷ℴ𝓊𝓉")
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
        SectionLabel("𝒟𝒶𝓉𝒶")
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

    if (editNcm) {
        TextInputDialog(
            title = "网易云 API 地址",
            initial = ncmBase,
            placeholder = "http://192.168.1.251:3000",
            onDismiss = { editNcm = false },
            onSave = { settings.setNcmBase(it.trim()); editNcm = false }
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
                    PerceptionApp.instance.repository.clearAll()
                    File(context.filesDir, "wallpaper").listFiles()?.forEach { it.delete() }
                    settings.clearAll()
                }
            },
            onDismiss = { clearStep = 0 }
        )
    }

    if (editName) {
        TextInputDialog(
            title = "纪念日名字",
            initial = annivText,
            placeholder = "比如 我们在一起",
            onDismiss = { editName = false },
            onSave = { settings.setAnnivText(it.ifBlank { "纪念日" }); editName = false }
        )
    }

    if (pickAnnivDate) {
        val initial = if (annivDate > 0) annivDate else System.currentTimeMillis()
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickAnnivDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { settings.setAnnivDate(it) }
                    pickAnnivDate = false
                }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pickAnnivDate = false }) { Text("取消", color = palette.textDim) }
            }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }

    if (showTools) {
        AlertDialog(
            onDismissRequest = { showTools = false },
            containerColor = palette.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("工具盒 · ${ToolCatalog.tools.size} 个", color = palette.text, fontWeight = FontWeight.Medium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ToolCatalog.tools.forEach { (name, desc) ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(name, color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(desc, color = palette.textLight, fontSize = 11.sp)
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
            containerColor = palette.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("运行日志", color = palette.text, fontWeight = FontWeight.Medium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
private fun AddressRow(label: String, value: String) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.textLight, fontSize = 12.sp, modifier = Modifier.width(46.dp))
        Text(value, color = palette.accent, fontSize = 12.sp)
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

private fun openNotificationAccess(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}
