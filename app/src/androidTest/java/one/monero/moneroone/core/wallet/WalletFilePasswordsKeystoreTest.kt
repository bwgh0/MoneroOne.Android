package one.monero.moneroone.core.wallet

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.WalletService
import io.horizontalsystems.monerokit.model.WalletManager
import io.horizontalsystems.monerokit.toElectrum
import io.horizontalsystems.monerokit.util.Helper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/**
 * On a device: the wallet-file passwords under a real Android Keystore key, and with real wallet2 files.
 * Each test has its own key alias and prefs file.
 */
@RunWith(AndroidJUnit4::class)
class WalletFilePasswordsKeystoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = UUID.randomUUID().toString()
    private val alias = "test.wallet_file_passwords.$tag"
    private val prefsName = "test_wallet_file_passwords_$tag"
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val walletIds = mutableListOf<String>()

    private fun store() = WalletFilePasswords(prefs, KeystoreSealer(alias))

    @After
    fun tearDown() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        context.deleteSharedPreferences(prefsName)
        walletIds.forEach { id ->
            Helper.getWalletRoot(context).listFiles()?.filter { it.name == id || it.name.startsWith("$id.") }?.forEach { it.delete() }
        }
    }

    @Test
    fun aPasswordIsSealedUnderTheKeystoreKeyAndReadsBack() {
        val password = store().passwordFor("cache-a", false) { false }
        assertTrue(Regex("[0-9a-f]{64}").matches(password))
        assertTrue(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        // A new instance (a new process) reads the same password.
        assertEquals(password, store().passwordFor("cache-a", true) { false })
        val entry = prefs.all.values.single() as String
        assertFalse("the password is in the clear", entry.contains(password))
    }

    @Test
    fun aLostKeystoreKeyFailsClosed() {
        store().passwordFor("cache-a", false) { false }
        val entry = prefs.getString("pw.cache-a", null)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        try {
            store().passwordFor("cache-a", true) { true }
            fail("an entry without its key was read or replaced")
        } catch (_: WalletFilePasswordException.Unreadable) {
        }
        assertEquals(entry, prefs.getString("pw.cache-a", null))
    }

    @Test
    fun aLegacyWalletFileGetsAPasswordTheKitMigratesTo() {
        val walletId = "wfp-test-${UUID.randomUUID()}".also { walletIds += it }
        val seed = Seed.Bip39(Mnemonic().generate(Mnemonic.EntropyStrength.VeryHigh), "").toElectrum()
        // What builds up to 1.1.0 (21) wrote: the empty password.
        val legacy = WalletManager.getInstance().recoveryWallet(
            Helper.getWalletFile(context, walletId), "", seed.mnemonic.joinToString(" "), seed.passphrase,
            MoneroKit.restoreHeightForNewWallet()
        )
        assertTrue(legacy.status.errorString, legacy.status.isOk)
        val address = legacy.address
        legacy.close()

        val password = store().forKitWallet(context, walletId, walletId)
        val service = WalletService(context)
        val wallet = service.openWallet(walletId, password, "")
        assertNotNull("the legacy file did not open", wallet)
        assertEquals(address, wallet!!.getSubaddress(0, 0))
        service.stop()

        val keys = MoneroKit.keysFile(context, walletId).absolutePath
        assertTrue(WalletManager.getInstance().verifyWalletPassword(keys, password, true))
        assertFalse(MoneroKit.opensWithLegacyPassword(context, walletId))
        assertEquals(password, store().forKitWallet(context, walletId, walletId))

        // The re-keyed file with its password gone: nothing new is made for it.
        prefs.edit().clear().commit()
        try {
            store().forKitWallet(context, walletId, walletId)
            fail("a re-keyed file got a new password")
        } catch (_: WalletFilePasswordException.Missing) {
        }
        assertTrue(prefs.all.isEmpty())
        assertTrue("the file was deleted", File(keys).exists())
    }
}
