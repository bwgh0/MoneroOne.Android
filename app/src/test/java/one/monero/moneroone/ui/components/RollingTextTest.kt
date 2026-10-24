package one.monero.moneroone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RollingText: which places roll, and which way. */
class RollingTextTest {
    @Test
    fun countsDownRollsDownAndCountsUpRollsUp() {
        // iOS numericText(countsDown: true): the new digit comes in from above.
        assertFalse(rollsUp(countsDown = true, old = '2', new = '0', rose = false))
        assertFalse(rollsUp(countsDown = true, old = '0', new = '9', rose = true))
        // countsDown: false, a rising count: the new digit comes in from below.
        assertTrue(rollsUp(countsDown = false, old = '0', new = '2', rose = true))
        assertTrue(rollsUp(countsDown = false, old = null, new = '2', rose = true))
    }

    @Test
    fun withoutADirectionTheAppLookStays() {
        // The balance card and the chart: every place rolls down, whatever the value does.
        assertFalse(rollsUp(countsDown = null, old = '1', new = '2', rose = true))
        assertFalse(rollsUp(countsDown = null, old = '2', new = '1', rose = false))
    }

    @Test
    fun aSameLengthChangeRollsOnlyTheChangedSpan() {
        assertEquals(RollDiff(start = 2, oldEnd = 4, newEnd = 4), rollDiff("1,284.503917 XMR", "1,034.503917 XMR"))
        assertEquals(RollDiff(start = 5, oldEnd = 5, newEnd = 5), rollDiff("1.234", "1.234"))
    }

    @Test
    fun aLongerTextKeepsItsSharedEnd() {
        // Only "25" comes in at the front; "0.2500 XMR" keeps its characters and slides.
        assertEquals(RollDiff(start = 0, oldEnd = 0, newEnd = 2), rollDiff("0.2500 XMR", "250.2500 XMR"))
        // "$149" stays; ".62" becomes ",769.46".
        assertEquals(RollDiff(start = 4, oldEnd = 7, newEnd = 11), rollDiff("$149.62", "$149,769.46"))
        // A digit added before the decimal: "10" stays, a "0" comes in, ".5" slides.
        assertEquals(RollDiff(start = 2, oldEnd = 2, newEnd = 3), rollDiff("10.5", "100.5"))
    }

    @Test
    fun aShorterTextKeepsItsSharedEnd() {
        // "25" goes away; "0.2500 XMR" keeps its characters and slides back.
        assertEquals(RollDiff(start = 0, oldEnd = 2, newEnd = 0), rollDiff("250.2500 XMR", "0.2500 XMR"))
        assertEquals(RollDiff(start = 1, oldEnd = 6, newEnd = 4), rollDiff("$1,000.00", "$999.00"))
    }

    @Test
    fun theSharedStartAndEndNeverOverlap() {
        assertEquals(RollDiff(start = 4, oldEnd = 4, newEnd = 5), rollDiff("1.00", "1.000"))
        assertEquals(RollDiff(start = 0, oldEnd = 0, newEnd = 3), rollDiff("", "1.0"))
        assertEquals(RollDiff(start = 0, oldEnd = 3, newEnd = 0), rollDiff("1.0", ""))
    }
}
