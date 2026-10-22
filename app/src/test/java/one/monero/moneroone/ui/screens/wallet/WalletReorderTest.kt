package one.monero.moneroone.ui.screens.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletReorderTest {
    private val gap = 10f

    @Test fun rowTopsStackHeightsAndGaps() {
        assertEquals(listOf(0f, 110f, 190f), reorderRowTops(listOf(100f, 70f, 100f), gap))
    }

    @Test fun aSmallMoveKeepsTheSlot() {
        val heights = listOf(100f, 100f, 100f)
        assertEquals(1, reorderDropIndex(heights, gap, 1, 0f))
        assertEquals(1, reorderDropIndex(heights, gap, 1, 50f))
        assertEquals(1, reorderDropIndex(heights, gap, 1, -50f))
    }

    @Test fun theLeadingEdgePastANeighboursMiddleTakesItsSlot() {
        val heights = listOf(100f, 100f, 100f, 100f)
        // Row 1 spans 110..210; row 2's middle is at 270, so the bottom passes it beyond +60.
        assertEquals(1, reorderDropIndex(heights, gap, 1, 59f))
        assertEquals(2, reorderDropIndex(heights, gap, 1, 61f))
        assertEquals(3, reorderDropIndex(heights, gap, 1, 200f))
        // Up: row 0's middle is at 50, so the top passes it beyond -60.
        assertEquals(1, reorderDropIndex(heights, gap, 1, -59f))
        assertEquals(0, reorderDropIndex(heights, gap, 1, -61f))
    }

    @Test fun eachRowCountsWithItsOwnHeight() {
        // A short row (no address yet) in the middle: tops 0, 110, 180, 290.
        val heights = listOf(100f, 60f, 100f, 100f)
        // Row 0 down: its bottom (100 + d) passes row 1's middle (140) beyond +40,
        // row 2's middle (230) beyond +130, row 3's middle (340) beyond +240.
        assertEquals(0, reorderDropIndex(heights, gap, 0, 39f))
        assertEquals(1, reorderDropIndex(heights, gap, 0, 41f))
        assertEquals(1, reorderDropIndex(heights, gap, 0, 129f))
        assertEquals(2, reorderDropIndex(heights, gap, 0, 131f))
        assertEquals(3, reorderDropIndex(heights, gap, 0, 241f))
        // Row 3 up: its top (290 + d) passes row 2's middle (230) below -60, row 1's (140) below -150.
        assertEquals(3, reorderDropIndex(heights, gap, 3, -59f))
        assertEquals(2, reorderDropIndex(heights, gap, 3, -61f))
        assertEquals(1, reorderDropIndex(heights, gap, 3, -151f))
        assertEquals(0, reorderDropIndex(heights, gap, 3, -500f))
    }

    @Test fun aDragPastTheEndsStaysInTheList() {
        val heights = listOf(100f, 100f)
        assertEquals(1, reorderDropIndex(heights, gap, 0, 10_000f))
        assertEquals(0, reorderDropIndex(heights, gap, 1, -10_000f))
        assertEquals(5, reorderDropIndex(heights, gap, 5, 0f))
    }
}
