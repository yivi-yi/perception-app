package com.yivi.perception.mcp

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 一个很小的 MCP 服务端（JSON-RPC over HTTP）。
 * 地址：http://<手机IP>:9001/mcp，直接在别的客户端里当 streamable http 用。
 *
 * 兼容性上做了几件事：
 * - 所有响应都带 CORS 头，OPTIONS 预检也回，浏览器版的客户端不会被拦
 * - 客户端要 SSE（Accept: text/event-stream）就按 SSE 回一条，不认 JSON 的客户端也能用
 * - 会在响应里带 Mcp-Session-Id，官方 SDK 那种会校验会话的也能连
 * - 记最近 20 条请求，/status 能看到，APP 的日志里也有：能分清\"请求没到\"还是\"到了但报错\"
 */
class HttpMcpServer(private val engine: McpEngine) {

    private val json = Json { ignoreUnknownKeys = true }
    private var httpd: NanoHTTPD? = null

    /** 服务起没起 */
    val isRunning: Boolean get() = httpd != null

    /** 起不来时的原因（端口被占之类），给界面显示 */
    @Volatile
    var lastError: String? = null
        private set

    private val sessionId = UUID.randomUUID().toString()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val requestLog = ArrayDeque<String>()

    /** 最近 20 条请求，新的在后 */
    fun recentRequests(): List<String> = synchronized(requestLog) { requestLog.toList() }

    private fun log(line: String) {
        val stamped = "${timeFmt.format(Date())} $line"
        synchronized(requestLog) {
            requestLog.addLast(stamped)
            while (requestLog.size > 20) requestLog.removeFirst()
        }
        Log.i("PerceptionMcp", stamped)
        runCatching { com.yivi.perception.PerceptionApp.instance.settings.addLog(stamped) }
    }

    fun start(port: Int, onReady: (Int) -> Unit) {
        if (httpd != null) return
        val server = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                val path = session.uri
                val isPost = session.method == Method.POST
                val accept = session.headers["accept"]?.lowercase() ?: ""
                val wantsSse = accept.contains("text/event-stream")
                val response = when {
                    session.method == Method.OPTIONS ->
                        cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))

                    path == "/health" ->
                        cors(newFixedLengthResponse(Response.Status.OK, "text/plain", "ok"))

                    path == "/status" -> {
                        val body = buildJsonObject {
                            put("name", JsonPrimitive("Perception"))
                            put("running", JsonPrimitive(true))
                            put("port", JsonPrimitive(port))
                            put("session", JsonPrimitive(sessionId))
                            put("tools", JsonPrimitive(engine.toolCount()))
                            put("requests", JsonArray(recentRequests().map { JsonPrimitive(it) }))
                        }
                        cors(
                            newFixedLengthResponse(
                                Response.Status.OK, "application/json",
                                json.encodeToString(JsonElement.serializer(), body)
                            )
                        )
                    }

                    (path == "/mcp" || path == "/") && isPost -> {
                        val raw = readBody(session)
                        val request = try {
                            json.parseToJsonElement(raw).jsonObject
                        } catch (e: Exception) {
                            null
                        }
                        when {
                            request == null -> {
                                log("POST $path → 400 解析不了：${raw.take(60)}")
                                cors(
                                    newFixedLengthResponse(
                                        Response.Status.BAD_REQUEST, "application/json",
                                        json.encodeToString(JsonElement.serializer(), buildJsonObject {
                                            put("jsonrpc", JsonPrimitive("2.0"))
                                            put("id", JsonPrimitive(""))
                                            put("error", buildJsonObject {
                                                put("code", JsonPrimitive(-32700))
                                                put("message", JsonPrimitive("parse error"))
                                            })
                                        })
                                    )
                                )
                            }

                            engine.isNotification(request) -> {
                                runBlocking { runCatching { engine.handle(request) } }
                                log("POST $path → 202 ${engine.methodName(request)}（通知，按规范不回内容）")
                                cors(newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", ""))
                            }

                            else -> {
                                val method = engine.methodName(request)
                                val resp = runBlocking {
                                    runCatching { engine.handle(request) }.getOrElse { e ->
                                        buildJsonObject {
                                            put("jsonrpc", JsonPrimitive("2.0"))
                                            put("id", request["id"] ?: JsonPrimitive(""))
                                            put("error", buildJsonObject {
                                                put("code", JsonPrimitive(-32603))
                                                put("message", JsonPrimitive(e.message ?: "internal error"))
                                            })
                                        }
                                    }
                                }
                                val text = json.encodeToString(JsonElement.serializer(), resp)
                                val bad = resp["error"] != null
                                log("POST $path → 200 $method${if (bad) "（报错：${resp["error"]}）" else ""}")
                                if (wantsSse) {
                                    cors(
                                        newFixedLengthResponse(
                                            Response.Status.OK, "text/event-stream",
                                            "event: message\ndata: $text\n\n"
                                        )
                                    )
                                } else {
                                    cors(newFixedLengthResponse(Response.Status.OK, "application/json", text))
                                }
                            }
                        }
                    }

                    path == "/mcp" || path == "/" -> {
                        log("${session.method} $path → 405（这个服务只收 POST）")
                        cors(newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "use POST"))
                    }

                    else -> {
                        log("${session.method} $path → 404")
                        cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found"))
                    }
                }
                response.addHeader("Mcp-Session-Id", sessionId)
                return response
            }
        }
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            httpd = server
            lastError = null
            log("服务起来了，监听 0.0.0.0:$port")
            onReady(port)
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            log("服务起不来：$lastError")
        }
    }

    fun stop() {
        try {
            httpd?.stop()
        } catch (_: Exception) {
        }
        httpd = null
        log("服务停了")
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        r.addHeader("Access-Control-Allow-Headers", "*")
        r.addHeader("Access-Control-Expose-Headers", "Mcp-Session-Id")
        return r
    }

    private fun readBody(session: IHTTPSession): String = try {
        val body = HashMap<String, String>()
        session.parseBody(body)
        body["postData"] ?: ""
    } catch (e: Exception) {
        try {
            session.inputStream?.bufferedReader()?.readText() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
