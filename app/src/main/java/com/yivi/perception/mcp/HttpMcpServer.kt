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

class HttpMcpServer(private val engine: McpEngine) {
    private val json = Json { ignoreUnknownKeys = true }
    private var httpd: NanoHTTPD? = null

    fun start(port: Int, onReady: (Int) -> Unit) {
        if (httpd != null) return
        val s = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                val path = session.uri
                return when {
                    path == "/health" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
                    path == "/status" -> newFixedLengthResponse(
                        Response.Status.OK, "application/json",
                        json.encodeToString(JsonElement.serializer(), buildJsonObject {
                            put("name", JsonPrimitive("Perception"))
                            put("running", JsonPrimitive(true))
                            put("port", JsonPrimitive(port))
                        })
                    )
                    path == "/mcp" -> {
                        val req = try { json.parseToJsonElement(readBody(session)).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) }
                        val resp = runBlocking { engine.handle(req) }
                        newFixedLengthResponse(Response.Status.OK, "application/json",
                            json.encodeToString(JsonElement.serializer(), resp))
                    }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
                }
            }
        }
        try {
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            httpd = s
            onReady(port)
        } catch (e: Exception) { }
    }

    fun stop() {
        httpd?.stop()
        httpd = null
    }

    private fun readBody(session: IHTTPSession): String {
        return try {
            session.inputStream?.bufferedReader()?.readText() ?: ""
        } catch (e: Exception) { "" }
    }
}
