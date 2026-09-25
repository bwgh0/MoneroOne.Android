package one.monero.moneroone.core.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.model.Wallet
import io.horizontalsystems.monerokit.model.WalletManager
import io.horizontalsystems.monerokit.toElectrum
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On a device: key and address derivation and wallet creation through the
 * kit's real wallet2 library, pinned to golden values. No network is used.
 *
 * This is the guard the iOS burn bug lacked. There, a library rebuild stubbed
 * a wallet2 static to return "", every BIP39 wallet came out keyless, and
 * Receive showed a burn address. Here a stubbed or broken static, or a broken
 * wallet2 build, fails this test: empty strings, all-zero keys and malformed
 * addresses fail with a message that says so, and every key and address must
 * equal a golden value.
 *
 * Golden values: the audit's independent Python oracle (derivation/oracle.py).
 * The velvet seed, its keys and its addresses (0,0) (0,1) (1,0) (1,2) (0,999)
 * are also wallet2's own vectors (tests/functional_tests/wallet.py). The BIP39
 * spend key and primary address are also pinned on iOS.
 *
 * Each address is read by its (account, index): the kit's subaddress lists
 * change size, the address at an index does not.
 */
@RunWith(AndroidJUnit4::class)
class NativeWalletCreationTest {

    private class Golden(
        val name: String,
        val seed: Seed,
        val words: List<String>,
        val type: SeedType,
        val spendKey: String,
        val publicSpendKey: String,
        val viewKey: String,
        val publicViewKey: String,
        /** (account, index) to address. */
        val addresses: Map<Pair<Int, Int>, String>
    ) {
        val primary: String get() = addresses.getValue(0 to 0)
    }

    private val bip39Words = List(23) { "abandon" } + "art"

    private val bip39 = Golden(
        name = "bip39",
        seed = Seed.Bip39(bip39Words, ""),
        words = bip39Words,
        type = SeedType.BIP39_24,
        spendKey = "4fe2e8fa6ad56846a4b70b5cf85a8a5ff310d8eb5daaf5b11af9591d79fc0a02",
        publicSpendKey = "32698cece58bce4fc230bfc85244917c046adc40abc88dd0952b1aac47b45022",
        viewKey = "f6375bd99c5d6ba250660fe1bda555cf1eee558a5076207afb7b12602b66980b",
        publicViewKey = "76064b01b75935a0936914a89af49f8756424f034350d6213ec8f216f3f649fb",
        addresses = mapOf(
            (0 to 0) to "43Xuqb8woKbELkxbc4U8ZEMk87rx8VingbtWmxXpFiJK6mKHJuK8bGGTrndC4y6DmGPdwQDyJaWgu6ZXCKNfeoRSVMTUBCX",
            (0 to 1) to "84SRAaUXDrhfxRo41T7TGD8WKCC3w6VjZW7uVLTMU7BLgqHnn2t59MW1qkhGU1rsC8bhsswDJaLyeQ9qbybxKWHq6PKnQc4",
            (0 to 2) to "83dTVFH9Tm9ZYuEE1fZvdiMPWjWnZfdteLMQWdeyqDTUfWZ2SZNdeKp2AhaikuQussfpd8PY8zFFdD13gfSXqdmNTPZ2KwU",
            (0 to 3) to "876gQQEXnZ33Kpqkvrrz4rJQZXwwV1ayRRgAPLUx9W2ZeHUp1gDAjSZBpqkykVFmTWEEJYkYnwv2eaDhjohSbzL6V7BaUJS",
            (1 to 0) to "89gxQfqWpNVUMkcCGFJVo2VFkmLkugY8KGngNcXRMUa8d4MziN7W95MDkqAR5pPLyoShd3KH1EnG9gGWGJ1MpuKgCgMTWf6",
            (1 to 2) to "8A2LGgTwAJcAkJpSCwkGg5Ltmqb4HsC9YJ8XstnP94xN22KjPkzXgkiahj3CAUkCx3FeE37zrUM4CPTM6VQpeYccQMszZeV",
            (0 to 999) to "87d7i2ZsxHL2vwHgjKtgNZ9cUvNTtLBdqWjd8EKS7rtPgqHvkvqjYXd9Ro73PALWXcDy5hzVysxiB6CpFHFVW67nE5jd98u"
        )
    )

    private val velvetWords = (
        "velvet lymph giddy number token physics poetry unquoted nibs useful sabotage limits benches " +
            "lifestyle eden nitrogen anvil fewest avoid batch vials washing fences goat unquoted"
        ).split(" ")

