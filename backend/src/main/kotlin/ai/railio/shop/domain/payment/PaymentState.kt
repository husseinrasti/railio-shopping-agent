package ai.railio.shop.domain.payment

/**
 * Lifecycle states of an Iranian card payment.
 *
 * The flow mirrors a real Shaparak/IPG-style transaction: the cardholder enters
 * the card number, expiry and CVV2 together on the bank page; the bank then
 * issues an OTP over SMS, which is entered to complete the payment.
 *
 * ```
 * create → AWAITING_CARD_DETAILS → (OTP issued) → AWAITING_OTP → SUCCESS | FAILED
 * ```
 */
enum class PaymentState {
    /** Session created; waiting for card number + expiry + CVV2 (submitted together). */
    AWAITING_CARD_DETAILS,

    /** Card details accepted and an OTP has been issued; waiting for OTP entry. */
    AWAITING_OTP,

    /** OTP verified; payment captured successfully. Terminal. */
    SUCCESS,

    /** Payment failed (wrong OTP, cancelled, or validation error). Terminal. */
    FAILED;

    /** Whether this is a terminal state with no further transitions. */
    val isTerminal: Boolean get() = this == SUCCESS || this == FAILED
}
