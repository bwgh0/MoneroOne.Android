package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyArrivesRulesTest {

    private fun incoming(hash: String, amount: Long, height: Long, confirmations: Long = 1, failed: Boolean = false) =
        ArrivalTx(hash, amount, incoming = true, failed = failed, confirmations = confirmations, blockHeight = height)

    private fun outgoing(hash: String, amount: Long, height: Long) =
        ArrivalTx(hash, amount, incoming = false, failed = false, confirmations = 5, blockHeight = height)

    @Test
    fun noBaselineTakesEverythingAsSeenWithNoPlay() {
        // First sync, restore, import or Reset Sync: nothing plays.
        val txs = listOf(incoming("a", 5, 100, confirmations = 40), incoming("b", 7, 120, confirmations = 20))
        val look = MoneyArrivesRules.look(null, txs, createdInApp = true, firstPaymentShown = false)
        assertNull(look.arrival)
        assertEquals(121L, look.baseline.height)
        assertEquals(listOf("a", "b"), look.baseline.recent)
    }

    @Test
    fun aPaymentPlaysAtItsFirstBlockOnly() {
        val baseline = MoneyArrivesRules.baselineOf(listOf(incoming("a", 5, 100, confirmations = 40)))
        // In the pool: no moment yet.
        val pool = MoneyArrivesRules.look(baseline, listOf(incoming("a", 5, 100, 40), incoming("n", 3, 0, confirmations = 0)), false, true)
        assertNull(pool.arrival)
        // Mined: one moment.
        val mined = MoneyArrivesRules.look(baseline, listOf(incoming("a", 5, 100, 40), incoming("n", 3, 150)), false, true)
        assertEquals(3L, mined.arrival?.total)
        // Looked at again: never twice.
        val again = MoneyArrivesRules.look(mined.baseline, listOf(incoming("a", 5, 100, 41), incoming("n", 3, 150, 2)), false, true)
        assertNull(again.arrival)
    }

    @Test
    fun paymentsFoundTogetherCombine() {
        val baseline = ArrivalBaseline(height = 101, recent = listOf("a"))
        val txs = listOf(incoming("a", 5, 100, 9), incoming("b", 2, 105, 4), incoming("c", 4, 105, 4), incoming("d", 1, 109))
        val look = MoneyArrivesRules.look(baseline, txs, false, true)
        assertEquals(7L, look.arrival?.total)
        assertEquals(listOf("b", "c", "d"), look.arrival?.hashes)
        assertEquals(110L, look.baseline.height)
    }

    @Test
    fun sendsAndFailedPaymentsNeverPlay() {
        val baseline = ArrivalBaseline(height = 100, recent = emptyList())
        val txs = listOf(outgoing("s", 9, 104), incoming("f", 3, 104, failed = true))
        assertNull(MoneyArrivesRules.look(baseline, txs, false, true).arrival)
    }

    @Test
    fun aReorgDoesNotPlayAPaymentAgain() {
        val first = MoneyArrivesRules.look(ArrivalBaseline(100, emptyList()), listOf(incoming("a", 5, 104)), false, true)
        assertEquals(5L, first.arrival?.total)
        // The same payment moves to a higher block.
        assertNull(MoneyArrivesRules.look(first.baseline, listOf(incoming("a", 5, 106)), false, true).arrival)
    }

    @Test
    fun firstPaymentOfAWalletMadeHere() {
        val empty = MoneyArrivesRules.look(null, emptyList(), createdInApp = true, firstPaymentShown = false).baseline
        val look = MoneyArrivesRules.look(empty, listOf(incoming("a", 5, 200)), createdInApp = true, firstPaymentShown = false)
        assertTrue(look.arrival!!.big)
        // A second payment still in the pool does not stop it.
        val withPool = MoneyArrivesRules.look(empty, listOf(incoming("a", 5, 200), incoming("p", 1, 0, confirmations = 0)), true, false)
        assertTrue(withPool.arrival!!.big)
    }

    @Test
    fun noBigMomentWhenRestoredShownBeforeHistoryOrClaim() {
        val empty = ArrivalBaseline(0, emptyList())
        val pay = listOf(incoming("a", 5, 200))
        assertFalse(MoneyArrivesRules.look(empty, pay, createdInApp = false, firstPaymentShown = false).arrival!!.big)
        assertFalse(MoneyArrivesRules.look(empty, pay, createdInApp = true, firstPaymentShown = true).arrival!!.big)
        assertFalse(MoneyArrivesRules.look(empty, pay, true, false, claimHashes = setOf("a")).arrival!!.big)
        val withHistory = ArrivalBaseline(200, emptyList())
        val later = listOf(outgoing("s", 1, 150), incoming("b", 5, 210))
        assertFalse(MoneyArrivesRules.look(withHistory, later, true, false).arrival!!.big)
    }

    @Test
    fun recentListStaysSmall() {
        var baseline = ArrivalBaseline(0, emptyList())
        val txs = mutableListOf<ArrivalTx>()
        repeat(80) { i ->
            txs += incoming("h$i", 1, 100L + i)
            baseline = MoneyArrivesRules.look(baseline, txs, false, true).baseline
        }
        assertEquals(MoneyArrivesRules.RECENT_LIMIT, baseline.recent.size)
        assertEquals(180L, baseline.height)
    }
}
