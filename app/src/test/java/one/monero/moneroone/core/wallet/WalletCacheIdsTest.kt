package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletCacheIdsTest {

    private val seed =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            .split(" ")

    // --- Golden vectors (sha256 first 16 bytes, lowercase hex) --------------

    @Test
    fun `stableWalletId matches golden vector`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223", WalletCacheIds.stableWalletId("abc"))
    }

    @Test
    fun `derivedWalletId with zero resets has NO suffix`() {
        // resetSuffix absent when syncResetCount == 0 (must not be "0")
        assertEquals("c557eec878dfd852ba3f88087c4f350f", WalletCacheIds.derivedWalletId(seed, 0))
    }

    @Test
    fun `derivedWalletId appends decimal reset count when positive`() {
        assertEquals("f0aeb80788ab6b81f0bc9df7e0236cfe", WalletCacheIds.derivedWalletId(seed, 1))
        assertEquals("d1fb8b10d385301391f1f9c951ba9bb3", WalletCacheIds.derivedWalletId(seed, 2))
    }

    @Test
    fun `derived id is 32 lowercase hex chars`() {
        val id = WalletCacheIds.derivedWalletId(seed, 0)
        assertEquals(32, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
    }

    // --- Duplicate-seed detection (H2 regressions) ----------------------------

    private fun row(id: String, name: String, derivedId: String?, resetCount: Int = 0) = WalletInfo(
        id = id, name = name, derivedWalletId = derivedId, syncResetCount = resetCount
    )

    @Test
    fun `duplicate of a fresh wallet is found by stored id equality`() {
        val fresh = row("a", "Fresh", WalletCacheIds.derivedWalletId(seed, 0))
        val dup = WalletCacheIds.findWalletWithSeed(seed, listOf(fresh)) { null }
        assertEquals(fresh, dup)
    }

    @Test
    fun `duplicate of a MIGRATED wallet (legacy UUID id) is found via stored seed`() {
        // Migrated rows keep the legacy random-UUID cache id verbatim — the
        // stored id is NOT seed-derived, so id equality alone can never match.
        val migrated = row("m", "Personal Wallet", "123e4567-e89b-12d3-a456-426614174000")
        val dup = WalletCacheIds.findWalletWithSeed(seed, listOf(migrated)) { id ->
            if (id == "m") seed else null
        }
        assertEquals(migrated, dup)
    }

    @Test
    fun `duplicate of a RESET wallet (sha seed+N id) is found via base derivation`() {
        val reset = row("r", "Reset Wallet", WalletCacheIds.derivedWalletId(seed, 2), resetCount = 2)
        val dup = WalletCacheIds.findWalletWithSeed(seed, listOf(reset)) { id ->
            if (id == "r") seed else null
        }
        assertEquals(reset, dup)
    }

    @Test
    fun `different seed is not a duplicate of migrated or reset rows`() {
        val otherSeed = List(24) { "word$it" }
        val migrated = row("m", "Personal Wallet", "123e4567-e89b-12d3-a456-426614174000")
        val reset = row("r", "Reset Wallet", WalletCacheIds.derivedWalletId(otherSeed, 1), resetCount = 1)
        val dup = WalletCacheIds.findWalletWithSeed(seed, listOf(migrated, reset)) { id ->
            if (id == "m" || id == "r") otherSeed else null
        }
        assertEquals(null, dup)
    }

    @Test
    fun `row with unreadable seed and non-derived id is not claimed as duplicate`() {
        val opaque = row("x", "Opaque", "123e4567-e89b-12d3-a456-426614174000")
        assertEquals(null, WalletCacheIds.findWalletWithSeed(seed, listOf(opaque)) { null })
    }

    // --- Cache base names ----------------------------------------------------

    @Test
    fun `cacheBaseName strips wallet file suffixes`() {
        assertEquals("abc123", WalletCacheIds.cacheBaseName("abc123"))
        assertEquals("abc123", WalletCacheIds.cacheBaseName("abc123.keys"))
        assertEquals("abc123", WalletCacheIds.cacheBaseName("abc123.address.txt"))
    }

    // --- Shape matching ------------------------------------------------------

    @Test
    fun `shape matching accepts 32-hex and legacy UUID only`() {
        assertTrue(WalletCacheIds.isCacheIdShaped("c557eec878dfd852ba3f88087c4f350f"))
        assertTrue(WalletCacheIds.isCacheIdShaped("C557EEC878DFD852BA3F88087C4F350F"))
        assertTrue(WalletCacheIds.isCacheIdShaped("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(WalletCacheIds.isCacheIdShaped("shorthex"))
        assertFalse(WalletCacheIds.isCacheIdShaped("c557eec878dfd852ba3f88087c4f350")) // 31 chars
        assertFalse(WalletCacheIds.isCacheIdShaped("c557eec878dfd852ba3f88087c4f350fa")) // 33 chars
        assertFalse(WalletCacheIds.isCacheIdShaped("zz57eec878dfd852ba3f88087c4f350f")) // non-hex
        assertFalse(WalletCacheIds.isCacheIdShaped("my-notes.txt"))
        assertFalse(WalletCacheIds.isCacheIdShaped(""))
    }

    // --- Orphan sweep decision table (port of iOS WalletCacheIdTests) --------

    private val known = "c557eec878dfd852ba3f88087c4f350f"
    private val orphanHex = "f0aeb80788ab6b81f0bc9df7e0236cfe"
    private val orphanUuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `sweep bails entirely when any wallet id is unresolved`() {
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf(orphanHex, orphanUuid),
            knownIds = setOf(known),
            allWalletIdsKnown = false
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sweep keeps known ids and deletes shape-matching orphans`() {
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf(known, orphanHex, orphanUuid),
            knownIds = setOf(known),
            allWalletIdsKnown = true
        )
        assertEquals(setOf(orphanHex, orphanUuid), result.toSet())
    }

    @Test
    fun `sweep never touches names outside the expected shape`() {
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf("random-file", "backup.bin", ".nomedia", orphanHex),
            knownIds = setOf(known),
            allWalletIdsKnown = true
        )
        assertEquals(listOf(orphanHex), result)
    }

    @Test
    fun `sweep groups the three wallet files into one base name`() {
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf(orphanHex, "$orphanHex.keys", "$orphanHex.address.txt"),
            knownIds = setOf(known),
            allWalletIdsKnown = true
        )
        assertEquals(listOf(orphanHex), result)
    }

    @Test
    fun `sweep keeps active wallet files including suffixed ones`() {
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf(known, "$known.keys", "$known.address.txt"),
            knownIds = setOf(known),
            allWalletIdsKnown = true
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sweep with empty known ids removes all shaped entries`() {
        // All wallets deleted; leftover caches are reclaimed on next launch.
        val result = WalletCacheIds.orphanedCacheBaseNames(
            entries = listOf(orphanHex, orphanUuid, "notes.txt"),
            knownIds = emptySet(),
            allWalletIdsKnown = true
        )
        assertEquals(setOf(orphanHex, orphanUuid), result.toSet())
    }
}
