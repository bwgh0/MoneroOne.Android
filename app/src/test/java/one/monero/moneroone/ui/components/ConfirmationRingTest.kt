package one.monero.moneroone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationRingTest {

    @Test
    fun ringFillsOneTenthPerConfirmation() {
        assertEquals(0f, ConfirmationRing.progress(0), 0f)
        assertEquals(0.3f, ConfirmationRing.progress(3), 1e-6f)
        assertEquals(0.9f, ConfirmationRing.progress(9), 1e-6f)
        assertEquals(1f, ConfirmationRing.progress(10), 0f)
        assertEquals(1f, ConfirmationRing.progress(250), 0f)
        assertEquals(0f, ConfirmationRing.progress(-1), 0f)
    }

    @Test
    fun ringShowsFromZeroToNine() {
        assertTrue(ConfirmationRing.shows(0, failed = false))
        assertTrue(ConfirmationRing.shows(9, failed = false))
        assertFalse(ConfirmationRing.shows(10, failed = false))
        assertFalse(ConfirmationRing.shows(3, failed = true))
        assertFalse(ConfirmationRing.shows(null, failed = false))
    }
}
