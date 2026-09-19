package com.yivi.perception.ui.common

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yivi.perception.ui.theme.LocalPalette
import java.time.LocalDate

/**
 * 小号日期选择面板：一排月份切换 + 7 列小格子。
 * Material3 自带那个面板占大半屏，这里只要 268dp 宽。
 */
@Composable
fun MiniDatePicker(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    var month by remember { mutableStateOf(initial.withDayOfMonth(1)) }
    var selected by remember { mutableStateOf(initial) }
    val today = remember { LocalDate.now() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(268.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.dialogTint)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", color = palette.text, fontSize = 20.sp,
                    modifier = Modifier.clickable { month = month.minusMonths(1) }.padding(horizontal = 6.dp)
                )
                Text(
                    "${month.year}年${month.monthValue}月",
                    color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Text(
                    "›", color = palette.text, fontSize = 20.sp,
                    modifier = Modifier.clickable { month = month.plusMonths(1) }.padding(horizontal = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        w, color = palette.textDim, fontSize = 10.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val offset = (month.withDayOfMonth(1).dayOfWeek.value + 6) % 7
            val days = month.lengthOfMonth()
            var day = 1 - offset
            while (day <= days) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val d = day + col
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            if (d in 1..days) {
                                val date = month.withDayOfMonth(d)
                                val isSelected = date == selected
                                val isToday = date == today
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { selected = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$d",
                                        color = when {
                                            isSelected -> palette.background
                                            isToday -> palette.accent
                                            else -> palette.text
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                day += 7
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "今天", color = palette.textDim, fontSize = 12.sp,
                    modifier = Modifier.clickable { selected = today; month = today.withDayOfMonth(1) }.padding(6.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "取消", color = palette.textDim, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "确定", color = palette.accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { onPick(selected) }.padding(6.dp)
                )
            }
        }
    }
}
