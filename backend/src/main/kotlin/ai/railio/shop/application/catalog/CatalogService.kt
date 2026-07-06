package ai.railio.shop.application.catalog

import ai.railio.shop.domain.catalog.Category
import ai.railio.shop.domain.catalog.CatalogRepository
import ai.railio.shop.domain.catalog.Product
import org.koin.core.annotation.Single

/**
 * Application service for browsing and searching the catalog.
 *
 * Thin use-case layer over [CatalogRepository]. It exists so callers (agent
 * tools, REST routes) depend on a stable use-case API rather than the repository
 * directly, and so cross-cutting rules (result limits, category resolution) live
 * in one place.
 */
@Single
class CatalogService(private val repository: CatalogRepository) {

    /** All products. */
    fun list(): List<Product> = repository.findAll()

    /** A single product by [id], or `null`. */
    fun get(id: String): Product? = repository.findById(id)

    /** Categories that currently have products. */
    fun categories(): List<Category> = repository.categories()

    /**
     * Searches the catalog.
     *
     * @param query free-text query.
     * @param categorySlug optional category slug filter (resolved leniently).
     * @param limit max results (coerced to `1..50`).
     */
    fun search(query: String, categorySlug: String? = null, limit: Int = 10): List<Product> {
        val category = Category.fromSlug(categorySlug)
        return repository.search(query, category, limit.coerceIn(1, 50))
    }
}