    private val velvet = Golden(
        name = "electrum",
        seed = Seed.Electrum(velvetWords, ""),
        words = velvetWords,
        type = SeedType.ELECTRUM_25,
        spendKey = "148d78d2aba7dbca5cd8f6abcfb0b3c009ffbdbea1ff373d50ed94d78286640e",
        publicSpendKey = "1b3bd040020d3712ab84992b773d0a965134eb2df0392fb84af95de8a17be2ab",
        viewKey = "49774391fa5e8d249fc2c5b45dadef13534bf2483dede880dac88f061e809100",
        publicViewKey = "231c9bf8341c6a870d92e3fb98063a90a355fb8dbf74a8561b9d7f9273247e99",
        addresses = mapOf(
            (0 to 0) to "42ey1afDFnn4886T7196doS9GPMzexD9gXpsZJDwVjeRVdFCSoHnv7KPbBeGpzJBzHRCAs9UxqeoyFQMYbqSWYTfJJQAWDm",
            (0 to 1) to "84QRUYawRNrU3NN1VpFRndSukeyEb3Xpv8qZjjsoJZnTYpDYceuUTpog13D7qPxpviS7J29bSgSkR11hFFoXWk2yNdsR9WF",
            (0 to 2) to "85M4M1RVRcoEeC8sdSxN1ef6GhQYChSfKPWkB4FLKYJiSWuMXXT4Ewv8BHCRzSJB4ZYvXUcFxN4DPcVu6uwoPNRvQ1QwaXB",
            (0 to 3) to "862oiydSvvBUynhpD6peZZFGDJM2QRskfckUMUgkZJPKXodj1qC8kcFRgc9iDEB68XfDGUvDKBy7h5ikYEbfP2upBK9oVXS",
            (1 to 0) to "82pP87g1Vkd3LUMssBCumk3MfyEsFqLAaGDf6oxddu61EgSFzt8gCwUD4tr3kp9TUfdPs2CnpD7xLZzyC1Ei9UsW3oyCWDf",
            (1 to 2) to "87KfgTZ8ER5D3Frefqnrqif11TjVsTPaTcp37kqqKMrdDRUhpJRczeR7KiBmSHF32UJLP3HHhKUDmEQyJrv2mV8yFDCq8eB",
            (0 to 999) to "8BQKgTSSqJjP14AKnZUBwnXWj46MuNmLvHfPTpmry52DbfNjjHVvHUk4mczU8nj8yZ57zBhksTJ8kM5xKeJXw55kCMVqyG7"
        )
    )

    private lateinit var dir: File

    @Before
    fun setUp() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        dir = File(filesDir, "native-wallet-test-${System.nanoTime()}")
        assertTrue("cannot create $dir", dir.mkdirs())
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    // ---------------------------------------------------------------- checks

    private fun assertKey(label: String, expected: String, actual: String?) {
        if (actual.isNullOrEmpty()) {
            fail("$label is empty: the native key derivation is a stub or failed")
        }
        assertTrue("$label is not 64 hex chars: '$actual'", HEX_64.matches(actual!!))
        assertFalse("$label is all zero: a keyless wallet", actual.all { it == '0' })
        assertEquals(label, expected, actual)
    }

    private fun assertAddress(label: String, expected: String, actual: String?, account: Int, index: Int) {
        if (actual.isNullOrEmpty()) {
            fail("$label is empty: the native address derivation is a stub or failed")
        }
        val prefix = if (account == 0 && index == 0) '4' else '8'
        assertTrue("$label is not a 95-char base58 address: '$actual'", BASE58_95.matches(actual!!))
        assertEquals("$label has the wrong network/type prefix: '$actual'", prefix, actual[0])
        assertFalse("$label has a null-key run of '1's: a burn address", NULL_KEY_RUN.containsMatchIn(actual))
        assertTrue("$label fails wallet2's own address check: '$actual'", Wallet.isAddressValid(actual))
        assertEquals(label, expected, actual)
    }

    private fun assertKeys(g: Golden) {
        val keys = MoneroKit.getKeys(g.seed)
        assertKey("${g.name} private spend key", g.spendKey, keys.privateSpendKey)
        assertKey("${g.name} public spend key", g.publicSpendKey, keys.publicSpendKey)
        assertKey("${g.name} private view key", g.viewKey, keys.privateViewKey)
        assertKey("${g.name} public view key", g.publicViewKey, keys.publicViewKey)
    }

    private fun assertStaticAddresses(g: Golden) {
        for ((at, expected) in g.addresses) {
            val (account, index) = at
            assertAddress(
                "${g.name} MoneroKit.getAddress($account, $index)",
                expected, MoneroKit.getAddress(g.seed, account, index), account, index
            )
        }
    }

