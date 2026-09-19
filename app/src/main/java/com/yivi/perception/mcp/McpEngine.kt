package com.yivi.perception.mcp

import com.yivi.perception.NativeToolkit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class McpEngine(
    private val toolkit: NativeToolkit,
    private val settings: com.yivi.perception.data.SettingsRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 工具参数：名字、类型、说明、是否必填 */
    private data class P(val name: String, val type: String, val desc: String, val required: Boolean = false)

    private fun str(name: String, desc: String) = P(name, "string", desc)
    private fun num(name: String, desc: String) = P(name, "integer", desc)
    private fun bool(name: String, desc: String) = P(name, "boolean", desc)
    private fun reqStr(name: String, desc: String) = P(name, "string", desc, true)
    private fun reqNum(name: String, desc: String) = P(name, "integer", desc, true)

    private val timeHint = "毫秒时间戳（13 位数字，比如 1789000000000），不是 '07:30' 这种字符串"
    private val repeatHint = "重复星期，1=周一 … 7=周日，如 \"1,3,5\"；空字符串=只响一次"
    private val actionHint = "update=改这一条；delete=删这一条"

    private fun toolList(): List<JsonObject> = listOf(
        // 日程
        tool("list_schedules", "查这台手机上所有日程，返回 id、标题、备注、时间（毫秒时间戳）、是否提醒。改或删之前先用它拿 id。"),
        tool(
            "add_schedule", "加一条日程。开了提醒的话，到点会在这台手机上弹通知。",
            reqStr("title", "日程标题"),
            str("note", "备注，可空"),
            num("time", timeHint),
            bool("remind", "true=到点弹通知提醒，不传按 false")
        ),
        tool(
            "change_schedule", "改或删一条日程。只传要改的字段，没传的保持原样。",
            reqStr("action", actionHint),
            reqNum("id", "日程 id，来自 list_schedules"),
            str("title", "新标题"),
            str("note", "新备注"),
            num("time", timeHint),
            bool("remind", "是否要提醒")
        ),
        // 闹钟
        tool("list_alarms", "查这台手机上所有闹钟，返回 id、标签、备注、时间、重复星期、是否开着。"),
        tool(
            "add_alarm", "加一个闹钟，到点会在这台手机上一直响（系统铃声+震动），响到有人点通知上的「暂停」或「关闭」为止。",
            str("title", "闹钟标签，可空"),
            str("note", "备注，可空"),
            num("time", "$timeHint；如果是重复闹钟，只取里面的时分"),
            str("repeatDays", repeatHint)
        ),
        tool(
            "change_alarm", "改或删一个闹钟。只传要改的字段，没传的保持原样。",
            reqStr("action", actionHint),
            reqNum("id", "闹钟 id，来自 list_alarms"),
            str("title", "新标签"),
            str("note", "新备注"),
            num("time", timeHint),
            str("repeatDays", repeatHint)
        ),
        // 设备
        tool("device_info", "这台手机的牌子、型号、安卓版本。"),
        tool("battery", "电量百分比，以及现在是不是在充电。"),
        tool("location", "这台手机现在在哪，返回经纬度和文字地址。会先试着拿实时定位（最多等 6 秒），拿不到就退回最近一次定位，并用 realtime / age_minutes 告诉你是不是实时的。"),
        tool(
            "weather", "查天气，来自 open-meteo（不用自己配 key）。",
            str("city", "城市名，如「广州」；不传就用手机当前位置（要定位权限）")
        ),
        tool("network", "当前连的 WiFi 名字、信号强度、本机 IP。安卓 10 以上读 WiFi 名需要定位权限。"),
        tool("sensors", "这台手机上有哪些传感器（只是清单）。要读数用 read_sensor。"),
        tool(
            "read_sensor", "读一次传感器的当前值：光线强弱、距离、有没有在动、手机朝向、走了多少步。",
            reqStr("kind", "light=光线 / proximity=距离 / motion=动静 / direction=朝向 / steps=步数 / all=全都要")
        ),
        tool("open_app", "在这台手机上打开一个应用。", reqStr("packageName", "应用包名，如 com.tencent.mm；用 installed_apps 拿")),
        tool("installed_apps", "列出这台手机上已安装、能启动的应用（名字 + 包名）。"),
        // 手机状态
        tool("current_app", "这台手机现在前台是哪个应用，返回应用名和包名（要开无障碍权限）。"),
        tool(
            "read_notifications", "读这台手机的通知，两路互不覆盖，要开通知监听权限。",
            str("kind", "current=只看通知栏现在挂着的 / recent=只看最近收到的；不传两个都返回"),
            num("limit", "每类最多几条，默认 20，最多 20")
        ),
        tool("ambient", "录 3 秒环境音，估一个分贝值，判断安静还是吵、像不像有人在说话（要麦克风权限）。"),
        // 点歌
        tool(
            "search_song", "搜歌，返回歌名、歌手、专辑和歌曲 id（id 交给 play_song）。这是公开接口，不用配任何东西。",
            reqStr("keyword", "歌名，或者「歌名 歌手」"),
            num("limit", "返回几条，默认 5，最多 10")
        ),
        tool(
            "play_song", "按歌曲 id 在这台手机上打开网易云的那首歌。id 要用 search_song 先搜出来。",
            reqNum("id", "歌曲 id，来自 search_song"),
            str("name", "歌名，可空，只用来回话时念一下")
        )
    )

    private fun tool(name: String, desc: String, vararg props: P): JsonObject {
        val schema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                props.forEach { p ->
                    put(p.name, buildJsonObject {
                        put("type", JsonPrimitive(p.type))
                        put("description", JsonPrimitive(p.desc))
                    })
                }
            })
            val required = props.filter { it.required }
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.map { JsonPrimitive(it.name) }))
            }
        }
        return buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(desc))
            put("inputSchema", schema)
        }
    }

    /** 通知（notifications/xxx 这种没有 id 的请求）不需要回内容 */
    fun isNotification(request: JsonObject): Boolean = request["id"] == null || request["id"] is JsonNull

    suspend fun handle(request: JsonObject): JsonObject {
        val id = request["id"]
        if (isNotification(request)) return JsonObject(emptyMap())

        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return error(id, "no method", -32600)
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        return when (method) {
            "initialize" -> result(
                id,
                buildJsonObject {
                    put("protocolVersion", JsonPrimitive("2024-11-05"))
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject { put("listChanged", JsonPrimitive(false)) })
                    })
                    put("serverInfo", buildJsonObject {
                        put("name", JsonPrimitive("Perception"))
                        put("version", JsonPrimitive("1.0"))
                    })
                    put("instructions", JsonPrimitive("手机上的日历、闹钟和一堆本机小工具。所有时间参数都是毫秒时间戳。工具出错时会返回 isError=true 和一句原因。"))
                }
            )

            "tools/list" -> result(id, buildJsonObject { put("tools", JsonArray(toolList())) })

            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return error(id, "no tool name", -32602)
                val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val (text, failed) = callTool(name, args)
                result(
                    id,
                    buildJsonObject {
                        put("content", JsonArray(listOf(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(text))
                        })))
                        put("isError", JsonPrimitive(failed))
                    }
                )
            }

            "ping" -> result(id, buildJsonObject { put("ok", JsonPrimitive(true)) })
            else -> error(id, "method not found: $method", -32601)
        }
    }

    private suspend fun callTool(name: String, args: JsonObject): Pair<String, Boolean> {
        settings.addLog("调用工具 $name")
        val s = { k: String -> args[k]?.jsonPrimitive?.contentOrNull }
        val l = { k: String -> args[k]?.jsonPrimitive?.longOrNull }
        val b = { k: String -> args[k]?.jsonPrimitive?.booleanOrNull }

        val out: Any? = when (name) {
            "list_schedules" -> toolkit.listSchedules()
            "add_schedule" -> toolkit.addSchedule(
                s("title") ?: "无标题", s("note") ?: "",
                l("time") ?: System.currentTimeMillis(), b("remind") ?: false
            )
            "change_schedule" -> {
                val id = l("id") ?: -1L
                if ((s("action") ?: "").lowercase().startsWith("del")) toolkit.deleteEvent(id)
                else toolkit.updateEvent(id, s("title"), s("note"), l("time"), b("remind"), null)
            }
            "list_alarms" -> toolkit.listAlarms()
            "add_alarm" -> toolkit.addAlarm(
                s("title") ?: "闹钟", s("note") ?: "",
                l("time") ?: System.currentTimeMillis(), s("repeatDays") ?: ""
            )
            "change_alarm" -> {
                val id = l("id") ?: -1L
                if ((s("action") ?: "").lowercase().startsWith("del")) toolkit.deleteEvent(id)
                else toolkit.updateEvent(id, s("title"), s("note"), l("time"), null, s("repeatDays"))
            }
            "device_info" -> toolkit.deviceInfo()
            "battery" -> toolkit.battery()
            "location" -> toolkit.location()
            "weather" -> toolkit.weather(s("city"))
            "network" -> toolkit.network()
            "sensors" -> toolkit.sensors()
            "read_sensor" -> toolkit.readSensor(s("kind") ?: "all")
            "open_app" -> toolkit.openApp(s("packageName") ?: "")
            "installed_apps" -> toolkit.installedApps()
            "current_app" -> toolkit.currentApp()
            "read_notifications" -> toolkit.notifications(s("kind"), (l("limit") ?: 20L).toInt())
            "ambient" -> toolkit.ambient()
            "search_song" -> toolkit.searchSong(s("keyword") ?: "", (l("limit") ?: 5L).toInt())
            "play_song" -> toolkit.playSong(l("id") ?: -1L, s("name") ?: "")
            else -> mapOf("ok" to false, "error" to "没有这个工具：$name")
        }

        val failed = out is Map<*, *> && (out["error"] != null || out["ok"] == false)
        return json.encodeToString(JsonElement.serializer(), toJson(out)) to failed
    }

    private fun toJson(m: Any?): JsonElement = when (m) {
        null -> JsonNull
        is Map<*, *> -> buildJsonObject { m.forEach { (k, v) -> put(k.toString(), toJson(v)) } }
        is List<*> -> buildJsonArray { m.forEach { add(toJson(it)) } }
        is Boolean -> JsonPrimitive(m)
        is Int -> JsonPrimitive(m)
        is Long -> JsonPrimitive(m)
        is Double -> JsonPrimitive(m)
        else -> JsonPrimitive(m.toString())
    }

    private fun result(id: JsonElement?, r: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        id?.let { put("id", it) } ?: put("id", JsonNull)
        put("result", r)
    }

    private fun error(id: JsonElement?, message: String, code: Int): JsonObject = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        id?.let { put("id", it) } ?: put("id", JsonNull)
        put("error", buildJsonObject {
            put("code", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        })
    }
}
