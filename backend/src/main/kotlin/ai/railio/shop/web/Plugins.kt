package ai.railio.shop.web

import ai.railio.shop.domain.payment.PaymentException
import ai.railio.shop.infrastructure.config.AppConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/**
 * Shared JSON configuration used both for HTTP content negotiation and for
 * encoding SSE payloads, so the wire format is identical on every channel.
 *
 * `classDiscriminator = "type"` drives the polymorphic `AgentEventDto` encoding
 * consumed by the frontend.
 */
val appJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

/** JSON (de)serialization for REST endpoints. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(appJson)
    }
}

/** CORS for the web UI origins configured via `CORS_ORIGINS`. */
fun Application.configureCors(config: AppConfig) {
    install(CORS) {
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Options)
        exposeHeader("X-Session-Id")
        if (config.corsOrigins.any { it == "*" }) {
            anyHost()
        } else {
            config.corsOrigins.forEach { origin ->
                val (scheme, host) = origin.substringBefore("://", "http") to origin.substringAfter("://")
                allowHost(host, schemes = listOf(scheme))
            }
        }
    }
}

/** Request logging at INFO. */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.local.uri.startsWith("/api") }
    }
}

/** Maps domain/validation errors to friendly JSON responses. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<PaymentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid payment request")))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid request")))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error: {}", cause.message, cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }
}

/** Content type used for the chat SSE stream. */
val EventStreamContentType: ContentType = ContentType.Text.EventStream
