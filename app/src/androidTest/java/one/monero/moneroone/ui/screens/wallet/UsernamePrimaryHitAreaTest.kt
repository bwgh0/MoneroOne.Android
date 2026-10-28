package one.monero.moneroone.ui.screens.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import one.monero.moneroone.ui.theme.MoneroOneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** iOS had a Continue button that took taps on its text only. On Android the whole button takes them. */
@RunWith(AndroidJUnit4::class)
class UsernamePrimaryHitAreaTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theWholeButtonTakesTaps() {
        var taps = 0
        rule.setContent {
            MoneroOneTheme {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    UsernamePrimary("Continue") { taps++ }
                }
            }
        }
        // The text's own node is the button's: its bounds are the whole button, not the label.
        val button = rule.onNodeWithText("Continue")
        val size = button.fetchSemanticsNode().size
        val minHeight = with(rule.density) { 54.dp.toPx() }
        assertTrue("height ${size.height}", size.height >= minHeight - 1)
        assertTrue("width ${size.width}", size.width > size.height * 4)
        // Outside the centered label, inside the button's pill shape: both ends, and above and below the text.
        val points = listOf(
            Offset(4f, size.height / 2f), Offset(size.width - 4f, size.height / 2f),
            Offset(size.width / 2f, 4f), Offset(size.width / 2f, size.height - 4f),
        )
        points.forEach { point -> button.performTouchInput { click(point) } }
        rule.waitForIdle()
        assertEquals(points.size, taps)
    }
}
