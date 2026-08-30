package com.yivi.perception.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val dayFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDialog(initialCategory: String, initialDate: Long, onDismiss: () -> Unit, onAdd: (EventEntity) -> Unit) {
    val palette = LocalPalette.current
    var cat by remember { mutableStateOf(initialCategory) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("5") }
    var repeat by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var dateMillis by remember { mutableStateOf(LocalDate.ofEpochDay(initialDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }

    val timeMillis = remember(dateMillis, hour, minute) {
        val zdt = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault())
        zdt.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBottom,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TabBtn("行程", cat == "行程") { cat = "行程" }
                TabBtn("闹钟", cat == "闹钟") { cat = "闹钟" }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                DateTimeSelect(dateMillis, hour, minute,
                    onChangeDate = { dateMillis = it },
                    onChangeTime = { h, m -> hour = h; minute = m })

                if (cat == "行程") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒（状态栏弹窗）", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = remind, onCheckedChange = { remind = it }, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("闹钟时长（分钟）", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, singleLine = true, modifier = Modifier.width(70.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每周重复", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = repeat, onCheckedChange = { repeat = it }, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent))
                    }
                    if (repeat) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (d, l) ->
                                val on = days.contains(d)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (on) palette.accent else Color.Transparent)
                                        .clickable { days = if (on) days - d else days + d },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(l, color = if (on) palette.bgBottom else palette.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (cat == "行程") {
                    onAdd(EventEntity(category = "行程", title = title.ifBlank { "无标题" }, note = note, time = timeMillis, remind = remind))
                } else {
                    onAdd(EventEntity(category = "闹钟", title = title.ifBlank { "无标题" }, note = note, time = timeMillis, repeatDays = if (repeat) days.sorted().joinToString(",") else ""))
                }
                onDismiss()
            }) { Text("保存", color = palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) } }
    )
}

@Composable
private fun TabBtn(label: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (active) palette.accent else palette.textSecondary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeSelect(dateMillis: Long, hour: Int, minute: Int, onChangeDate: (Long) -> Unit, onChangeTime: (Int, Int) -> Unit) {
    val palette = LocalPalette.current
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateText = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardViolet.copy(alpha = 0.5f))
                .clickable { showDate = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text("📅 $dateText", color = palette.textPrimary, fontSize = 13.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardViolet.copy(alpha = 0.5f))
                .clickable { showTime = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text("⏰ %02d:%02d".format(hour, minute), color = palette.textPrimary, fontSize = 13.sp)
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChangeDate(it) }; showDate = false }) { Text("确定", color = palette.accent) } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消", color = palette.textSecondary) } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = palette.cardBottom,
            confirmButton = { TextButton(onClick = { onChangeTime(state.hour, state.minute); showTime = false }) { Text("确定", color = palette.accent) } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消", color = palette.textSecondary) } },
            text = { TimePicker(state = state) }
        )
    }
}
