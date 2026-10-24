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
    fun withoutADirectionEveryChangeRollsUpAsOnIos() {
        // The balance card, "As of" and the chart: iOS .numericText() is countsDown false,
        // so the new digit comes in from below whether the value rises or falls.
        assertTrue(rollsUp(countsDown = null, old = '1', new = '2', rose = true))
        assertTrue(rollsUp(countsDown = null, old = '2', new = '1', rose = false))
        assertTrue(rollsUp(countsDown = null, old = null, new = '2', rose = true))
        assertTrue(rollsUp(countsDown = null, old = '2', new = null, rose = false))
    }

    @Test
    fun aSlideShowsTheOldPlaceUntilItsFirstFrameThenMovesOnlyOneWay() {
        // "0.2500 XMR" to "250.2500 XMR": the shared end moves 34 px right over 200 ms.
        val slide = RollSlide()
        slide.add(by = 34f, ms = 200f)
        // The layout that places it at its new x, before any frame: it still shows at the old x.
        assertEquals(-34f, slide.offset(now = 5_000L), 0f)
        var last = -34f
        var time = 10_000L
        while (slide.tick(time, pace = 1f)) {
            val offset = slide.offset(time)
            assertTrue("moved back at ${time - 10_000} ms: $last to $offset", offset >= last)
            assertTrue(offset <= 0f)
            last = offset
            time += 16
        }
        assertEquals(0f, slide.offset(time), 0f)
        assertTrue("took ${time - 10_000} ms", time - 10_000L in 200L..216L)
    }

    @Test
    fun aMoveDuringASlideGoesOnFromWhereItShows() {
        val slide = RollSlide()
        slide.add(by = 34f, ms = 200f)
        slide.tick(0L, pace = 1f)
        slide.tick(100L, pace = 1f)
        val before = 34f + slide.offset(100L)
        // The text grows again: the place's x moves 17 px further.
        slide.add(by = 17f, ms = 200f)
        assertEquals(before, 51f + slide.offset(100L), 0.001f)
        var last = before
        var time = 116L
        while (slide.tick(time, pace = 1f)) {
            val shown = 51f + slide.offset(time)
            assertTrue("moved back at $time ms", shown >= last)
            last = shown
            time += 16
        }
        assertEquals(51f, 51f + slide.offset(time), 0f)
    }

    @Test
    fun aSlideStopsAtOnce() {
        val slide = RollSlide()
        slide.add(by = 34f, ms = 200f)
        slide.stop()
        assertEquals(0f, slide.offset(0L), 0f)
        assertFalse(slide.tick(0L, pace = 1f))
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
