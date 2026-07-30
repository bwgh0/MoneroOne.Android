package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persistent list of wallets + the active wallet id. Mirrors iOS `WalletStore`.
 *
 * Stored in the plain `monero_wallet` prefs (metadata only — secrets live in
 * [WalletSecrets]). Order = list order; new wallets append.
 */
class WalletStore(private val prefs: SharedPreferences) {

    fun wallets(): List<WalletInfo> =
        decodeWallets(prefs.getString(KEY_WALLETS, null))

    fun saveWallets(wallets: List<WalletInfo>) {
        prefs.edit().putString(KEY_WALLETS, encodeWallets(wallets)).apply()
    }

    fun addWallet(info: WalletInfo) {
        saveWallets(wallets() + info)
    }

    fun updateWallet(info: WalletInfo) {
        saveWallets(wallets().map { if (it.id == info.id) info else it })
    }

    fun removeWallet(id: String) {
        saveWallets(wallets().filterNot { it.id == id })
    }

    fun activeWalletId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActiveWalletId(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, id)
        }.apply()
    }

    fun activeWallet(): WalletInfo? {
        val list = wallets()
        val activeId = activeWalletId()
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    var migrated: Boolean
        get() = prefs.getBoolean(KEY_MIGRATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MIGRATED, value).apply()
        }

    fun deleteAll() {
        prefs.edit()
            .remove(KEY_WALLETS)
            .remove(KEY_ACTIVE_ID)
            .apply()
    }

    companion object {
        const val KEY_WALLETS = "wallet_store.wallets"
        const val KEY_ACTIVE_ID = "wallet_store.active_wallet_id"
        const val KEY_MIGRATED = "wallet_store.migrated"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val listSerializer = ListSerializer(WalletInfo.serializer())

        fun encodeWallets(wallets: List<WalletInfo>): String =
            json.encodeToString(listSerializer, wallets)

        fun decodeWallets(encoded: String?): List<WalletInfo> {
            if (encoded.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString(listSerializer, encoded)
            } catch (e: Exception) {
                Timber.e(e, "WalletStore: failed to decode wallet list")
                emptyList()
            }
        }

        /** "Wallet N" starting at count+1, bumped until unique. */
        fun nextWalletName(existingNames: Collection<String>): String {
            var n = existingNames.size + 1
            while (existingNames.contains("Wallet $n")) {
                n += 1
            }
            return "Wallet $n"
        }

        /** True when any wallet exists (new store or legacy single-wallet key). */
        fun hasAnyWallet(context: Context): Boolean {
            val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            return decodeWallets(prefs.getString(KEY_WALLETS, null)).isNotEmpty() ||
                prefs.getString("wallet_id", null) != null
        }

        /** Active wallet metadata for out-of-viewmodel consumers (widget, service). */
        fun activeWalletInfo(context: Context): WalletInfo? {
            val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            return WalletStore(prefs).activeWallet()
        }
    }
}
