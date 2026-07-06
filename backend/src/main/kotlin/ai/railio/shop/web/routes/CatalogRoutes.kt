package ai.railio.shop.web.routes

import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.web.dto.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * REST endpoints for direct catalog browsing (used by tests and any non-chat UI).
 * The chat flow reaches the same [CatalogService] through the agent's tools.
 */
fun Route.catalogRoutes(catalog: CatalogService) {

    // GET /api/catalog?q=&category=&limit=  — list or search products.
    get("/api/catalog") {
        val q = call.request.queryParameters["q"]
        val category = call.request.queryParameters["category"]
        val maxPriceToman = call.request.queryParameters["maxPriceToman"]?.toLongOrNull()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val products = if (q.isNullOrBlank() && category.isNullOrBlank() && maxPriceToman == null) {
            catalog.list()
        } else {
            catalog.search(q.orEmpty(), category, maxPriceToman, limit)
        }
        call.respond(products.map { it.toResponse() })
    }

    // GET /api/catalog/{id} — one product.
    get("/api/catalog/{id}") {
        val id = call.parameters["id"].orEmpty()
        val product = catalog.get(id)
        if (product == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Product '$id' not found"))
        } else {
            call.respond(product.toResponse())
        }
    }

    // GET /api/categories — available categories.
    get("/api/categories") {
        call.respond(catalog.categories().map { it.toResponse() })
    }
}
