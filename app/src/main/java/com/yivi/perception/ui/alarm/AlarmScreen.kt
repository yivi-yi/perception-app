package com.yivi.perception.ui.alarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.common.ActionPill
import com.yivi.perception.ui.common.AlarmDialog
import com.yivi.perception.ui.common.ConfirmDialog
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.theme.LocalPalette
import com.yivi.perception.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val dayNames = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

@Composable
fun AlarmScreen(
    vm: AlarmViewModel = viewModel(factory = AlarmViewModel.Factory(PerceptionApp.instance.repository))
) {
    val palette = LocalPalette.current
    val alarms by vm.alarms.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EventEntity?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "𝒜𝓁𝒶𝓇𝓂",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                style = MaterialTheme.typography.titleMedium.copy(brush = palette.titleBrush)
            )
            Spacer(Modifier.weight(1f))
            Text("${alarms.count { it.remind }} 个开着", fontSize = 12.sp, color = palette.textDim)
        }

        Spacer(Modifier.height(14.dp))
        ActionPill("＋  添加闹钟", onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        if (alarms.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), showHighlight = false) {
                Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Text("还没有闹钟，点上面加一个", fontSize = 12.sp, color = palette.textDim)
                }
            }
        } else {
            alarms.forEach { alarm ->
                AlarmCard(palette, alarm, onToggle = { vm.toggle(alarm) }, onLongPress = { pendingDelete = alarm })
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("长按一条可以删除", fontSize = 11.sp, color = palette.textDim)
        }
        Spacer(Modifier.height(120.dp))
    }

    if (showAdd) {
        AlarmDialog(onDismiss = { showAdd = false }, onSave = { vm.add(it); showAdd = false })
    }

    pendingDelete?.let { e ->
        ConfirmDialog(
            title = "删除闹钟",
            text = "「${e.title}」删掉就没了，确定吗？",
            onConfirm = { vm.delete(e.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(palette: Palette, alarm: EventEntity, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val on = alarm.remind
    GlassCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = RoundedCornerShape(24.dp),
        showHighlight = false
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    timeOf(alarm.time),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) palette.accent else palette.textDim
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        alarm.title.takeIf { it.isNotBlank() },
                        repeatText(alarm.repeatDays)
                    ).joinToString(" · "),
                    fontSize = 11.sp,
                    color = palette.textLight
                )
                if (alarm.note.isNotBlank()) {
                    Text(alarm.note, fontSize = 11.sp, color = palette.textDim, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Switch(
                checked = on,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun timeOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(hhmm)

private fun repeatText(days: String): String {
    if (days.isBlank()) return "仅一次"
    val list = days.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    if (list.size == 7) return "每天"
    val weekdays = listOf(1, 2, 3, 4, 5)
    if (list == weekdays) return "工作日"
    if (list == listOf(6, 7)) return "周末"
    return list.mapNotNull { dayNames[it] }.joinToString(" ") { "周$it" }
}
