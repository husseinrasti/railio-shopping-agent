package ai.railio.shop.web.routes

import ai.railio.shop.application.payment.PaymentService
import ai.railio.shop.domain.payment.PaymentException
import ai.railio.shop.web.dto.PaymentStepRequest
import ai.railio.shop.web.dto.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

/** Body for starting checkout. */
@Serializable
private data class CheckoutRequest(val productId: String)

/**
 * REST endpoints that drive the Iranian card payment flow directly from the UI
 * form. Each step advances the [PaymentService] state machine and returns the
 * updated session. [PaymentException]s are mapped to 400 by the status-pages
 * plugin.
 */
fun Route.paymentRoutes(payments: PaymentService) {

    // POST /api/payment/checkout — create a session for a product.
    post("/api/payment/checkout") {
        val req = call.receive<CheckoutRequest>()
        call.respond(payments.startCheckout(req.productId).toResponse())
    }

    // GET /api/payment/{sessionId} — current session state.
    get("/api/payment/{sessionId}") {
        val id = call.parameters["sessionId"].orEmpty()
        val session = payments.get(id)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session '$id' not found"))
        } else {
            call.respond(session.toResponse())
        }
    }

    // POST /api/payment/{sessionId}/{step} — submit one step (card|expiry|cvv2|otp).
    post("/api/payment/{sessionId}/{step}") {
        val id = call.parameters["sessionId"].orEmpty()
        val step = call.parameters["step"].orEmpty()
        val value = call.receive<PaymentStepRequest>().value
        val session = when (step) {
            "card" -> payments.submitCard(id, value)
            "expiry" -> payments.submitExpiry(id, value)
            "cvv2" -> payments.submitCvv2(id, value)
            "otp" -> payments.submitOtp(id, value)
            "resend-otp" -> payments.resendOtp(id)
            else -> throw PaymentException("Unknown payment step '$step'.")
        }
        call.respond(session.toResponse())
    }
}
