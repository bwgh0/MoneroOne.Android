package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.data.Subaddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiveAddressesTest {

    private fun sub(index: Int) = Subaddress(0, index, "address-$index", "")

    private fun addresses(
        walletId: String = "wallet-a",
        indices: List<Int> = listOf(0, 1, 2),
        complete: Boolean = true,
        blocked: Boolean = false
    ) = ReceiveAddresses(walletId, indices.map(::sub), complete, blocked)

    // --- Which wallet the list belongs to -------------------------------------

    @Test
    fun `addresses of another wallet are never returned`() {
        // The switch window: wallet B is active while wallet A's kit is still held.
        val state = WalletState(addresses = addresses(walletId = "wallet-a"))
        assertNull(state.addressesOf("wallet-b"))
    }

    @Test
    fun `addresses are returned for their own wallet`() {
        val state = WalletState(addresses = addresses(walletId = "wallet-a"))
        assertEquals("wallet-a", state.addressesOf("wallet-a")?.walletId)
    }

    @Test
    fun `no active wallet shows no addresses`() {
        val state = WalletState(addresses = addresses())
        assertNull(state.addressesOf(null))
    }

    // --- Which address is shown ------------------------------------------------

    @Test
    fun `the selected address is shown when the list has it`() {
        assertEquals(2, addresses().shownAddress(2)?.addressIndex)
    }

    @Test
    fun `a selected index missing from the list falls back to the primary`() {
        // Before the wallet file opens the list holds index 0 only.
        val shown = addresses(indices = listOf(0), complete = false).shownAddress(5)
        assertEquals(0, shown?.addressIndex)
    }

    @Test
    fun `entries are found by address index, not by position`() {
        // Before the file opens: the primary plus the selected index, derived from the seed.
        val shown = addresses(indices = listOf(0, 7), complete = false).shownAddress(7)
        assertEquals("address-7", shown?.address)
    }

    @Test
    fun `a blocked list shows nothing`() {
        // Blocked lists are published empty; even a stray entry must not be shown.
        assertNull(addresses(blocked = true).shownAddress(0))
        assertNull(addresses(indices = emptyList(), blocked = true).shownAddress(0))
    }

    @Test
    fun `an empty list shows nothing`() {
        assertNull(addresses(indices = emptyList()).shownAddress(0))
    }
}
