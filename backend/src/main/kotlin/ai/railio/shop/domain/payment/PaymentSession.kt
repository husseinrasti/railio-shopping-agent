package ai.railio.shop.domain.payment

import ai.railio.shop.domain.catalog.Money

/**
 * A single in-flight payment for one order.
 *
 * The session is an immutable snapshot; the [PaymentProvider] returns a new copy
 * on every step transition. Sensitive fields (PAN, CVV2, OTP) are intentionally
 * *not* stored here — a real PSP never returns them to the client. Only the
 * masked card and the current [state] are exposed.
 *
 * @property id opaque session identifier.
 * @property orderId identifier of the order/product being paid for.
 * @property amount the amount to charge.
 * @property state current [PaymentState].
 * @property maskedCard masked PAN (e.g. `6037-99**-****-1234`) once a card is set.
 * @property failureReason human-readable reason when [state] is [PaymentState.FAILED].
 */
data class PaymentSession(
    val id: String,
    val orderId: String,
    val amount: Money,
    val state: PaymentState,
    val maskedCard: String? = null,
    val failureReason: String? = null,
)

/**
 * Raised when a payment step is invalid: wrong state order, malformed input, or
 * an unknown session. Carried as a typed error so the application layer can turn
 * it into a friendly agent/UI message.
 */
class PaymentException(message: String) : RuntimeException(message)
