package ai.railio.shop.domain.chat

import ai.railio.shop.domain.catalog.Product
import ai.railio.shop.domain.payment.PaymentState

/**
 * A single unit of agent output streamed to the client during one chat turn.
 *
 * The agent emits a mix of natural-language [Token]s and structured UI events
 * (product cards, payment forms). The web layer serializes each event to a
 * Server-Sent Event; the frontend renders tokens as text and structured events
 * as inline cards/forms. This is what makes product cards and payment forms
 * appear *inside* the chat stream.
 */
sealed interface AgentEvent {

    /** An incremental chunk of assistant prose (streamed token/delta). */
    data class Token(val text: String) : AgentEvent

    /** A set of products to render as cards, produced by a catalog tool. */
    data class ProductCards(val products: List<Product>) : AgentEvent

    /**
     * A request for the next payment input, produced by a checkout tool.
     *
     * @property sessionId the payment session to submit against.
     * @property state which field the flow is waiting for (drives the form step).
     * @property orderId the order/product being paid for.
     * @property amountRial amount to charge, in Rial.
     * @property maskedCard masked PAN once known, for display.
     */
    data class PaymentForm(
        val sessionId: String,
        val state: PaymentState,
        val orderId: String,
        val amountRial: Long,
        val maskedCard: String? = null,
    ) : AgentEvent

    /** Terminal payment outcome for a session. */
    data class PaymentResult(
        val sessionId: String,
        val success: Boolean,
        val message: String,
    ) : AgentEvent

    /** A recoverable error surfaced to the user as a chat message. */
    data class Error(val message: String) : AgentEvent

    /** Marks the end of the turn's stream. */
    data object Done : AgentEvent
}
