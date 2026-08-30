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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.yivi.perception.ui.theme.LocalPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnivEditDialog(initialText: String, initialType: String, initialDate: Long, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(initialText) }
    var type by remember { mutableStateOf(initialType) }
    var dateMillis by remember { mutableStateOf(initialDate) }
    var showDate by remember { mutableStateOf(false) }
    val dateText = if (dateMillis > 0) Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dFmt) else "请选择日期"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBottom,
        shape = RoundedCornerShape(24.dp),
        title = { Text("设置倒数 / 纪念日", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("文案") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeChip("倒数", type == "倒数") { type = "倒数" }
                    TypeChip("纪念日", type == "纪念日") { type = "纪念日" }
                }

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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, type, dateMillis); onDismiss() }) { Text("保存", color = palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.textSecondary) } }
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (dateMillis > 0) dateMillis else null)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { dateMillis = it }; showDate = false }) { Text("确定", color = palette.accent) } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消", color = palette.textSecondary) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun TypeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (active) palette.textPrimary else palette.textSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
