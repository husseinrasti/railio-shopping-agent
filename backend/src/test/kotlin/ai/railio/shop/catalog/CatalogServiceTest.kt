package ai.railio.shop.catalog

import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.infrastructure.catalog.JsonCatalogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogServiceTest {

    private val service = CatalogService(JsonCatalogRepository())

    @Test
    fun `loads the full catalog`() {
        assertEquals(20, service.list().size)
    }

    @Test
    fun `exposes four categories`() {
        assertEquals(4, service.categories().size)
    }

    @Test
    fun `search matches by name`() {
        val results = service.search("headphones")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().name.contains("Headphones", ignoreCase = true))
    }

    @Test
    fun `search can be filtered by category`() {
        val books = service.search("", categorySlug = "books", limit = 50)
        assertTrue(books.isNotEmpty())
        assertTrue(books.all { it.category.slug == "books" })
    }

    @Test
    fun `search honours the limit`() {
        assertEquals(2, service.search("", categorySlug = "electronics", limit = 2).size)
    }

    @Test
    fun `search filters by max price in Toman`() {
        val cheap = service.search("", maxPriceToman = 2_000_000, limit = 50)
        assertTrue(cheap.isNotEmpty())
        assertTrue(cheap.all { it.price.toman <= 2_000_000 })
        // A zero/negative cap is treated as "no limit".
        assertEquals(service.list().size, service.search("", maxPriceToman = 0, limit = 50).size)
    }

    @Test
    fun `get returns a known product and null for unknown`() {
        assertNotNull(service.get("elec-001"))
        assertNull(service.get("does-not-exist"))
    }
}
