package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.ui.screens.send.PaymentRequestError.INVALID_ADDRESS
import one.monero.moneroone.ui.screens.send.PaymentRequestError.INVALID_AMOUNT
import one.monero.moneroone.ui.screens.send.PaymentRequestError.NOT_PAYMENT_LINK
import one.monero.moneroone.ui.screens.send.PaymentRequestError.PAYMENT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentRequestTest {
    private val address = "86AWuSFkMKCNp4e7dWho3CBvFpvAzj8hnZNWM9fedD5LKb2mXVfnmH9XuDD9zYqzzR6LAFxUSsdGTVUDABzcgjMfFVfBHpP"
    private val valid: (String) -> Boolean = { it == address }

    private fun read(input: String) = readPaymentRequest(input, valid)
    private fun error(input: String) = (read(input) as? PaymentRequestResult.Invalid)?.error

    @Test fun readsAddressAndAmount() {
        val result = read("monero:$address?tx_amount=0.25&tx_description=Coffee")
        assertEquals(
            PaymentRequestResult.Valid(MoneroUriData(address, "0.25", description = "Coffee")),
            result
        )
    }

    @Test fun addressErrorsSayTheAddressIsBad() {
        assertEquals(INVALID_ADDRESS, error("monero:${address.dropLast(1)}"))
        assertEquals(INVALID_ADDRESS, error("monero:${address.replaceFirst('8', '9')}"))
        // Right shape, failed checksum or network.
        assertEquals(INVALID_ADDRESS, readPaymentRequest("monero:$address") { false }.let { (it as PaymentRequestResult.Invalid).error })
    }

    @Test fun amountErrorsSayTheAmountIsBad() {
        assertEquals(INVALID_AMOUNT, error("monero:$address?tx_amount=abc"))
        assertEquals(INVALID_AMOUNT, error("monero:$address?tx_amount=0"))
        assertEquals(INVALID_AMOUNT, error("monero:$address?tx_amount=1.1234567890123"))
        assertEquals(INVALID_AMOUNT, error("monero:$address?tx_amount=99999999"))
    }

    @Test fun paymentIdIsRefusedWithItsOwnReason() {
        assertEquals(PAYMENT_ID, error("monero:$address?tx_payment_id=0123456789abcdef"))
        assertEquals(PAYMENT_ID, error("monero:$address?payment_id=0123456789abcdef"))
    }

    @Test fun malformedLinksAreNotPaymentLinks() {
        assertEquals(NOT_PAYMENT_LINK, error("monero:$address?tx_amount"))
        assertEquals(NOT_PAYMENT_LINK, error("monero:$address?tx_amount=1&amount=2"))
        assertEquals(NOT_PAYMENT_LINK, error("monero:$address?req-fee=1"))
        assertEquals(NOT_PAYMENT_LINK, error("monero:$address#fragment"))
    }

    @Test fun parseKeepsItsNullContract() {
        assertEquals(MoneroUriData(address), parsePaymentRequest(address, valid))
        assertNull(parsePaymentRequest("monero:$address?tx_amount=abc", valid))
    }

    // iOS SendFlowPhase.acceptsPaymentRequest: an open Send takes a new link
    // until the user confirms, then refuses it.
    @Test fun openSendTakesALinkUntilTheUserConfirms() {
        for (phase in listOf(SendPhase.ADDRESS, SendPhase.AMOUNT, SendPhase.REVIEW)) {
            assertTrue(phase.name, acceptsPaymentLink(phase, confirmed = false))
        }
        assertFalse(acceptsPaymentLink(SendPhase.REVIEW, confirmed = true))
        for (phase in listOf(SendPhase.SENDING, SendPhase.SUCCESS, SendPhase.ERROR)) {
            assertFalse(phase.name, acceptsPaymentLink(phase, confirmed = false))
        }
    }

    // iOS SendFlowView: an open Transfer refuses a link at every step, so the
    // link never replaces the wallet under To.
    @Test fun openTransferRefusesALink() {
        for (phase in SendPhase.entries) {
            assertFalse(phase.name, acceptsPaymentLink(phase, confirmed = false, transfer = true))
        }
    }
}
