package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDestinationTest {

    private val valid: (String) -> Boolean = { it.startsWith("4") && it.length > 20 }
    private val addrA = "4" + "A".repeat(94)
    private val addrB = "4" + "B".repeat(94)

    private val current = WalletInfo(id = "a", name = "QA Transfer", emoji = "💰", cachedPrimaryAddress = addrA)
    private val other = WalletInfo(id = "b", name = "Wallet 3", emoji = "🐷", cachedPrimaryAddress = addrB)
    private val notOpened = WalletInfo(id = "c", name = "New", cachedPrimaryAddress = null)

    private fun ready(w: WalletInfo) = TransferDestination.of(w, needsRestore = false, isValidAddress = valid)

    @Test
    fun oneWalletIsFixedOnThisWallet() {
        val wallets = listOf(current)
        assertFalse(TransferTo.hasMenu(wallets, current.id))
        assertEquals(current, TransferTo.shown(wallets, current.id, chosenId = null))
        val choice = TransferTo.resolve(wallets, current.id, chosenId = null, destinationOf = ::ready)
        assertEquals(TransferChoice(current, addrA), choice)
        assertTrue(TransferTo.isThisWallet(choice?.wallet, current.id))
    }

    @Test
    fun oneWalletWithNoSavedAddressWaits() {
        val wallets = listOf(current.copy(cachedPrimaryAddress = null))
        assertNull(TransferTo.resolve(wallets, current.id, chosenId = null, destinationOf = ::ready))
    }

    @Test
    fun twoWalletsStartOnChooseAWallet() {
        val wallets = listOf(current, other)
        assertTrue(TransferTo.hasMenu(wallets, current.id))
        assertNull(TransferTo.shown(wallets, current.id, chosenId = null))
        assertNull(TransferTo.resolve(wallets, current.id, chosenId = null, destinationOf = ::ready))
    }

    @Test
    fun twoWalletsThisWalletSendsToItsMainAddress() {
        val wallets = listOf(current, other)
        val choice = TransferTo.resolve(wallets, current.id, chosenId = current.id, destinationOf = ::ready)
        assertEquals(TransferChoice(current, addrA), choice)
        assertTrue(TransferTo.isThisWallet(choice?.wallet, current.id))
    }

    @Test
    fun twoWalletsOtherWalletSendsToItsMainAddress() {
        val wallets = listOf(current, other)
        val choice = TransferTo.resolve(wallets, current.id, chosenId = other.id, destinationOf = ::ready)
        assertEquals(TransferChoice(other, addrB), choice)
        assertFalse(TransferTo.isThisWallet(choice?.wallet, current.id))
    }

    @Test
    fun walletWithNoSavedAddressIsDisabled() {
        assertEquals(TransferDestination.NeedsOpen, ready(notOpened))
        assertEquals(TransferDestination.NeedsOpen, ready(other.copy(cachedPrimaryAddress = "not an address")))
        val wallets = listOf(current, notOpened)
        assertNull(TransferTo.resolve(wallets, current.id, chosenId = notOpened.id, destinationOf = ::ready))
    }

    @Test
    fun walletWithoutKeysNeedsRestore() {
        val state = TransferDestination.of(other, needsRestore = true, isValidAddress = valid)
        assertEquals(TransferDestination.NeedsRestore, state)
        val wallets = listOf(current, other)
        val choice = TransferTo.resolve(wallets, current.id, chosenId = other.id) {
            TransferDestination.of(it, needsRestore = it.id == other.id, isValidAddress = valid)
        }
        assertNull(choice)
    }

    @Test
    fun removedChoiceFallsBack() {
        // The chosen wallet is gone and another remains: back to "Choose a wallet".
        val third = other.copy(id = "d", name = "Third")
        assertNull(TransferTo.resolve(listOf(current, third), current.id, chosenId = other.id, destinationOf = ::ready))
        // The chosen wallet is gone and this is the only wallet: This Wallet.
        assertEquals(
            TransferChoice(current, addrA),
            TransferTo.resolve(listOf(current), current.id, chosenId = other.id, destinationOf = ::ready)
        )
    }
}
