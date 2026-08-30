package com.yivi.perception.data

object ToolCatalog {
    val tools: List<Pair<String, String>> = listOf(
        "list_schedules" to "查询所有行程",
        "add_schedule" to "添加一条行程",
        "update_schedule" to "修改行程",
        "delete_schedule" to "删除行程",
        "list_alarms" to "查询所有闹钟",
        "add_alarm" to "添加闹钟",
        "delete_alarm" to "删除闹钟",
        "device_info" to "获取设备品牌/型号/安卓版本",
        "battery" to "获取电量与是否充电",
        "storage" to "获取存储占用",
        "location" to "获取最近一次定位经纬度",
        "network" to "获取wifi名称/ip",
        "sensors" to "列出设备传感器",
        "open_app" to "打开指定app",
        "installed_apps" to "列出已安装可启动的app"
    )
}
