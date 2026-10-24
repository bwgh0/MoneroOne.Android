package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.SignedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFeeTest {
    private val address = "4AdUndXHHZ6cfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2684Rge"
    private val amount = 1_000_000_000_000L
    private val unlocked = 3_000_000_000_000L

    private fun signed(amount: Long, fee: Long) = SignedTransaction(listOf("aa11"), amount, fee)

    @Test
    fun `review shows the fee of the transfer it built, not the one-input estimate`() {
        // wallet2's estimate counts one input; this transfer spends three and pays more.
        val prepareCalls = mutableListOf<Pair<Long, Boolean>>()
        val result = ReviewFee.build(
            address, amount, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 30_720_000L },
            prepare = { a, s -> prepareCalls += a to s; signed(a, 61_440_000L) }
        )
        assertTrue(result is ReviewFeeResult.Ready)
        val prepared = (result as ReviewFeeResult.Ready).prepared
        assertEquals(61_440_000L, prepared.fee)
        assertEquals(amount, prepared.sentAmount)
        assertEquals(amount + 61_440_000L, prepared.total)
        assertEquals(listOf(amount to false), prepareCalls)
    }

    @Test
    fun `an amount the estimate already puts over the balance builds nothing`() {
        var prepared = false
        val result = ReviewFee.build(
            address, unlocked, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 30_720_000L },
            prepare = { a, _ -> prepared = true; signed(a, 30_720_000L) }
        )
        assertEquals(ReviewFeeResult.OverBalance(30_720_000L), result)
        assertFalse(prepared)
    }

    @Test
    fun `real inputs that put the transfer over the balance read as over balance`() {
        val result = ReviewFee.build(
            address, amount, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 30_720_000L },
            prepare = { _, _ ->
                throw IllegalStateException(
                    "Create Transaction failed: not enough money to transfer, available only 1.000020000000, " +
                        "transaction amount 1.000061440000 = 1.000000000000 + 0.000061440000 (fee)"
                )
            }
        )
        assertEquals(ReviewFeeResult.OverBalance(30_720_000L), result)
    }

    @Test
    fun `any other build failure keeps its message for the error path`() {
        val message = "Create Transaction failed: not enough outputs for specified ring size = 16"
        val result = ReviewFee.build(
            address, amount, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 30_720_000L },
            prepare = { _, _ -> throw IllegalStateException(message) }
        )
        assertEquals(ReviewFeeResult.Failed(message), result)
    }

    @Test
    fun `a failed estimate still builds the transfer`() {
        val result = ReviewFee.build(
            address, amount, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 0L },
            prepare = { a, _ -> signed(a, 45_000_000L) }
        )
        assertEquals(45_000_000L, (result as ReviewFeeResult.Ready).prepared.fee)
    }

    @Test
    fun `a sweep is built at once and sends what it spends less its fee`() {
        var estimated = false
        val sweepFee = 92_160_000L
        val sweepAmount = unlocked - sweepFee
        val result = ReviewFee.build(
            address, amount, sweepAll = true, unlocked = unlocked,
            estimate = { _, _ -> estimated = true; 30_720_000L },
            prepare = { _, s -> assertTrue(s); signed(sweepAmount, sweepFee) }
        )
        val prepared = (result as ReviewFeeResult.Ready).prepared
        assertFalse(estimated)
        assertEquals(sweepFee, prepared.fee)
        assertEquals(sweepAmount, prepared.sentAmount)
        // The total is what leaves the wallet, never the balance plus the fee.
        assertEquals(unlocked, prepared.total)
    }

    @Test
    fun `a sweep the wallet cannot pay is an error, not an over-balance choice`() {
        val result = ReviewFee.build(
            address, amount, sweepAll = true, unlocked = unlocked,
            estimate = { _, _ -> 0L },
            prepare = { _, _ -> throw IllegalStateException("Create Transaction failed: not enough unlocked money") }
        )
        assertTrue(result is ReviewFeeResult.Failed)
    }

    @Test
    fun `a transfer without a fee is not ready to send`() {
        val result = ReviewFee.build(
            address, amount, sweepAll = false, unlocked = unlocked,
            estimate = { _, _ -> 30_720_000L },
            prepare = { a, _ -> signed(a, 0L) }
        )
        assertEquals(ReviewFeeResult.Failed(null), result)
    }

    @Test
    fun `only the transfer the review showed may go out`() {
        val prepared = PreparedSend(address, amount, sweepAll = false, signed = signed(amount, 61_440_000L))
        assertTrue(prepared.matches(address, amount, sweepAll = false))
        assertFalse(prepared.matches(address, amount + 1, sweepAll = false))
        assertFalse(prepared.matches(address.dropLast(1) + "x", amount, sweepAll = false))
        assertFalse(prepared.matches(address, amount, sweepAll = true))

        val sweep = PreparedSend(address, amount, sweepAll = true, signed = signed(unlocked - 1, 1))
        // A sweep's amount field is the balance text; the sweep sends what it built.
        assertTrue(sweep.matches(address, amount + 5, sweepAll = true))
        assertFalse(sweep.matches(address, amount, sweepAll = false))
    }

    @Test
    fun `wallet2 shortfall messages read as not enough money`() {
        assertTrue(ReviewFee.isNotEnoughMoney("not enough money to transfer, overall balance only 0.1, sent amount 0.2"))
        assertTrue(ReviewFee.isNotEnoughMoney("Create Transaction failed: not enough unlocked money"))
        assertFalse(ReviewFee.isNotEnoughMoney("not enough outputs for specified ring size = 16"))
        assertFalse(ReviewFee.isNotEnoughMoney(null))
    }

    @Test
    fun `a decoy shortage is not over balance on the Send with a Link Review`() {
        // The link Review's funding build uses this check: too few decoys (a fresh test chain) shows as an error,
        // not as the over-balance estimates.
        assertFalse(ReviewFee.isNotEnoughMoney("Create Transaction failed: Not enough rct outputs"))
        assertFalse(ReviewFee.isNotEnoughMoney("Not enough rct outputs"))
        assertTrue(
            ReviewFee.isNotEnoughMoney(
                "Create Transaction failed: not enough money to transfer, available only 1.000020000000, " +
                    "sent amount 2.000000000000"
            )
        )
        assertTrue(ReviewFee.isNotEnoughMoney("Create Transaction failed: not enough unlocked money"))
    }

    @Test
    fun `the review state is ready only with a fee and no error`() {
        assertFalse(ReviewFeeState().ready)
        assertFalse(ReviewFeeState(loading = true).ready)
        assertFalse(ReviewFeeState(error = "x", fee = 5).ready)
        assertTrue(ReviewFeeState(fee = 5).ready)
    }
}
