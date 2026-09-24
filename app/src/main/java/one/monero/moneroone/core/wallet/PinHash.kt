package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.util.NativeCrypto
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 PIN hashes, stored as "iterations:saltHex:hashHex".
 * Older formats still verify: "saltHex:hashHex" (600k rounds) and a bare
 * String.hashCode(); [needsRehash] flags them for rewriting after an unlock.
 *
 * The rounds run in native code through the kit (tens of ms) when its library
 * loads, else in the JCA provider, which on Android is pure Java (a second or
 * more per unlock). Both derive from the same bytes: the JCA provider feeds
 * PBKDF2 the UTF-8 encoding of the PIN, and for the ASCII digits a PIN pad
 * produces that is one byte per char. Any non-ASCII PIN takes the JCA path, so
 * stored hashes never need migrating between the two.
 */
object PinHash {

    // OWASP's floor for PBKDF2-HMAC-SHA256. Hashes at another count are
    // rewritten at this one on the next successful unlock.
    const val ITERATIONS = 600_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH = 16
    private const val LEGACY_ITERATIONS = 600_000
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Loads the native library, so the first unlock after a cold start does not pay for it. */
    fun preload() {
        NativeCrypto.isAvailable
    }

    fun create(pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        return "$ITERATIONS:${salt.toHex()}:${derive(pin, salt, ITERATIONS).toHex()}"
    }

    /** Constant-time check of [pin] against a stored hash in any supported format. */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        val (iterations, saltHex, hashHex) = when (parts.size) {
            // Legacy format: String.hashCode()
            1 -> return constantTimeEquals(pin.hashCode().toString().toByteArray(), stored.toByteArray())
            2 -> Triple(LEGACY_ITERATIONS, parts[0], parts[1])
            3 -> Triple(parts[0].toIntOrNull() ?: return false, parts[1], parts[2])
            else -> return false
        }
        if (iterations < 1) return false
        val salt = hexToBytes(saltHex)?.takeIf { it.isNotEmpty() } ?: return false
        val expected = hexToBytes(hashHex)?.takeIf { it.isNotEmpty() } ?: return false
        return constantTimeEquals(derive(pin, salt, iterations), expected)
    }

    /** True for a verified hash in an older format or at another round count. */
    fun needsRehash(stored: String): Boolean {
        val parts = stored.split(":")
        return when (parts.size) {
            1, 2 -> true
            3 -> parts[0].toIntOrNull() != ITERATIONS
            else -> false
        }
    }

    fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        if (NativeCrypto.isAvailable && pin.all { it.code < 0x80 }) {
            deriveNative(pin, salt, iterations)
        } else {
            deriveJca(pin, salt, iterations)
        }

    internal fun deriveNative(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val password = ByteArray(pin.length) { pin[it].code.toByte() }
        try {
            return NativeCrypto.pbkdf2HmacSha256(password, salt, iterations, KEY_LENGTH_BITS / 8)
        } finally {
            password.fill(0)
        }
    }

    internal fun deriveJca(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val password = pin.toCharArray()
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    private const val HEX = "0123456789abcdef"

    internal fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        forEachIndexed { i, byte ->
            val v = byte.toInt() and 0xff
            out[2 * i] = HEX[v ushr 4]
            out[2 * i + 1] = HEX[v and 0x0f]
        }
        return String(out)
    }

    internal fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(hex[2 * i])
            val lo = hexDigit(hex[2 * i + 1])
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
