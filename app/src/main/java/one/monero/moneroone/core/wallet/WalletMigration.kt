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
 *
 * Legacy keys are ONLY wiped after a successful import (or a proven
 * duplicate). Anything this build cannot migrate — a seed with no PIN hash,
 * a wallet_id with no seed, a layout it does not know — is left in place and
 * logged; [legacyCacheId] keeps the orphan sweep away from its cache files.
 * The first hardware upgrade (Pixel, 2026-09-19) hit exactly that: the
 * audit-remediation build stores pin_hash in the encrypted prefs, the
 * migration only read the plain prefs, judged the wallet "incomplete", wiped
 * the seed and swept the cache.
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
            // 2858727 and earlier: plain prefs. 54de740 (audit): encrypted prefs.
            pinHash = plainPrefs.getString("pin_hash", null) ?: secrets.legacyPinHash(),
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
        // before setting the flag — import or clean up any legacy leftovers.
        if (store.migrated || store.wallets().isNotEmpty()) {
            if (!store.migrated) store.migrated = true
            importOrClearLegacyLeftovers(plainPrefs, secrets, store)
            return
        }

        val legacy = readLegacySnapshot(plainPrefs, secrets)
        if (!legacy.hasCompleteWallet) {
            if (legacy.walletId != null || legacy.pinHash != null || legacy.seedWords != null) {
                // Fragments this build cannot turn into a wallet. Never wipe
                // them (a seed or an on-disk cache may still be the only copy
                // of someone's keys) and do not mark migrated: a later build
                // may know how to read them, and the orphan sweep stays off
                // while the flag is unset.
                Timber.w(
                    "WalletMigration: incomplete legacy wallet state left in place " +
                        "(walletId=${legacy.walletId != null}, pin=${legacy.pinHash != null}, " +
                        "seed=${!legacy.seedWords.isNullOrEmpty()}, type=${legacy.seedType != null})"
                )
                return
            }
            // Fresh install: nothing to migrate.
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

    /**
     * Post-migration launches: legacy keys are normally absent, so this is a
     * no-op (the old behavior wiped them on EVERY launch). When legacy keys
     * reappear after migration it means a downgraded single-wallet build ran
     * and wrote a wallet — destroying that seed unseen loses funds. Import a
     * complete legacy wallet as a new row (unless its seed already exists),
     * then wipe the legacy keys.
     */
    private fun importOrClearLegacyLeftovers(
        plainPrefs: SharedPreferences,
        secrets: WalletSecrets,
        store: WalletStore
    ) {
        val legacy = readLegacySnapshot(plainPrefs, secrets)
        val hasAnyFragment =
            legacy.walletId != null || legacy.pinHash != null || !legacy.seedWords.isNullOrEmpty()
        if (!hasAnyFragment) return

        if (legacy.hasCompleteWallet) {
            val existing = store.wallets()
            val duplicate =
                // The crash-recovery case: the store row IS this legacy wallet
                // (its cache id was kept verbatim).
                existing.any { it.derivedWalletId == legacy.walletId } ||
                    WalletCacheIds.findWalletWithSeed(legacy.seedWords!!, existing) { id ->
                        secrets.loadSeed(id)?.first
                    } != null
            if (!duplicate) {
                val newId = UUID.randomUUID().toString()
                // "Personal Wallet" already exists from the real migration; give
                // the imported row the next free name instead of a duplicate.
                val info = buildMigratedWallet(legacy, newId, System.currentTimeMillis())!!
                    .let { it.copy(name = WalletStore.nextWalletName(existing.map { w -> w.name })) }
                Timber.i("WalletMigration: importing post-migration legacy wallet -> $newId (cache ${info.derivedWalletId})")
                secrets.saveSeed(newId, legacy.seedWords!!, legacy.seedType!!)
                // Keep the one-app-wide-PIN invariant: prefer the existing
                // wallets' hash; fall back to the legacy build's hash.
                val appPinHash = existing.firstNotNullOfOrNull { secrets.pinHash(it.id) }
                secrets.savePinHash(newId, appPinHash ?: legacy.pinHash!!)
                plainPrefs.edit()
                    .putInt("wallet.$newId.selected_address_index", legacy.selectedAddressIndex)
                    .apply()
                store.addWallet(info)
            } else {
                Timber.i("WalletMigration: post-migration legacy wallet duplicates an existing row; wiping")
            }
            wipeLegacyKeys(plainPrefs, secrets)
        } else {
            // Same rule as first-run: never destroy what we cannot import.
            Timber.w("WalletMigration: incomplete post-migration legacy fragments left in place")
        }
    }

    /**
     * Cache id of a legacy single wallet whose prefs are still present
     * (migration pending or fragments kept). The orphan sweep must treat it
     * as known: its `.keys` file may be the only copy of the keys.
     */
    fun legacyCacheId(plainPrefs: SharedPreferences): String? =
        plainPrefs.getString("wallet_id", null)

    private fun wipeLegacyKeys(plainPrefs: SharedPreferences, secrets: WalletSecrets) {
        secrets.wipeLegacySecrets()
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
