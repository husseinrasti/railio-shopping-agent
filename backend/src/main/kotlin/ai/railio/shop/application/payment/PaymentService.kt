package ai.railio.shop.application.payment

import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.domain.payment.PaymentException
import ai.railio.shop.domain.payment.PaymentProvider
import ai.railio.shop.domain.payment.PaymentSession
import org.koin.core.annotation.Single

/**
 * Application service driving the Iranian card payment flow.
 *
 * Wraps the [PaymentProvider] with catalog-aware checkout creation and a single
 * [submit] entry point per step. Keeps the agent tools and REST routes free of
 * provider-sequencing details.
 */
@Single
class PaymentService(
    private val provider: PaymentProvider,
    private val catalog: CatalogService,
) {

    /**
     * Starts checkout for [productId], creating a payment session priced from the
     * catalog.
     *
     * @throws PaymentException if the product is unknown or out of stock.
     */
    fun startCheckout(productId: String): PaymentSession {
        val product = catalog.get(productId)
            ?: throw PaymentException("Product '$productId' was not found.")
        if (!product.inStock) throw PaymentException("'${product.name}' is out of stock.")
        return provider.createPayment(orderId = product.id, amount = product.price)
    }

    /** Current session snapshot, or `null`. */
    fun get(sessionId: String): PaymentSession? = provider.get(sessionId)

    /** Submits the card number step. */
    fun submitCard(sessionId: String, cardNumber: String): PaymentSession =
        provider.setCardNumber(sessionId, cardNumber)

    /** Submits the expiry step. */
    fun submitExpiry(sessionId: String, expiry: String): PaymentSession =
        provider.setExpiry(sessionId, expiry)

    /** Submits the CVV2 step (triggers OTP issuance). */
    fun submitCvv2(sessionId: String, cvv2: String): PaymentSession =
        provider.setCvv2(sessionId, cvv2)

    /** Verifies the OTP, completing or failing the payment. */
    fun submitOtp(sessionId: String, otp: String): PaymentSession =
        provider.verifyOtp(sessionId, otp)

    /** Re-issues the OTP for the session. */
    fun resendOtp(sessionId: String): PaymentSession = provider.requestOtp(sessionId)
}
