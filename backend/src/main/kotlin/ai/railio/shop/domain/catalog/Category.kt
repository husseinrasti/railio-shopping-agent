package ai.railio.shop.domain.catalog

/**
 * Product categories offered by the store.
 *
 * Kept as an enum for type-safety in the domain. The wire/JSON representation
 * uses the lowercase [slug]; a future database schema can persist the same slug
 * as a foreign key without touching domain code.
 *
 * @property slug stable, lowercase identifier used in JSON and API payloads.
 * @property displayName human-friendly label shown in the UI.
 */
enum class Category(val slug: String, val displayName: String) {
    ELECTRONICS("electronics", "Electronics"),
    FASHION("fashion", "Fashion"),
    HOME("home", "Home & Kitchen"),
    BOOKS("books", "Books");

    companion object {
        /** Resolves a [Category] from its [slug], case-insensitively, or `null`. */
        fun fromSlug(slug: String?): Category? =
            slug?.let { s -> entries.firstOrNull { it.slug.equals(s.trim(), ignoreCase = true) } }
    }
}
