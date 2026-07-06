package ai.railio.shop.web.dto

import ai.railio.shop.domain.catalog.Category
import ai.railio.shop.domain.catalog.Product
import ai.railio.shop.domain.payment.PaymentSession
import kotlinx.serialization.Serializable

/** Product as returned by the REST API and embedded in chat product-card events. */
@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val categoryLabel: String,
    val priceRial: Long,
    val priceToman: Long,
    val currency: String,
    val imageUrl: String,
    val stock: Int,
    val inStock: Boolean,
    val rating: Double,
    val attributes: Map<String, String>,
)

/** A store category. */
@Serializable
data class CategoryResponse(val slug: String, val label: String)

/** Public view of a payment session (never contains PAN/CVV2/OTP). */
@Serializable
data class PaymentResponse(
    val sessionId: String,
    val orderId: String,
    val amountRial: Long,
    val amountToman: Long,
    val state: String,
    val maskedCard: String? = null,
    val failureReason: String? = null,
)

/** Chat request body. A blank [sessionId] means "start a new session". */
@Serializable
data class ChatRequest(val sessionId: String = "", val message: String)

/** Body for a single payment step submission. */
@Serializable
data class PaymentStepRequest(val value: String)

/** Maps a domain [Product] to its API representation. */
fun Product.toResponse(): ProductResponse = ProductResponse(
    id = id,
    name = name,
    description = description,
    category = category.slug,
    categoryLabel = category.displayName,
    priceRial = price.amount,
    priceToman = price.toman,
    currency = price.currency,
    imageUrl = imageUrl,
    stock = stock,
    inStock = inStock,
    rating = rating,
    attributes = attributes,
)

/** Maps a [Category] to its API representation. */
fun Category.toResponse(): CategoryResponse = CategoryResponse(slug, displayName)

/** Maps a domain [PaymentSession] to its API representation. */
fun PaymentSession.toResponse(): PaymentResponse = PaymentResponse(
    sessionId = id,
    orderId = orderId,
    amountRial = amount.amount,
    amountToman = amount.toman,
    state = state.name,
    maskedCard = maskedCard,
    failureReason = failureReason,
)
