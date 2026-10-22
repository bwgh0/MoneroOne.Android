package one.monero.moneroone.ui.screens.transactions

import io.horizontalsystems.monerokit.model.TransactionInfo
import io.horizontalsystems.monerokit.model.Transfer
import one.monero.moneroone.core.wallet.ReceiveAddressLogic
import io.horizontalsystems.monerokit.data.Subaddress
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class TransactionHistoryLogicTest {
    private fun tx(incoming: Boolean = true, index: Int = 0, amount: Long = 1000, fee: Long = 10, failed: Boolean = false) =
        TransactionInfo(if (incoming) 0 else 1, false, failed, amount, fee, 50, "hash-$incoming-$index-$amount", 100, "", 0, index, 12, 0, "", emptyList())
    @Test fun `totals include sent fees exclude failures and do not overflow long`() {
        val totals = TransactionHistoryLogic.totals(listOf(tx(amount = Long.MAX_VALUE), tx(amount = 1), tx(false), tx(false, failed = true)))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE, totals.received)
        assertEquals(BigInteger.valueOf(1010), totals.sent)
        assertEquals(3, totals.count)
    }
    @Test fun `address filter excludes outgoing and other accounts`() {
        val incoming = tx(index = 2)
        val otherAccount = tx(index = 2).apply { accountIndex = 1 }
        assertEquals(listOf(incoming), TransactionHistoryLogic.filter(listOf(incoming, tx(false, 2), otherAccount), TransactionFilter.ALL, 2, "", emptyList()))
    }
    @Test fun `search includes named addresses and outgoing recipients`() {
        val incoming = tx(index = 2)
        val rows = ReceiveAddressLogic.rows(listOf(Subaddress(0, 2, "receive-address", "")), listOf(incoming), mapOf(2 to "🎁 Gifts"))
        assertEquals(listOf(incoming), TransactionHistoryLogic.filter(listOf(incoming), TransactionFilter.ALL, null, "gift", rows))
        val sent = tx(false).apply { transfers = listOf(Transfer(1000, "recipient-address")) }
        assertEquals(listOf(sent), TransactionHistoryLogic.filter(listOf(sent), TransactionFilter.ALL, null, "recipient", rows))
    }
    @Test fun `row keys stay unique when wallet2 lists a transaction twice`() {
        val paidTwice = tx(index = 1)
        val sameHash = tx(index = 2).apply { hash = paidTwice.hash }
        val sent = tx(false).apply { hash = paidTwice.hash }
        val keys = TransactionHistoryLogic.rowKeys(listOf(paidTwice, sameHash, sent, tx(index = 3)))
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys, TransactionHistoryLogic.rowKeys(listOf(paidTwice, sameHash, sent, tx(index = 3))))
    }
    @Test fun `failed transaction never appears as pending`() {
        val failed = tx(failed = true).apply { confirmations = 0; isPending = true }
        assertTrue(TransactionHistoryLogic.filter(listOf(failed), TransactionFilter.PENDING, null, "", emptyList()).isEmpty())
    }
    @Test fun `links filter keeps payment link transactions only`() {
        val funding = tx(false, 1)
        val claim = tx(true, 2)
        val plain = tx(true, 3)
        val isLink = { t: TransactionInfo -> t.hash == funding.hash || t.hash == claim.hash }
        val links = TransactionHistoryLogic.filter(listOf(funding, claim, plain), TransactionFilter.LINKS, null, "", emptyList(), isLink)
        assertEquals(setOf(funding, claim), links.toSet())
        assertTrue(TransactionHistoryLogic.filter(listOf(plain), TransactionFilter.LINKS, null, "", emptyList()).isEmpty())
        assertEquals(3, TransactionHistoryLogic.filter(listOf(funding, claim, plain), TransactionFilter.ALL, null, "", emptyList(), isLink).size)
    }
    @Test fun `missing historical quote suppresses complete fiat totals`() {
        val first = tx(); val later = tx(false).apply { timestamp = 200 }
        assertNull(TransactionHistoryLogic.fiatTotals(listOf(first, later)) { if (it == 100L) 200.0 else null })
        val totals = TransactionHistoryLogic.fiatTotals(listOf(first, later)) { 200.0 }!!
        assertEquals(0, totals.sent.compareTo(java.math.BigDecimal("0.000000202")))
    }
    @Test fun `copy all carries recipients historical value and marks transaction key sensitive`() {
        val sent = tx(false).apply { transfers = listOf(Transfer(1000, "recipient")) }
        val fields = TransactionHistoryLogic.details(sent, emptyList(), "Sep 28", "$2.00", "$3.00", "abc")
        val text = TransactionHistoryLogic.copyAll(fields)
        assertTrue(text.contains("Sent to: recipient"))
        assertTrue(text.contains("Value when sent: $2.00"))
        assertTrue(text.contains("Value today: $3.00"))
        assertEquals(listOf("abc"), fields.filter { it.secret }.map { it.value })
        assertTrue(TransactionHistoryLogic.details(tx(), emptyList(), "date", null, null, null).none { it.secret })
    }
    @Test fun `through keeps what had happened by the moment picked`() {
        val first = tx(index = 1).apply { timestamp = 100 }
        val later = tx(index = 2).apply { timestamp = 200 }
        assertEquals(listOf(first, later), TransactionHistoryLogic.through(null, listOf(first, later)))
        assertEquals(listOf(first), TransactionHistoryLogic.through(150_000, listOf(first, later)))
        // Inclusive: a transaction at the very moment picked is shown.
        assertEquals(listOf(first, later), TransactionHistoryLogic.through(200_000, listOf(first, later)))
        assertTrue(TransactionHistoryLogic.through(99_999, listOf(first, later)).isEmpty())
    }
    @Test fun `row label names the address that received it, and only a subaddress`() {
        val onGifts = tx(index = 2, amount = 1_250_000_000_000)
        val rows = ReceiveAddressLogic.rows(listOf(Subaddress(0, 2, "receive-address", "")), listOf(onGifts), mapOf(2 to "🎁 Gifts"))
        assertEquals("🎁 Gifts", TransactionHistoryLogic.receivedOnName(onGifts, rows))
        assertNull(TransactionHistoryLogic.receivedOnName(tx(index = 0), rows))
        assertNull(TransactionHistoryLogic.receivedOnName(tx(false, 2), rows))
        assertNull(TransactionHistoryLogic.receivedOnName(tx(index = 2).apply { accountIndex = 1 }, rows))
        assertEquals(
            "Received 1.2500 XMR on 🎁 Gifts, worth $187.50 at the time, Sep 28, Confirmed",
            TransactionHistoryLogic.rowLabel(onGifts, false, "$187.50", "Sep 28", "🎁 Gifts")
        )
        assertTrue(
            TransactionHistoryLogic.rowLabel(onGifts, true, "$187.50", "Sep 28", "🎁 Gifts")
                .startsWith("Received $187.50 at the time, ")
        )
        assertEquals("Sent 1.2500 XMR, Sep 28, Confirmed", TransactionHistoryLogic.rowLabel(tx(false, amount = 1_250_000_000_000), false, null, "Sep 28", null))
    }
    @Test fun `missing recipients are stated instead of substituted with our receive address`() {
        val fields = TransactionHistoryLogic.details(tx(false), emptyList(), "date", null, null, null)
        assertTrue(fields.single { it.label == "Recipient" }.value.contains("not available", ignoreCase = true))
    }
    @Test fun `fee row and copy all show an unknown fee as Not available`() {
        fun details(of: TransactionInfo) = TransactionHistoryLogic.details(of, emptyList(), "date", null, null, null)
        fun fee(of: TransactionInfo) = details(of).single { it.label == "Fee" }.value
        assertEquals("0.0001 XMR", fee(tx(false, fee = 100_000_000)))
        assertEquals("Not available", fee(tx(false, fee = 0)))
        // wallet2's fee for a pre-RingCT send with another wallet's inputs wraps negative.
        val wrapped = tx(false, fee = -500_000_000_000)
        assertEquals("Not available", fee(wrapped))
        assertTrue(TransactionHistoryLogic.copyAll(details(wrapped)).lines().contains("Fee: Not available"))
        // A received transaction (a coinbase too, whose fee is 0) has no fee row.
        assertTrue(details(tx(fee = 0)).none { it.label == "Fee" })
    }
    @Test fun `a negative fee stays signed so totals count what left the wallet`() {
        // wallet2, pre-RingCT send with another wallet's inputs: our inputs 10,
        // our change 1.99, all outputs 14.98, so fee = -4.98 and amount = 12.99.
        val sent = tx(false, amount = 12_990_000_000_000, fee = -4_980_000_000_000)
        assertEquals(BigInteger.valueOf(8_010_000_000_000), TransactionHistoryLogic.totals(listOf(sent)).sent)
        assertEquals(0, TransactionHistoryLogic.fiatTotals(listOf(sent)) { 1.0 }!!.sent.compareTo(java.math.BigDecimal("8.01")))
    }
}
