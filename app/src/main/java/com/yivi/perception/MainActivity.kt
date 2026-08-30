package com.yivi.perception

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yivi.perception.ui.AppBackground
import com.yivi.perception.ui.home.HomeScreen
import com.yivi.perception.ui.settings.SettingsScreen
import com.yivi.perception.ui.theme.LocalPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.yivi.perception.ui.theme.PerceptionTheme {
                AppBackground { MainScreen() }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Calendar("日历", Icons.Filled.CalendarMonth),
    Settings("设置", Icons.Filled.Settings)
}

@Composable
private fun MainScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        when (Tab.entries[selected]) {
            Tab.Calendar -> HomeScreen()
            Tab.Settings -> SettingsScreen()
        }
        BottomNav(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 20.dp)
        )
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth(0.42f)
            .height(58.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(palette.cardViolet.copy(alpha = 0.75f), palette.cardBottom.copy(alpha = 0.9f))
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEachIndexed { i, tab ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(
                        if (active) palette.accent.copy(alpha = 0.25f) else Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (active) palette.accent else palette.textSecondary,
                    modifier = Modifier.height(22.dp)
                )
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    color = if (active) palette.textPrimary else palette.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
