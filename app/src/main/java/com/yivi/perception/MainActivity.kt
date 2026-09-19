package com.yivi.perception

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yivi.perception.ui.AppBackground
import com.yivi.perception.ui.alarm.AlarmScreen
import com.yivi.perception.ui.calendar.CalendarScreen
import com.yivi.perception.ui.common.GlassCard
import com.yivi.perception.ui.settings.SettingsScreen
import com.yivi.perception.ui.theme.LocalPalette
import com.yivi.perception.ui.theme.PerceptionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerceptionTheme {
                AppBackground { MainScreen() }
            }
        }
    }
}

private enum class Tab(val label: String) {
    Calendar("日历"),
    Alarm("闹钟"),
    Settings("设置")
}

@Composable
private fun MainScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        when (Tab.entries[selected]) {
            Tab.Calendar -> CalendarScreen()
            Tab.Alarm -> AlarmScreen()
            Tab.Settings -> SettingsScreen()
        }
        GlassTabBar(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
        )
    }
}

/** 底部悬浮毛玻璃胶囊导航 */
@Composable
private fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tint = palette.surface.copy(alpha = 0.7f),
        showHighlight = false,
        grain = false
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab.entries.forEachIndexed { i, tab ->
                val active = i == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        tab.label,
                        fontSize = 13.sp,
                        color = if (active) Color.White else palette.textDim,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}
