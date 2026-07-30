package one.monero.moneroone.core.wallet

import android.content.SharedPreferences
import timber.log.Timber
import java.util.UUID

/**
 * Idempotent one-time migration from the single-wallet layout to the
 * multi-wallet store. Mirrors iOS `WalletManager.migrateFromSingleWallet`.
 *
 * The existing wallet becomes one [WalletInfo] named "Personal Wallet".
 * Its legacy random-UUID cache id is stored as `derivedWalletId` VERBATIM so
 * the cache-id lockstep and the orphan sweep keep holding without renaming
 * files on disk.
 *
 * Crash-safety: new data is fully written (secrets, then store + flag) BEFORE
 * any legacy key is wiped, so no crash ordering can lose the wallet.
 */
object WalletMigration {

    data class LegacySnapshot(
        val walletId: String?,
        val pinHash: String?,
        val seedWords: List<String>?,
        val seedType: SeedType?,
        val restoreHeight: Long,
        val restoreDateMillis: Long,
        val selectedAddressIndex: Int
    ) {
        val hasCompleteWallet: Boolean
            get() = walletId != null && pinHash != null && !seedWords.isNullOrEmpty() && seedType != null
    }

    /** Pure: build the migrated WalletInfo (null when nothing to migrate). */
    fun buildMigratedWallet(legacy: LegacySnapshot, newId: String, now: Long): WalletInfo? {
        if (!legacy.hasCompleteWallet) return null
        return WalletInfo(
            id = newId,
            name = "Personal Wallet",
            emoji = "💰",
            source = WalletSource.fromSeedType(legacy.seedType!!),
            createdAt = now,
            restoreHeight = legacy.restoreHeight,
            restoreDateMillis = legacy.restoreDateMillis,
            syncResetCount = 0,
            // Legacy cache files keep their UUID name on disk.
            derivedWalletId = legacy.walletId
        )
    }

    fun readLegacySnapshot(plainPrefs: SharedPreferences, secrets: WalletSecrets): LegacySnapshot {
        val seed = secrets.legacySeed()
        val heightLong = plainPrefs.getLong("restore_height", 0L)
        val heightStr = plainPrefs.getString("restore_height_str", null)?.toLongOrNull() ?: 0L
        return LegacySnapshot(
            walletId = plainPrefs.getString("wallet_id", null),
            pinHash = plainPrefs.getString("pin_hash", null),
            seedWords = seed?.first,
            seedType = seed?.second,
            restoreHeight = if (heightLong > 0L) heightLong else heightStr,
            restoreDateMillis = plainPrefs.getLong("restore_date_millis", 0L),
            selectedAddressIndex = plainPrefs.getInt("selected_address_index", 0)
        )
    }

    /**
     * Run the migration if it has not completed. Safe to call every launch.
     */
    fun migrateIfNeeded(
        plainPrefs: SharedPreferences,
        secrets: WalletSecrets,
        store: WalletStore
    ) {
        // Already migrated, or a previous run wrote the store but crashed
        // before setting the flag — just clean up leftovers.
        if (store.migrated || store.wallets().isNotEmpty()) {
            if (!store.migrated) store.migrated = true
            wipeLegacyKeys(plainPrefs, secrets)
            return
        }

        val legacy = readLegacySnapshot(plainPrefs, secrets)
        if (!legacy.hasCompleteWallet) {
            // Nothing (or only stale fragments) to migrate. Clear fragments so
            // onboarding starts clean, then mark done.
            if (legacy.walletId != null || legacy.pinHash != null || legacy.seedWords != null) {
                Timber.w("WalletMigration: clearing incomplete legacy wallet state")
                wipeLegacyKeys(plainPrefs, secrets)
            }
            store.migrated = true
            return
        }

        val newId = UUID.randomUUID().toString()
        val info = buildMigratedWallet(legacy, newId, System.currentTimeMillis()) ?: return
        Timber.i("WalletMigration: migrating single wallet -> ${info.id} (cache ${info.derivedWalletId})")

        // 1. Write new secrets (copy, don't move).
        secrets.saveSeed(newId, legacy.seedWords!!, legacy.seedType!!)
        secrets.savePinHash(newId, legacy.pinHash!!)

        // 2. Per-wallet plain prefs.
        plainPrefs.edit()
            .putInt("wallet.$newId.selected_address_index", legacy.selectedAddressIndex)
            .apply()

        // 3. Write store + active id, then flag.
        store.saveWallets(listOf(info))
        store.setActiveWalletId(newId)
        store.migrated = true

        // 4. Only now wipe legacy keys.
        wipeLegacyKeys(plainPrefs, secrets)
    }

    private fun wipeLegacyKeys(plainPrefs: SharedPreferences, secrets: WalletSecrets) {
        secrets.wipeLegacySeed()
        plainPrefs.edit()
            .remove("wallet_id")
            .remove("pin_hash")
            .remove("selected_address_index")
            .remove("restore_height")
            .remove("restore_height_str")
            .remove("restore_date_millis")
            .apply()
    }
}
