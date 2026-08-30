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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.ui.theme.AccentPink
import com.yivi.perception.ui.theme.CardBg
import com.yivi.perception.ui.theme.CardViolet
import com.yivi.perception.ui.theme.DeepBg
import com.yivi.perception.ui.theme.SoftAmber
import com.yivi.perception.ui.theme.TextPrimary
import com.yivi.perception.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Sig = "happy"
private val Nickname = "dawn"
private val Mood = "🌸"
private val AnnivTitle = "和 dawn 在一起"
private val AnnivDate = LocalDate.of(2026, 6, 6)
private val timeFmt = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val df = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(PerceptionApp.instance.repository))) {
    val events by vm.events.collectAsState()
    val category by vm.category.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileHeader()
        MainCard(category = category, onCategory = { vm.switchCategory(it) })
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentPink.copy(alpha = 0.2f))
                    .clickable { showAdd = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = AccentPink, modifier = Modifier.size(20.dp))
            }
        }
        InfoList(category = category, events = events, onDelete = { vm.delete(it) })
    }

    if (showAdd) {
        AddDialog(
            initialCategory = category,
            onDismiss = { showAdd = false },
            onAdd = { event -> vm.add(event); showAdd = false }
        )
    }
}

@Composable
private fun ProfileHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(CardViolet),
                contentAlignment = Alignment.Center
            ) {
                Text("🐰", fontSize = 30.sp)
            }
            Text(Nickname, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(14.dp))
        SignatureBubble(text = Sig)
        Spacer(Modifier.weight(1f))
        MoodChip(Mood)
    }
}

@Composable
private fun SignatureBubble(text: String) {
    val charCount = text.length
    val width = (charCount * 22 + 40).coerceAtLeast(72)
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF332844).copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun MoodChip(emoji: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SoftAmber.copy(alpha = 0.24f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(emoji, fontSize = 15.sp)
    }
}

@Composable
private fun MainCard(category: String, onCategory: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF241C30).copy(alpha = 0.8f))
                .padding(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier.width(150.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(CardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(AnnivTitle, color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(daysBetween().toString(), color = AccentPink, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text("- ${AnnivDate.format(df)}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryToggle("行程", category == "行程") { onCategory("行程") }
                        CategoryToggle("闹钟", category == "闹钟") { onCategory("闹钟") }
                    }
                }
                CalendarView(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) AccentPink.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (active) AccentPink else TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun CalendarView(modifier: Modifier = Modifier) {
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableIntStateOf(LocalDate.now().monthValue) }
    var selected by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { if (month == 1) { month = 12; year-- } else month-- }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
            Text("$year/${month.padStart()}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = { if (month == 12) { month = 1; year++ } else month++ }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        MonthGrid(year, month, selected) { selected = it }
    }
}

@Composable
private fun MonthGrid(year: Int, month: Int, selected: Long, onSelect: (Long) -> Unit) {
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val offset = firstDay.dayOfWeek.value % 7
    val today = LocalDate.now().toEpochDay()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var cell = 0
        while (cell < totalCells) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) { col ->
                    val dayNum = cell - offset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = LocalDate.of(year, month, dayNum)
                        val epochP = date.toEpochDay()
                        val isToday = epochP == today
                        val isSelected = epochP == selected
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> AccentPink
                                        isToday -> AccentPink.copy(alpha = 0.25f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelect(epochP) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNum.toString(),
                                color = if (isSelected) DeepBg else TextPrimary,
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
private fun InfoList(category: String, events: List<EventEntity>, onDelete: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (events.isEmpty()) {
            Text("暂无${category}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            return@Column
        }
        events.forEach { e ->
            if (category == "行程") ScheduleCard(e, onDelete) else AlarmCard(e, onDelete)
        }
    }
}

@Composable
private fun ScheduleCard(e: EventEntity, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(14.dp)
            .clickable { onDelete(e.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(e.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (e.note.isNotBlank()) Text(e.note, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(formatTime(e.time), color = TextSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun AlarmCard(e: EventEntity, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .clickable { onDelete(e.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(e.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (e.note.isNotBlank()) Text(e.note, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(formatTime(e.time), color = AccentPink, fontSize = 13.sp)
    }
}

private fun formatTime(millis: Long): String =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(timeFmt)

private fun daysBetween(): Long = java.time.temporal.ChronoUnit.DAYS.between(AnnivDate, LocalDate.now()).let { if (it < 0) -it else it }

private fun Int.padStart(): String = toString().padStart(2, '0')
