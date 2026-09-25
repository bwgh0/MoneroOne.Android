package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.CakeWalletStyleConverter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Legacy seeds that fail validation (B5): migration logs why and imports
 * them anyway, because a legacy seed can be the only copy of the keys.
 */
class WalletMigrationSeedCheckTest {

    private lateinit var plain: FakeSharedPreferences
    private lateinit var encrypted: FakeSharedPreferences
    private lateinit var secrets: WalletSecrets
    private lateinit var store: WalletStore

    private val warnings = mutableListOf<String>()
    private val recorder = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.WARN) warnings += message
        }
    }

    private val bip39 = ("blind ginger glare shrimp copper farm useless pluck task disease this laugh " +
        "build frog prison inner heavy delay scissors order eager treat youth genre").split(" ")

    @Before
    fun setUp() {
        plain = FakeSharedPreferences()
        encrypted = FakeSharedPreferences()
        secrets = WalletSecrets(encrypted)
        store = WalletStore(plain)
        Timber.plant(recorder)
    }

    @After
    fun tearDown() {
        Timber.uproot(recorder)
    }

    private fun writeLegacyWallet(walletId: String, words: List<String>, type: SeedType) {
        plain.edit()
            .putString("wallet_id", walletId)
            .putString("pin_hash", "80000:aa:bb")
            .apply()
        encrypted.edit()
            .putString("seed_words", words.joinToString(" "))
            .putString("seed_type", type.name)
            .apply()
    }

    private fun assertNoSeedWordLogged(words: List<String>) {
        val logged = warnings.flatMap { it.split(Regex("\\W+")) }.toSet()
        assertTrue("a seed word reached the log", words.none { it in logged })
    }

    private fun badChecksumElectrum(): List<String> {
        val words = CakeWalletStyleConverter.getLegacySeedFromBip39(bip39, "")!!.toMutableList()
        words[24] = CakeWalletStyleConverter.MONERO_WORDLIST.first { it !in words.take(24) }
        assertNotNull(SeedValidation.electrumSeedProblem(words))
        return words
    }

    @Test
    fun `electrum seed with a bad checksum is imported with a warning`() {
        val words = badChecksumElectrum()
        writeLegacyWallet("123e4567-e89b-12d3-a456-426614174000", words, SeedType.ELECTRUM_25)

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        val wallet = store.wallets().single()
        assertEquals(words to SeedType.ELECTRUM_25, secrets.loadSeed(wallet.id))
        assertTrue(warnings.any { "checksum word mismatch" in it })
        assertNoSeedWordLogged(words)
    }

    @Test
    fun `bip39 seed with an unknown word is imported and the word is not logged`() {
        // hd-wallet-kit quotes the bad word in its exception message.
        val words = bip39.toMutableList().apply { this[3] = "notaword" }
        writeLegacyWallet("123e4567-e89b-12d3-a456-426614174000", words, SeedType.BIP39_24)

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        val wallet = store.wallets().single()
        assertEquals(words to SeedType.BIP39_24, secrets.loadSeed(wallet.id))
        assertTrue(warnings.any { "BIP39 validation failed" in it })
        assertNoSeedWordLogged(words)
    }

    @Test
    fun `post-migration legacy seed that fails validation is still imported`() {
        writeLegacyWallet("123e4567-e89b-12d3-a456-426614174000", bip39, SeedType.BIP39_24)
        WalletMigration.migrateIfNeeded(plain, secrets, store)
        assertEquals(1, store.wallets().size)

        // A downgraded single-wallet build then wrote a seed with a bad checksum word.
        val words = badChecksumElectrum()
        writeLegacyWallet("aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000", words, SeedType.ELECTRUM_25)
        warnings.clear()

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        val imported = store.wallets().single { it.derivedWalletId == "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000" }
        assertEquals(words, secrets.loadSeed(imported.id)?.first)
        assertTrue(warnings.any { "checksum word mismatch" in it })
        assertNoSeedWordLogged(words)
    }

    @Test
    fun `a valid legacy seed logs no validation warning`() {
        writeLegacyWallet("123e4567-e89b-12d3-a456-426614174000", bip39, SeedType.BIP39_24)

        WalletMigration.migrateIfNeeded(plain, secrets, store)

        assertEquals(1, store.wallets().size)
        assertTrue(warnings.none { "fails validation" in it })
    }
}
