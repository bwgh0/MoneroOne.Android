package one.monero.moneroone.core.wallet

import java.security.MessageDigest

/**
 * Seed-derived wallet cache ids + orphan-sweep decision logic.
 *
 * THE single place cache ids are derived. Both the code that persists
 * `WalletInfo.derivedWalletId` and the code that opens/creates the kit MUST
 * go through [derivedWalletId] — persisting an id the kit did not derive (or
 * vice versa) makes the launch sweep delete a live cache, destroying wallet2
 * per-transaction keys permanently (iOS 6f1053f).
 *
 * Formula mirrors iOS `MoneroWallet.walletCacheId`:
 *   stableWalletId(seedPhrase + resetSuffix)   // mainnet contributes no suffix
 * where resetSuffix = decimal syncResetCount when > 0, else absent (never "0").
 */
object WalletCacheIds {

    /** First 16 bytes of SHA-256 as lowercase hex (32 chars). */
    fun stableWalletId(identifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Cache id for a seed-based wallet. [seedWords] are the words as stored
     * (joined with single spaces).
     */
    fun derivedWalletId(seedWords: List<String>, syncResetCount: Int): String {
        val resetSuffix = if (syncResetCount > 0) syncResetCount.toString() else ""
        return stableWalletId(seedWords.joinToString(" ") + resetSuffix)
    }

    // --- Orphan-cache sweep -------------------------------------------------

    private val HEX_32 = Regex("^[0-9a-fA-F]{32}$")

    /** Legacy single-wallet cache names were java.util.UUID strings. */
    private val LEGACY_UUID = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /** True when [name] has the shape of a wallet cache id we manage. */
    fun isCacheIdShaped(name: String): Boolean =
        HEX_32.matches(name) || LEGACY_UUID.matches(name)

    /**
     * Reduce a wallet-dir file name to its cache base name.
     * Wallet files are `<id>`, `<id>.keys`, `<id>.address.txt`.
     */
    fun cacheBaseName(fileName: String): String = fileName
        .removeSuffix(".address.txt")
        .removeSuffix(".keys")

    /**
     * Decide which cache base names are orphans safe to delete.
     * Port of iOS `WalletManager.orphanedCacheDirNames`:
     *  - if ANY wallet's id is unresolved, sweep NOTHING (returns empty)
     *  - never delete a known id
     *  - only delete names matching an expected cache-id shape
     */
    fun orphanedCacheBaseNames(
        entries: Collection<String>,
        knownIds: Set<String>,
        allWalletIdsKnown: Boolean
    ): List<String> {
        if (!allWalletIdsKnown) return emptyList()
        return entries
            .map { cacheBaseName(it) }
            .distinct()
            .filter { name -> name !in knownIds && isCacheIdShaped(name) }
    }
}
