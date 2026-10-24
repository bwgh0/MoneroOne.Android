package one.monero.moneroone.ui.screens.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The unit the Send amount step starts in (iOS SendAmountStartUnitTests). */
class SendAmountStartTest {

    private val price = 165.42

    @Test fun fiatModeOnStartsInFiat() {
        assertTrue(SendAmountStart.startsInFiat(fiatMode = true, price = price, amount = ""))
    }

    @Test fun fiatModeOffStartsInXmr() {
        assertFalse(SendAmountStart.startsInFiat(fiatMode = false, price = price, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = false, price = null, amount = ""))
    }

    @Test fun noUsablePriceStartsInXmr() {
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = null, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = 0.0, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = Double.NaN, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = 0.1, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = 2_000_000.0, amount = ""))
    }

    @Test fun aFilledInAmountStartsInXmr() {
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = price, amount = "1.5"))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = false, price = price, amount = "1.5"))
    }

    private val typed = TypedFiat(fiat = "50", xmr = "0.302260911618")

    @Test fun anAmountTypedInFiatComesBackInFiat() {
        assertEquals("50", SendAmountStart.restoredFiat(typed, amount = "0.302260911618", price = price))
    }

    @Test fun anAmountFilledInSinceKeepsXmr() {
        assertNull(SendAmountStart.restoredFiat(typed, amount = "1.5", price = price))
        assertNull(SendAmountStart.restoredFiat(typed, amount = "", price = price))
    }

    @Test fun anAmountTypedInXmrHasNothingToRestore() {
        assertNull(SendAmountStart.restoredFiat(null, amount = "0.302260911618", price = price))
    }

    @Test fun noUsablePriceRestoresNothing() {
        assertNull(SendAmountStart.restoredFiat(typed, amount = "0.302260911618", price = null))
        assertNull(SendAmountStart.restoredFiat(typed, amount = "0.302260911618", price = Double.NaN))
    }

    @Test fun aLinkWithAnAmountShowsItInXmr() {
        // iOS SendAmountStep.linkKeepsFiat: the link's amount, as it came, not the old fiat.
        assertFalse(SendAmountStart.linkKeepsFiat(linkAmount = "0.25"))
    }

    @Test fun aLinkWithNoAmountKeepsTheUnit() {
        // The amount clears; the step stays in the unit it shows (in fiat, an empty fiat amount).
        assertTrue(SendAmountStart.linkKeepsFiat(linkAmount = ""))
    }

    @Test fun aLinkOnTheRecipientStepOrReviewDoesNotBringTheOldFiatBack() {
        // The link's amount equals the XMR of the typed fiat: any link drops the typed fiat, so
        // the step that comes next starts in XMR with the link's amount.
        val linkAmount = typed.xmr
        assertNull(SendAmountStart.restoredFiat(null, amount = linkAmount, price = price))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = true, price = price, amount = linkAmount))
    }

    @Test fun aLinkWithNoAmountOnTheRecipientStepOrReviewStartsEmptyInFiat() {
        // The amount cleared and the typed fiat is dropped: the step starts as on a fresh Send.
        assertNull(SendAmountStart.restoredFiat(null, amount = "", price = price))
        assertTrue(SendAmountStart.startsInFiat(fiatMode = true, price = price, amount = ""))
        assertFalse(SendAmountStart.startsInFiat(fiatMode = false, price = price, amount = ""))
    }

    @Test fun aSwappedFiatAmountShowsAsTyped() {
        // iOS formatFiatNumber: two decimals at most, no trailing zeros, no thousands separator.
        assertEquals("1223.56", SendAmountStart.fiatInput(1223.564))
        assertEquals("2.5", SendAmountStart.fiatInput(2.5))
        assertEquals("100", SendAmountStart.fiatInput(100.0))
        assertEquals("0", SendAmountStart.fiatInput(0.001))
    }
}
