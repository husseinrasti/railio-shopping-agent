package ai.railio.shop.payment

import ai.railio.shop.domain.catalog.Money
import ai.railio.shop.domain.payment.PaymentException
import ai.railio.shop.domain.payment.PaymentState
import ai.railio.shop.infrastructure.config.AppConfig
import ai.railio.shop.infrastructure.config.LlmProvider
import ai.railio.shop.infrastructure.payment.MockPaymentProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockPaymentProviderTest {

    private val config = AppConfig(
        llmProvider = LlmProvider.OLLAMA,
        llmModel = "test",
        ollamaBaseUrl = "http://localhost:11434",
        openAiApiKey = "",
        mockOtp = "12345",
        agentSecret = "s",
        corsOrigins = listOf("http://localhost:3000"),
        unloadModelAfterResponse = false,
    )

    private fun provider() = MockPaymentProvider(config)

    // A valid Luhn-passing test PAN.
    private val validPan = "4111111111111111"

    @Test
    fun `happy path completes with correct otp`() {
        val p = provider()
        var s = p.createPayment("elec-001", Money.rial(42_000_000))
        assertEquals(PaymentState.AWAITING_CARD_DETAILS, s.state)

        // Card number + expiry + CVV2 are submitted together; provider issues the OTP.
        s = p.submitCardDetails(s.id, validPan, "05/28", "123")
        assertEquals(PaymentState.AWAITING_OTP, s.state)
        val masked = s.maskedCard
        assertNotNull(masked)
        assertTrue(masked.endsWith("1111"))

        s = p.verifyOtp(s.id, "12345")
        assertEquals(PaymentState.SUCCESS, s.state)
        assertTrue(s.state.isTerminal)
    }

    @Test
    fun `wrong otp fails the payment`() {
        val p = provider()
        val created = p.createPayment("o1", Money.rial(1000))
        p.submitCardDetails(created.id, validPan, "05/28", "123")
        val s = p.verifyOtp(created.id, "00000")
        assertEquals(PaymentState.FAILED, s.state)
        assertEquals("Incorrect OTP.", s.failureReason)
    }

    @Test
    fun `steps out of order are rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        // OTP before card details is not allowed.
        assertFailsWith<PaymentException> { p.verifyOtp(s.id, "12345") }
    }

    @Test
    fun `invalid card number is rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        assertFailsWith<PaymentException> { p.submitCardDetails(s.id, "1234", "05/28", "123") }
        assertFailsWith<PaymentException> { p.submitCardDetails(s.id, "1234567812345678", "05/28", "123") }
    }

    @Test
    fun `invalid expiry and cvv2 are rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        assertFailsWith<PaymentException> { p.submitCardDetails(s.id, validPan, "13/28", "123") } // bad month
        assertFailsWith<PaymentException> { p.submitCardDetails(s.id, validPan, "05/28", "12") }  // short cvv2
    }

    @Test
    fun `unknown session returns null and throws on mutation`() {
        val p = provider()
        assertNull(p.get("nope"))
        assertFailsWith<PaymentException> { p.submitCardDetails("nope", validPan, "05/28", "123") }
    }

    @Test
    fun `custom otp from config is honoured`() {
        val p = MockPaymentProvider(config.copy(mockOtp = "99999"))
        val created = p.createPayment("o1", Money.rial(1000))
        p.submitCardDetails(created.id, validPan, "05/28", "123")
        assertEquals(PaymentState.SUCCESS, p.verifyOtp(created.id, "99999").state)
    }
}
