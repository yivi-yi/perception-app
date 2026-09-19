package com.yivi.perception.mcp

import com.yivi.perception.NativeToolkit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class McpEngine(
    private val toolkit: NativeToolkit,
    private val settings: com.yivi.perception.data.SettingsRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 工具参数：名字、类型、说明 */
    private data class P(val name: String, val type: String, val desc: String)

    private fun str(name: String, desc: String) = P(name, "string", desc)
    private fun num(name: String, desc: String) = P(name, "integer", desc)
    private fun bool(name: String, desc: String) = P(name, "boolean", desc)

    private val timeHint = "要填毫秒时间戳（13 位），不是 'HH:mm'"
    private val repeatHint = "重复星期，如 1,3,5（1=周一 7=周日）；留空就是只响一次"

    private fun toolList(): List<JsonObject> = listOf(
        tool("list_schedules", "查询所有日程（带 id / 标题 / 备注 / 时间 / 是否提醒）"),
        tool(
            "add_schedule", "添加一条日程",
            str("title", "标题，必填"),
            str("note", "备注，可空"),
            num("time", timeHint),
            bool("remind", "true 到点弹通知提醒")
        ),
        tool(
            "update_schedule", "修改一条日程，只传要改的字段，id 必传",
            num("id", "日程 id"),
            str("title", "新标题"),
            str("note", "新备注"),
            num("time", timeHint),
            bool("remind", "是否提醒")
        ),
        tool("delete_schedule", "删除一条日程", num("id", "日程 id")),
        tool("list_alarms", "查询所有闹钟（带 id / 标题 / 时间 / 重复星期）"),
        tool(
            "add_alarm", "添加一个闹钟，到点会响铃并弹通知",
            str("title", "标签，可空"),
            str("note", "备注，可空"),
            num("time", timeHint + "；重复闹钟只取里面的时分"),
            str("repeatDays", repeatHint)
        ),
        tool(
            "update_alarm", "修改一个闹钟，只传要改的字段，id 必传",
            num("id", "闹钟 id"),
            str("title", "新标签"),
            str("note", "新备注"),
            num("time", timeHint),
            str("repeatDays", repeatHint)
        ),
        tool("delete_alarm", "删除一个闹钟", num("id", "闹钟 id")),
        tool("device_info", "设备品牌 / 型号 / 安卓版本"),
        tool("battery", "电量百分比 + 是否在充电"),
        tool("storage", "存储总容量 / 可用 / 已用（字节）"),
        tool("location", "最近一次定位（经纬度 + 时间），没给定位权限或没定位过会返回 ok=false"),
        tool("network", "当前 WiFi 名 / 信号强度 / 本机 IP"),
        tool("sensors", "列出设备上的传感器"),
        tool("open_app", "按包名打开一个应用", str("packageName", "应用包名，如 com.tencent.mm")),
        tool("installed_apps", "列出已安装、可启动的应用（名称 + 包名）")
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
            val required = props.filter { it.name == "id" || (it.name == "title" && name.startsWith("add")) }
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
    fun isNotification(request: JsonObject): Boolean =
        request["id"] == null || request["id"] is JsonNull

    suspend fun handle(request: JsonObject): JsonObject {
        val id = request["id"]
        if (isNotification(request)) {
            // initialized / cancelled 这些是通知，不用答
            return JsonObject(emptyMap())
        }
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
                    put("instructions", JsonPrimitive("手机上的日历和闹钟。时间参数一律用毫秒时间戳。"))
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
            "update_schedule" -> toolkit.updateEvent(l("id") ?: -1, s("title"), s("note"), l("time"), b("remind"), null)
            "delete_schedule" -> toolkit.deleteEvent(l("id") ?: -1)
            "list_alarms" -> toolkit.listAlarms()
            "add_alarm" -> toolkit.addAlarm(
                s("title") ?: "闹钟", s("note") ?: "",
                l("time") ?: System.currentTimeMillis(), s("repeatDays") ?: ""
            )
            "update_alarm" -> toolkit.updateEvent(l("id") ?: -1, s("title"), s("note"), l("time"), null, s("repeatDays"))
            "delete_alarm" -> toolkit.deleteEvent(l("id") ?: -1)
            "device_info" -> toolkit.deviceInfo()
            "battery" -> toolkit.battery()
            "storage" -> toolkit.storage()
            "location" -> toolkit.lastLocation()
            "network" -> toolkit.network()
            "sensors" -> toolkit.sensors()
            "open_app" -> toolkit.openApp(s("packageName") ?: "")
            "installed_apps" -> toolkit.installedApps()
            else -> mapOf("error" to "unknown tool: $name")
        }

        val failed = out is Map<*, *> && (out["error"] != null || out["ok"] == false)
        val text = json.encodeToString(JsonElement.serializer(), toJson(out))
        return text to failed
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
