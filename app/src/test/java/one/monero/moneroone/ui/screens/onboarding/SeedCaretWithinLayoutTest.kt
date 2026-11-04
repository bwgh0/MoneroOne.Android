package one.monero.moneroone.ui.screens.onboarding

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A seed cell's caret before its new word is laid out. */
class SeedCaretWithinLayoutTest {

    @Test
    fun `a word set before its layout gets the caret at the laid out end`() {
        val value = TextFieldValue("abbey", TextRange(5))
        assertEquals(TextRange(0), caretWithinLayout(value, laidOutLength = 0).selection)
        assertEquals("abbey", caretWithinLayout(value, laidOutLength = 0).text)
    }

    @Test
    fun `a laid out word passes through unchanged`() {
        val value = TextFieldValue("abbey", TextRange(5))
        assertSame(value, caretWithinLayout(value, laidOutLength = 5))
    }

    @Test
    fun `a caret inside the old layout passes through unchanged`() {
        val value = TextFieldValue("abbey", TextRange(2))
        assertSame(value, caretWithinLayout(value, laidOutLength = 3))
    }

    @Test
    fun `both ends of a selection are held to the old layout`() {
        val value = TextFieldValue("academy", TextRange(1, 7))
        assertEquals(TextRange(1, 3), caretWithinLayout(value, laidOutLength = 3).selection)
    }

    @Test
    fun `a reversed selection keeps its direction`() {
        val value = TextFieldValue("academy", TextRange(7, 2))
        assertEquals(TextRange(4, 2), caretWithinLayout(value, laidOutLength = 4).selection)
    }
}
