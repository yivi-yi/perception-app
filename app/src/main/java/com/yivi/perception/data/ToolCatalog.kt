package com.yivi.perception.data

/** 工具盒里显示给用户看的清单，跟 McpEngine 里注册的工具一一对应 */
object ToolCatalog {
    val tools: List<Pair<String, String>> = listOf(
        "list_schedules" to "查所有日程（id / 标题 / 备注 / 时间 / 是否提醒）",
        "add_schedule" to "加一条日程，可带提醒",
        "update_schedule" to "改一条日程",
        "delete_schedule" to "删一条日程",
        "list_alarms" to "查所有闹钟",
        "add_alarm" to "加一个闹钟，到点响铃",
        "update_alarm" to "改一个闹钟",
        "delete_alarm" to "删一个闹钟",
        "device_info" to "设备品牌 / 型号 / 安卓版本",
        "battery" to "电量和是否充电",
        "storage" to "存储总容量 / 可用 / 已用",
        "location" to "最近一次定位（要定位权限）",
        "network" to "WiFi 名 / 信号 / 本机 IP",
        "sensors" to "设备上有哪些传感器",
        "open_app" to "按包名打开应用",
        "installed_apps" to "已安装可启动的应用（名称 + 包名）",
        "current_app" to "当前前台应用包名（要开无障碍）",
        "screen_text" to "屏幕上最近一次的文字，粗略（要开无障碍）",
        "read_notifications" to "最近收到的通知（要开通知监听）"
    )
}
