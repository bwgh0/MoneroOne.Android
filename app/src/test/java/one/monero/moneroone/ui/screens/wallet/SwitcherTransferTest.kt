package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The switcher shows Transfer when the active wallet can send (iOS walletManager.canSend). */
class SwitcherTransferTest {

    @Test
    fun aWalletThatCanSendShowsTransferAlsoAlone() {
        assertTrue(switcherShowsTransfer(WalletInfo(id = "a", name = "Only", source = WalletSource.POLYSEED)))
        assertTrue(switcherShowsTransfer(WalletInfo(id = "b", name = "Legacy", source = WalletSource.LEGACY)))
    }

    @Test
    fun aViewOnlyWalletHasNoTransfer() {
        assertFalse(switcherShowsTransfer(WalletInfo(id = "v", name = "Watch", source = WalletSource.VIEW_ONLY)))
    }

    @Test
    fun noActiveWalletHasNoTransfer() {
        assertFalse(switcherShowsTransfer(null))
    }
}
