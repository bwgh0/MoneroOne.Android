package one.monero.moneroone.core.wallet

import one.monero.moneroone.core.wallet.PinHash.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM side of PinHash: formats and the JCA path (the native library does not
 * load on the JVM). Native == JCA is checked on a device by PinHashNativeTest.
 */
class PinHashTest {

    private fun hex(s: String) = PinHash.hexToBytes(s)!!

    // RFC 7914 section 11 PBKDF2-HMAC-SHA256 vectors (first 32 of the 64 bytes:
    // PBKDF2 output blocks are independent, so a 32-byte key is block one).
    @Test
    fun `jca path matches the rfc 7914 vectors`() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
            PinHash.deriveJca("passwd", "salt".toByteArray(), 1).toHex()
        )
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56",
            PinHash.deriveJca("Password", "NaCl".toByteArray(), 80_000).toHex()
        )
    }

    @Test
    fun `created hash is in the current format and verifies`() {
        val stored = PinHash.create("135790")
        val parts = stored.split(":")
        assertEquals(3, parts.size)
        assertEquals(PinHash.ITERATIONS.toString(), parts[0])
        assertEquals(PinHash.SALT_LENGTH * 2, parts[1].length)
        assertEquals(PinHash.KEY_LENGTH_BITS / 4, parts[2].length)
        assertTrue(PinHash.verify("135790", stored))
        assertFalse(PinHash.verify("135791", stored))
        assertFalse(PinHash.needsRehash(stored))
    }

    @Test
    fun `hash written by the old WalletViewModel code still verifies`() {
        // Old hashPin(): "%02x" hex of salt and of the JCA key, "iterations:salt:hash".
        val salt = ByteArray(16) { (it * 13 + 1).toByte() }
        val key = PinHash.deriveJca("482913", salt, 1_000)
        val oldHex = { b: ByteArray -> b.joinToString("") { "%02x".format(it) } }
        val stored = "1000:${oldHex(salt)}:${oldHex(key)}"
        assertTrue(PinHash.verify("482913", stored))
        assertFalse(PinHash.verify("482914", stored))
        assertTrue(PinHash.needsRehash(stored))
    }

    @Test
    fun `two part format is 600k rounds`() {
        val salt = ByteArray(16) { (255 - it).toByte() }
        val stored = "${salt.toHex()}:${PinHash.deriveJca("000000", salt, 600_000).toHex()}"
        assertTrue(PinHash.verify("000000", stored))
        assertFalse(PinHash.verify("000001", stored))
        assertTrue(PinHash.needsRehash(stored))
    }

    @Test
    fun `legacy hashCode format`() {
        val stored = "123456".hashCode().toString()
        assertTrue(PinHash.verify("123456", stored))
        assertFalse(PinHash.verify("654321", stored))
        assertTrue(PinHash.needsRehash(stored))
    }

    @Test
    fun `malformed stored hashes fail closed without throwing`() {
        val salt = "00112233445566778899aabbccddeeff"
        val hash = "00".repeat(32)
        listOf(
            "",
            ":",
            "::",
            "0:$salt:$hash",
            "-5:$salt:$hash",
            "abc:$salt:$hash",
            "1000:0g:$hash",
            "1000:abc:$hash",
            "1000:$salt:zz",
            "1000:$salt:$hash:extra",
            "$salt:${hash}0"
        ).forEach { stored ->
            assertFalse("'$stored' must not verify", PinHash.verify("123456", stored))
        }
        assertFalse(PinHash.needsRehash("1000:$salt:$hash:extra"))
    }

    @Test
    fun `hex helpers`() {
        val bytes = ByteArray(256) { it.toByte() }
        assertArrayEquals(bytes, hex(bytes.toHex()))
        assertArrayEquals(byteArrayOf(0xab.toByte(), 0x0f), hex("AB0f"))
        assertNull(PinHash.hexToBytes("abc"))
        assertNull(PinHash.hexToBytes("0x"))
        assertNull(PinHash.hexToBytes("٣٣"))
    }
}
