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

/** Body for the combined card-details step. */
@Serializable
private data class CardDetailsRequest(val cardNumber: String, val expiry: String, val cvv2: String)

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

    // POST /api/payment/{sessionId}/card-details — submit card number, expiry and CVV2 together.
    post("/api/payment/{sessionId}/card-details") {
        val id = call.parameters["sessionId"].orEmpty()
        val req = call.receive<CardDetailsRequest>()
        call.respond(payments.submitCardDetails(id, req.cardNumber, req.expiry, req.cvv2).toResponse())
    }

    // POST /api/payment/{sessionId}/{step} — submit the OTP, or resend it.
    post("/api/payment/{sessionId}/{step}") {
        val id = call.parameters["sessionId"].orEmpty()
        val step = call.parameters["step"].orEmpty()
        val session = when (step) {
            "otp" -> payments.submitOtp(id, call.receive<PaymentStepRequest>().value)
            "resend-otp" -> payments.resendOtp(id)
            else -> throw PaymentException("Unknown payment step '$step'.")
        }
        call.respond(session.toResponse())
    }
}
