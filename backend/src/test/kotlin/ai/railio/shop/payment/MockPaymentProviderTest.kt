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
        assertEquals(PaymentState.AWAITING_CARD, s.state)

        s = p.setCardNumber(s.id, validPan)
        assertEquals(PaymentState.AWAITING_EXPIRY, s.state)
        val masked = s.maskedCard
        assertNotNull(masked)
        assertTrue(masked.endsWith("1111"))

        s = p.setExpiry(s.id, "05/28")
        assertEquals(PaymentState.AWAITING_CVV2, s.state)

        s = p.setCvv2(s.id, "123")
        assertEquals(PaymentState.AWAITING_OTP, s.state)

        s = p.verifyOtp(s.id, "12345")
        assertEquals(PaymentState.SUCCESS, s.state)
        assertTrue(s.state.isTerminal)
    }

    @Test
    fun `wrong otp fails the payment`() {
        val p = provider()
        val created = p.createPayment("o1", Money.rial(1000))
        p.setCardNumber(created.id, validPan)
        p.setExpiry(created.id, "05/28")
        p.setCvv2(created.id, "123")
        val s = p.verifyOtp(created.id, "00000")
        assertEquals(PaymentState.FAILED, s.state)
        assertEquals("Incorrect OTP.", s.failureReason)
    }

    @Test
    fun `steps out of order are rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        // Expiry before card number is not allowed.
        assertFailsWith<PaymentException> { p.setExpiry(s.id, "05/28") }
    }

    @Test
    fun `invalid card number is rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        assertFailsWith<PaymentException> { p.setCardNumber(s.id, "1234") }        // too short
        assertFailsWith<PaymentException> { p.setCardNumber(s.id, "1234567812345678") } // bad checksum
    }

    @Test
    fun `invalid expiry and cvv2 are rejected`() {
        val p = provider()
        val s = p.createPayment("o1", Money.rial(1000))
        p.setCardNumber(s.id, validPan)
        assertFailsWith<PaymentException> { p.setExpiry(s.id, "13/28") } // month out of range
        p.setExpiry(s.id, "05/28")
        assertFailsWith<PaymentException> { p.setCvv2(s.id, "12") }      // too short
    }

    @Test
    fun `unknown session returns null and throws on mutation`() {
        val p = provider()
        assertNull(p.get("nope"))
        assertFailsWith<PaymentException> { p.setCardNumber("nope", validPan) }
    }

    @Test
    fun `custom otp from config is honoured`() {
        val p = MockPaymentProvider(config.copy(mockOtp = "99999"))
        val created = p.createPayment("o1", Money.rial(1000))
        p.setCardNumber(created.id, validPan)
        p.setExpiry(created.id, "05/28")
        p.setCvv2(created.id, "123")
        assertEquals(PaymentState.SUCCESS, p.verifyOtp(created.id, "99999").state)
    }
}
