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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.theme.LocalPalette
import com.yivi.perception.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Nickname = "dawn"
private val Mood = "🌸"
private val timeFmt = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val df = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(PerceptionApp.instance.repository))) {
    val palette = LocalPalette.current
    val settings = PerceptionApp.instance.settings
    val events by vm.events.collectAsState()
    val category by vm.category.collectAsState()

    val annivText by settings.annivText.collectAsState()
    val annivType by settings.annivType.collectAsState()
    val annivDate by settings.annivDate.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var showAnnivEdit by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now().toEpochDay()) }

    val filtered = events.filter { sameDay(it.time, selectedDate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader(palette)
        MainCard(
            palette,
            category = category,
            selectedDate = selectedDate,
            onCategory = { vm.switchCategory(it) },
            annivText = annivText,
            annivType = annivType,
            annivDate = annivDate,
            onAnnivEdit = { showAnnivEdit = true },
            onSelectDate = { selectedDate = it }
        )
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accent.copy(alpha = 0.2f))
                    .clickable { showAdd = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = palette.accent, modifier = Modifier.size(20.dp))
            }
        }
        InfoList(palette, category = category, events = filtered, onDelete = { vm.delete(it) })
    }

    if (showAdd) {
        AddDialog(
            initialCategory = category,
            initialDate = selectedDate,
            onDismiss = { showAdd = false },
            onAdd = { event -> vm.add(event); showAdd = false }
        )
    }
    if (showAnnivEdit) {
        AnnivEditDialog(
            initialText = annivText,
            initialType = annivType,
            initialDate = annivDate,
            onDismiss = { showAnnivEdit = false },
            onSave = { t, ty, d -> settings.setAnnivText(t); settings.setAnnivType(ty); settings.setAnnivDate(d) }
        )
    }
}

@Composable
private fun ProfileHeader(palette: Palette) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(palette.cardViolet),
                contentAlignment = Alignment.Center
            ) {
                Text("🐰", fontSize = 30.sp)
            }
            Text(Nickname, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(14.dp))
        SignatureBubble(palette, text = SigHolder.text)
        Spacer(Modifier.weight(1f))
        MoodChip(palette, Mood)
    }
}

object SigHolder { var text: String = "happy" }

@Composable
private fun SignatureBubble(palette: Palette, text: String) {
    val width = (text.length * 22 + 40).coerceAtLeast(72)
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.cardViolet.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = palette.textPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun MoodChip(palette: Palette, emoji: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.accent.copy(alpha = 0.24f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(emoji, fontSize = 15.sp)
    }
}

@Composable
private fun MainCard(
    palette: Palette,
    category: String,
    selectedDate: Long,
    onCategory: (String) -> Unit,
    annivText: String,
    annivType: String,
    annivDate: Long,
    onAnnivEdit: () -> Unit,
    onSelectDate: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(palette.cardGradient))
            .padding(18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.width(150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnnivCard(palette, annivText, annivType, annivDate, onAnnivEdit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryToggle(palette, "行程", category == "行程") { onCategory("行程") }
                    CategoryToggle(palette, "闹钟", category == "闹钟") { onCategory("闹钟") }
                }
            }
            CalendarView(palette, selectedDate, onSelectDate, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnnivCard(palette: Palette, text: String, type: String, date: Long, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.cardBottom)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(if (date > 0) annivDays(type, date).toString() else "--", color = palette.accent, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (date > 0) (if (type == "倒数") "还有 · ${formatDate(date)}" else "在一起 · ${formatDate(date)}") else "点击设置", color = palette.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryToggle(palette: Palette, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (active) palette.accent else palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun CalendarView(palette: Palette, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var month by remember { mutableIntStateOf(LocalDate.now().monthValue) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
            Text("$year/${month.toString().padStart(2, '0')}", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), color = palette.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        MonthGrid(palette, year, month, selected, onSelect)
    }
}

@Composable
private fun MonthGrid(palette: Palette, year: Int, month: Int, selected: Long, onSelect: (Long) -> Unit) {
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val offset = firstDay.dayOfWeek.value % 7
    val today = LocalDate.now().toEpochDay()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var cell = 0
        while (cell < totalCells) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) {
                    val dayNum = cell - offset + 1
                    if (dayNum in 1..daysInMonth) {
                        val epoch = LocalDate.of(year, month, dayNum).toEpochDay()
                        val isToday = epoch == today
                        val isSelected = epoch == selected
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> palette.accent
                                        isToday -> palette.accent.copy(alpha = 0.25f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelect(epoch) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNum.toString(),
                                color = if (isSelected) palette.bgBottom else palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    } else {
                        Spacer(Modifier.size(34.dp))
                    }
                    cell++
                }
            }
        }
    }
}

@Composable
private fun InfoList(palette: Palette, category: String, events: List<EventEntity>, onDelete: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (events.isEmpty()) {
            Text("该日期暂无${category}", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            return@Column
        }
        events.forEach { e ->
            if (category == "行程") ScheduleCard(palette, e, onDelete) else AlarmCard(palette, e, onDelete)
        }
    }
}

@Composable
private fun ScheduleCard(palette: Palette, e: EventEntity, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBottom)
            .padding(14.dp)
            .clickable { onDelete(e.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(e.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (e.note.isNotBlank()) Text(e.note, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(formatTime(e.time), color = palette.textSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun AlarmCard(palette: Palette, e: EventEntity, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.cardBottom)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .clickable { onDelete(e.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(e.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (e.note.isNotBlank()) Text(e.note, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(formatTime(e.time), color = palette.accent, fontSize = 13.sp)
    }
}

private fun formatTime(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(timeFmt)

private fun formatDate(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalDate().format(df)

private fun sameDay(millis: Long, epochDay: Long): Boolean {
    if (millis <= 0) return false
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalDate().toEpochDay() == epochDay
}

private fun annivDays(type: String, millis: Long): Long {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return if (type == "倒数") {
        val diff = java.time.temporal.ChronoUnit.DAYS.between(today, d)
        if (diff < 0) 0 else diff
    } else {
        java.time.temporal.ChronoUnit.DAYS.between(d, today).let { if (it < 0) -it else it }
    }
}
