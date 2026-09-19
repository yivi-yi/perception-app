@file:OptIn(ExperimentalMaterial3Api::class)

package com.yivi.perception.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.theme.LocalPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** 添加日程 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit
) {
    val palette = LocalPalette.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var remind by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(22.dp),
        title = { Text("添加日程", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("做什么") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可空）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftPill(date.format(dateFmt)) { showDate = true }
                    SoftPill("%02d:%02d".format(hour, minute)) { showTime = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("到点提醒", color = palette.textLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = remind, onCheckedChange = { remind = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val millis = date.atTime(LocalTime.of(hour, minute))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(
                    EventEntity(
                        category = "行程",
                        title = title.ifBlank { "无标题" },
                        note = note,
                        time = millis,
                        remind = remind
                    )
                )
            }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textDim) } }
    )

    if (showDate) {
        MiniDatePicker(
            initial = date,
            onPick = { picked -> date = picked; showDate = false },
            onDismiss = { showDate = false }
        )
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = palette.dialogTint,
            shape = RoundedCornerShape(22.dp),
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour; minute = state.minute; showTime = false
                }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消", color = palette.textDim) } },
            text = { TimePicker(state = state) }
        )
    }
}

/** 添加闹钟：时间 + 标签 + 重复星期 */
@Composable
fun AlarmDialog(onDismiss: () -> Unit, onSave: (EventEntity) -> Unit) {
    val palette = LocalPalette.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(30) }
    var repeat by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var showTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(22.dp),
        title = { Text("添加闹钟", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%02d:%02d".format(hour, minute),
                        fontSize = 30.sp,
                        color = palette.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showTime = true }
                    )
                    Box(Modifier.weight(1f))
                    SoftPill("改时间") { showTime = true }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标签（可空）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可空）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每周重复", color = palette.textLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = repeat, onCheckedChange = { repeat = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
                if (repeat) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (d, l) ->
                            val on = days.contains(d)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else palette.chipBg.copy(alpha = 0.5f))
                                    .clickable { days = if (on) days - d else days + d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l, color = if (on) Color.White else palette.textLight, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var millis = LocalDate.now().atTime(LocalTime.of(hour, minute))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // 只响一次的闹钟，如果时间已经过了就顺延到明天，不然装上就不会响
                if (!repeat && millis <= System.currentTimeMillis()) millis += 24L * 60 * 60 * 1000
                onSave(
                    EventEntity(
                        category = "闹钟",
                        title = title.ifBlank { "闹钟" },
                        note = note,
                        time = millis,
                        remind = true,
                        repeatDays = if (repeat) days.sorted().joinToString(",") else ""
                    )
                )
            }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textDim) } }
    )

    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = palette.dialogTint,
            shape = RoundedCornerShape(22.dp),
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour; minute = state.minute; showTime = false
                }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消", color = palette.textDim) } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun SoftPill(text: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.chipBg.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, color = palette.text, fontSize = 13.sp)
    }
}
