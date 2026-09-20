package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletStoreTest {

    private val wallet = WalletInfo(
        id = "11111111-2222-3333-4444-555555555555",
        name = "Personal Wallet",
        emoji = "🪙",
        source = WalletSource.LEGACY,
        createdAt = 1720000000000L,
        restoreHeight = 3200000L,
        restoreDateMillis = 1710000000000L,
        syncResetCount = 2,
        userCreatedSubaddressIndices = listOf(1, 3),
        cachedPrimaryAddress = "4ABCD",
        cachedBalance = 1234567890L,
        cachedUnlockedBalance = 1234L,
        derivedWalletId = "c557eec878dfd852ba3f88087c4f350f",
        deviceWalletId = null
    )

    @Test
    fun `wallet list round-trips through JSON`() {
        val second = WalletInfo(id = "id2", name = "Wallet 2")
        val decoded = WalletStore.decodeWallets(WalletStore.encodeWallets(listOf(wallet, second)))
        assertEquals(listOf(wallet, second), decoded)
    }

    @Test
    fun `decode preserves order`() {
        val list = (1..5).map { WalletInfo(id = "id$it", name = "W$it") }
        assertEquals(list.map { it.id }, WalletStore.decodeWallets(WalletStore.encodeWallets(list)).map { it.id })
    }

    @Test
    fun `decode tolerates unknown fields and missing optionals`() {
        val encoded = """[{"id":"x","name":"N","seedType":"bip39","someFutureField":42}]"""
        val decoded = WalletStore.decodeWallets(encoded)
        assertEquals(1, decoded.size)
        assertEquals("x", decoded[0].id)
        assertEquals("💰", decoded[0].emoji)
        assertNull(decoded[0].derivedWalletId)
        assertNull(decoded[0].cachedBalance)
        assertEquals(0, decoded[0].syncResetCount)
    }

    @Test
    fun `source is persisted under legacy seedType key`() {
        val encoded = WalletStore.encodeWallets(listOf(wallet))
        assertTrue(encoded.contains("\"seedType\":\"legacy\""))
    }

    @Test
    fun `decode of garbage or empty yields empty list`() {
        assertTrue(WalletStore.decodeWallets(null).isEmpty())
        assertTrue(WalletStore.decodeWallets("").isEmpty())
        assertTrue(WalletStore.decodeWallets("not json").isEmpty())
    }

    @Test
    fun `nextWalletName is count plus one, bumped until unique`() {
        assertEquals("Wallet 1", WalletStore.nextWalletName(emptyList()))
        assertEquals("Wallet 2", WalletStore.nextWalletName(listOf("Personal Wallet")))
        assertEquals("Wallet 3", WalletStore.nextWalletName(listOf("Personal Wallet", "Wallet 2")))
        // "Wallet 3" taken -> bump
        assertEquals("Wallet 4", WalletStore.nextWalletName(listOf("A", "B", "Wallet 3")))
    }

    @Test
    fun `reordered moves a wallet before another and to the end`() {
        val a = WalletInfo(id = "a", name = "A")
        val b = WalletInfo(id = "b", name = "B")
        val c = WalletInfo(id = "c", name = "C")
        val list = listOf(a, b, c)
        assertEquals(listOf(c, a, b), WalletStore.reordered(list, "c", "a"))
        assertEquals(listOf(b, c, a), WalletStore.reordered(list, "a", null))
        assertEquals(listOf(a, c, b), WalletStore.reordered(list, "c", "b"))
    }

    @Test
    fun `reordered ignores unknown ids and a self-target`() {
        val a = WalletInfo(id = "a", name = "A")
        val b = WalletInfo(id = "b", name = "B")
        val list = listOf(a, b)
        assertEquals(list, WalletStore.reordered(list, "zzz", "a"))
        assertEquals(list, WalletStore.reordered(list, "a", "zzz"))
        assertEquals(list, WalletStore.reordered(list, "a", "a"))
    }
}
