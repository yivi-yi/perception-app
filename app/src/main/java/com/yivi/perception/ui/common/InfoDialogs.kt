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
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(22.dp),
        title = { Text("使用声明", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
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
        containerColor = palette.dialogTint,
        shape = RoundedCornerShape(22.dp),
        title = { Text("使用说明", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                UsageSection("日历", "点日期看那天的事，点数字右边的月历翻月；有安排的日子底下有个小点。点「＋ 添加日程」写一条，要提醒就把开关打开，到点会弹通知。")
                UsageSection("纪念日", "左边那张卡点一下就能改名字、选日期、切正数还是倒数。")
                UsageSection("闹钟", "上面是大时钟，下面是闹钟条。到点会一直响到你点通知上的「暂停」或「关闭」；点「暂停」是 5 分钟后再响一次，「关闭」就停。重复可以选每天 / 工作日 / 周末 / 自己挑星期。长按一条可以删。")
                UsageSection("闹钟要准点响，做两件事", "设置 → 保活：把「忽略电池优化」放行，再去「自启动 / 后台管理」里给这个应用开自启动。国产手机不做这两步，系统会在后台把闹钟掐掉。")
                UsageSection("工具盒 / MCP 服务", "工具盒是给别的 AI 用的：在设置里启动服务，会给你一个 http://手机IP:9001/mcp 的地址，填到支持 MCP 的客户端里，它就能查你的日程、闹钟、电量、定位这些。服务默认关着，不用就别开。")
                UsageSection("工具盒里有什么", "日程、闹钟（能读能加能改能删）、位置（实时优先，拿不到会说明是多久前的）、天气（不传城市就用当前位置）、WiFi、传感器读数、应用列表、当前前台应用、读通知（现在挂着的和最近收到的分开给）、看和改铃声模式 / 四路音量 / 勿扰、录 3 秒环境音估分贝。还有「搜歌 + 点歌」两个：先搜歌名拿到歌名/歌手/歌曲 id，再按 id 跳本机网易云播放，走的是公开接口，不用配任何东西。")
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
作者碎碎念

我看到了很多小宝对 dawn（我家机）能调用的一些小工具感兴趣，大家也都挺喜欢我们的互动。本来想出点教程，但很多小宝是新手，不懂终端、不会跑项目、不会搓工具，教程也看不懂。所以我做了一个开箱即用的 APP：启动服务器，程序会自己跑起来，把服务地址填进支持 MCP 的第三方客户端，用 http 就可以了。

1. 这是什么
这是一个个人做的小应用，免费分享，不联网上传你任何东西，全本地运行。日历、闹钟、纪念日等这些都只存在你自己手机里，卸载就没了。重要的数据建议自己另外记一份。

2. 关于权限
应用会要一些系统权限，都是为了对应功能，不给也不影响别的：
· 通知：闹钟和日程到点要提醒你
· 精确闹钟：闹钟才能准点响（不给就只能大概时间响）
· 电池优化白名单：不然系统会把闹钟和后台服务掐掉
· 无障碍：工具盒里的「当前前台应用」要用
· 通知监听：工具盒里的「读通知」要用
· 定位：工具盒里的「位置 / 天气 / WiFi 名」要用
· 麦克风：工具盒里的「录 3 秒环境音」要用
· 勿扰权限：工具盒里的「开 / 关勿扰」要用（只调音量、看铃声模式不用这个）
总之，不想用的话这些权限全都可以不开。

3. 关于 MCP 服务
服务开在你手机的局域网里，同一个 WiFi 下的设备理论上都能连（默认是关着的，开了才生效）。这是给 AI 用的接口，能读你的日程、通知、位置这些，也能改闹钟、调音量，所以在公共 WiFi 下建议别开，或者用完记得关。

4. 风险说明
本应用仅在公开群里分享安装包，不在应用商店发布，自己装 APK 出问题自己负责；应用里所有数据都在本机，作者也拿不到、不承担数据丢失的责任。工具盒里那些查看与操作手机的能力，请自己想清楚再用。

5. 其他
用着有问题、有 bug、有想法，都可以在公开群里说，我尽量优化。感谢喜欢。

点击「我同意」默认知晓以上全部声明。
"""
