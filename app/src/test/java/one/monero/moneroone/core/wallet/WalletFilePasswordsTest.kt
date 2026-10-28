package one.monero.moneroone.core.wallet

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The store of the wallet-file passwords (the `.keys` password of each wallet2 file). The Keystore is
 * replaced by a software AES-GCM key here; WalletFilePasswordsKeystoreTest runs the real one.
 */
class WalletFilePasswordsTest {

    /** AES-256-GCM with a key in memory; [dropKey] stands for a Keystore key that is gone. */
    private class SoftSealer : WalletFilePasswords.Sealer {
        var key: SecretKey? = null
        var keysCreated = 0
        var breakOpen = false

        override fun hasKey() = key != null
        override fun createKey() {
            key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            keysCreated++
        }

        override fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key!!, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return iv + cipher.doFinal(plain)
        }

        override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
            check(!breakOpen) { "Keystore failure" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key!!, GCMParameterSpec(128, sealed, 0, 12))
            cipher.updateAAD(aad)
            return cipher.doFinal(sealed, 12, sealed.size - 12)
        }

        fun dropKey() {
            key = null
        }
    }

    /** Prefs whose commit fails and writes nothing, as on a full disk. */
    private class NoCommitPrefs(private val inner: SharedPreferences) : SharedPreferences by inner {
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = this
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = this
            override fun clear() = this
            override fun commit() = false
            override fun apply() = Unit
        }
    }

    private val prefs = FakeSharedPreferences()
    private val sealer = SoftSealer()
    private val store = WalletFilePasswords(prefs, sealer)

    private val newFile = { fail("a file that is not on disk needs no legacy check"); false }

    private fun entries() = prefs.all.filterKeys { it.startsWith("pw.") }

    @Test
    fun aNewWalletGets32RandomBytesAsHexAndKeepsThem() {
        val password = store.passwordFor("cache-a", fileExists = false, opensWithLegacyPassword = newFile)
        assertTrue(Regex("[0-9a-f]{64}").matches(password))
        assertEquals(password, store.passwordFor("cache-a", fileExists = true) { fail("an entry needs no legacy check"); false })
        // A second process reads the same entry.
        assertEquals(password, WalletFilePasswords(prefs, sealer).passwordFor("cache-a", true) { false })
        // The entry is not the password in the clear.
        assertFalse(entries().values.any { (it as String).contains(password) })
    }

    @Test
    fun eachWalletGetsItsOwnPassword() {
        val a = store.passwordFor("cache-a", false, newFile)
        val b = store.passwordFor("cache-b", false, newFile)
        val envelope = store.passwordFor(WalletFilePasswords.envelopeName("link1"), false, newFile)
        assertEquals(3, setOf(a, b, envelope).size)
        assertEquals(1, sealer.keysCreated)
    }

    @Test
    fun aLegacyFileGetsANewPassword() {
        var checked = false
        val password = store.passwordFor("cache-a", fileExists = true) { checked = true; true }
        assertTrue(checked)
        assertEquals(64, password.length)
        assertTrue(store.has("cache-a"))
    }

    @Test
    fun aMissingPasswordIsNeverReplaced() {
        try {
            store.passwordFor("cache-a", fileExists = true) { false }
            fail("a re-keyed file with no stored password got a new one")
        } catch (_: WalletFilePasswordException.Missing) {
        }
        assertTrue("something was written", entries().isEmpty())
        assertEquals(0, sealer.keysCreated)
    }

    @Test
    fun aDamagedEntryFailsClosedAndStays() {
        store.passwordFor("cache-a", false, newFile)
        prefs.edit().putString("pw.cache-a", "v1:AAAA").commit()
        assertUnreadable { store.passwordFor("cache-a", fileExists = true) { true } }
        assertEquals("the damaged entry was replaced", "v1:AAAA", prefs.getString("pw.cache-a", null))

        prefs.edit().putString("pw.cache-a", "garbage").commit()
        assertUnreadable { store.passwordFor("cache-a", fileExists = false) { true } }
        assertEquals("garbage", prefs.getString("pw.cache-a", null))
    }

    @Test
    fun anEntryCopiedToAnotherNameDoesNotDecrypt() {
        store.passwordFor("cache-a", false, newFile)
        prefs.edit().putString("pw.cache-b", prefs.getString("pw.cache-a", null)).commit()
        assertUnreadable { store.passwordFor("cache-b", true) { true } }
    }

    @Test
    fun aLostKeyFailsClosedForOldAndNewEntries() {
        val before = store.passwordFor("cache-a", false, newFile)
        val entry = prefs.getString("pw.cache-a", null)
        sealer.dropKey()
        assertUnreadable { store.passwordFor("cache-a", true) { true } }
        // No new key while entries need the old one: they would stay unreadable for good.
        assertUnreadable { store.passwordFor("cache-b", false, newFile) }
        assertEquals(1, sealer.keysCreated)
        assertEquals(entry, prefs.getString("pw.cache-a", null))
        assertNull(prefs.getString("pw.cache-b", null))
        assertNotEquals("", before)
    }

    @Test
    fun aKeystoreFailureIsNotAMissingPassword() {
        store.passwordFor("cache-a", false, newFile)
        sealer.breakOpen = true
        assertUnreadable { store.passwordFor("cache-a", true) { fail("a stored entry is never re-created"); true } }
        sealer.breakOpen = false
        assertEquals(64, store.passwordFor("cache-a", true) { false }.length)
    }

    @Test
    fun anEntryThatDoesNotReadBackIsNotUsed() {
        sealer.createKey()
        sealer.breakOpen = true
        assertUnreadable { store.passwordFor("cache-a", false, newFile) }
        assertTrue("an unconfirmed entry stayed", entries().isEmpty())
    }

    @Test
    fun anEntryThatIsNotSavedIsNotUsed() {
        val failing = WalletFilePasswords(NoCommitPrefs(prefs), sealer)
        assertUnreadable { failing.passwordFor("cache-a", false, newFile) }
        assertTrue(entries().isEmpty())
    }

    @Test
    fun deleteRemovesOnlyThatEntry() {
        val a = store.passwordFor("cache-a", false, newFile)
        store.passwordFor("cache-b", false, newFile)
        store.delete("cache-b")
        assertFalse(store.has("cache-b"))
        assertEquals(a, store.passwordFor("cache-a", true) { false })
    }

    @Test
    fun throwawayPasswordsAreRandomAndNeverStored() {
        val a = WalletFilePasswords.throwaway()
        val b = WalletFilePasswords.throwaway()
        assertTrue(Regex("[0-9a-f]{64}").matches(a))
        assertNotEquals(a, b)
        assertTrue(entries().isEmpty())
    }

    private fun assertUnreadable(block: () -> Unit) {
        try {
            block()
            fail("expected an unreadable password")
        } catch (_: WalletFilePasswordException.Unreadable) {
        }
    }
}
