package com.yivi.perception.mcp

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * MCP 的 Streamable HTTP 服务端（按 2025-06-18/2025-11-25 规范实现）。
 *
 * 端点就一个 /mcp，按规范：
 * - POST  收 JSON-RPC。是请求就回一条结果（默认 JSON，客户端只要 SSE 时用 text/event-stream）
 *         是通知（无 id）或客户端回的 response（无 method）→ 202 且不带 body
 * - GET   开一条 SSE 流（我们没东西要主动推，就发心跳挂着）
 * - DELETE 结束会话 → 204
 * - 响应统一带 Mcp-Session-Id；initialize 里按客户端报的协议版本回（认不出才回自己的最新）
 * - 所有响应带 CORS 头，OPTIONS 预检也回；Origin 记进日志
 *
 * 另外记最近 60 条日志（请求头、请求体、响应体），/status 和 APP 的运行日志都能看到。
 */
class HttpMcpServer(private val engine: McpEngine) {

    private val json = Json { ignoreUnknownKeys = true }
    private var httpd: NanoHTTPD? = null

    val isRunning: Boolean get() = httpd != null

    /** 起不来时的原因（端口被占之类），给界面显示 */
    @Volatile
    var lastError: String? = null
        private set

    private val sessionId = UUID.randomUUID().toString()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val requestLog = ArrayDeque<String>()

    fun recentRequests(): List<String> = synchronized(requestLog) { requestLog.toList() }

    private fun log(line: String) {
        val stamped = "${timeFmt.format(Date())} $line"
        synchronized(requestLog) {
            requestLog.addLast(stamped)
            while (requestLog.size > 60) requestLog.removeFirst()
        }
        Log.i("PerceptionMcp", stamped)
        runCatching { com.yivi.perception.PerceptionApp.instance.settings.addLog(stamped) }
    }

    fun start(port: Int, onReady: (Int) -> Unit) {
        if (httpd != null) return
        val server = object : NanoHTTPD(port) {

            /**
             * GET 上的 SSE 流。newChunkedResponse 是 protected 静态方法，
             * 用反射拿；拿不到就退回一条普通的 SSE 响应。
             */
            private fun sse(): Response {
                return try {
                    val method = NanoHTTPD::class.java.getDeclaredMethod(
                        "newChunkedResponse",
                        Response.IStatus::class.java,
                        String::class.java,
                        java.io.InputStream::class.java
                    )
                    method.isAccessible = true
                    val out = java.io.PipedOutputStream()
                    val input = java.io.PipedInputStream(out, 8192)
                    val thread = Thread {
                        try {
                            out.write(": perception ready\n\n".toByteArray())
                            out.flush()
                            var beats = 0
                            while (beats < 40) {
                                Thread.sleep(15000)
                                out.write(": ping\n\n".toByteArray())
                                out.flush()
                                beats++
                            }
                        } catch (_: Exception) {
                            // 客户端走了
                        } finally {
                            runCatching { out.close() }
                        }
                    }
                    thread.isDaemon = true
                    thread.start()
                    cors(method.invoke(null, Response.Status.OK, "text/event-stream", input) as Response)
                } catch (e: Exception) {
                    log("SSE 流没挂上（${e.message}），回一条普通的")
                    cors(newFixedLengthResponse(Response.Status.OK, "text/event-stream", ": perception ready\n\n"))
                }
            }

            override fun serve(session: IHTTPSession): Response {
                val isPost = session.method == Method.POST
                val path = session.uri.trimEnd('/').ifBlank { "/" }
                val accept = session.headers["accept"]?.lowercase() ?: ""
                // 规范要求客户端 Accept 里两个都要给。它只要 SSE 我们就回 SSE，其余回 JSON（两种都合规）
                val wantsSse = accept.contains("text/event-stream") && !accept.contains("application/json")
                val origin = session.headers["origin"] ?: "无"
                val ua = (session.headers["user-agent"] ?: "").take(40)
                val version = session.headers["mcp-protocol-version"] ?: "无"

                val isMcpEndpoint = path == "/mcp" || path == "/" || (isPost && path != "/health" && path != "/status")

                val response = when {
                    session.method == Method.OPTIONS ->
                        cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))

                    !isMcpEndpoint && path == "/health" ->
                        cors(newFixedLengthResponse(Response.Status.OK, "text/plain", "ok"))

                    !isMcpEndpoint && path == "/status" -> {
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

                    isMcpEndpoint && session.method == Method.GET -> {
                        log("GET $path → 200 SSE 流（Accept: ${accept.ifBlank { "无" }}）")
                        sse()
                    }

                    isMcpEndpoint && session.method == Method.DELETE -> {
                        log("DELETE $path → 204（客户端结束了会话）")
                        cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
                    }

                    isMcpEndpoint && isPost -> {
                        val raw = readBody(session)
                        val request = try {
                            json.parseToJsonElement(raw).jsonObject
                        } catch (e: Exception) {
                            null
                        }
                        when {
                            request == null -> {
                                log("POST $path → 400 解析不了：${raw.take(80)}")
                                cors(
                                    newFixedLengthResponse(
                                        Response.Status.BAD_REQUEST, "application/json",
                                        json.encodeToString(JsonElement.serializer(), buildJsonObject {
                                            put("jsonrpc", JsonPrimitive("2.0"))
                                            put("id", JsonNull)
                                            put("error", buildJsonObject {
                                                put("code", JsonPrimitive(-32700))
                                                put("message", JsonPrimitive("parse error"))
                                            })
                                        })
                                    )
                                )
                            }

                            engine.needsNoBody(request) -> {
                                val what = if (request["method"] != null) {
                                    request["method"]?.jsonPrimitive?.contentOrNull ?: "?"
                                } else "response"
                                runBlocking { runCatching { engine.handle(request) } }
                                log("POST $path → 202 $what（通知/response，规范要求不带 body）")
                                cors(newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain", ""))
                            }

                            else -> {
                                val method = engine.methodName(request)
                                log("收到 $method（Accept: $accept / UA: $ua / Origin: $origin / 版本头: $version）")
                                log("请求体：${raw.replace("\n", " ").take(200)}")
                                val resp = runBlocking {
                                    runCatching { engine.handle(request) }.getOrElse { e ->
                                        buildJsonObject {
                                            put("jsonrpc", JsonPrimitive("2.0"))
                                            put("id", request["id"] ?: JsonNull)
                                            put("error", buildJsonObject {
                                                put("code", JsonPrimitive(-32603))
                                                put("message", JsonPrimitive(e.message ?: "internal error"))
                                            })
                                        }
                                    }
                                }
                                val text = json.encodeToString(JsonElement.serializer(), resp)
                                val bad = resp["error"] != null
                                log("POST $path → 200 $method（回 ${if (wantsSse) "SSE" else "JSON"}）${if (bad) "（报错：${resp["error"]}）" else ""}")
                                log("响应体：${text.take(200)}")
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
                        log("${session.method} $path → 405（这个端点只收 POST/GET/DELETE）")
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
            log("服务起来了，监听 0.0.0.0:$port · session=$sessionId")
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
        r.addHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS")
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
