package one.monero.moneroone.core.wallet

import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.monerokit.CakeWalletStyleConverter
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.toElectrum
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Seed acceptance rules applied BEFORE anything is persisted or the running
 * kit is torn down. H1 closed the BIP39 side (the eager Bip39→Electrum
 * conversion throws for unconvertible input); this closes the rest:
 *
 *  - BIP39: wordlist + checksum via hd-wallet-kit (a typo'd word converts to
 *    a DIFFERENT wallet silently otherwise).
 *  - 25-word Electrum (English): every word in the wordlist and the 25th
 *    word equal to the CRC32 checksum word — wallet2's rule. The kit's
 *    `Seed.Electrum` only checks the count, so a typo used to be persisted
 *    as an active wallet that can never open.
 *  - Any seed: derived keys must not be null (all-zero) and the primary
 *    address must be a well-formed mainnet address without a null-key run.
 *    A null spend key (one word repeated 25 times, the all-"abandon" BIP39
 *    vector, ...) yields a burn address: funds received there are lost.
 *
 * [validate] runs native key derivation; call it off the main thread.
 */
object SeedValidation {

    class Validated(val electrum: Seed.Electrum, val primaryAddress: String)

    private const val PREFIX_LENGTH = 3
    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")

    /** 32 zero bytes render as a long run of '1' in Monero base58; a real key never yields 16 in a row. */
    private val NULL_KEY_RUN = Regex("1{16,}")

    /** Mainnet primary address: 95 base58 chars starting with '4'. */
    private val MAINNET_PRIMARY = Regex("^4[1-9A-HJ-NP-Za-km-z]{94}$")

    private val englishWords: Set<String> by lazy { CakeWalletStyleConverter.MONERO_WORDLIST.toHashSet() }

    /** True when every word is in the English Electrum wordlist. */
    fun isEnglishElectrum(words: List<String>): Boolean = words.isNotEmpty() && words.all { it in englishWords }

    /**
     * Pure wallet2 checksum rule for an English 25-word seed (no native code).
     * Returns null when valid, otherwise a short reason (never echoes the seed).
     */
    fun electrumSeedProblem(words: List<String>): String? {
        if (words.size != 25) return "expected 25 words, got ${words.size}"
        if (!isEnglishElectrum(words)) return "word not in wordlist"
        val trimmed = StringBuilder()
        for (word in words.take(24)) {
            trimmed.append(word.substring(0, minOf(PREFIX_LENGTH, word.length)))
        }
        val crc = CRC32().apply { update(trimmed.toString().toByteArray(StandardCharsets.UTF_8)) }.value
        val expected = words[(crc % 24).toInt()]
        return if (words[24] == expected) null else "checksum word mismatch"
    }

    /** Null, malformed, or all-zero 32-byte key (hex). */
    fun isNullKey(hex: String?): Boolean =
        hex == null || !HEX_64.matches(hex) || hex.all { it == '0' }

    /** Well-formed mainnet primary address with no null-key run. */
    fun isPlausiblePrimaryAddress(address: String?): Boolean =
        address != null && MAINNET_PRIMARY.matches(address) && !NULL_KEY_RUN.containsMatchIn(address)

    fun kitSeed(words: List<String>, type: SeedType): Seed = when (type) {
        SeedType.ELECTRUM_25 -> Seed.Electrum(words, "")
        SeedType.BIP39_24 -> Seed.Bip39(words, "")
    }

    /**
     * Full validation. Throws [InvalidSeedException] for anything that must
     * not become a wallet. Runs native key derivation (JNI).
     */
    fun validate(words: List<String>, type: SeedType): Validated {
        when (type) {
            SeedType.BIP39_24 -> try {
                Mnemonic().validate(words)
            } catch (e: Exception) {
                Timber.w("Seed rejected: BIP39 validation failed (${e.javaClass.simpleName})")
                throw InvalidSeedException()
            }
            SeedType.ELECTRUM_25 -> if (isEnglishElectrum(words)) {
                electrumSeedProblem(words)?.let {
                    Timber.w("Seed rejected: $it")
                    throw InvalidSeedException()
                }
            }
            // Non-English Electrum seeds are left to wallet2's own decoder
            // (below + the post-start check in the ViewModel).
        }

        val electrum = try {
            kitSeed(words, type).toElectrum()
        } catch (e: Exception) {
            Timber.w("Seed rejected: conversion failed (${e.javaClass.simpleName})")
            throw InvalidSeedException()
        }

        val keys = try {
            MoneroKit.getKeys(electrum)
        } catch (e: Throwable) {
            Timber.w("Seed rejected: key derivation failed (${e.javaClass.simpleName})")
            throw InvalidSeedException()
        }
        if (isNullKey(keys.privateSpendKey) || isNullKey(keys.publicSpendKey) ||
            isNullKey(keys.privateViewKey) || isNullKey(keys.publicViewKey)
        ) {
            Timber.w("Seed rejected: null or malformed derived key")
            throw InvalidSeedException()
        }

        val address = try {
            MoneroKit.getAddress(electrum, 0, 0)
        } catch (e: Throwable) {
            Timber.w("Seed rejected: address derivation failed (${e.javaClass.simpleName})")
            throw InvalidSeedException()
        }
        if (!isPlausiblePrimaryAddress(address)) {
            Timber.w("Seed rejected: implausible primary address")
            throw InvalidSeedException()
        }
        return Validated(electrum, address)
    }
}
