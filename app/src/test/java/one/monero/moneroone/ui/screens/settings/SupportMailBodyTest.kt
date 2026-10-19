package one.monero.moneroone.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportMailBodyTest {
    @Test
    fun `body leaves room to write then lists app and device facts`() {
        assertEquals(
            "\n\n----------\nMonero One 1.1.0 (19)\nAndroid 15 (API 35)\nGoogle Pixel 9",
            supportMailBody("1.1.0", 19, "15", 35, "Google", "Pixel 9")
        )
    }
}
