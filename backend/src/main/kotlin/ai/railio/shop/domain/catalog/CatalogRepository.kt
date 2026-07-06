package ai.railio.shop.domain.catalog

/**
 * Read model over the product catalog.
 *
 * This interface is the seam that keeps the catalog storage swappable: the
 * current implementation is JSON-backed
 * ([ai.railio.shop.infrastructure.catalog.JsonCatalogRepository]), but a future
 * database-backed implementation (e.g. Exposed/Postgres) can replace it without
 * changing any application or domain code.
 *
 * Implementations must be safe for concurrent reads.
 */
interface CatalogRepository {

    /** Returns every product in the catalog. */
    fun findAll(): List<Product>

    /** Returns the product with [id], or `null` if none exists. */
    fun findById(id: String): Product?

    /** Returns all products in [category]. */
    fun findByCategory(category: Category): List<Product>

    /**
     * Full-text-ish search over name, description and category.
     *
     * @param query free text; blank matches nothing special (see [limit]).
     * @param category optional filter applied in addition to [query].
     * @param limit maximum number of results to return.
     */
    fun search(query: String, category: Category? = null, limit: Int = 10): List<Product>

    /** Returns the distinct categories that currently have at least one product. */
    fun categories(): List<Category>
}
