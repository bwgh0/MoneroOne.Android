package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.CakeWalletStyleConverter
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.toElectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Golden vectors for the kit's BIP39 -> 25-word conversion
 * (CakeWalletStyleConverter): PBKDF2 seed, BIP32 m/44'/128'/0'/0/0, reduce
 * mod l, then wallet2's word encoding. This conversion gives every 24-word
 * wallet its keys: if it drifts, a restore opens a different, empty wallet.
 *
 * The expected values come from an independent Python oracle (the audit's
 * derivation/oracle.py). iOS pins the first spend key too (MoneroKit.Swift
 * Bip39DerivationTests), so both apps derive the same wallet from the same
 * 24 words.
 *
 * Runs on the JVM without the native library. NativeWalletCreationTest
 * checks the same seeds through wallet2 on a device.
 */
class Bip39ConversionGoldenTest {

    private class Vector(val name: String, val bip39: String, val electrum: String, val spendKey: String)

    private val vectors = listOf(
        Vector(
            "abandon x23 + art",
            bip39 = "abandon ".repeat(23) + "art",
            electrum = "coal gourmet geometry raking lilac sewage pawnshop rudely bays ascend gifts reinvest " +
                "voted moisture kept podcast vocal paradise acidic espionage hijack wrap vogue waist sewage",
            spendKey = "4fe2e8fa6ad56846a4b70b5cf85a8a5ff310d8eb5daaf5b11af9591d79fc0a02"
        ),
        Vector(
            "legal winner ... title",
            bip39 = "legal winner thank year wave sausage worth useful legal winner thank year " +
                "wave sausage worth useful legal winner thank year wave sausage worth title",
            electrum = "inbound bite sighting mighty academy phone pool erosion oxidant went natural shackles " +
                "juicy pipeline hotel gang divers adrenalin iris pumpkins mice bowling venomous village sighting",
            spendKey = "076bfda9fa30dba503321e5f6f310b356cb616bbbfc64fd086a65ad91fc30402"
        ),
        Vector(
            "letter advice ... bless",
            bip39 = "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd " +
                "amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless",
            electrum = "jukebox unmask cycling assorted nouns iguana focus nimbly necklace atrium acquire army " +
                "washing stick comb fainted snout obliged awkward vampire village sonic oilfield pheasants stick",
            spendKey = "c7a25a46826fc6d2228471fd9f92db0d8afd1d5aa93e0cce2c8d12042adcbb0c"
        ),
        Vector(
            "zoo x23 + vote",
            bip39 = "zoo ".repeat(23) + "vote",
            electrum = "peeled examine razor sack ourselves rims soya hesitate nowhere bamboo koala alarms " +
                "rapid sushi fall peeled gambit cadets losing because adept cocoa vocal vowels alarms",
            spendKey = "962f0a6feb6a681827cfd33508d9ef8fbef9f274963511d13a4b0eebd52e3a01"
        )
    )

    private fun words(s: String) = s.split(" ")

    /**
     * wallet2's ElectrumWords::words_to_bytes for English: each 3 words give
     * one little-endian uint32 of the 32-byte key (uint32 arithmetic, as in C++).
     * Written from the wallet2 source, independent of the converter's encoder.
     */
    private fun spendKeyOf(electrum: List<String>): String {
        val list = CakeWalletStyleConverter.MONERO_WORDLIST
        val n = list.size.toLong()
        val index = HashMap<String, Long>().apply { list.forEachIndexed { i, w -> put(w, i.toLong()) } }
        val hex = StringBuilder()
        for (i in 0 until 8) {
            val (w1, w2, w3) = (0 until 3).map { j ->
                index[electrum[3 * i + j]] ?: throw AssertionError("word ${3 * i + j + 1} is not in the wordlist")
            }
            val v = (w1 + n * (((n - w1) + w2) % n) + n * n * (((n - w2) + w3) % n)) and 0xFFFFFFFFL
            assertEquals("word triple ${i + 1} does not decode", w1, v % n)
            for (b in 0 until 4) hex.append("%02x".format((v shr (8 * b)) and 0xFF))
        }
        return hex.toString()
    }

    @Test
    fun `converter gives the golden 25 words`() {
        assertEquals(1626, CakeWalletStyleConverter.MONERO_WORDLIST.size)
        for (v in vectors) {
            val bip39 = words(v.bip39)
            assertEquals(v.name, 24, bip39.size)
            val electrum = CakeWalletStyleConverter.getLegacySeedFromBip39(bip39, "")
            assertNotNull("${v.name}: conversion returned null", electrum)
            assertEquals(v.name, words(v.electrum), electrum)
        }
    }

    @Test
    fun `kit seed conversion gives the golden 25 words`() {
        // Seed.toElectrum() is what MoneroKit.getKeys/getAddress and wallet creation call.
        for (v in vectors) {
            val electrum = Seed.Bip39(words(v.bip39), "").toElectrum()
            assertEquals(v.name, words(v.electrum), electrum.mnemonic)
            assertEquals(v.name, "", electrum.passphrase)
        }
    }

    @Test
    fun `golden 25 words decode to the golden spend key`() {
        for (v in vectors) {
            assertEquals(v.name, v.spendKey, spendKeyOf(words(v.electrum)))
        }
    }

    @Test
    fun `golden 25 words pass the electrum checksum rule`() {
        for (v in vectors) {
            assertNull(v.name, SeedValidation.electrumSeedProblem(words(v.electrum)))
        }
    }
}
