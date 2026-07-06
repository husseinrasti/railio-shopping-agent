package ai.railio.shop.domain.payment

/**
 * Lifecycle states of an Iranian card payment.
 *
 * The flow mirrors a real Shaparak/IPG-style transaction, collected step by
 * step: card number → expiry → CVV2 → OTP verification. Each state names the
 * single piece of information the provider is currently waiting for.
 */
enum class PaymentState {
    /** Session created; waiting for the 16-digit card number (PAN). */
    AWAITING_CARD,

    /** Card accepted; waiting for the card expiry (MM/YY). */
    AWAITING_EXPIRY,

    /** Expiry accepted; waiting for the CVV2 (3–4 digits). */
    AWAITING_CVV2,

    /** CVV2 accepted and an OTP has been issued; waiting for OTP entry. */
    AWAITING_OTP,

    /** OTP verified; payment captured successfully. Terminal. */
    SUCCESS,

    /** Payment failed (wrong OTP, cancelled, or validation error). Terminal. */
    FAILED;

    /** Whether this is a terminal state with no further transitions. */
    val isTerminal: Boolean get() = this == SUCCESS || this == FAILED
}
