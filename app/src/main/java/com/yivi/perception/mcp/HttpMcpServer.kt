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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * MCP 的 Streamable HTTP 服务端。行为照着官方 SDK（TS 的 StreamableHTTPServerTransport /
 * Python 的 StreamableHTTPSessionManager）来写：
 *
 * - POST：Accept 必须同时包含 application/json 和 text/event-stream（否则 406）；
 *   Content-Type 必须是 application/json（否则 415）；除 initialize 外必须带 Mcp-Session-Id
 *   （没带 400，对不上 404）；请求默认用 SSE 回（客户端不接受 SSE 才回 JSON）；
 *   通知（无 id）和客户端发来的 response（无 method）→ 202 且不带 body
 * - GET：开一条 SSE 流（retry + 心跳），要有效的 session
 * - DELETE：结束会话 → 204
 * - OPTIONS：CORS 预检
 * - 会话超过 30 分钟没动静就作废（之后 404，客户端会重新 initialize）
 * - 记最近 60 条日志：请求头、请求体、响应体，/status 和 APP 运行日志里都能看
 */
class HttpMcpServer(private val engine: McpEngine) {

    private val json = Json { ignoreUnknownKeys = true }
    private var httpd: NanoHTTPD? = null

    val isRunning: Boolean get() = httpd != null

    @Volatile
    var lastError: String? = null
        private set

    private val supportedVersions = setOf("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25")
    private val sessionIdleMs = 30 * 60 * 1000L

    /** 服务器自己的会话：id → 最后一次活动时间 */
    private val sessions = mutableMapOf<String, Long>()
    private var currentSession: String? = null

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

