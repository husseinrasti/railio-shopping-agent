package ai.railio.shop.infrastructure.catalog

import ai.railio.shop.domain.catalog.Category
import ai.railio.shop.domain.catalog.Money
import ai.railio.shop.domain.catalog.Product
import kotlinx.serialization.Serializable

/**
 * Wire/JSON representation of a product as stored in `products.json`.
 *
 * Kept separate from the domain [Product] so the storage format can evolve
 * independently. A future database row/entity would map to the domain the same
 * way [toDomain] does here.
 *
 * @property priceRial price in Rial (integer, avoids float rounding).
 */
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val priceRial: Long,
    val imageUrl: String,
    val stock: Int,
    val rating: Double,
    val attributes: Map<String, String> = emptyMap(),
) {
    /** Maps this DTO to the domain [Product], resolving the category slug. */
    fun toDomain(): Product {
        val cat = Category.fromSlug(category)
            ?: error("Unknown category slug '$category' for product '$id'")
        return Product(
            id = id,
            name = name,
            description = description,
            category = cat,
            price = Money.rial(priceRial),
            imageUrl = imageUrl,
            stock = stock,
            rating = rating,
            attributes = attributes,
        )
    }
}
