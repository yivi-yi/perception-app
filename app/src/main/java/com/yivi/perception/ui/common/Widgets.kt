package com.yivi.perception.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.yivi.perception.ui.theme.LocalPalette

/** 小标题：花体英文 + 中文，像 𝒯𝒽ℯ𝓂ℯ 主题 */
@Composable
fun SectionLabel(script: String, cn: String? = null, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier.padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(script, fontSize = 12.5.sp, letterSpacing = 1.sp, color = palette.textLight)
        if (!cn.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(cn, fontSize = 11.sp, color = palette.textDim)
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier.fillMaxWidth().height(1.dp).background(palette.chipBorder.copy(alpha = 0.45f)))
}

/** 胶囊小按钮，选中是实心主色 */
@Composable
fun PillButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primary else palette.surface.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) Color.White else palette.textDim,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 整行玻璃胶囊按钮 */
@Composable
fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val palette = LocalPalette.current
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        showHighlight = false
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 14.sp,
                color = if (danger) palette.danger else palette.accent
            )
        }
    }
}

/** 分段选择（暗/亮、正数/倒数这种） */
@Composable
fun SegRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.chipBg.copy(alpha = 0.38f))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (active) Color.White else palette.textDim,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/** 确认弹窗（删东西用） */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, color = palette.text, fontSize = 15.5.sp, fontWeight = FontWeight.Medium) },
        text = { Text(text, color = palette.textLight, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textDim) }
        }
    )
}

/** 单行文字输入弹窗（改名、签名这类） */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, color = palette.text, fontSize = 15.5.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder, color = palette.textDim, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textDim) }
        }
    )
}
