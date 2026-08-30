package com.yivi.perception.mcp

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HttpMcpServer(private val engine: McpEngine) {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: EmbeddedServer<CIO, Application, *>? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun start(port: Int, onReady: (Int) -> Unit) {
        if (server != null) return
        GlobalScope.launch {
            server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                routing {
                    get("/health") { call.respondText("ok") }
                    get("/status") {
                        call.respondText(
                            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(),
                                buildJsonObject {
                                    put("name", kotlinx.serialization.json.JsonPrimitive("Perception"))
                                    put("running", kotlinx.serialization.json.JsonPrimitive(true))
                                    put("port", kotlinx.serialization.json.JsonPrimitive(port))
                                }), ContentType.Application.Json)
                    }
                    post("/mcp") {
                        val body = call.receiveText()
                        val req = json.parseToJsonElement(body).jsonObject
                        val resp = engine.handle(req)
                        call.respondText(
                            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), resp),
                            ContentType.Application.Json)
                    }
                }
            }.also { it.start(wait = false) }
            onReady(port)
        }
    }

    fun stop() {
        server?.stop(1000, 1000)
        server = null
    }
}
