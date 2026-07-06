package ai.railio.shop.web.dto

import ai.railio.shop.domain.chat.AgentEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of an [AgentEvent], streamed as the `data:` payload of each
 * Server-Sent Event. A polymorphic `type` discriminator lets the frontend switch
 * on the event kind (token / product_cards / payment_form / ...).
 */
@Serializable
sealed interface AgentEventDto {

    @Serializable
    @SerialName("token")
    data class Token(val text: String) : AgentEventDto

    @Serializable
    @SerialName("product_cards")
    data class ProductCards(val products: List<ProductResponse>) : AgentEventDto

    @Serializable
    @SerialName("payment_form")
    data class PaymentForm(
        val sessionId: String,
        val state: String,
        val orderId: String,
        val amountRial: Long,
        val amountToman: Long,
        val maskedCard: String? = null,
    ) : AgentEventDto

    @Serializable
    @SerialName("payment_result")
    data class PaymentResult(
        val sessionId: String,
        val success: Boolean,
        val message: String,
    ) : AgentEventDto

    @Serializable
    @SerialName("trace")
    data class Trace(val category: String, val message: String, val ts: Long) : AgentEventDto

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : AgentEventDto

    @Serializable
    @SerialName("done")
    data object Done : AgentEventDto
}

/** Maps a domain [AgentEvent] to its serializable wire form. */
fun AgentEvent.toDto(): AgentEventDto = when (this) {
    is AgentEvent.Token -> AgentEventDto.Token(text)
    is AgentEvent.ProductCards -> AgentEventDto.ProductCards(products.map { it.toResponse() })
    is AgentEvent.PaymentForm -> AgentEventDto.PaymentForm(
        sessionId = sessionId,
        state = state.name,
        orderId = orderId,
        amountRial = amountRial,
        amountToman = amountRial / 10,
        maskedCard = maskedCard,
    )
    is AgentEvent.PaymentResult -> AgentEventDto.PaymentResult(sessionId, success, message)
    is AgentEvent.Trace -> AgentEventDto.Trace(category, message, System.currentTimeMillis())
    is AgentEvent.Error -> AgentEventDto.Error(message)
    AgentEvent.Done -> AgentEventDto.Done
}
