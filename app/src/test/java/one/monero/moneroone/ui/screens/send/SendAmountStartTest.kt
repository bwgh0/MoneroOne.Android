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
}
