package ai.railio.shop.infrastructure.agent

import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.application.payment.PaymentService
import ai.railio.shop.domain.catalog.Product
import ai.railio.shop.domain.chat.AgentEvent
import ai.railio.shop.domain.payment.PaymentException
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * The catalog + checkout tools exposed to the LLM.
 *
 * A fresh instance is created per chat turn, bound to that turn's
 * [AgentEventSink], so tools can push structured UI events (product cards,
 * payment forms) into the stream while also returning a concise text summary for
 * the model to reason over.
 *
 * Tool descriptions are written for the model: they explain when to call each
 * tool and what the arguments mean via [LLMDescription].
 */
@LLMDescription("Tools for searching a product catalog and running a card checkout.")
class ShoppingTools(
    private val catalog: CatalogService,
    private val payments: PaymentService,
    private val sink: AgentEventSink,
) : ToolSet {

    @Tool
    @LLMDescription(
        "Search the store catalog for products. Call this whenever the user is looking for " +
            "something to buy. The matching products are shown to the user as cards automatically.",
    )
    fun searchProducts(
        @LLMDescription("What the user is looking for, e.g. 'wireless headphones'. Use an empty string to list everything.")
        query: String,
        @LLMDescription("Optional category filter: electronics, fashion, home, or books.")
        category: String = "",
        @LLMDescription("Optional maximum price in Toman. 0 means no limit.")
        maxPrice: Long = 0,
    ): String {
        val results = catalog.search(query, category.ifBlank { null }, maxPrice)
        if (results.isNotEmpty()) sink.emit(AgentEvent.ProductCards(results))
        return if (results.isEmpty()) {
            "No products matched '$query'."
        } else {
            "Found ${results.size} product(s):\n" + results.joinToString("\n") { it.forModel() }
        }
    }

    @Tool
    @LLMDescription("List the available product categories in the store.")
    fun listCategories(): String =
        "Categories: " + catalog.categories().joinToString(", ") { it.displayName }

    @Tool
    @LLMDescription("Get full details for one product by its id. Shows the product card to the user.")
    fun getProduct(
        @LLMDescription("The product id, e.g. 'elec-001'.")
        productId: String,
    ): String {
        val product = catalog.get(productId) ?: return "No product with id '$productId'."
        sink.emit(AgentEvent.ProductCards(listOf(product)))
        return product.forModel()
    }

    @Tool
    @LLMDescription(
        "Start checkout for a product. Creates a payment session and shows the user a secure " +
            "card form. Return the session id so later steps can reference it.",
    )
    fun startCheckout(
        @LLMDescription("The id of the product to buy, e.g. 'elec-001'.")
        productId: String,
    ): String = runCatching {
        val session = payments.startCheckout(productId)
        sink.emit(
            AgentEvent.PaymentForm(
                sessionId = session.id,
                state = session.state,
                orderId = session.orderId,
                amountRial = session.amount.amount,
                maskedCard = session.maskedCard,
            ),
        )
        "Checkout started. Payment session '${session.id}' is awaiting the card details. " +
            "A secure card form has been shown to the user."
    }.getOrElse { it.toToolMessage() }

    @Tool
    @LLMDescription(
        "Submit the card number, expiry (MM/YY) and CVV2 together for a payment session. " +
            "This triggers the OTP being sent to the cardholder.",
    )
    fun submitCardDetails(
        @LLMDescription("The payment session id from startCheckout.") sessionId: String,
        @LLMDescription("The 16-digit card number.") cardNumber: String,
        @LLMDescription("Expiry date in MM/YY format.") expiry: String,
        @LLMDescription("The 3 or 4 digit CVV2.") cvv2: String,
    ): String = advance(sessionId) { payments.submitCardDetails(sessionId, cardNumber, expiry, cvv2) }

    @Tool
    @LLMDescription("Submit the OTP to complete a payment session.")
    fun submitOtp(
        @LLMDescription("The payment session id.") sessionId: String,
        @LLMDescription("The one-time password sent to the cardholder.") otp: String,
    ): String {
        return runCatching {
            val session = payments.submitOtp(sessionId, otp)
            val success = session.state.name == "SUCCESS"
            sink.emit(
                AgentEvent.PaymentResult(
                    sessionId = sessionId,
                    success = success,
                    message = if (success) "Payment successful." else (session.failureReason ?: "Payment failed."),
                ),
            )
            if (success) "Payment for order '${session.orderId}' completed successfully."
            else "Payment failed: ${session.failureReason ?: "unknown reason"}."
        }.getOrElse { it.toToolMessage() }
    }

    /** Runs a payment step and re-emits the next [AgentEvent.PaymentForm]. */
    private inline fun advance(sessionId: String, step: () -> Unit): String = runCatching {
        step()
        val session = payments.get(sessionId) ?: throw PaymentException("Unknown session '$sessionId'.")
        sink.emit(
            AgentEvent.PaymentForm(
                sessionId = session.id,
                state = session.state,
                orderId = session.orderId,
                amountRial = session.amount.amount,
                maskedCard = session.maskedCard,
            ),
        )
        "Accepted. The payment is now at step ${session.state.name}."
    }.getOrElse { it.toToolMessage() }

    private fun Product.forModel(): String =
        "- $name (id: $id) — ${price.toman} Toman — ${if (inStock) "in stock" else "out of stock"}"

    private fun Throwable.toToolMessage(): String =
        (this as? PaymentException)?.message ?: "Something went wrong: ${message ?: "unknown error"}."
}
