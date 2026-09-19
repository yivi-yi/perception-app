package com.yivi.perception.data

/** 一个工具参数：名字、类型、说明、是否必填 */
data class ToolParam(
    val name: String,
    val type: String,
    val desc: String,
    val required: Boolean = false
)

/** 一个工具：名字、干嘛用的、参数 */
data class ToolSpec(
    val name: String,
    val desc: String,
    val params: List<ToolParam> = emptyList()
)

/**
 * 工具盒里所有工具的「说明书」。
 * MCP 服务下发的 tools/list 和设置页里给用户看的清单都从这一份生成，改一处两边都对。
 */
object Tools {

    private const val TIME_HINT = "毫秒时间戳（13 位数字，比如 1789000000000），不是 '07:30' 这种"
    private const val REPEAT_HINT = "重复星期，1=周一 … 7=周日，如 \"1,3,5\"；空字符串=只响一次"
    private const val ACTION_HINT = "update=改这一条；delete=删这一条"

    val all: List<ToolSpec> = listOf(
        // ── 日程 ──
        ToolSpec(
            "list_schedules",
            "查这台手机上所有日程，返回 id、标题、备注、时间、是否提醒。改或删之前先用它拿 id。"
        ),
        ToolSpec(
            "add_schedule",
            "加一条日程。开了提醒的话，到点会在这台手机上弹通知。",
            listOf(
                ToolParam("title", "string", "日程标题", true),
                ToolParam("note", "string", "备注，可空"),
                ToolParam("time", "integer", TIME_HINT),
                ToolParam("remind", "boolean", "true=到点弹通知提醒，不传按 false")
            )
        ),
        ToolSpec(
            "change_schedule",
            "改或删一条日程。只传要改的字段，没传的保持原样。",
            listOf(
                ToolParam("action", "string", ACTION_HINT, true),
                ToolParam("id", "integer", "日程 id，来自 list_schedules", true),
                ToolParam("title", "string", "新标题"),
                ToolParam("note", "string", "新备注"),
                ToolParam("time", "integer", TIME_HINT),
                ToolParam("remind", "boolean", "是否要提醒")
            )
        ),
        // ── 闹钟 ──
        ToolSpec(
            "list_alarms",
            "查这台手机上所有闹钟，返回 id、标签、备注、时间、重复星期、是否开着。"
        ),
        ToolSpec(
            "add_alarm",
            "加一个闹钟，到点会在这台手机上一直响（系统铃声+震动），响到有人点通知上的「暂停」或「关闭」为止。",
            listOf(
                ToolParam("title", "string", "闹钟标签，可空"),
                ToolParam("note", "string", "备注，可空"),
                ToolParam("time", "integer", "$TIME_HINT；如果是重复闹钟，只取里面的时分"),
                ToolParam("repeatDays", "string", REPEAT_HINT)
            )
        ),
        ToolSpec(
            "change_alarm",
            "改或删一个闹钟。只传要改的字段，没传的保持原样。",
            listOf(
                ToolParam("action", "string", ACTION_HINT, true),
                ToolParam("id", "integer", "闹钟 id，来自 list_alarms", true),
                ToolParam("title", "string", "新标签"),
                ToolParam("note", "string", "新备注"),
                ToolParam("time", "integer", TIME_HINT),
                ToolParam("repeatDays", "string", REPEAT_HINT)
            )
        ),
        // ── 设备 ──
        ToolSpec("device_info", "这台手机的牌子、型号、安卓版本。"),
        ToolSpec("battery", "电量百分比，以及现在是不是在充电。"),
        ToolSpec(
            "location",
            "这台手机现在在哪，返回经纬度和文字地址。会先试着拿实时定位（最多等 6 秒），拿不到就退回最近一次定位，并用 realtime / age_minutes 告诉你是不是实时的。"
        ),
        ToolSpec(
            "weather",
            "查天气（open-meteo，不用配 key，不通会自动切备用源）。默认只返回当前天气；要预报就把 source 传 forecast。",
            listOf(
                ToolParam("city", "string", "城市名，如「广州」，中文或拼音都行；不传就用设置里的默认城市，都没有才用手机当前位置（要定位权限）"),
                ToolParam("source", "string", "不传=只给当前天气；forecast=当前 + 未来三天")
            )
        ),
        ToolSpec("network", "当前连的 WiFi 名字、信号强度、本机 IP。安卓 10 以上读 WiFi 名需要定位权限。"),
        ToolSpec(
            "read_sensor",
            "读一次手机传感器的当前值。只读每台手机基本都有的那几路：光线、距离、动静、朝向、步数。" +
                "kind 可以给：light=光线（lux，还会给"很暗/偏暗/正常/很亮"）、proximity=距离（贴近/远离）、" +
                "motion=动静（在一小段时间里看抖动，判断放着没动/轻轻动/在晃）、direction=朝向（方位角 + 东南西北）、" +
                "steps=步数（开机以来的步数，要活动识别权限）；kind=list 看这台机器有哪几路；" +
                "不传或 all = 上面几路全读一遍。读不到的会说明原因（没那路传感器，或者它只在变化时才上报）。",
            listOf(
                ToolParam(
                    "kind", "string",
                    "light 光线 / proximity 距离 / motion 动静 / direction 朝向 / steps 步数 / list 看这台机器有哪些；不传 = 全部"
                )
            )
        ),
        ToolSpec(
            "open_app",
            "在这台手机上打开一个应用。",
            listOf(ToolParam("packageName", "string", "应用包名，如 com.tencent.mm；用 installed_apps 拿", true))
        ),
        ToolSpec("installed_apps", "列出这台手机上已安装、能启动的应用（名字 + 包名）。"),
        // ── 声音 ──
        ToolSpec(
            "sound_state",
            "看这台手机现在的铃声模式（响铃 / 震动 / 静音）、有没有开勿扰，以及媒体 / 铃声 / 通知 / 闹钟四路音量（0-100 的百分比）。改之前先看这个就知道现在多大。"
        ),
        ToolSpec(
            "set_sound",
            "改这台手机的铃声模式、音量或勿扰，改完会返回现在的状态。只传要改的那个就行。四路音量是：媒体 music、铃声 ring、通知 notification、闹钟 alarm。",
            listOf(
                ToolParam("mode", "string", "normal=响铃 / vibrate=震动 / silent=静音（改的是铃声模式）"),
                ToolParam("dnd", "boolean", "true=开勿扰，false=关勿扰（要手机先给「勿扰权限」，没给会提示去开）"),
                ToolParam("level", "integer", "音量百分比 0-100（不是原始档位），配合 stream 用"),
                ToolParam("stream", "string", "改哪一路音量：music 媒体（默认）/ ring 铃声 / notification 通知 / alarm 闹钟")
            )
        ),
        // ── 手机状态 ──
        ToolSpec("current_app", "这台手机现在前台是哪个应用，返回应用名和包名（要开无障碍权限）。"),
        ToolSpec(
            "read_notifications",
            "读这台手机的通知，两路互不覆盖，要开通知监听权限。",
            listOf(
                ToolParam("kind", "string", "current=只看通知栏现在挂着的 / recent=只看最近收到的；不传两个都返回"),
                ToolParam("limit", "integer", "每类最多几条，默认 20，最多 20")
            )
        ),
        ToolSpec("ambient", "录 3 秒环境音，估一个分贝值，判断安静还是吵、像不像有人在说话（要麦克风权限）。"),
        // ── 点歌 ──
        ToolSpec(
            "search_song",
            "搜歌，返回歌名、歌手、专辑和歌曲 id（id 交给 play_song）。走的是网易云公开搜索接口，不用配 key；只会搜到网易云的歌。",
            listOf(
                ToolParam("keyword", "string", "歌名，或者「歌名 歌手」", true),
                ToolParam("limit", "integer", "返回几条，默认 5，最多 10")
            )
        ),
        ToolSpec(
            "play_song",
            "用歌曲 id 在这台手机上唤起网易云播那首歌。注意：只对「网易云音乐」的歌曲 id 有效（别的平台不行），手机没装网易云会退回网页版；id 要先用 search_song 搜出来。",
            listOf(
                ToolParam("id", "integer", "网易云的歌曲 id，来自 search_song", true),
                ToolParam("name", "string", "歌名，可空，只用来回话时念一下")
            )
        )
    )
}
