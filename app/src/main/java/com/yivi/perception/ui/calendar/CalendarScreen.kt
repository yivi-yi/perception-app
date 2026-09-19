@file:OptIn(ExperimentalMaterial3Api::class)

package com.yivi.perception.ui.calendar

import androidx.compose.material3.ExperimentalMaterial3Api

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.common.ActionPill
import com.yivi.perception.ui.common.ConfirmDialog
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.common.PillButton
import com.yivi.perception.ui.common.ScheduleDialog
import com.yivi.perception.ui.common.SectionLabel
import com.yivi.perception.ui.common.TextInputDialog
import com.yivi.perception.ui.theme.LocalPalette
import com.yivi.perception.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.M.d")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun CalendarScreen(
    vm: CalendarViewModel = viewModel(
        factory = CalendarViewModel.Factory(
            PerceptionApp.instance.repository,
            PerceptionApp.instance.applicationContext
        )
    )
) {
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings
    val context = LocalContext.current
    val events by vm.events.collectAsState()
    val annivText by settings.annivText.collectAsState()
    val annivType by settings.annivType.collectAsState()
    val annivDate by settings.annivDate.collectAsState()

    var selected by remember { mutableStateOf(LocalDate.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var showAnniv by remember { mutableStateOf(false) }
    var editingAnniv by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EventEntity?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val dayList = events.filter { sameDay(it.time, selected) }
    val marked = remember(events) { events.mapNotNull { millisToDate(it.time) }.toSet() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "𝒞𝒶𝓁ℯ𝓃𝒹𝒶𝓇",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                style = MaterialTheme.typography.titleMedium.copy(brush = palette.titleBrush)
            )
            Spacer(Modifier.weight(1f))
            PillButton("今天", selected == LocalDate.now()) { selected = LocalDate.now() }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.86f)) {
                    AnnivCard(palette, annivText, annivType, annivDate) { showAnniv = true }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1.14f)) {
                    MonthCalendar(palette, selected, marked) { selected = it }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ActionPill("＋  添加日程", onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(18.dp))
        SectionLabel("${selected.monthValue} 月 ${selected.dayOfMonth} 日 · ${weekdayCn(selected)}")
        Spacer(Modifier.height(10.dp))

        if (dayList.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), showHighlight = false) {
                Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    Text("这天还没有安排", fontSize = 12.sp, color = palette.textDim)
                }
            }
        } else {
            dayList.forEach { e ->
                ScheduleCard(palette, e) { pendingDelete = e }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(120.dp))
    }

    if (showAdd) {
        ScheduleDialog(
            initialDate = selected,
            onDismiss = { showAdd = false },
            onSave = { e ->
                vm.add(e)
                // 开了提醒但还没给通知权限，顺手要一下，不然到点静悄悄
                if (e.remind && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showAdd = false
            }
        )
    }

    if (showAnniv) {
        AnnivEditDialog(
            name = annivText,
            type = annivType,
            date = annivDate,
            onDismiss = { showAnniv = false },
            onEditName = { editingAnniv = true },
            onPickType = { settings.setAnnivType(it) },
            onSetDate = { settings.setAnnivDate(it) }
        )
    }

    if (editingAnniv) {
        TextInputDialog(
            title = "纪念日名字",
            initial = annivText,
            placeholder = "比如 我们在一起",
            onDismiss = { editingAnniv = false },
            onSave = { settings.setAnnivText(it.ifBlank { "纪念日" }); editingAnniv = false }
        )
    }

    pendingDelete?.let { e ->
        ConfirmDialog(
            title = "删除日程",
            text = "「${e.title}」删掉就没了，确定吗？",
            onConfirm = { vm.delete(e); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** 纪念日卡：名字 + 大数字 + 起始日期，点一下改 */
@Composable
private fun AnnivCard(palette: Palette, name: String, type: String, date: Long, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tint = palette.surface.copy(alpha = 0.28f),
        showHighlight = false
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                name.ifBlank { "纪念日" },
                fontSize = 11.sp,
                color = palette.textLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (date > 0) annivDays(type, date).toString() else "--",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                style = MaterialTheme.typography.displaySmall.copy(brush = palette.titleBrush)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (date > 0) (if (type == "倒数") "还有 · ${formatDate(date)}" else "自 ${formatDate(date)}")
                else "点一下设置",
                fontSize = 10.sp,
                color = palette.textDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 月历：翻月 + 有安排的日子点一个小点 */
@Composable
private fun MonthCalendar(
    palette: Palette,
    selected: LocalDate,
    marked: Set<LocalDate>,
    onPick: (LocalDate) -> Unit
) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var month by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    val today = LocalDate.now()
    val first = LocalDate.of(year, month, 1)
    val daysInMonth = first.lengthOfMonth()
    val leading = first.dayOfWeek.value % 7

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarNav("‹") {
                if (month == 1) { month = 12; year-- } else month--
            }
            Text(
                "$year/${month.toString().padStart(2, '0')}",
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                textAlign = TextAlign.Center
            )
            CalendarNav("›") {
                if (month == 12) { month = 1; year++ } else month++
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    color = palette.textDim.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(2.dp))

        val rows = (leading + daysInMonth + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..daysInMonth) {
                            val d = LocalDate.of(year, month, day)
                            val isToday = d == today
                            val isSel = d == selected
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSel -> MaterialTheme.colorScheme.primary
                                            isToday -> palette.accent.copy(alpha = 0.22f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onPick(d) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    fontSize = 11.5.sp,
                                    color = if (isSel) Color.White else palette.text,
                                    fontWeight = if (isSel || isToday) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                            if (marked.contains(d) && !isSel) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 1.dp)
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarNav(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 16.sp, color = palette.textDim)
    }
}

@Composable
private fun ScheduleCard(palette: Palette, e: EventEntity, onDelete: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDelete),
        shape = RoundedCornerShape(20.dp),
        showHighlight = false
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.accent.copy(alpha = 0.8f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontSize = 15.sp, color = palette.text, fontWeight = FontWeight.Medium)
                if (e.note.isNotBlank()) {
                    Text(e.note, fontSize = 11.sp, color = palette.textLight, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(formatTime(e.time), fontSize = 12.sp, color = palette.accent)
        }
    }
}

/** 纪念日编辑：改名字 / 选日期 / 正数倒数 */
@Composable
private fun AnnivEditDialog(
    name: String,
    type: String,
    date: Long,
    onDismiss: () -> Unit,
    onEditName: () -> Unit,
    onPickType: (String) -> Unit,
    onSetDate: (Long) -> Unit
) {
    val palette = LocalPalette.current
    var showDate by remember { mutableStateOf(false) }
    val current = if (date > 0) millisToDate(date) ?: LocalDate.now() else LocalDate.now()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(22.dp),
        title = { Text("纪念日", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "纪念日" }, color = palette.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    PillButton("改名字", false, onClick = onEditName)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (date > 0) current.format(dateFmt) else "还没选日期",
                        color = palette.textLight,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    PillButton("选日期", false) { showDate = true }
                }
                com.yivi.perception.ui.common.SegRow(
                    options = listOf("正数", "倒数"),
                    selected = if (type == "倒数") 1 else 0,
                    onSelect = { onPickType(if (it == 1) "倒数" else "正数") }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("好", color = MaterialTheme.colorScheme.primary)
            }
        }
    )

    if (showDate) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { onSetDate(it) }
                    showDate = false
                }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDate = false }) {
                    Text("取消", color = palette.textDim)
                }
            }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

// ── 小工具函数 ──────────────────────────────

private fun millisToDate(millis: Long): LocalDate? {
    if (millis <= 0L) return null
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun sameDay(millis: Long, date: LocalDate): Boolean = millisToDate(millis) == date

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)

private fun formatDate(millis: Long): String =
    (millisToDate(millis) ?: LocalDate.now()).format(dateFmt)

private fun annivDays(type: String, millis: Long): Long {
    val start = millisToDate(millis) ?: return 0
    val today = LocalDate.now()
    return if (type == "倒数") {
        ChronoUnit.DAYS.between(today, start).coerceAtLeast(0)
    } else {
        kotlin.math.abs(ChronoUnit.DAYS.between(start, today))
    }
}

private fun weekdayCn(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
