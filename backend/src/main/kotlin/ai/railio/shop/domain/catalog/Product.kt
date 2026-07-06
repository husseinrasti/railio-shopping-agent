package ai.railio.shop.domain.catalog

/**
 * A catalog product.
 *
 * This is a pure domain entity with no serialization or persistence annotations
 * so the domain stays framework-agnostic. Infrastructure adapters map to/from
 * this type (e.g. [ai.railio.shop.infrastructure.catalog.JsonCatalogRepository]).
 *
 * @property id stable unique identifier (also used by the agent's checkout tools).
 * @property name display name.
 * @property description short marketing description used for search and cards.
 * @property category the product's [Category].
 * @property price the product [Money] price.
 * @property imageUrl absolute or relative URL to a product image.
 * @property stock available units; `0` means out of stock.
 * @property rating average customer rating in the range `0.0..5.0`.
 * @property attributes free-form key/value specs (e.g. color, size) for display.
 */
data class Product(
    val id: String,
    val name: String,
    val description: String,
    val category: Category,
    val price: Money,
    val imageUrl: String,
    val stock: Int,
    val rating: Double,
    val attributes: Map<String, String> = emptyMap(),
) {
    /** Whether the product can currently be purchased. */
    val inStock: Boolean get() = stock > 0
}
