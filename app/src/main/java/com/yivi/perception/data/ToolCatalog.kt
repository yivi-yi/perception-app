package com.yivi.perception.data

/** 工具盒里显示给用户看的清单，跟 McpEngine 里注册的工具一一对应 */
object ToolCatalog {
    val tools: List<Pair<String, String>> = listOf(
        "list_schedules" to "查所有日程",
        "add_schedule" to "加一条日程，可带提醒",
        "change_schedule" to "改 / 删一条日程",
        "list_alarms" to "查所有闹钟",
        "add_alarm" to "加一个闹钟，到点一直响",
        "change_alarm" to "改 / 删一个闹钟",
        "device_info" to "设备牌子 / 型号 / 安卓版本",
        "battery" to "电量和是否充电",
        "location" to "现在在哪（实时优先，拿不到说清多久前）",
        "weather" to "查天气（可传城市，不传用当前位置）",
        "network" to "WiFi 名 / 信号 / 本机 IP",
        "sensors" to "设备上有哪些传感器",
        "read_sensor" to "读一次传感器：光 / 距离 / 动静 / 朝向 / 步数",
        "open_app" to "按包名打开应用",
        "installed_apps" to "已安装可启动的应用（名字 + 包名）",
        "current_app" to "当前前台应用（名字 + 包名，要无障碍）",
        "read_screen" to "读当前屏幕上的文字（要无障碍）",
        "read_notifications" to "读通知栏的通知（要通知监听）",
        "ambient" to "录 3 秒环境音，估分贝（要麦克风）",
        "play_song" to "跳本机网易云打开这首歌"
    )
}
