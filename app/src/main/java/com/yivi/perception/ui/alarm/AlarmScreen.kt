package com.yivi.perception.ui.alarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yivi.perception.PerceptionApp
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.common.ActionPill
import com.yivi.perception.ui.common.AlarmDialog
import com.yivi.perception.ui.common.ConfirmDialog
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.theme.LocalPalette
import com.yivi.perception.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hmFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("M月d日")
private val dayNames = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")

@Composable
fun AlarmScreen(
    vm: AlarmViewModel = viewModel(
        factory = AlarmViewModel.Factory(
            PerceptionApp.instance.repository,
            PerceptionApp.instance.applicationContext
        )
    )
) {
    val palette = LocalPalette.current
    val alarms by vm.alarms.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<EventEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<EventEntity?>(null) }

    val nextOne = alarms.filter { it.remind }
        .mapNotNull { alarm -> AlarmScheduler.nextTrigger(alarm)?.let { alarm to it } }
        .minByOrNull { it.second }

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
        BigClock(palette, nextOne?.second)

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
                AlarmCard(
                    palette = palette,
                    alarm = alarm,
                    onToggle = { vm.toggle(alarm) },
                    onPress = { editingAlarm = alarm },
                    onLongPress = { pendingDelete = alarm }
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text("点一下改，长按删除", fontSize = 11.sp, color = palette.textDim)
        }
        Spacer(Modifier.height(120.dp))
    }

    if (showAdd || editingAlarm != null) {
        val editing = editingAlarm
        AlarmDialog(
            editing = editing,
            onDismiss = { showAdd = false; editingAlarm = null },
            onSave = { e ->
                if (editing != null) vm.update(e) else vm.add(e)
                showAdd = false
                editingAlarm = null
            }
        )
    }

    pendingDelete?.let { e ->
        ConfirmDialog(
            title = "删除闹钟",
            text = "「${e.title}」删掉就没了，确定吗？",
            onConfirm = { vm.delete(e); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 顶部大时钟，一秒一跳 */
@Composable
private fun BigClock(palette: Palette, next: LocalDateTime?) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                now.format(hmFmt),
                fontSize = 54.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                style = MaterialTheme.typography.displayLarge.copy(brush = palette.titleBrush)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${now.format(dateFmt)} ${weekdayCn(now.dayOfWeek)}",
                fontSize = 12.sp,
                color = palette.textLight
            )
            if (next != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("下一个 · ${whenText(next)}", fontSize = 11.sp, color = palette.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    palette: Palette,
    alarm: EventEntity,
    onToggle: () -> Unit,
    onPress: () -> Unit,
    onLongPress: () -> Unit
) {
    val on = alarm.remind
    val next = if (on) AlarmScheduler.nextTrigger(alarm) else null

    GlassCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onPress, onLongClick = onLongPress),
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
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) palette.accent else palette.textDim
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        alarm.title.takeIf { it.isNotBlank() },
                        repeatText(alarm.repeatDays)
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = palette.textLight
                )
                if (alarm.note.isNotBlank()) {
                    Text(alarm.note, fontSize = 11.sp, color = palette.textDim, modifier = Modifier.padding(top = 2.dp))
                }
                if (next != null) {
                    Text("下次 ${whenText(next)}", fontSize = 10.5.sp, color = palette.accent.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
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
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(hmFmt)

private fun repeatText(days: String): String {
    if (days.isBlank()) return "仅一次"
    val list = days.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    if (list.size == 7) return "每天"
    if (list == listOf(1, 2, 3, 4, 5)) return "工作日"
    if (list == listOf(6, 7)) return "周末"
    return list.mapNotNull { dayNames[it] }.joinToString(" ") { "周$it" }
}

/** "今天 07:30 / 明天 / 周三 8月20日" 这种说法 */
private fun whenText(at: LocalDateTime): String {
    val today = LocalDate.now()
    val time = at.format(hmFmt)
    return when (at.toLocalDate()) {
        today -> "今天 $time"
        today.plusDays(1) -> "明天 $time"
        else -> "${weekdayCn(at.dayOfWeek)} ${at.format(dateFmt)} $time"
    }
}

private fun weekdayCn(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
