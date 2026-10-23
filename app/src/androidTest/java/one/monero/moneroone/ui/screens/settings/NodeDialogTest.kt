package one.monero.moneroone.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.node.NodeCredentials
import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.ui.theme.MoneroOneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeDialogTest {
    @get:Rule val rule = createComposeRule()

    // The fields in order: Name, Node URI, then Username and Password once
    // Authentication is open.
    private fun field(index: Int) = rule.onAllNodes(hasSetTextAction())[index]

    @Test fun onionNodeReadsAsTorNeverAsUnencryptedHttp() {
        var torEnabled by mutableStateOf(false)
        val draft = NodeDraft("", "", null)
        rule.setContent {
            MoneroOneTheme {
                NodeDialog(tr("Add Node"), tr("Add"), draft, emptySet(), torEnabled, { _, _, _, _ -> }, {})
            }
        }
        val uriField = field(1)
        val unencrypted = tr("Connection will be unencrypted (HTTP)")

        uriField.performTextReplacement(DefaultNodes.TOR.last().uri)
        rule.onNodeWithText(tr("Requires Tor")).assertExists()
        rule.onNodeWithText(unencrypted).assertDoesNotExist()

        torEnabled = true
        rule.onNodeWithText(tr("Tor")).assertExists()
        rule.onNodeWithText(tr("Requires Tor")).assertDoesNotExist()
        rule.onNodeWithText(unencrypted).assertDoesNotExist()

        // A clearnet node on a plain port still reads as unencrypted.
        uriField.performTextReplacement("node.example.com:18081")
        rule.onNodeWithText(unencrypted).assertExists()
    }

    @Test fun httpsNodeReadsAsTlsOnAnyPort() {
        val draft = NodeDraft("", "", null)
        rule.setContent {
            MoneroOneTheme {
                NodeDialog(tr("Add Node"), tr("Add"), draft, emptySet(), false, { _, _, _, _ -> }, {})
            }
        }
        // As on iOS, the https:// scheme marks a TLS node on any port.
        field(1).performTextReplacement("https://node.example.com:18089")
        rule.onNodeWithText(tr("Connection will be encrypted (TLS)")).assertExists()
        rule.onNodeWithText(tr("Connection will be unencrypted (HTTP)")).assertDoesNotExist()
    }

    // A theme change or a rotation recreates the Activity. The draft lives in a
    // ViewModel then, so a new composition must show what the user typed.
    @Test fun whatWasTypedOutlivesARecreatedScreen() {
        val draft = NodeDraft("", "", null)
        var shown by mutableStateOf(true)
        rule.setContent {
            MoneroOneTheme {
                if (shown) NodeDialog(tr("Add Node"), tr("Add"), draft, emptySet(), false, { _, _, _, _ -> }, {})
            }
        }
        field(0).performTextReplacement("Home Node")
        field(1).performTextReplacement("node.example.com:18081")
        rule.onNodeWithContentDescription(tr("Show authentication")).performClick()
        field(2).performTextReplacement("alice")
        field(3).performTextReplacement("secret")

        shown = false
        rule.waitForIdle()
        rule.onNodeWithText(tr("Add Node")).assertDoesNotExist()
        shown = true

        rule.onNodeWithText(tr("Add Node")).assertExists()
        rule.onNodeWithText("Home Node").assertExists()
        rule.onNodeWithText("node.example.com:18081").assertExists()
        rule.onNodeWithText("alice").assertExists()
        rule.onNodeWithContentDescription(tr("Show password")).performClick()
        rule.onNodeWithText("secret").assertExists()
    }

    // As on iOS, a node needs a name: Add stays off while the name is blank.
    @Test fun addStaysOffUntilTheNodeHasAName() {
        val draft = NodeDraft("", "", null)
        var confirmed: Triple<String, String, NodeCredentials?>? = null
        rule.setContent {
            MoneroOneTheme {
                NodeDialog(tr("Add Node"), tr("Add"), draft, emptySet(), false, { name, uri, login, _ ->
                    confirmed = Triple(name, uri, login)
                }, {})
            }
        }
        field(1).performTextReplacement("node.example.com:18081")
        rule.onNodeWithText(tr("Add")).assertIsNotEnabled()

        field(0).performTextReplacement("   ")
        rule.onNodeWithText(tr("Add")).assertIsNotEnabled()

        field(0).performTextReplacement("  Home Node ")
        rule.onNodeWithText(tr("Add")).assertIsEnabled().performClick()
        assertEquals(Triple("Home Node", "node.example.com:18081", null), confirmed)
    }

    // A node from before names opens with the label its row shows, so it saves as is.
    @Test fun editOpensWithTheNameTheRowShows() {
        val draft = NodeDraft(tr("Custom Node"), "node.example.com:18081", null)
        var savedName: String? = null
        rule.setContent {
            MoneroOneTheme {
                NodeDialog(tr("Edit Node"), tr("Save"), draft, emptySet(), false, { name, _, _, _ -> savedName = name }, {})
            }
        }
        rule.onNodeWithText(tr("Custom Node")).assertExists()
        rule.onNodeWithText(tr("Save")).assertIsEnabled().performClick()
        assertEquals(tr("Custom Node"), savedName)
    }
}
