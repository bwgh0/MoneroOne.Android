package one.monero.moneroone.core.wallet

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.horizontalsystems.monerokit.util.NativeCrypto
import one.monero.moneroone.core.wallet.PinHash.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * On a device: the kit's native PBKDF2 gives the same bytes as the platform
 * JCA provider, so PIN hashes written by either path verify with the other.
 */
@RunWith(AndroidJUnit4::class)
class PinHashNativeTest {

    private val random = SecureRandom()

    private fun jca(password: String, salt: ByteArray, iterations: Int, bytes: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, bytes * 8))
            .encoded

    @Test
    fun nativeLibraryLoads() {
        assertTrue(NativeCrypto.isAvailable)
    }

    // RFC 7914 section 11, full 64-byte outputs (two PBKDF2 blocks).
    @Test
    fun nativeMatchesRfc7914() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            NativeCrypto.pbkdf2HmacSha256("passwd".toByteArray(), "salt".toByteArray(), 1, 64).toHex()
        )
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
            NativeCrypto.pbkdf2HmacSha256("Password".toByteArray(), "NaCl".toByteArray(), 80_000, 64).toHex()
        )
    }

    @Test
    fun nativeMatchesJcaForPins() {
        repeat(40) { i ->
            val pin = (0 until 6).joinToString("") { random.nextInt(10).toString() }
            val salt = ByteArray(PinHash.SALT_LENGTH).also { random.nextBytes(it) }
            val iterations = if (i < 3) PinHash.ITERATIONS else 1 + random.nextInt(3_000)
            assertArrayEquals(
                "native != JCA (case $i, $iterations rounds)",
                PinHash.deriveJca(pin, salt, iterations),
                PinHash.deriveNative(pin, salt, iterations)
            )
        }
    }

    @Test
    fun nativeMatchesJcaForOddShapes() {
        // Over-64-byte passwords (the HMAC key is then hashed), short and long salts,
        // output lengths that are not a multiple of one block. (The JCA rejects an
        // empty password or salt.)
        val passwords = listOf("0", "a".repeat(64), "b".repeat(65), "c".repeat(200))
        val salts = listOf(ByteArray(1) { 7 }, ByteArray(100) { it.toByte() })
        for (password in passwords) for (salt in salts) for (bytes in listOf(1, 20, 32, 33, 64)) {
            assertArrayEquals(
                "native != JCA (${password.length}-char password, ${salt.size}-byte salt, $bytes bytes)",
                jca(password, salt, 257, bytes),
                NativeCrypto.pbkdf2HmacSha256(password.toByteArray(), salt, 257, bytes)
            )
        }
    }

    @Test
    fun hashesVerifyAcrossPaths() {
        val salt = ByteArray(PinHash.SALT_LENGTH).also { random.nextBytes(it) }
        // Written by the old JCA-only code, checked by the native path.
        val jcaStored = "${PinHash.ITERATIONS}:${salt.toHex()}:${PinHash.deriveJca("908172", salt, PinHash.ITERATIONS).toHex()}"
        assertTrue(PinHash.verify("908172", jcaStored))
        // Written by the native path, checked by the JCA.
        val nativeStored = PinHash.create("563412")
        val parts = nativeStored.split(":")
        assertEquals(
            parts[2],
            PinHash.deriveJca("563412", PinHash.hexToBytes(parts[1])!!, PinHash.ITERATIONS).toHex()
        )
    }

    @Test
    fun timing() {
        val salt = ByteArray(PinHash.SALT_LENGTH).also { random.nextBytes(it) }
        repeat(3) { run ->
            var t0 = SystemClock.elapsedRealtime()
            val native = PinHash.deriveNative("111111", salt, PinHash.ITERATIONS)
            val nativeMs = SystemClock.elapsedRealtime() - t0
            t0 = SystemClock.elapsedRealtime()
            val jca = PinHash.deriveJca("111111", salt, PinHash.ITERATIONS)
            val jcaMs = SystemClock.elapsedRealtime() - t0
            assertArrayEquals(jca, native)
            Log.i("PinHashNativeTest", "run $run: ${PinHash.ITERATIONS} rounds native=${nativeMs}ms jca=${jcaMs}ms")
        }
    }
}
