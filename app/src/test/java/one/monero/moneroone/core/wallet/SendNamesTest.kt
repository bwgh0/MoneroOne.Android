package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transfer between your wallets and the History names of sends between them. */
class SendNamesTest {

    private val addrA = "4" + "A".repeat(94)
    private val addrB = "4" + "B".repeat(94)
    private val daily = WalletInfo(id = "a", name = "Daily", emoji = "🦊", cachedPrimaryAddress = addrA)
    private val savings = WalletInfo(id = "b", name = "Savings", emoji = "🦉", cachedPrimaryAddress = addrB)
    private val fresh = WalletInfo(id = "c", name = "Fresh", cachedPrimaryAddress = null)
    private val wallets = listOf(daily, savings, fresh)

    // Own-address rule (wallet2 books a confirmed send to yourself as 0 plus the fee)

    @Test fun sendOfZeroWithAFeeIsASendToSelf() {
        assertTrue(SendNames.isSendToSelf(incoming = false, amount = 0, fee = 31_420_000))
    }

    @Test fun otherRowsAreNotASendToSelf() {
        assertFalse(SendNames.isSendToSelf(incoming = false, amount = 5, fee = 31_420_000))
        assertFalse(SendNames.isSendToSelf(incoming = true, amount = 0, fee = 31_420_000))
        assertFalse(SendNames.isSendToSelf(incoming = false, amount = 0, fee = 0))
    }

    @Test fun oldSendToSelfWithoutARecordReadsToThisWallet() {
        val name = SendNames.nameFor("old", incoming = false, amount = 0, fee = 9, walletId = "a", records = emptyList(), wallets = wallets)
        assertEquals(SendName.ToThisWallet, name)
        assertEquals(9L, SendNames.shownAmount(name, amount = 0, fee = 9))
    }

    @Test fun recordedSendToOwnAddressReadsToThisWalletWithTheFeeOnly() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", toWalletId = "a"))
        // In the pool wallet2 can still show the full amount.
        val name = SendNames.nameFor("t", incoming = false, amount = 700, fee = 9, walletId = "a", records = records, wallets = wallets)
        assertEquals(SendName.ToThisWallet, name)
        assertEquals(9L, SendNames.shownAmount(name, amount = 700, fee = 9))
    }

    // Row names

    @Test fun transferNamesBothSides() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", toWalletId = "b"))
        assertEquals(SendName.ToWallet(savings), SendNames.nameFor("t", false, 500, 9, "a", records, wallets))
        assertEquals(SendName.FromWallet(daily), SendNames.nameFor("t", true, 500, 0, "b", records, wallets))
        assertEquals(500L, SendNames.shownAmount(SendName.ToWallet(savings), 500, 9))
    }

    @Test fun renamedWalletShowsItsNewName() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", toWalletId = "b"))
        val renamed = listOf(daily, savings.copy(name = "Rainy Day"))
        val name = SendNames.nameFor("t", false, 500, 9, "a", records, renamed) as SendName.ToWallet
        assertEquals("Rainy Day", name.wallet.name)
    }

    @Test fun removedWalletNamesNothing() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", toWalletId = "b"))
        assertNull(SendNames.nameFor("t", false, 500, 9, "a", records, listOf(daily)))
        assertNull(SendNames.nameFor("t", true, 500, 0, "b", records, listOf(savings)))
    }

    @Test fun recordOfAnotherWalletDoesNotNameThisOne() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", toWalletId = "b"))
        // The same txid seen by a third wallet (it cannot be, but the lookup stays strict).
        assertNull(SendNames.nameFor("t", false, 500, 9, "c", records, wallets))
        assertNull(SendNames.nameFor("t", true, 500, 0, "c", records, wallets))
        assertNull(SendNames.nameFor("other", false, 500, 9, "a", records, wallets))
        assertNull(SendNames.nameFor("t", false, 500, 9, null, records, wallets))
    }

    @Test fun recordWithoutAWalletKeepsTodaysLabel() {
        val records = listOf(SendNameRecord("t", fromWalletId = "a", recipient = "donate@example.org"))
        assertNull(SendNames.nameFor("t", false, 500, 9, "a", records, wallets))
    }

    // Address-to-wallet match

    @Test fun savedPrimaryAddressMatchesItsWallet() {
        assertEquals(savings, SendNames.matchWallet(addrB, wallets))
        assertEquals(daily, SendNames.matchWallet("  $addrA\n", wallets))
    }

    @Test fun otherAddressesMatchNoWallet() {
        assertNull(SendNames.matchWallet("4" + "C".repeat(94), wallets))
        assertNull(SendNames.matchWallet("", wallets))
        assertNull(SendNames.matchWallet(addrB.dropLast(1), wallets))
    }

    // To row states

    @Test fun transferTargetsAreTheOtherWalletsInOrder() {
        assertEquals(listOf(savings, fresh), SendNames.transferTargets(wallets, "a"))
        assertEquals(listOf(daily, fresh), SendNames.transferTargets(wallets, "b"))
    }

    @Test fun walletWithAValidSavedAddressCanBePicked() {
        assertEquals(addrB, SendNames.transferAddress(savings) { true })
    }

    @Test fun walletNotOpenedSinceTheUpdateIsDimmed() {
        assertNull(SendNames.transferAddress(fresh) { true })
        assertNull(SendNames.transferAddress(fresh.copy(cachedPrimaryAddress = "")) { true })
    }

    @Test fun savedAddressThatFailsSendsCheckIsDimmed() {
        assertNull(SendNames.transferAddress(savings) { false })
    }
}