    private fun newSession(): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val now = System.currentTimeMillis()
        synchronized(sessions) {
            sessions[id] = now
            currentSession = id
            // 顺手清掉过期的
            sessions.entries.removeAll { now - it.value > sessionIdleMs }
        }
        return id
    }

    private fun touchSession(id: String): Boolean = synchronized(sessions) {
        val last = sessions[id] ?: return false
        if (System.currentTimeMillis() - last > sessionIdleMs) {
            sessions.remove(id)
            return false
        }
        sessions[id] = System.currentTimeMillis()
        true
    }

    private fun dropSession(id: String) = synchronized(sessions) {
        sessions.remove(id)
        if (currentSession == id) currentSession = null
    }

    fun start(port: Int, onReady: (Int) -> Unit) {
        if (httpd != null) return
        val server = object : NanoHTTPD(port) {

            /** GET 上的 SSE 流：retry + 心跳，客户端断开就结束 */
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
                            out.write("retry: 1000\n\n".toByteArray())
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
                    cors(newFixedLengthResponse(Response.Status.OK, "text/event-stream", "retry: 1000\n\n"))
                }
            }

            /** 传输层的 JSON-RPC 错误（跟官方一样：id 给 null） */
            private fun rpcError(status: Response.IStatus, code: Int, message: String): Response =
                cors(
                    newFixedLengthResponse(
                        status, "application/json",
                        json.encodeToString(JsonElement.serializer(), buildJsonObject {
                            put("jsonrpc", JsonPrimitive("2.0"))
                            put("id", JsonNull)
                            put("error", buildJsonObject {
                                put("code", JsonPrimitive(code))
                                put("message", JsonPrimitive(message))
                            })
                        })
                    )
                )

            override fun serve(session: IHTTPSession): Response {
                val method = session.method
                val path = session.uri.trimEnd('/').ifBlank { "/" }
                val accept = session.headers["accept"]?.lowercase() ?: ""
                val contentType = session.headers["content-type"]?.lowercase() ?: ""
                val origin = session.headers["origin"] ?: "无"
                val ua = (session.headers["user-agent"] ?: "").take(40)
                val protocolHeader = session.headers["mcp-protocol-version"]
                val sessionHeader = session.headers["mcp-session-id"]

                val isMcpPath = path == "/mcp" || path == "/" || (method == Method.POST && path != "/health" && path != "/status")
                val isPost = method == Method.POST
                // POST 先读 body（后面判 initialize、判会话头都要用）
                val raw = if (isPost) readBody(session) else ""
                val request = if (isPost) {
                    runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                } else null
                val isInitialize = request?.get("method")?.jsonPrimitive?.contentOrNull == "initialize"

                val response: Response = when {
                    method == Method.OPTIONS ->
                        cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))

                    path == "/health" && !isMcpPath ->
                        cors(newFixedLengthResponse(Response.Status.OK, "text/plain", "ok"))

                    path == "/status" && !isMcpPath -> {
                        val body = buildJsonObject {
                            put("name", JsonPrimitive("Perception"))
                            put("running", JsonPrimitive(true))
                            put("port", JsonPrimitive(port))
                            put("session", JsonPrimitive(currentSession ?: ""))
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

                    isMcpPath && method == Method.GET -> {
                        if (sessionHeader == null || !touchSession(sessionHeader)) {
                            log("GET $path → 400/404 会话不对（session=${sessionHeader?.take(8) ?: "无"}）")
                            rpcError(Response.Status.BAD_REQUEST, -32600, "Mcp-Session-Id is required")
                        } else {
                            log("GET $path → 200 SSE 流（Accept: ${accept.ifBlank { "无" }}）")
                            sse()
                        }
                    }

                    isMcpPath && method == Method.DELETE -> {
                        if (sessionHeader != null) {
                            dropSession(sessionHeader)
                            log("DELETE $path → 204（会话已结束）")
                        } else {
                            log("DELETE $path → 204（本来就没会话）")
                        }
                        cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
                    }

                    isMcpPath && isPost -> {
                        val knownSession = sessionHeader != null && touchSession(sessionHeader)

                        when {
                            // 跟官方一样：Accept 必须两个都要，Content-Type 必须是 json
                            request == null ->
                                rpcError(Response.Status.BAD_REQUEST, -32700, "parse error")

                            !accept.contains("application/json") || !accept.contains("text/event-stream") -> {
                                log("POST $path → 406 Accept 不对（$accept）")
                                rpcError(Response.Status.NOT_ACCEPTABLE, -32600, "Not Acceptable: Client must accept both application/json and text/event-stream")
                            }

                            contentType.isNotBlank() && !contentType.contains("application/json") -> {
                                log("POST $path → 415 Content-Type 不对（$contentType）")
                                rpcError(Response.Status.UNSUPPORTED_MEDIA_TYPE, -32600, "Unsupported Media Type: Content-Type must be application/json")
                            }

                            protocolHeader != null && protocolHeader !in supportedVersions -> {
                                log("POST $path → 400 协议版本不认（$protocolHeader）")
                                rpcError(Response.Status.BAD_REQUEST, -32600, "Unsupported protocol version: $protocolHeader")
                            }

                            !isInitialize && sessionHeader == null -> {
                                log("POST $path → 400 少了 Mcp-Session-Id")
                                rpcError(Response.Status.BAD_REQUEST, -32600, "Bad Request: Mcp-Session-Id header is required")
                            }

                            !isInitialize && !knownSession -> {
                                log("POST $path → 404 会话对不上（${sessionHeader?.take(8)}），客户端会重新 initialize")
                                rpcError(Response.Status.NOT_FOUND, -32600, "Session not found")
                            }

                            engine.needsNoBody(request) -> {
                                val what = request["method"]?.jsonPrimitive?.contentOrNull ?: "response"
                                runBlocking { runCatching { engine.handle(request) } }
                                log("POST $path → 202 $what（通知/response，规范要求不带 body）")
                                cors(newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain", ""))
                            }

                            else -> {
                                val name = engine.methodName(request)
                                log("收到 $name（Accept: $accept / UA: $ua / Origin: $origin / 版本头: ${protocolHeader ?: "无"} / session: ${sessionHeader?.take(8) ?: "无"}）")
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
                                // 官方默认用 SSE 回请求结果；客户端不接受 SSE 才回 JSON
                                val asSse = accept.contains("text/event-stream")
                                log("POST $path → 200 $name（回 ${if (asSse) "SSE" else "JSON"}）${if (bad) "（报错：${resp["error"]}）" else ""}")
                                log("响应体：${text.take(200)}")
                                if (asSse) {
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

                    else -> {
                        log("${session.method} $path → 404")
                        cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found"))
                    }
                }

                // 会话头：initialize（且没带会话头）才开新会话，跟官方一致
                val respondSession = when {
                    sessionHeader != null -> sessionHeader
                    isMcpPath && isPost && isInitialize && request != null -> newSession()
                    else -> currentSession ?: ""
                }
                if (respondSession.isNotBlank()) response.addHeader("Mcp-Session-Id", respondSession)
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
        synchronized(sessions) {
            sessions.clear()
            currentSession = null
        }
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
