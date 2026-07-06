package ai.railio.shop.domain.payment

import ai.railio.shop.domain.catalog.Money

/**
 * Payment provider abstraction modelled on a real Iranian PSP (Shaparak/IPG).
 *
 * The method sequence deliberately matches the real hosted-card flow so that a
 * production adapter can implement the same interface:
 *
 * ```
 * createPayment → submitCardDetails → (OTP issued) → verifyOtp
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

    /** Creates a new session for [orderId] charging [amount]; state = AWAITING_CARD_DETAILS. */
    fun createPayment(orderId: String, amount: Money): PaymentSession

    /** Returns the current session for [sessionId], or `null` if unknown. */
    fun get(sessionId: String): PaymentSession?

    /**
     * Supplies the full card details together (PAN, expiry `MM/YY`, CVV2). On
     * success the details are validated, an OTP is issued (as a real PSP would
     * send over SMS), and the session advances to [PaymentState.AWAITING_OTP].
     */
    fun submitCardDetails(
        sessionId: String,
        cardNumber: String,
        expiry: String,
        cvv2: String,
    ): PaymentSession

    /**
     * (Re)issues the OTP for the session. Called implicitly by [submitCardDetails];
     * exposed so a UI/agent can trigger a resend. State is left unchanged.
     */
    fun requestOtp(sessionId: String): PaymentSession

    /**
     * Verifies the [otp]. On success the session moves to
     * [PaymentState.SUCCESS]; on mismatch to [PaymentState.FAILED].
     */
    fun verifyOtp(sessionId: String, otp: String): PaymentSession
}
