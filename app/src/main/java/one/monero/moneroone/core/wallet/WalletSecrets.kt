package one.monero.moneroone.core.wallet

import android.content.SharedPreferences

/**
 * Per-wallet secrets in EncryptedSharedPreferences ("secure_wallet_data"),
 * scoped under `wallet.<id>.` prefixes. Mirrors iOS per-wallet keychain
 * accounts (`one.monero.MoneroOne.wallet.<UUID>`).
 *
 * The PIN is one app-wide PIN UX-wise, but its hash is stored per wallet
 * (iOS parity); PIN change rewrites every wallet's entry two-pass
 * (see WalletViewModel.changePin). PIN rate-limit counters stay GLOBAL —
 * lockout is app-wide.
 */
class WalletSecrets(private val prefs: SharedPreferences) {

    private fun seedKey(id: String) = "wallet.$id.seed_words"
    private fun seedTypeKey(id: String) = "wallet.$id.seed_type"
    private fun pinHashKey(id: String) = "wallet.$id.pin_hash"

    fun saveSeed(id: String, words: List<String>, type: SeedType) {
        prefs.edit()
            .putString(seedKey(id), words.joinToString(" "))
            .putString(seedTypeKey(id), type.name)
            .apply()
    }

    fun loadSeed(id: String): Pair<List<String>, SeedType>? {
        val words = prefs.getString(seedKey(id), null) ?: return null
        val typeName = prefs.getString(seedTypeKey(id), null) ?: return null
        val type = runCatching { SeedType.valueOf(typeName) }.getOrNull() ?: return null
        return words.split(" ") to type
    }

    fun hasSeed(id: String): Boolean = prefs.getString(seedKey(id), null) != null

    fun savePinHash(id: String, hash: String) {
        prefs.edit().putString(pinHashKey(id), hash).apply()
    }

    fun pinHash(id: String): String? = prefs.getString(pinHashKey(id), null)

    /** Wipe everything belonging to one wallet. */
    fun deleteWalletSecrets(id: String) {
        prefs.edit()
            .remove(seedKey(id))
            .remove(seedTypeKey(id))
            .remove(pinHashKey(id))
            .apply()
    }

    // --- Legacy single-wallet keys (pre-multiwallet), used by migration ------

    fun legacySeed(): Pair<List<String>, SeedType>? {
        val words = prefs.getString(LEGACY_SEED_WORDS, null) ?: return null
        val typeName = prefs.getString(LEGACY_SEED_TYPE, null) ?: return null
        val type = runCatching { SeedType.valueOf(typeName) }.getOrNull() ?: return null
        return words.split(" ") to type
    }

    fun wipeLegacySeed() {
        prefs.edit()
            .remove(LEGACY_SEED_WORDS)
            .remove(LEGACY_SEED_TYPE)
            .apply()
    }

    companion object {
        const val LEGACY_SEED_WORDS = "seed_words"
        const val LEGACY_SEED_TYPE = "seed_type"
    }
}
