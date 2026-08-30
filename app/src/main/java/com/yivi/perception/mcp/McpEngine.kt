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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull

class McpEngine(private val toolkit: NativeToolkit, private val settings: com.yivi.perception.data.SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    fun listTools(): JsonArray = buildJsonArray {
        add(tool("list_schedules", "查询所有行程", listOf()))
        add(tool("add_schedule", "添加一条行程", listOf("title", "note", "time", "remind")))
        add(tool("update_schedule", "修改行程，需传id", listOf("id", "title", "note", "time", "remind")))
        add(tool("delete_schedule", "删除行程，传id", listOf("id")))
        add(tool("list_alarms", "查询所有闹钟", listOf()))
        add(tool("add_alarm", "添加闹钟，传title/note/time/repeatDays(如 '1,3,5')", listOf("title", "note", "time", "repeatDays")))
        add(tool("delete_alarm", "删除闹钟，传id", listOf("id")))
        add(tool("device_info", "获取设备品牌/型号/安卓版本", listOf()))
        add(tool("battery", "获取电量与是否充电", listOf()))
        add(tool("storage", "获取存储占用", listOf()))
        add(tool("location", "获取最近一次定位经纬度", listOf()))
        add(tool("network", "获取wifi名称/ip", listOf()))
        add(tool("sensors", "列出设备传感器", listOf()))
        add(tool("open_app", "打开指定app，传packageName", listOf("packageName")))
        add(tool("installed_apps", "列出已安装可启动的app", listOf()))
    }

    private fun tool(name: String, desc: String, props: List<String>): JsonObject {
        val schema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            val p = buildJsonObject {}
            props.forEach {
                p[it] = buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive(""))
                }
            }
            put("properties", p)
            if (props.isNotEmpty()) put("required", JsonArray(props.map { JsonPrimitive(it) }))
        }
        return buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(desc))
            put("inputSchema", schema)
        }
    }

    suspend fun handle(request: JsonObject): JsonObject {
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return error(id, "no method", -32600)
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        return when (method) {
            "initialize" -> result(id, buildJsonObject {
                put("protocolVersion", JsonPrimitive("2024-11-05"))
                put("capabilities", buildJsonObject { put("tools", buildJsonObject { put("listChanged", JsonPrimitive(true)) }) })
                put("serverInfo", buildJsonObject {
                    put("name", JsonPrimitive("Perception"))
                    put("version", JsonPrimitive("1.0"))
                })
            })
            "tools/list" -> result(id, buildJsonObject { put("tools", listTools()) })
            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return error(id, "no tool name", -32602)
                val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val out = callTool(name, args)
                result(id, buildJsonObject {
                    put("content", JsonArray(listOf(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(out))
                    })))
                    put("isError", JsonPrimitive(false))
                })
            }
            "ping" -> result(id, buildJsonObject { put("ok", JsonPrimitive(true)) })
            else -> error(id, "method not found", -32601)
        }
    }

    private suspend fun callTool(name: String, args: JsonObject): String {
        settings.addLog("调用工具 $name")
        val s = { k: String -> args[k]?.jsonPrimitive?.contentOrNull }
        val l = { k: String -> args[k]?.jsonPrimitive?.longOrNull }
        val b = { k: String -> args[k]?.jsonPrimitive?.booleanOrNull }
        val out = when (name) {
            "list_schedules" -> toolkit.listSchedules()
            "add_schedule" -> toolkit.addSchedule(s("title") ?: "无标题", s("note") ?: "", l("time") ?: System.currentTimeMillis(), b("remind") ?: false)
            "update_schedule" -> toolkit.updateEvent(l("id") ?: -1, s("title"), s("note"), l("time"), b("remind"), null)
            "delete_schedule" -> toolkit.deleteEvent(l("id") ?: -1)
            "list_alarms" -> toolkit.listAlarms()
            "add_alarm" -> toolkit.addAlarm(s("title") ?: "无标题", s("note") ?: "", l("time") ?: System.currentTimeMillis(), s("repeatDays") ?: "")
            "delete_alarm" -> toolkit.deleteEvent(l("id") ?: -1)
            "device_info" -> toolkit.deviceInfo()
            "battery" -> toolkit.battery()
            "storage" -> toolkit.storage()
            "location" -> toolkit.lastLocation()
            "network" -> toolkit.network()
            "sensors" -> toolkit.sensors()
            "open_app" -> toolkit.openApp(s("packageName") ?: "")
            "installed_apps" -> toolkit.installedApps()
            else -> mapOf("error" to "unknown tool")
        }
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), toJson(out))
    }

    private fun toJson(m: Any): JsonElement = when (m) {
        is Map<*, *> -> buildJsonObject {
            m.forEach { (k, v) -> put(k.toString(), toJson(v)) }
        }
        is List<*> -> buildJsonArray { m.forEach { add(toJson(it)) } }
        is Boolean -> JsonPrimitive(m)
        is Int -> JsonPrimitive(m)
        is Long -> JsonPrimitive(m)
        is Double -> JsonPrimitive(m)
        else -> JsonPrimitive(m?.toString() ?: "")
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
