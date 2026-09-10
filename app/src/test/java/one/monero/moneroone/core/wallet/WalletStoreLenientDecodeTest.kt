package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A row this build cannot decode (newer build's source encoding, damaged
 * value) must neither hide the other wallets nor be dropped by a save, and
 * must be reported so the orphan sweep stays off.
 */
class WalletStoreLenientDecodeTest {

    private val good = WalletInfo(id = "good-id", name = "Good", derivedWalletId = "c4d72b4f19457914877dd76e4e355ecf")

    /** Shape a hardware-wallet row from a newer build might have: unknown seedType value + extra field. */
    private val foreignRow =
        """{"id":"hw-id","name":"Trezor","emoji":"🔐","seedType":"hardware:trezor:safe7|abc|def","createdAt":1,""" +
            """"restoreHeight":0,"restoreDateMillis":0,"syncResetCount":0,"userCreatedSubaddressIndices":[],""" +
            """"cachedPrimaryAddress":null,"cachedBalance":null,"cachedUnlockedBalance":null,""" +
            """"derivedWalletId":null,"deviceWalletId":"deadbeefdeadbeefdeadbeefdeadbeef","binding":"ble:00:11"}"""

    private fun storeWith(raw: String): Pair<WalletStore, FakeSharedPreferences> {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(WalletStore.KEY_WALLETS, raw).apply()
        return WalletStore(prefs) to prefs
    }

    @Test
    fun `foreign row does not hide decodable rows`() {
        val raw = "[" + WalletStore.encodeWallets(listOf(good)).trim('[', ']') + "," + foreignRow + "]"
        val (store, _) = storeWith(raw)
        assertEquals(listOf(good), store.wallets())
        assertTrue(store.hasUndecodableRows())
    }

    @Test
    fun `foreign row survives add update remove`() {
        val raw = "[" + WalletStore.encodeWallets(listOf(good)).trim('[', ']') + "," + foreignRow + "]"
        val (store, prefs) = storeWith(raw)

        store.addWallet(WalletInfo(id = "second", name = "Second"))
        store.updateWallet(good.copy(name = "Renamed"))
        store.removeWallet("second")

        val stored = prefs.getString(WalletStore.KEY_WALLETS, null)!!
        assertTrue("foreign row must be preserved verbatim", stored.contains("hardware:trezor:safe7|abc|def"))
        assertTrue(stored.contains("\"binding\":\"ble:00:11\""))
        assertEquals(listOf("Renamed"), store.wallets().map { it.name })
        assertTrue(store.hasUndecodableRows())
    }

    @Test
    fun `clean store reports no undecodable rows and round-trips`() {
        val (store, _) = storeWith(WalletStore.encodeWallets(listOf(good)))
        assertFalse(store.hasUndecodableRows())
        store.addWallet(WalletInfo(id = "b", name = "B"))
        assertEquals(listOf("good-id", "b"), store.wallets().map { it.id })
        assertFalse(store.hasUndecodableRows())
    }

    @Test
    fun `empty or missing value is not undecodable`() {
        assertFalse(WalletStore(FakeSharedPreferences()).hasUndecodableRows())
        val (store, _) = storeWith("")
        assertFalse(store.hasUndecodableRows())
        assertTrue(store.wallets().isEmpty())
    }

    @Test
    fun `corrupt value yields no rows, is reported, and is backed up on first save`() {
        val (store, prefs) = storeWith("{not json")
        assertTrue(store.wallets().isEmpty())
        assertTrue(store.hasUndecodableRows())
        assertNull(prefs.getString(WalletStore.KEY_WALLETS_CORRUPT_BACKUP, null))

        store.addWallet(good)
        assertEquals("{not json", prefs.getString(WalletStore.KEY_WALLETS_CORRUPT_BACKUP, null))
        assertEquals(listOf(good), store.wallets())
        assertFalse(store.hasUndecodableRows())
    }

    @Test
    fun `non-array json is treated as corrupt`() {
        val (store, _) = storeWith("""{"id":"x"}""")
        assertTrue(store.wallets().isEmpty())
        assertTrue(store.hasUndecodableRows())
    }

    @Test
    fun `whole-list decode drops nothing that is decodable`() {
        val raw = "[" + foreignRow + "," + WalletStore.encodeWallets(listOf(good)).trim('[', ']') + "]"
        val decoded = WalletStore.decodeWallets(raw)
        assertEquals(1, decoded.size)
        assertNotNull(decoded.first().derivedWalletId)
    }

    @Test
    fun `sweep decision with foreign rows present must be made by the caller - ids stay unknown`() {
        // The pure sweep helper cannot see foreign rows; the VM gates on
        // hasUndecodableRows(). This pins the contract: with allKnown=false
        // nothing is swept even when knownIds is empty.
        val orphans = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf("c4d72b4f19457914877dd76e4e355ecf", "c4d72b4f19457914877dd76e4e355ecf.keys"),
            knownIds = emptySet(),
            allWalletIdsKnown = false
        )
        assertTrue(orphans.isEmpty())
    }
}
