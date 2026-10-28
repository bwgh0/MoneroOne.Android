package one.monero.moneroone.core.username

import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The claim messages of the shared contract (registry code or platform error, then the text). */
class UsernameMessagesTest {
    @Test fun eachRegistryCodeShowsItsText() {
        val texts = mapOf(
            "geo.blocked" to "Username registration isn't available in your region.",
            "device.invalid" to "This phone can't claim a username.",
            "device.unsupported" to "This phone can't claim a username.",
            "device.unavailable" to "The claim could not be completed. Please try again.",
            "not_configured" to "Username registration isn't available yet.",
            "device.slot_used" to "A free username isn't available on this phone.",
            "name.unavailable" to "This username is unavailable.",
            "handle.reserved" to "This username is reserved to protect users from scams.",
            "handle.blocked" to "This username is reserved to protect users from scams.",
            "rate_limited" to "The username service couldn't complete the request. Please try again.",
            "wallet_changed" to "The wallet changed. Open this flow from the wallet you want to use.",
            "wallet.opening" to "Your wallet is still opening. Try again in a moment.",
            "wallet.offline" to "Could not reach the node. Check your connection and try again.",
        )
        texts.forEach { (code, text) -> assertEquals(code, text, UsernameException(code).message) }
    }

    @Test fun theNewTextsAreTranslated() {
        for (text in listOf("Username registration isn't available in your region.", "This phone can't claim a username.",
                "Your wallet is still opening. Try again in a moment.", "Could not reach the node. Check your connection and try again."))
            assertTrue(text, text in one.monero.moneroone.core.locale.localizedStrings)
    }

    /** The same wallet with its file not open: still opening or connecting, or its node failed. Never "wallet_changed". */
    @Test fun aWalletThatIsNotOpenSaysWhy() {
        val states = mapOf(
            SyncState.Connecting(waiting = false) to "wallet.opening",
            SyncState.Connecting(waiting = true) to "wallet.opening",
            SyncState.Syncing(0.5) to "wallet.opening",
            SyncState.Synced to "wallet.opening",
            SyncState.NotSynced(MoneroKit.SyncError.NotStarted) to "wallet.opening",
            SyncState.NotSynced(MoneroKit.SyncError.StartError("Connection refused")) to "wallet.offline",
            SyncState.NotSynced(MoneroKit.SyncError.InvalidNode("Invalid node")) to "wallet.offline",
            // The wallet's own files, not the node: the app opens the wallet again.
            SyncState.NotSynced(MoneroKit.SyncError.InvalidNode("Invalid wallet")) to "wallet.opening",
        )
        states.forEach { (state, code) -> assertEquals(state.description, code, UsernameException.walletNotReady(state).code) }
    }
}
