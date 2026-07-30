package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end migration behavior over fake prefs: single -> multi, crash
 * orderings, idempotency, and legacy-key wipe.
 */
class WalletMigrationIntegrationTest {

    private lateinit var plain: FakeSharedPreferences
    private lateinit var encrypted: FakeSharedPreferences
    private lateinit var secrets: WalletSecrets
    private lateinit var store: WalletStore

    private val legacyUuid = "123e4567-e89b-12d3-a456-426614174000"
    private val seedWords = List(24) { "word$it" }

    @Before
    fun setUp() {
        plain = FakeSharedPreferences()
        encrypted = FakeSharedPreferences()
        secrets = WalletSecrets(encrypted)
        store = WalletStore(plain)
    }

    private fun seedLegacyInstall() {
        plain.edit()
            .putString("wallet_id", legacyUuid)
            .putString("pin_hash", "80000:aa:bb")
            .putLong("restore_height", 3200000L)
            .putLong("restore_date_millis", 1710000000000L)
            .putInt("selected_address_index", 2)
            .apply()
        encrypted.edit()
            .putString("seed_words", seedWords.joinToString(" "))
            .putString("seed_type", "BIP39_24")
            .apply()
    }

    @Test
    fun `full migration moves wallet into store and wipes legacy keys`() {
        seedLegacyInstall()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        val wallets = store.wallets()
        assertEquals(1, wallets.size)
        val w = wallets[0]
        assertEquals("Personal Wallet", w.name)
        assertEquals(legacyUuid, w.derivedWalletId)
        assertEquals(3200000L, w.restoreHeight)
        assertEquals(w.id, store.activeWalletId())
        assertTrue(store.migrated)

        // Secrets copied to per-wallet keys.
        assertEquals(seedWords to SeedType.BIP39_24, secrets.loadSeed(w.id))
        assertEquals("80000:aa:bb", secrets.pinHash(w.id))
        assertEquals(2, plain.getInt("wallet.${w.id}.selected_address_index", 0))

        // Legacy keys wiped.
        assertNull(plain.getString("wallet_id", null))
        assertNull(plain.getString("pin_hash", null))
        assertNull(encrypted.getString("seed_words", null))
        assertFalse(plain.contains("restore_height"))
    }

    @Test
    fun `migration is idempotent`() {
        seedLegacyInstall()
        WalletMigration.migrateIfNeeded(plain, secrets, store)
        val first = store.wallets()

        WalletMigration.migrateIfNeeded(plain, secrets, store)
        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertEquals(first, store.wallets())
        assertEquals(1, store.wallets().size)
    }

    @Test
    fun `crash after store write but before flag does not duplicate the wallet`() {
        seedLegacyInstall()
        // Simulate: store written, flag not set, legacy keys still present.
        val info = WalletMigration.buildMigratedWallet(
            WalletMigration.readLegacySnapshot(plain, secrets), "existing-id", 1L
        )!!
        store.saveWallets(listOf(info))
        store.setActiveWalletId(info.id)

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertEquals(1, store.wallets().size)
        assertEquals("existing-id", store.wallets()[0].id)
        assertTrue(store.migrated)
        // Leftover legacy keys cleaned up.
        assertNull(plain.getString("wallet_id", null))
        assertNull(encrypted.getString("seed_words", null))
    }

    @Test
    fun `fresh install migrates nothing and sets flag`() {
        WalletMigration.migrateIfNeeded(plain, secrets, store)
        assertTrue(store.migrated)
        assertTrue(store.wallets().isEmpty())
    }

    @Test
    fun `incomplete legacy state is cleared not migrated`() {
        // wallet_id without seed (stale onboarding)
        plain.edit().putString("wallet_id", legacyUuid).apply()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertTrue(store.wallets().isEmpty())
        assertTrue(store.migrated)
        assertNull(plain.getString("wallet_id", null))
    }

    // --- Post-migration legacy leftovers (M4) --------------------------------

    @Test
    fun `post-migration launch with no legacy fragments is a no-op`() {
        seedLegacyInstall()
        WalletMigration.migrateIfNeeded(plain, secrets, store)
        val before = store.wallets()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertEquals(before, store.wallets())
    }

    @Test
    fun `legacy wallet written by a downgraded build is imported, not destroyed`() {
        seedLegacyInstall()
        WalletMigration.migrateIfNeeded(plain, secrets, store)

        // Downgraded single-wallet build ran and created a NEW wallet.
        val otherSeed = List(24) { "other$it" }
        plain.edit()
            .putString("wallet_id", "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000")
            .putString("pin_hash", "80000:cc:dd")
            .apply()
        encrypted.edit()
            .putString("seed_words", otherSeed.joinToString(" "))
            .putString("seed_type", "BIP39_24")
            .apply()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        val wallets = store.wallets()
        assertEquals(2, wallets.size)
        val imported = wallets.first { it.derivedWalletId == "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000" }
        // The seed survives — the old behavior wiped it unseen.
        assertEquals(otherSeed, secrets.loadSeed(imported.id)?.first)
        // One-app-wide-PIN invariant: imported row gets the existing hash.
        assertEquals("80000:aa:bb", secrets.pinHash(imported.id))
        // Legacy keys wiped after import.
        assertNull(plain.getString("wallet_id", null))
        assertNull(encrypted.getString("seed_words", null))
    }

    @Test
    fun `post-migration legacy wallet with an already-known seed is wiped without import`() {
        seedLegacyInstall()
        WalletMigration.migrateIfNeeded(plain, secrets, store)

        // Downgraded build restored the SAME seed under a fresh cache UUID.
        plain.edit()
            .putString("wallet_id", "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000")
            .putString("pin_hash", "80000:cc:dd")
            .apply()
        encrypted.edit()
            .putString("seed_words", seedWords.joinToString(" "))
            .putString("seed_type", "BIP39_24")
            .apply()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertEquals(1, store.wallets().size)
        assertNull(plain.getString("wallet_id", null))
        assertNull(encrypted.getString("seed_words", null))
    }

    @Test
    fun `migration never wipes legacy before new data is written`() {
        // Guarded by ordering in migrateIfNeeded; verify the migrated wallet's
        // secrets exist and match the legacy ones after a normal run.
        seedLegacyInstall()
        WalletMigration.migrateIfNeeded(plain, secrets, store)
        val w = store.wallets()[0]
        assertEquals(seedWords, secrets.loadSeed(w.id)?.first)
    }
}
