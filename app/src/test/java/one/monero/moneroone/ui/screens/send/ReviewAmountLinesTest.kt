package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.core.locale.localizedStrings
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

    @Test
    fun spokenFiatFirstReadsTheFiatThenTheExactXmr() {
        val lines = ReviewAmountLines.of("0.00003152", "$0.01", fiatFirst = true)
        assertEquals("$0.01, 0.00003152 XMR", lines.spokenFiatFirst)
        assertNull(ReviewAmountLines.of("0.00003152", "$0.01", fiatFirst = false).spokenFiatFirst)
        assertNull(ReviewAmountLines.of("0.00003152", null, fiatFirst = true).spokenFiatFirst)
    }

    @Test
    fun theFeeLabelFollowsFiatMode() {
        val fiatFirst = ReviewAmountLines.of(fee, price, Currency.USD, fiatMode = true)
        assertEquals("Network fee: $0.02, 0.00003152 XMR", SendReviewSpoken.feeLabel(fee, fiatFirst))
        val xmrFirst = ReviewAmountLines.of(fee, price, Currency.USD, fiatMode = false)
        assertEquals("Network fee: 0.00003152 XMR", SendReviewSpoken.feeLabel(fee, xmrFirst))
        assertEquals("Network fee: loading", SendReviewSpoken.feeLoadingLabel())
    }

    @Test
    fun theSendHintReadsFiatFirstOnlyInFiatModeWithAPlausiblePrice() {
        val fiatFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = true)
        assertEquals("Double tap to send $2.51, 0.004181066248 XMR", SendReviewSpoken.sendHint(sent, fiatFirst))
        val xmrFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = false)
        assertEquals("Double tap to send 0.004181066248 XMR", SendReviewSpoken.sendHint(sent, xmrFirst))
        for (fiatMode in listOf(true, false)) {
            val noPrice = ReviewAmountLines.of(sent, null, Currency.USD, fiatMode)
            assertEquals("Double tap to send 0.004181066248 XMR", SendReviewSpoken.sendHint(sent, noPrice))
        }
        val implausible = ReviewAmountLines.of(sent, 0.0, Currency.USD, fiatMode = true)
        assertEquals("Double tap to send 0.004181066248 XMR", SendReviewSpoken.sendHint(sent, implausible))
    }

    @Test
    fun theLinkFeeRowsReadFiatFirstOnlyInFiatModeWithAPlausiblePrice() {
        val fiatFirst = ReviewAmountLines.of(fee, price, Currency.USD, fiatMode = true)
        assertEquals("Network Fee: $0.02, 0.00003152 XMR", LinkReviewSpoken.valueLabel("Network Fee", fee, fiatFirst))
        assertEquals("Claim fee reserve: $0.02, 0.00003152 XMR", LinkReviewSpoken.valueLabel("Claim fee reserve", fee, fiatFirst))
        val xmrFirst = ReviewAmountLines.of(fee, price, Currency.USD, fiatMode = false)
        assertEquals("Network Fee: 0.00003152 XMR", LinkReviewSpoken.valueLabel("Network Fee", fee, xmrFirst))
        val noPrice = ReviewAmountLines.of(fee, null, Currency.USD, fiatMode = true)
        assertEquals("Claim fee reserve: 0.00003152 XMR", LinkReviewSpoken.valueLabel("Claim fee reserve", fee, noPrice))
        val implausible = ReviewAmountLines.of(fee, 0.0, Currency.USD, fiatMode = true)
        assertEquals("Network Fee: 0.00003152 XMR", LinkReviewSpoken.valueLabel("Network Fee", fee, implausible))
    }

    @Test
    fun aLoadingLinkFeeRowReadsLoading() {
        assertEquals("Network Fee: loading", LinkReviewSpoken.loadingLabel("Network Fee"))
        assertEquals("Claim fee reserve: loading", LinkReviewSpoken.loadingLabel("Claim fee reserve"))
    }

    @Test
    fun theFundHintReadsFiatFirstOnlyInFiatModeWithAPlausiblePrice() {
        val fiatFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = true)
        assertEquals("Double tap to fund the link with $2.51, 0.004181066248 XMR", LinkReviewSpoken.fundHint(sent, fiatFirst))
        val xmrFirst = ReviewAmountLines.of(sent, price, Currency.USD, fiatMode = false)
        assertEquals("Double tap to fund the link with 0.004181066248 XMR", LinkReviewSpoken.fundHint(sent, xmrFirst))
        for (fiatMode in listOf(true, false)) {
            val noPrice = ReviewAmountLines.of(sent, null, Currency.USD, fiatMode)
            assertEquals("Double tap to fund the link with 0.004181066248 XMR", LinkReviewSpoken.fundHint(sent, noPrice))
        }
        val implausible = ReviewAmountLines.of(sent, 0.0, Currency.USD, fiatMode = true)
        assertEquals("Double tap to fund the link with 0.004181066248 XMR", LinkReviewSpoken.fundHint(sent, implausible))
    }

    @Test
    fun theSpokenStringsAreTranslated() {
        for (source in listOf(
            "Network fee: %s", "Network fee: %s XMR", "Network fee: loading", "Double tap to send %s", "Double tap to send %s XMR",
            "%s: loading", "Double tap to fund the link with %s", "Double tap to fund the link with %s XMR"
        )) {
            assertTrue(source, localizedStrings.containsKey(source))
        }
    }
}
