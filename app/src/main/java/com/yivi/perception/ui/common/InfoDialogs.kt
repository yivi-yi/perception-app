package com.yivi.perception.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yivi.perception.ui.theme.LocalPalette

/**
 * 声明。首次打开应用会弹一次（不能点外面关掉），同意后就不再弹。
 * 设置页里也能再看一遍，那时候带"关闭"按钮。
 */
@Composable
fun StatementDialog(onAgree: () -> Unit, onDismiss: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = { if (onDismiss != null) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null
        ),
        containerColor = palette.surface,
        shape = RoundedCornerShape(26.dp),
        title = { Text("使用声明", color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(STATEMENT, color = palette.textLight, fontSize = 12.5.sp, lineHeight = 20.sp)
            }
        },
        confirmButton = {
            if (onDismiss == null) {
                TextButton(onClick = onAgree) { Text("我同意", color = MaterialTheme.colorScheme.primary) }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭", color = MaterialTheme.colorScheme.primary) }
            }
        }
    )
}

/** 使用说明：怎么用、权限干嘛的、工具盒是什么 */
@Composable
fun UsageDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = RoundedCornerShape(26.dp),
        title = { Text("使用说明", color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                UsageSection("日历", "点日期看那天的事，点数字右边的月历翻月；有安排的日子底下有个小点。点「＋ 添加日程」写一条，要提醒就把开关打开，到点会弹通知。")
                UsageSection("纪念日", "左边那张卡点一下就能改名字、选日期、切正数还是倒数。")
                UsageSection("闹钟", "上面是大时钟，下面是闹钟条。到点会一直响到你点通知上的「暂停」或「关闭」；点「暂停」是 5 分钟后再响一次，「关闭」就停。重复可以选每天 / 工作日 / 周末 / 自己挑星期。长按一条可以删。")
                UsageSection("闹钟要准点响，做两件事", "设置 → 保活：把「忽略电池优化」放行，再去「自启动 / 后台管理」里给这个应用开自启动。国产手机不做这两步，系统会在后台把闹钟掐掉。")
                UsageSection("工具盒 / MCP 服务", "工具盒是给别的 AI 用的：在设置里启动服务，会给你一个 http://手机IP:9001/mcp 的地址，填到支持 MCP 的客户端里，它就能查你的日程、闹钟、电量、定位这些。服务默认关着，不用就别开。")
                UsageSection("换背景", "设置 → 背景 → 选图，选完卡片会跟着糊成毛玻璃。壁纸存在应用自己目录里，卸载就没了。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = MaterialTheme.colorScheme.primary) }
        }
    )
}

@Composable
private fun UsageSection(title: String, body: String) {
    val palette = LocalPalette.current
    Text(title, color = palette.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(3.dp))
    Text(body, color = palette.textLight, fontSize = 12.5.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(14.dp))
}

private val STATEMENT = """
一、这是什么
这是一个个人做的小应用，免费分享，不联网上传你的东西。日历、闹钟、纪念日这些都只存在你自己手机里，卸载就没了，重要的日子建议自己另外记一份。

二、关于权限
应用会要一些系统权限，都是为了对应功能，不给也不影响别的：
· 通知：闹钟和日程到点要提醒你
· 精确闹钟：闹钟才能准点响（不给就只能大概时间响）
· 电池优化白名单：不然系统会把闹钟和后台服务掐掉
· 无障碍：工具盒里的「点屏幕 / 输入 / 滑动 / 打开应用」要用
· 通知监听：工具盒里的「读通知」要用
· 定位：工具盒里的「当前位置」和天气要用
不想用工具盒的话，这些权限全都可以不开。

三、关于 MCP 服务
服务开在你手机的局域网里，同一个 WiFi 下的设备理论上都能连（默认是关着的，开了才生效）。在公共 WiFi 下建议别开，或者开完记得关。

四、风险说明
这个应用不是在应用商店发布的，自己装 APK 出问题自己负责；应用里所有数据都在本机，作者也拿不到、不承担数据丢失的责任。工具盒里那些操作手机的能力，请自己想清楚再用。

五、其他
用着有问题、有想法，在自己拿到的群里说，我尽量改。
"""
