package com.yivi.perception.mcp

import com.yivi.perception.NativeToolkit
import com.yivi.perception.data.ToolSpec
import com.yivi.perception.data.Tools
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

    /** 请求里的方法名，给日志用 */
    fun methodName(request: JsonObject): String =
        request["method"]?.jsonPrimitive?.contentOrNull ?: "?"

    /** 工具数量，给 /status 用 */
    fun toolCount(): Int = Tools.all.size

    /** 工具表从 data/Tools 生成，跟设置页里显示的说明书是同一份 */
    private fun toolList(): List<JsonObject> = Tools.all.map { spec ->
        buildJsonObject {
            put("name", JsonPrimitive(spec.name))
            put("description", JsonPrimitive(spec.desc))
            put("inputSchema", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    spec.params.forEach { p ->
                        put(p.name, buildJsonObject {
                            put("type", JsonPrimitive(p.type))
                            put("description", JsonPrimitive(p.desc))
                        })
                    }
                })
                val required = spec.params.filter { it.required }
                if (required.isNotEmpty()) {
                    put("required", JsonArray(required.map { JsonPrimitive(it.name) }))
                }
            })
        }
    }

    /**
     * 这条消息要不要回内容。
     * 规范：没有 id 的（通知）和没有 method 的（客户端发来的 response）都只回 202，不带 body。
     */
    fun needsNoBody(request: JsonObject): Boolean {
        val hasMethod = request["method"] != null && request["method"] !is JsonNull
        val hasId = request["id"] != null && request["id"] !is JsonNull
        return !hasMethod || !hasId
    }

    suspend fun handle(request: JsonObject): JsonObject {
        val id = request["id"]
        if (needsNoBody(request)) return JsonObject(emptyMap())

        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return error(id, "no method", -32600)
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        return when (method) {
            "initialize" -> {
                val want = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
                val known = setOf("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25")
                val reply = if (want != null && want in known) want else "2025-06-18"
                result(
                id,
                buildJsonObject {
                    put("protocolVersion", JsonPrimitive(reply))
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
            }

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
            "weather" -> toolkit.weather(s("city"), s("source"))
            "network" -> toolkit.network()
            "sensors" -> toolkit.sensors()
            "read_sensor" -> toolkit.readSensor(s("kind") ?: "all")
            "sound_state" -> toolkit.soundState()
            "set_sound" -> toolkit.setSound(s("mode"), b("dnd"), l("level")?.toInt(), s("stream"))
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
        // 注意：put 会返回"这个键原来的值"，所以不能写成 id?.let{ put("id", it) } ?: put("id", JsonNull)
        // —— 那样 elvis 每次都会再写一次 null，把 id 覆盖掉
        put("id", id ?: JsonNull)
        put("result", r)
    }

    private fun error(id: JsonElement?, message: String, code: Int): JsonObject = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("id", id ?: JsonNull)
        put("error", buildJsonObject {
            put("code", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        })
    }
}
