package ai.railio.shop.infrastructure.payment

import ai.railio.shop.domain.catalog.Money
import ai.railio.shop.domain.payment.PaymentException
import ai.railio.shop.domain.payment.PaymentProvider
import ai.railio.shop.domain.payment.PaymentSession
import ai.railio.shop.domain.payment.PaymentState
import ai.railio.shop.infrastructure.config.AppConfig
import org.koin.core.annotation.Single
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory mock of an Iranian card PSP (Shaparak/IPG-style).
 *
 * Simulates the real hosted-card sequence — card details (number + expiry +
 * CVV2) submitted together, then OTP verification — but
 * keeps everything local and issues a fixed OTP (from [AppConfig.mockOtp],
 * default `12345`). Sensitive inputs are validated and held only in a transient
 * server-side record; only a masked PAN ever leaves the provider.
 *
 * ### Replacing with a real provider
 * A production adapter implements the same [PaymentProvider] interface. The
 * mapping is direct:
 * - `createPayment` → create a transaction / obtain a gateway token.
 * - `submitCardDetails` → card number, expiry and CVV2 are collected together on
 *   the bank's hosted page (never touching your server in a real PCI flow) or via
 *   a tokenization SDK.
 * - `requestOtp` → the bank sends the OTP over SMS.
 * - `verifyOtp` → submit the OTP to the gateway to capture the transaction.
 *
 * Swap by providing a different [PaymentProvider] `@Single` in the Koin graph.
 */
@Single(binds = [PaymentProvider::class])
class MockPaymentProvider(config: AppConfig) : PaymentProvider {

    private val expectedOtp: String = config.mockOtp

    /** Transient server-side record; not exposed to callers. */
    private data class Record(
        val session: PaymentSession,
        val cardNumber: String? = null,
        val expiry: String? = null,
        val cvv2: String? = null,
        val otpIssued: Boolean = false,
    )

    private val records = ConcurrentHashMap<String, Record>()

    override fun createPayment(orderId: String, amount: Money): PaymentSession {
        val session = PaymentSession(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            amount = amount,
            state = PaymentState.AWAITING_CARD_DETAILS,
        )
        records[session.id] = Record(session)
        return session
    }

    override fun get(sessionId: String): PaymentSession? = records[sessionId]?.session

    override fun submitCardDetails(
        sessionId: String,
        cardNumber: String,
        expiry: String,
        cvv2: String,
    ): PaymentSession {
        val rec = require(sessionId, PaymentState.AWAITING_CARD_DETAILS)

        val pan = cardNumber.filter { it.isDigit() }
        if (pan.length != 16) throw PaymentException("Card number must be 16 digits.")
        if (!luhnValid(pan)) throw PaymentException("Card number failed the checksum check.")

        val exp = expiry.trim()
        if (!EXPIRY_REGEX.matches(exp)) throw PaymentException("Expiry must be in MM/YY format.")

        val cvv = cvv2.filter { it.isDigit() }
        if (cvv.length !in 3..4) throw PaymentException("CVV2 must be 3 or 4 digits.")

        // Details accepted → the bank "sends" the OTP over SMS.
        val updated = rec.copy(
            session = rec.session.copy(state = PaymentState.AWAITING_OTP, maskedCard = mask(pan)),
            cardNumber = pan,
            expiry = exp,
            cvv2 = cvv,
            otpIssued = true,
        )
        records[sessionId] = updated
        return updated.session
    }

    override fun requestOtp(sessionId: String): PaymentSession {
        val rec = require(sessionId, PaymentState.AWAITING_OTP)
        records[sessionId] = rec.copy(otpIssued = true)
        return rec.session
    }

    override fun verifyOtp(sessionId: String, otp: String): PaymentSession {
        val rec = require(sessionId, PaymentState.AWAITING_OTP)
        if (!rec.otpIssued) throw PaymentException("No OTP has been issued for this session.")
        val entered = otp.filter { it.isDigit() }
        val result = if (entered == expectedOtp) {
            rec.session.copy(state = PaymentState.SUCCESS)
        } else {
            rec.session.copy(state = PaymentState.FAILED, failureReason = "Incorrect OTP.")
        }
        records[sessionId] = rec.copy(session = result)
        return result
    }

    /** Fetches the record and asserts it is in [expected]; throws otherwise. */
    private fun require(sessionId: String, expected: PaymentState): Record {
        val rec = records[sessionId] ?: throw PaymentException("Unknown payment session '$sessionId'.")
        if (rec.session.state != expected) {
            throw PaymentException(
                "Payment step out of order: expected $expected but session is ${rec.session.state}.",
            )
        }
        return rec
    }

    /** Masks a 16-digit PAN as `NNNN-NN**-****-NNNN` (Iranian card grouping). */
    private fun mask(digits: String): String =
        "${digits.substring(0, 4)}-${digits.substring(4, 6)}**-****-${digits.takeLast(4)}"

    /** Luhn checksum validation for the PAN. */
    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    private companion object {
        val EXPIRY_REGEX = Regex("^(0[1-9]|1[0-2])/\\d{2}$")
    }
}
