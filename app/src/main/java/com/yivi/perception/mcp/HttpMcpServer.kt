package com.yivi.perception.mcp

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 一个很小的 MCP 服务端（JSON-RPC over HTTP）。
 * 地址：http://<手机IP>:9001/mcp，直接在别的客户端里当 streamable http 用。
 */
class HttpMcpServer(private val engine: McpEngine) {

    private val json = Json { ignoreUnknownKeys = true }
    private var httpd: NanoHTTPD? = null

    val isRunning: Boolean get() = httpd != null

    fun start(port: Int, onReady: (Int) -> Unit) {
        if (httpd != null) return
        val server = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                val path = session.uri
                val isPost = session.method == Method.POST
                return when {
                    path == "/health" ->
                        newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")

                    path == "/status" ->
                        newFixedLengthResponse(
                            Response.Status.OK, "application/json",
                            json.encodeToString(JsonElement.serializer(), buildJsonObject {
                                put("name", JsonPrimitive("Perception"))
                                put("running", JsonPrimitive(true))
                                put("port", JsonPrimitive(port))
                            })
                        )

                    (path == "/mcp" || path == "/") && isPost -> {
                        val request = try {
                            json.parseToJsonElement(readBody(session)).jsonObject
                        } catch (e: Exception) {
                            null
                        }
                        if (request == null) {
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
                        } else if (engine.isNotification(request)) {
                            // 通知按规范不回内容，给个 202 就完事
                            runBlocking { engine.handle(request) }
                            newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", "")
                        } else {
                            val resp = runBlocking { engine.handle(request) }
                            newFixedLengthResponse(
                                Response.Status.OK, "application/json",
                                json.encodeToString(JsonElement.serializer(), resp)
                            )
                        }
                    }

                    path == "/mcp" || path == "/" ->
                        newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "use POST")

                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
                }
            }
        }
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            httpd = server
            onReady(port)
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            httpd?.stop()
        } catch (_: Exception) {
        }
        httpd = null
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
