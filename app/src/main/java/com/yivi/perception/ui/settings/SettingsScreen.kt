package com.yivi.perception.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yivi.perception.service.NetworkUtils
import com.yivi.perception.service.ServerService
import com.yivi.perception.ui.theme.AccentPink
import com.yivi.perception.ui.theme.CardBg
import com.yivi.perception.ui.theme.DeepBg
import com.yivi.perception.ui.theme.TextPrimary
import com.yivi.perception.ui.theme.TextSecondary

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    val notifPermission = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startServer() {
        if (!notifPermission && Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START }
        ContextCompat.startForegroundService(context, intent)
        running = true
    }

    fun stopServer() {
        val intent = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
        context.startService(intent)
        running = false
    }

    val ip = NetworkUtils.localIp()
    val addr = "http://$ip:${ServerService.PORT}/mcp"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card("MCP 服务器") {
            Text("在其他客户端（如 Claude）填这个地址就能调用工具：", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(addr, color = AccentPink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "运行中" else "未启动", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { if (running) stopServer() else startServer() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink, contentColor = Color.Black)
                ) {
                    Text(if (running) "停止" else "启动")
                }
            }
        }

        Card("权限") {
            Text("· 定位 / 传感器 / 存储：系统弹窗授权", color = TextSecondary, fontSize = 13.sp)
            Text("· 无障碍：设置 → 无障碍 → Perception", color = TextSecondary, fontSize = 13.sp)
            Text("· 通知监听：设置 → 通知使用权 → Perception", color = TextSecondary, fontSize = 13.sp)
            Text("· 应用时间线：设置 → 特殊应用权限 → 使用情况访问权限", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(18.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
