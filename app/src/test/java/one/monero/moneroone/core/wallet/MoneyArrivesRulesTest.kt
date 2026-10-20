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

    @Test
    fun aReclaimOfOurOwnLinkNeverPlaysButIsSeen() {
        val baseline = MoneyArrivesRules.baselineOf(listOf(incoming("a", 5, 100, confirmations = 40)))
        val txs = listOf(incoming("a", 5, 100, confirmations = 41), incoming("r", 3, 140))
        val look = MoneyArrivesRules.look(baseline, txs, true, false, reclaimHashes = setOf("r"))
        assertNull(look.arrival)
        assertEquals(141L, look.baseline.height)
    }

    @Test
    fun aBaselineAboveTheChainTopResetsWithNoPlay() {
        // A reset chain: the wallet now sees only blocks up to 60.
        val baseline = ArrivalBaseline(500, listOf("old"))
        val txs = listOf(incoming("n", 2, 50, confirmations = 11))
        val look = MoneyArrivesRules.look(baseline, txs, false, true)
        assertNull(look.arrival)
        assertEquals(51L, look.baseline.height)
    }

    @Test
    fun aHistoryWithNoMinedPaymentKeepsTheBaseline() {
        // No chain top (only a pool payment): no reset, so the old payments never play again.
        val baseline = ArrivalBaseline(500, listOf("old"))
        val txs = listOf(incoming("p", 2, 0, confirmations = 0))
        val look = MoneyArrivesRules.look(baseline, txs, false, true)
        assertNull(look.arrival)
        assertEquals(baseline, look.baseline)
    }

    @Test
    fun aSendWhileAPaymentWaitsShowsAtOnceAndTheRollEqualsThePlusAmount() {
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        assertEquals(10_000L, hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = null))
        // A new block: the kit publishes the history first, the balance after it.
        val pay = incoming("p", 2_000, 140)
        val h1 = listOf(old, pay)
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        assertEquals(10_000L, hold.hero(h1, h1, 10_000, waiting, syncing = false, lastShown = null))
        assertFalse(hold.allCounted)
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = false, lastShown = null))
        assertTrue(hold.allCounted)
        // A send of 1 000 with a fee of 100 while the payment waits: the hero drops at once.
        val h2 = h1 + outgoing("s", 1_000, 0)
        val before = hold.hero(h2, h2, 10_900, waiting, syncing = false, lastShown = null)
        assertEquals(8_900L, before)
        // The moment: the hero rolls from its held value to the real balance.
        val arrival = MoneyArrivesRules.look(baseline, h2, createdInApp = false, firstPaymentShown = true).arrival!!
        assertEquals(arrival.total, 10_900L - before)
    }

    @Test
    fun aPaymentFoundInACatchUpSyncHoldsTheLastShownValueUntilSynced() {
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        // Cold launch: the cached balance, then the sync finds a payment.
        assertEquals(10_000L, hold.hero(h0, h0, 10_000, emptyList(), syncing = true, lastShown = 10_000))
        val h1 = listOf(old, incoming("p", 2_000, 140))
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = true, lastShown = 10_000))
        // Synced: the balance minus the waiting payment, the same value, so no tick up and down.
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = false, lastShown = 10_000))
        assertTrue(hold.allCounted)
    }

    @Test
    fun aCatchUpMakesOnePlusAmountAndTheHeroNeverTicks() {
        // The kit: each scan publishes its history, then its balance; the last scan of a
        // catch-up publishes its history before Synced.
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val shown = mutableListOf<Long>()
        fun frame(history: List<ArrivalTx>, balance: Long, syncing: Boolean) {
            val waiting = history.filter { MoneyArrivesRules.isNew(it, baseline) }
            shown += hold.hero(history, history, balance, waiting, syncing, lastShown = 10_000)
        }
        val h0 = listOf(old)
        frame(h0, 10_000, syncing = true)
        val h1 = h0 + incoming("p1", 2_000, 140, confirmations = 30)
        frame(h1, 10_000, syncing = true)
        frame(h1, 12_000, syncing = true)
        val h2 = h1 + incoming("p2", 3_000, 160, confirmations = 10)
        frame(h2, 12_000, syncing = true)
        // Synced with the balance the kit had (p2 not in it yet), then the last balance.
        frame(h2, 12_000, syncing = false)
        assertFalse(hold.allCounted)
        frame(h2, 15_000, syncing = false)
        assertTrue(hold.allCounted)
        assertTrue(shown.all { it == 10_000L })
        // One moment: its +amount is both payments, and the roll equals it.
        val arrival = MoneyArrivesRules.look(baseline, h2, createdInApp = false, firstPaymentShown = true).arrival!!
        assertEquals(5_000L, arrival.total)
        assertEquals(arrival.total, 15_000L - shown.last())
    }

    @Test
    fun aNewBlockHoldsTheHeroUntilTheBalanceCountsItsPayment() {
        // Synced: a payment the pool never showed. Its history comes first, its balance after.
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        assertEquals(10_000L, hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = 10_000))
        val h1 = h0 + incoming("p", 2_000, 150)
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        assertEquals(10_000L, hold.hero(h1, h1, 10_000, waiting, syncing = false, lastShown = 10_000))
        assertFalse(hold.allCounted)
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = false, lastShown = 10_000))
        assertTrue(hold.allCounted)
    }

    @Test
    fun aPoolPaymentTheBalanceHoldsCountsAtOnceWhenMined() {
        // wallet2's balance takes in a pool payment. When the payment is mined, the balance does not move again.
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = 10_000)
        // In the pool: the history first, then the balance with the payment. The hero holds.
        val h1 = h0 + incoming("p", 2_000, 0, confirmations = 0)
        assertEquals(10_000L, hold.hero(h1, h1, 10_000, emptyList(), syncing = false, lastShown = 10_000))
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, emptyList(), syncing = false, lastShown = 10_000))
        // Mined: a new history and the same balance. The payment counts at once, so the hero still holds.
        val h2 = h0 + incoming("p", 2_000, 150)
        val waiting = h2.filter { MoneyArrivesRules.isNew(it, baseline) }
        assertEquals(10_000L, hold.hero(h2, h2, 12_000, waiting, syncing = false, lastShown = 10_000))
        assertTrue(hold.allCounted)
    }

    @Test
    fun aBalanceBeforeItsHistoryCountsThePaymentAtOnce() {
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = 10_000)
        // The balance lands first, in its own frame (the VM maps the history off the main thread).
        assertEquals(10_000L, hold.hero(h0, h0, 12_000, emptyList(), syncing = true, lastShown = 10_000))
        val h1 = h0 + incoming("p", 2_000, 150, confirmations = 2)
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = true, lastShown = 10_000))
        // The balance already holds the payment: it counts with no 3 s wait, and the hero does not rise.
        assertTrue(hold.allCounted)
        assertEquals(10_000L, hold.hero(h1, h1, 12_000, waiting, syncing = false, lastShown = 10_000))
        assertTrue(hold.allCounted)
    }

    @Test
    fun aBalanceThatRoseLessThanItsPaymentWaitsForTheFallback() {
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = 10_000)
        hold.hero(h0, h0, 11_000, emptyList(), syncing = true, lastShown = 10_000)
        val h1 = h0 + incoming("p", 2_000, 150)
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        // A rise of 1,000 does not show that the 2,000 payment is in the balance.
        hold.hero(h1, h1, 11_000, waiting, syncing = false, lastShown = 10_000)
        assertFalse(hold.allCounted)
        hold.hero(h1, h1, 11_000, waiting, syncing = false, lastShown = 10_000, fallbacks = 1)
        assertTrue(hold.allCounted)
    }

    @Test
    fun aPaymentTheBalanceDoesNotCountCountsAfterTheFallback() {
        val hold = HeroHold()
        val old = incoming("a", 10_000, 100, confirmations = 40)
        val baseline = MoneyArrivesRules.baselineOf(listOf(old))
        val h0 = listOf(old)
        hold.hero(h0, h0, 10_000, emptyList(), syncing = false, lastShown = 10_000)
        val h1 = h0 + incoming("p", 2_000, 150)
        val waiting = h1.filter { MoneyArrivesRules.isNew(it, baseline) }
        hold.hero(h1, h1, 10_000, waiting, syncing = false, lastShown = 10_000)
        assertFalse(hold.allCounted)
        // 3 s and no balance move: the payment counts, so the moment can play.
        hold.hero(h1, h1, 10_000, waiting, syncing = false, lastShown = 10_000, fallbacks = 1)
        assertTrue(hold.allCounted)
    }
}
