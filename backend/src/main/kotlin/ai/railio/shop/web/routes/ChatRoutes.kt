package ai.railio.shop.web.routes

import ai.railio.shop.application.agent.ChatAgent
import ai.railio.shop.web.appJson
import ai.railio.shop.web.dto.AgentEventDto
import ai.railio.shop.web.dto.ChatRequest
import ai.railio.shop.web.dto.toDto
import io.ktor.http.ContentType
import io.ktor.server.request.receive
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * Chat endpoint. Accepts a user message and streams the agent's response as
 * Server-Sent Events: each event's `data:` line is a JSON [AgentEventDto]
 * (token, product_cards, payment_form, payment_result, error, done).
 *
 * The resolved session id is returned in the `X-Session-Id` response header so a
 * new client can persist it and keep conversation continuity.
 */
fun Route.chatRoutes(agent: ChatAgent) {

    post("/api/chat") {
        val req = call.receive<ChatRequest>()
        val sessionId = req.sessionId.ifBlank { UUID.randomUUID().toString() }
        call.response.headers.append("X-Session-Id", sessionId)
        call.response.headers.append("Cache-Control", "no-cache")
        call.response.headers.append("Connection", "keep-alive")

        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            agent.chat(sessionId, req.message).collect { event ->
                val payload = appJson.encodeToString<AgentEventDto>(event.toDto())
                write("data: $payload\n\n")
                flush()
            }
        }
    }
}
