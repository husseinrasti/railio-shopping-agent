package ai.railio.shop.domain.catalog

/**
 * Monetary amount stored as an integer number of minor-less Rial units.
 *
 * Iranian pricing is quoted in Rial (IRR) but commonly displayed in Toman
 * (1 Toman = 10 Rial). Amounts are kept as [Long] to avoid floating-point
 * rounding errors; formatting for display is a presentation concern.
 *
 * @property amount value in Rial.
 * @property currency ISO-ish currency code; defaults to `IRR`.
 */
data class Money(
    val amount: Long,
    val currency: String = "IRR",
) {
    init {
        require(amount >= 0) { "Money amount must be non-negative, was $amount" }
    }

    /** Value expressed in Toman (Rial / 10), the common consumer-facing unit. */
    val toman: Long get() = amount / 10

    companion object {
        /** Convenience constructor for a Rial amount. */
        fun rial(amount: Long): Money = Money(amount, "IRR")

        /** Convenience constructor for a Toman amount, converted to Rial. */
        fun toman(amount: Long): Money = Money(amount * 10, "IRR")
    }
}
