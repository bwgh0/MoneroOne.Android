package one.monero.moneroone.ui.screens.wallet

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.ui.theme.MoneroOneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNodeInteraction.withCustomActions(block: (List<CustomAccessibilityAction>) -> Unit) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { block(actions) }
    }

    @Test fun talkBackCanSwitchRenameAndReorderWithoutGestures() {
        val wallets = listOf(WalletInfo("first", "First"), WalletInfo("second", "Second"), WalletInfo("third", "Third"))
        var switched: String? = null
        var moved: Pair<String, String?>? = null
        rule.setContent { MoneroOneTheme {
            WalletManagerRows(wallets, wallets.first(), "0 XMR", { "0" }, { switched = it.id }, {}, { _, _, _ -> },
                { id, before -> moved = id to before }, {})
        } }
        val active = rule.onNode(hasContentDescription("First", substring = true))
        active.assertIsSelected()
        active.withCustomActions() { actions ->
            assertFalse(actions.any { it.label == tr("Delete") || it.label == tr("Move up") })
            assertTrue(actions.first { it.label == tr("Move down") }.action())
        }
        assertEquals("first" to "third", moved)
        val second = rule.onNode(hasContentDescription("Second", substring = true))
        second.performClick()
        assertEquals("second", switched)
        second.withCustomActions() { actions ->
            assertTrue(actions.any { it.label == tr("Delete") })
            assertTrue(actions.first { it.label == tr("Move up") }.action())
        }
        assertEquals("second" to "first", moved)
        second.withCustomActions() { actions ->
            assertTrue(actions.first { it.label == tr("Rename") }.action())
        }
        rule.onNode(hasSetTextAction()).assertExists()
    }

    @Test fun eachDragHandleReadsReorderAndMovesItsRow() {
        val wallets = listOf(WalletInfo("first", "First"), WalletInfo("second", "Second"))
        var moved: Pair<String, String?>? = null
        rule.setContent { MoneroOneTheme {
            WalletManagerRows(wallets, wallets.first(), "0 XMR", { "0" }, {}, {}, { _, _, _ -> },
                { id, before -> moved = id to before }, {})
        } }
        val handles = rule.onAllNodesWithContentDescription(tr("Reorder"))
        handles.assertCountEquals(2)
        handles[1].withCustomActions { actions ->
            assertTrue(actions.first { it.label == tr("Move up") }.action())
        }
        assertEquals("second" to "first", moved)
    }

    @Test fun emojiPickerReadsEachEmojiByNameAsAButton() {
        var picked: String? = null
        rule.setContent { MoneroOneTheme { EmojiPickerGrid(selected = "💰", onSelect = { picked = it }) } }
        val bag = rule.onNodeWithContentDescription("money bag")
        bag.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        bag.assertIsSelected()
        bag.assertHasClickAction()
        val other = rule.onAllNodes(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .onFirst()
        other.performClick()
        assertNotNull(picked)
        assertNotEquals("💰", picked)
        assertEquals("Icon, money bag", tr("Icon, %s", emojiSpokenName("💰")))
    }
}
