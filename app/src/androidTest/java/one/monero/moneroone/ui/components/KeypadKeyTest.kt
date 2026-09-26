package one.monero.moneroone.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeypadKeyTest {

    @get:Rule
    val rule = createComposeRule()

    private val pressed = mutableListOf<String>()

    @Before
    fun setUp() {
        rule.setContent {
            Row {
                listOf("1", "2").forEach { digit ->
                    KeypadKey(onPress = { pressed += digit }) { onClick ->
                        GlassButton(
                            onClick = onClick,
                            modifier = Modifier.size(80.dp).testTag("key$digit")
                        ) {
                            Text(digit)
                        }
                    }
                }
            }
        }
    }

    private fun center(tag: String): Offset =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun actsOnDownOnce() {
        rule.onNodeWithTag("key1").performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals(listOf("1"), pressed)
        rule.onNodeWithTag("key1").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf("1"), pressed)
    }

    @Test
    fun slidingOffTheKeyKeepsTheDigit() {
        rule.onNodeWithTag("key1").performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 3f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("1"), pressed)
    }

    @Test
    fun accessibilityClickActsOnce() {
        rule.onNodeWithTag("key2").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(listOf("2"), pressed)
    }

    @Test
    fun clickPathWorksAfterTouches() {
        rule.onNodeWithTag("key1").performTouchInput { down(center); up() }
        // a touch the key's clickable cancels still re-arms the click path
        rule.onNodeWithTag("key1").performTouchInput { down(center); moveBy(Offset(width * 3f, 0f)); up() }
        rule.onNodeWithTag("key1").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(listOf("1", "1", "1"), pressed)
    }

    @Test
    fun repeatedTapsOnOneKeyAllCount() {
        repeat(3) { rule.onNodeWithTag("key2").performTouchInput { down(center); up() } }
        rule.waitForIdle()
        assertEquals(listOf("2", "2", "2"), pressed)
    }

    @Test
    fun twoThumbsKeepTheOrderTheyLandedIn() {
        val one = center("key1")
        val two = center("key2")
        // "1" lands first, "2" second; "2" lifts before "1".
        rule.onRoot().performTouchInput {
            down(0, one)
            down(1, two)
            up(1)
            up(0)
        }
        rule.waitForIdle()
        assertEquals(listOf("1", "2"), pressed)
    }
}
