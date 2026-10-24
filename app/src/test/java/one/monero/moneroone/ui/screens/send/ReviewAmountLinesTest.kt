package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.data.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Review amounts follow Fiat Mode as on iOS (ReviewAmountLinesTests). */
class ReviewAmountLinesTest {

    private val price = 600.0
    private val sent = 4_181_066_248L // 0.004181066248 XMR
    private val fee = 31_520_000L // 0.00003152 XMR

    @Test
    fun fiatModeWithAPlausiblePriceShowsFiatOnTopAndTheExactXmrUnder() {
        assertTrue(ReviewAmountLines.fiatFirst(fiatMode = true, price = price))
        val lines = ReviewAmountLines.of("0.302260911618", "$181.36", fiatFirst = true)
        assertEquals("$181.36", lines.primary)
        assertEquals("0.302260911618 XMR", lines.secondary)
        assertTrue(lines.isFiatFirst)
    }

    @Test
    fun fiatModeOffShowsXmrOnTopAndAnEstimateUnder() {
        assertFalse(ReviewAmountLines.fiatFirst(fiatMode = false, price = price))
        val lines = ReviewAmountLines.of("0.302260911618", "$181.36", fiatFirst = false)
        assertEquals("0.302260911618 XMR", lines.primary)
        assertEquals("≈ $181.36", lines.secondary)
        assertFalse(lines.isFiatFirst)
    }

    @Test
    fun noUsablePriceKeepsXmrOnTop() {
        assertFalse(ReviewAmountLines.fiatFirst(fiatMode = true, price = null))
        assertFalse(ReviewAmountLines.fiatFirst(fiatMode = true, price = 0.0))
        assertFalse(ReviewAmountLines.fiatFirst(fiatMode = true, price = Double.POSITIVE_INFINITY))
        assertFalse(ReviewAmountLines.fiatFirst(fiatMode = true, price = Double.NaN))
        val lines = ReviewAmountLines.of("0.5", null, fiatFirst = true)
        assertEquals("0.5 XMR", lines.primary)
        assertNull(lines.secondary)
        assertFalse(lines.isFiatFirst)
    }

    @Test
    fun atomicAmountsUseTheFullXmrPrecisionAndTheFiatFormat() {
        val fiatFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = true)
        assertEquals("$2.51", fiatFirst.primary)
        assertEquals("0.004181066248 XMR", fiatFirst.secondary)

        val xmrFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = false)
        assertEquals("0.004181066248 XMR", xmrFirst.primary)
        assertEquals("≈ $2.51", xmrFirst.secondary)

        val noPrice = ReviewAmountLines.of(fee, null, Currency.USD, fiatMode = true)
        assertEquals("0.00003152 XMR", noPrice.primary)
        assertNull(noPrice.secondary)
    }

    @Test
    fun theThousandsSeparatorShowsInFiat() {
        val lines = ReviewAmountLines.of(2_000_000_000_000L, 611.78, Currency.USD, fiatMode = true)
        assertEquals("$1,223.56", lines.primary)
        assertEquals("2.0000 XMR", lines.secondary)
    }
}
