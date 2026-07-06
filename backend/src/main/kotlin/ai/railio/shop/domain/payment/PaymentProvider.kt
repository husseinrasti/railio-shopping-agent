package ai.railio.shop.domain.payment

import ai.railio.shop.domain.catalog.Money

/**
 * Payment provider abstraction modelled on a real Iranian PSP (Shaparak/IPG).
 *
 * The method sequence deliberately matches the real hosted-card flow so that a
 * production adapter can implement the same interface:
 *
 * ```
 * createPayment → setCardNumber → setExpiry → setCvv2 → requestOtp → verifyOtp
 * ```
 *
 * The mock implementation
 * ([ai.railio.shop.infrastructure.payment.MockPaymentProvider]) runs this
 * entirely in-memory and issues a fixed OTP. A real adapter would instead
 * redirect to the bank gateway; see the KDoc there for the mapping.
 *
 * Every mutating call validates the current [PaymentState] and throws
 * [PaymentException] on an out-of-order or malformed request.
 */
interface PaymentProvider {

    /** Creates a new session for [orderId] charging [amount]; state = AWAITING_CARD. */
    fun createPayment(orderId: String, amount: Money): PaymentSession

    /** Returns the current session for [sessionId], or `null` if unknown. */
    fun get(sessionId: String): PaymentSession?

    /** Supplies the 16-digit PAN; advances to AWAITING_EXPIRY. */
    fun setCardNumber(sessionId: String, cardNumber: String): PaymentSession

    /** Supplies the card expiry as `MM/YY`; advances to AWAITING_CVV2. */
    fun setExpiry(sessionId: String, expiry: String): PaymentSession

    /** Supplies the CVV2 (3–4 digits); advances to AWAITING_OTP and issues an OTP. */
    fun setCvv2(sessionId: String, cvv2: String): PaymentSession

    /**
     * (Re)issues the OTP for the session. Called implicitly by [setCvv2]; exposed
     * so a UI/agent can trigger a resend. Returns the session unchanged in state.
     */
    fun requestOtp(sessionId: String): PaymentSession

    /**
     * Verifies the [otp]. On success the session moves to
     * [PaymentState.SUCCESS]; on mismatch to [PaymentState.FAILED].
     */
    fun verifyOtp(sessionId: String, otp: String): PaymentSession
}
