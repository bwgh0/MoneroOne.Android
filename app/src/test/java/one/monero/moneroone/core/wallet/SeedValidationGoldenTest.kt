package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.CakeWalletStyleConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SeedValidation's pure (non-native) rules against pinned vectors: wallet2's
 * own functional-test seed, the BIP39 conversion output, the primaries those
 * seeds derive, and the burn addresses a keyless wallet shows.
 *
 * Sources: wallet2 tests/functional_tests/wallet.py (velvet seed and its
 * primary); the audit's derivation/oracle.py (BIP39 vectors and their
 * primaries); the audit's nullkey/keyless.py (burn addresses).
 */
class SeedValidationGoldenTest {

    private val velvet = (
        "velvet lymph giddy number token physics poetry unquoted nibs useful sabotage limits benches " +
            "lifestyle eden nitrogen anvil fewest avoid batch vials washing fences goat unquoted"
        ).split(" ")

    // Kit conversion of BIP39 "abandon" x23 + "art" (see Bip39ConversionGoldenTest).
    private val abandonArt = (
        "coal gourmet geometry raking lilac sewage pawnshop rudely bays ascend gifts reinvest " +
            "voted moisture kept podcast vocal paradise acidic espionage hijack wrap vogue waist sewage"
        ).split(" ")

    /** All-zero spend and view keys (a default-constructed wallet2 account). */
    private val zeroKeysPrimary =
        "41d7FXjswpK1111111111111111111111111111111111111111111111111111111111111111111111111111112KhNi4"

    /** "abbey" x25: spend key 0, view key Hs(0). */
    private val abbeyPrimary =
        "41fJjQDhryD11111111111111111111111111111111112N1GuTZeagfRbbKcALdcZev4QXGGuoLh2x36LhaxLSxCc2YDhi"

    private val goldenPrimaries = listOf(
        // velvet (wallet2 functional tests)
        "42ey1afDFnn4886T7196doS9GPMzexD9gXpsZJDwVjeRVdFCSoHnv7KPbBeGpzJBzHRCAs9UxqeoyFQMYbqSWYTfJJQAWDm",
        // BIP39 abandon x23 + art (same value pinned on iOS)
        "43Xuqb8woKbELkxbc4U8ZEMk87rx8VingbtWmxXpFiJK6mKHJuK8bGGTrndC4y6DmGPdwQDyJaWgu6ZXCKNfeoRSVMTUBCX",
        // BIP39 legal winner ... title
        "4BCmqSJ5GVJ7cqoxcu5wXURMDfhgvw596Dp7mjtVoD8fcpuYR3gad8VHBgLBwC11HZ3eWM3DJqWk7UrSKDZ26RtBKwJccRo",
        // BIP39 letter advice ... bless
        "42XUmA3CVuJ4of3wV6LoYJgXgHracWu3Sc9qwyPnrCQL9VB58nf4Vrtc3Z1aMZuAVaY8PNhYPwwemaCzz27W9eH2EWRdQFX",
        // BIP39 zoo x23 + vote
        "43Gm3jx3JZU7Wi5D9PTNGhLeRp1GoCtqDRxW9TuT4Niid542C2t3AU5LBkj6jdBSf6h6WjEx79AuxYAALM3bk6b9CxMcvGS"
    )

    /** The same seed with its 25th (checksum) word swapped for the next word in the list. */
    private fun withWrongChecksumWord(words: List<String>): List<String> {
        val list = CakeWalletStyleConverter.MONERO_WORDLIST
        val wrong = list[(list.indexOf(words[24]) + 1) % list.size]
        assertNotEquals(words[24], wrong)
        return words.take(24) + wrong
    }

    @Test
    fun `wallet2 functional-test seed passes the checksum rule`() {
        assertEquals(25, velvet.size)
        assertTrue(SeedValidation.isEnglishElectrum(velvet))
        assertNull(SeedValidation.electrumSeedProblem(velvet))
    }

    @Test
    fun `converted bip39 seed passes the checksum rule`() {
        assertNull(SeedValidation.electrumSeedProblem(abandonArt))
    }

    @Test
    fun `a changed checksum word fails`() {
        for (seed in listOf(velvet, abandonArt)) {
            assertEquals("checksum word mismatch", SeedValidation.electrumSeedProblem(withWrongChecksumWord(seed)))
        }
    }

    @Test
    fun `burn addresses are not plausible primaries`() {
        for (burn in listOf(zeroKeysPrimary, abbeyPrimary)) {
            assertEquals(95, burn.length)
            assertFalse(burn, SeedValidation.isPlausiblePrimaryAddress(burn))
        }
    }

    @Test
    fun `golden primaries are plausible`() {
        for (address in goldenPrimaries) {
            assertEquals(95, address.length)
            assertTrue(address, SeedValidation.isPlausiblePrimaryAddress(address))
        }
    }
}
