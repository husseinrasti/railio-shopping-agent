package ai.railio.shop.infrastructure.catalog

import ai.railio.shop.domain.catalog.Category
import ai.railio.shop.domain.catalog.CatalogRepository
import ai.railio.shop.domain.catalog.Product
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * [CatalogRepository] backed by a JSON resource loaded once at startup.
 *
 * Products are read from `products.json` on the classpath, mapped to the domain,
 * and kept in an immutable in-memory index. Reads are therefore lock-free and
 * thread-safe.
 *
 * **Database-ready:** swapping to Postgres/Exposed means providing another
 * [CatalogRepository] `@Single` and removing this one from the Koin graph — no
 * domain or application change required.
 *
 */
@Single(binds = [CatalogRepository::class])
class JsonCatalogRepository : CatalogRepository {

    private val products: List<Product>
    private val byId: Map<String, Product>

    init {
        val json = Json { ignoreUnknownKeys = true }
        val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH)
            ?: error("Catalog resource '$RESOURCE_PATH' not found on classpath")
        val raw = stream.bufferedReader().use { it.readText() }
        products = json.decodeFromString<List<ProductDto>>(raw).map { it.toDomain() }
        byId = products.associateBy { it.id }
    }

    override fun findAll(): List<Product> = products

    override fun findById(id: String): Product? = byId[id]

    override fun findByCategory(category: Category): List<Product> =
        products.filter { it.category == category }

    override fun search(query: String, category: Category?, maxPriceToman: Long?, limit: Int): List<Product> {
        val q = query.trim().lowercase()
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        return products.asSequence()
            .filter { category == null || it.category == category }
            .filter { maxPriceToman == null || it.price.toman <= maxPriceToman }
            .map { it to it.relevance(terms) }
            .filter { (_, score) -> terms.isEmpty() || score > 0 }
            .sortedWith(compareByDescending<Pair<Product, Int>> { it.second }.thenByDescending { it.first.rating })
            .map { it.first }
            .take(limit)
            .toList()
    }

    override fun categories(): List<Category> =
        products.map { it.category }.distinct()

    /** Simple additive relevance: name matches weigh more than description/category. */
    private fun Product.relevance(terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val name = name.lowercase()
        val desc = description.lowercase()
        val cat = category.slug + " " + category.displayName.lowercase()
        return terms.sumOf { term ->
            var s = 0
            if (name.contains(term)) s += 3
            if (desc.contains(term)) s += 1
            if (cat.contains(term)) s += 2
            s
        }
    }

    private companion object {
        const val RESOURCE_PATH = "products.json"
    }
}
