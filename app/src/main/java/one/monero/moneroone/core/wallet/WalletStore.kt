package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import timber.log.Timber

/**
 * Persistent list of wallets + the active wallet id. Mirrors iOS `WalletStore`.
 *
 * Stored in the plain `monero_wallet` prefs (metadata only — secrets live in
 * [WalletSecrets]). Order = list order; new wallets append.
 *
 * Decoding is lenient PER ROW: a row this build cannot decode (written by a
 * newer build, e.g. a hardware-wallet source, or damaged) is kept verbatim
 * by every save and reported through [hasUndecodableRows] so the orphan
 * sweep never runs against an incomplete picture. The old all-or-nothing
 * decode turned one foreign row into "no wallets": every cache on disk was
 * then swept as an orphan and every wallet vanished from the UI.
 */
class WalletStore(private val prefs: SharedPreferences) {

    internal class Decoded(
        val rows: List<WalletInfo>,
        /** Elements that failed to decode as a WalletInfo — preserved as-is on save. */
        val foreign: List<JsonElement>,
        /** The stored value was not a JSON array at all. */
        val corrupt: Boolean
    ) {
        val undecodable: Boolean get() = corrupt || foreign.isNotEmpty()
    }

    private fun decode(): Decoded = decodeLenient(prefs.getString(KEY_WALLETS, null))

    fun wallets(): List<WalletInfo> = decode().rows

    /**
     * True when the stored list holds rows this build cannot decode. Their
     * cache ids are unknown, so nothing on disk can be proven orphaned and
     * no destructive cleanup may assume the visible rows are all there is.
     */
    fun hasUndecodableRows(): Boolean = decode().undecodable

    fun saveWallets(wallets: List<WalletInfo>) {
        val current = decode()
        if (current.corrupt && !prefs.contains(KEY_WALLETS_CORRUPT_BACKUP)) {
            // Keep the unreadable value for recovery instead of silently replacing it.
            prefs.getString(KEY_WALLETS, null)?.let { raw ->
                prefs.edit().putString(KEY_WALLETS_CORRUPT_BACKUP, raw).apply()
            }
        }
        prefs.edit().putString(KEY_WALLETS, encodeWallets(wallets, current.foreign)).apply()
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

    /** Full wipe of the list (brute-force protection / remove-all). Foreign rows go too. */
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
        const val KEY_WALLETS_CORRUPT_BACKUP = "wallet_store.wallets.corrupt_backup"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val rowSerializer = WalletInfo.serializer()

        fun encodeWallets(wallets: List<WalletInfo>, foreign: List<JsonElement> = emptyList()): String =
            JsonArray(wallets.map { json.encodeToJsonElement(rowSerializer, it) } + foreign).toString()

        /** Rows this build can decode (foreign/corrupt content dropped from the RESULT only). */
        fun decodeWallets(encoded: String?): List<WalletInfo> = decodeLenient(encoded).rows

        internal fun decodeLenient(encoded: String?): Decoded {
            if (encoded.isNullOrBlank()) return Decoded(emptyList(), emptyList(), corrupt = false)
            val array = try {
                json.parseToJsonElement(encoded) as? JsonArray
            } catch (e: Exception) {
                null
            }
            if (array == null) {
                Timber.e("WalletStore: stored wallet list is not a JSON array")
                return Decoded(emptyList(), emptyList(), corrupt = true)
            }
            val rows = ArrayList<WalletInfo>(array.size)
            val foreign = ArrayList<JsonElement>()
            array.forEach { element ->
                try {
                    rows.add(json.decodeFromJsonElement(rowSerializer, element))
                } catch (e: Exception) {
                    Timber.e("WalletStore: keeping undecodable wallet row (${e.javaClass.simpleName})")
                    foreign.add(element)
                }
            }
            return Decoded(rows, foreign, corrupt = false)
        }

        /** "Wallet N" starting at count+1, bumped until unique. */
        fun nextWalletName(existingNames: Collection<String>): String {
            var n = existingNames.size + 1
            while (existingNames.contains("Wallet $n")) {
                n += 1
            }
            return "Wallet $n"
        }

        /**
         * The list with [movedId] placed right before [beforeId] (or last when
         * null). Unknown ids leave the list untouched. Order in the store is
         * the order the user sees: insertion order until they drag.
         */
        fun reordered(list: List<WalletInfo>, movedId: String, beforeId: String?): List<WalletInfo> {
            val moved = list.firstOrNull { it.id == movedId } ?: return list
            if (beforeId == movedId) return list
            val remaining = list.filterNot { it.id == movedId }
            val at = if (beforeId == null) remaining.size
                else remaining.indexOfFirst { it.id == beforeId }.takeIf { it >= 0 } ?: return list
            return remaining.toMutableList().apply { add(at, moved) }
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