    /** Recover a wallet file from the seed the way MoneroKit.createWalletIfNotExists does, then reopen it. */
    private fun assertWalletFile(g: Golden) {
        val manager = WalletManager.getInstance()
        val electrum = g.seed.toElectrum()
        val file = File(dir, g.name)

        val wallet = manager.recoveryWallet(file, "", electrum.mnemonic.joinToString(" "), electrum.passphrase, RESTORE_HEIGHT)
        try {
            assertTrue("${g.name}: wallet2 recovery failed: ${wallet.status}", wallet.status.isOk)
            val primary = wallet.address
            assertAddress("${g.name} wallet.address", g.primary, primary, 0, 0)
            assertEquals("${g.name}: wallet primary != static primary", MoneroKit.getAddress(g.seed, 0, 0), primary)
            for (i in 1..3) {
                val sub = wallet.getSubaddress(i)
                assertAddress("${g.name} wallet.getSubaddress($i)", g.addresses.getValue(0 to i), sub, 0, i)
                assertEquals("${g.name}: wallet subaddress $i != static", MoneroKit.getAddress(g.seed, 0, i), sub)
            }
            assertKey("${g.name} wallet.secretViewKey", g.viewKey, wallet.secretViewKey)
            assertKey("${g.name} wallet.secretSpendKey", g.spendKey, wallet.secretSpendKey)
        } finally {
            wallet.close()
        }
        assertTrue("${g.name}: no keys file written", File(dir, "${g.name}.keys").isFile)

        // The kit deletes the cache after creating a wallet and later opens it from the keys file.
        File(dir, g.name).delete()
        val reopened = manager.openWallet(file.absolutePath, "")
        try {
            assertTrue("${g.name}: reopen failed: ${reopened.status}", reopened.status.isOk)
            assertAddress("${g.name} reopened wallet.address", g.primary, reopened.address, 0, 0)
            assertAddress("${g.name} reopened wallet.getSubaddress(1)", g.addresses.getValue(0 to 1), reopened.getSubaddress(1), 0, 1)
            assertKey("${g.name} reopened wallet.secretViewKey", g.viewKey, reopened.secretViewKey)
        } finally {
            reopened.close()
        }
    }

    // ---------------------------------------------------------------- BIP39 (24 words)

    @Test
    fun bip39KeysMatchGolden() = assertKeys(bip39)

    @Test
    fun bip39StaticAddressesMatchGolden() = assertStaticAddresses(bip39)

    @Test
    fun bip39WalletFileMatchesGolden() = assertWalletFile(bip39)

    // ---------------------------------------------------------------- Electrum (25 words)

    @Test
    fun electrumKeysMatchGolden() = assertKeys(velvet)

    @Test
    fun electrumStaticAddressesMatchGolden() = assertStaticAddresses(velvet)

    @Test
    fun electrumWalletFileMatchesGolden() = assertWalletFile(velvet)

    // ---------------------------------------------------------------- SeedValidation (native part)

    @Test
    fun validationAcceptsGoldenSeeds() {
        for (g in listOf(bip39, velvet)) {
            val validated = SeedValidation.validate(g.words, g.type)
            assertEquals(g.name, g.primary, validated.primaryAddress)
        }
    }

    @Test
    fun validationRejectsZeroSpendKeySeed() {
        // "abbey" x25 passes the checksum rule, but its spend key is zero: the address is a burn address.
        val abbey = List(25) { "abbey" }
        assertNull(SeedValidation.electrumSeedProblem(abbey))
        val seed = Seed.Electrum(abbey, "")
        assertEquals("0".repeat(64), MoneroKit.getKeys(seed).privateSpendKey)
        assertEquals(ABBEY_BURN_PRIMARY, MoneroKit.getAddress(seed, 0, 0))

        assertThrows(InvalidSeedException::class.java) {
            SeedValidation.validate(abbey, SeedType.ELECTRUM_25)
        }
    }

    private companion object {
        /** Any fixed height works: recovery reads no chain data. */
        const val RESTORE_HEIGHT = 3_400_000L

        val HEX_64 = Regex("^[0-9a-f]{64}$")
        val BASE58_95 = Regex("^[1-9A-HJ-NP-Za-km-z]{95}$")

        /** 32 zero bytes encode as a long run of '1' in Monero base58. */
        val NULL_KEY_RUN = Regex("1{16,}")

        /** nullkey/keyless.py: spend key 0, view key Hs(0). */
        const val ABBEY_BURN_PRIMARY =
            "41fJjQDhryD11111111111111111111111111111111112N1GuTZeagfRbbKcALdcZev4QXGGuoLh2x36LhaxLSxCc2YDhi"
    }
}
