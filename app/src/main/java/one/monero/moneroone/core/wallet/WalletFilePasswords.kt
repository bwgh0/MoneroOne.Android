package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.horizontalsystems.monerokit.MoneroKit
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The password of each wallet2 file. The `.keys` file holds the private spend key. Builds up to
 * 1.1.0 (21) encrypted it with the empty password, so anyone with the app's files could read the
 * spend key with no secret. Now each wallet file has its own password: 32 random bytes as hex,
 * encrypted with AES-256-GCM under a non-exportable Android Keystore key, in the prefs file
 * "wallet_file_passwords". The kit re-keys a file from an older build to it when the file opens.
 *
 * Fails closed. An entry that does not decrypt (damaged, or the Keystore key is gone) throws
 * [WalletFilePasswordException.Unreadable] and is never replaced. A wallet file with no entry gets a
 * new password only when the file is new or still opens with the empty password of older builds.
 * Else the password is missing: [WalletFilePasswordException.Missing], and nothing is written.
 *
 * An entry goes only after its wallet files are gone ([delete]). Names: a wallet's cache id, or
 * [envelopeName] for a payment-link envelope.
 */
class WalletFilePasswords internal constructor(
    private val prefs: SharedPreferences,
    private val sealer: Sealer,
    private val random: SecureRandom = SecureRandom()
) {

    /** Encrypts the entries. The real one is [KeystoreSealer]. */
    interface Sealer {
        fun hasKey(): Boolean
        fun createKey()
        fun seal(plain: ByteArray, aad: ByteArray): ByteArray
        fun open(sealed: ByteArray, aad: ByteArray): ByteArray
    }

    /**
     * The password of the wallet file [name]. [fileExists]: its `.keys` file is on disk.
     * [opensWithLegacyPassword] runs only for a file with no entry. Keystore and file reads: off Main.
     */
    @Synchronized
    fun passwordFor(name: String, fileExists: Boolean, opensWithLegacyPassword: () -> Boolean): String {
        prefs.getString(key(name), null)?.let { return read(name, it) }
        if (fileExists && !opensWithLegacyPassword()) throw WalletFilePasswordException.Missing()
        return create(name)
    }

    /** The kit wallet [walletId] under the password name [name]. JNI, Keystore and file reads: off Main. */
    fun forKitWallet(context: Context, name: String, walletId: String): String =
        passwordFor(name, MoneroKit.keysFile(context, walletId).exists()) {
            MoneroKit.opensWithLegacyPassword(context, walletId)
        }

    fun has(name: String): Boolean = prefs.contains(key(name))

    /** Call only after the wallet files of [name] are gone: a file without its password never opens again. */
    @Synchronized
    fun delete(name: String) {
        prefs.edit().remove(key(name)).commit()
    }

    private fun read(name: String, stored: String): String = try {
        check(stored.startsWith(FORMAT)) { "unknown entry format" }
        check(sealer.hasKey()) { "the Keystore key is missing" }
        val plain = sealer.open(Base64.getDecoder().decode(stored.removePrefix(FORMAT)), aad(name))
        check(plain.size == SECRET_BYTES) { "wrong secret length" }
        hex(plain).also { plain.fill(0) }
    } catch (e: WalletFilePasswordException) {
        throw e
    } catch (e: Exception) {
        throw WalletFilePasswordException.Unreadable(e)
    }

    private fun create(name: String): String {
        val secret = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        try {
            val password = hex(secret)
            val sealed = try {
                if (!sealer.hasKey()) {
                    // A new key would leave the old entries unreadable for good: they need the old one.
                    check(prefs.all.keys.none { it.startsWith(PREFIX) }) { "the Keystore key is missing" }
                    sealer.createKey()
                }
                sealer.seal(secret, aad(name))
            } catch (e: Exception) {
                throw WalletFilePasswordException.Unreadable(e)
            }
            val entry = FORMAT + Base64.getEncoder().encodeToString(sealed)
            if (!prefs.edit().putString(key(name), entry).commit()) {
                throw WalletFilePasswordException.Unreadable(IllegalStateException("the entry was not saved"))
            }
            // Read it back: a password that cannot be read again must never encrypt a file.
            val readBack = runCatching { prefs.getString(key(name), null)?.let { read(name, it) } }.getOrNull()
            if (readBack != password) {
                // No file uses it yet (a new file has none, an older file keeps the empty password).
                prefs.edit().remove(key(name)).commit()
                throw WalletFilePasswordException.Unreadable(IllegalStateException("the entry did not read back"))
            }
            return password
        } finally {
            secret.fill(0)
        }
    }

    private fun key(name: String) = PREFIX + name

    // Binds each entry to its name: an entry copied to another name does not decrypt.
    private fun aad(name: String) = "$FORMAT$name".toByteArray(Charsets.UTF_8)

    companion object {
        private const val PREFS_NAME = "wallet_file_passwords"
        private const val PREFIX = "pw."
        private const val FORMAT = "v1:"
        private const val SECRET_BYTES = 32

        fun envelopeName(linkId: String) = "envelope.$linkId"

        /** A random password that is never stored, for a wallet file that is deleted right after use. */
        fun throwaway(): String = ByteArray(SECRET_BYTES).let { bytes ->
            SecureRandom().nextBytes(bytes)
            hex(bytes).also { bytes.fill(0) }
        }

        private fun hex(bytes: ByteArray): String {
            val chars = "0123456789abcdef"
            val out = CharArray(bytes.size * 2)
            bytes.forEachIndexed { i, b ->
                out[i * 2] = chars[(b.toInt() shr 4) and 0xF]
                out[i * 2 + 1] = chars[b.toInt() and 0xF]
            }
            return String(out)
        }

        @Volatile
        private var instance: WalletFilePasswords? = null

        fun get(context: Context): WalletFilePasswords =
            instance ?: synchronized(this) {
                instance ?: WalletFilePasswords(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    KeystoreSealer(KEY_ALIAS)
                ).also { instance = it }
            }

        private const val KEY_ALIAS = "one.monero.moneroone.wallet_file_passwords"
    }
}

/** The password of a wallet file cannot be used. Nothing was written. */
sealed class WalletFilePasswordException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause) {
    /** The `.keys` file exists, no password is stored for it, and the empty password does not open it. */
    class Missing : WalletFilePasswordException("The wallet file password is missing")

    /** A stored password does not decrypt, or a new one could not be stored. */
    class Unreadable(cause: Throwable) : WalletFilePasswordException("The wallet file password cannot be read", cause)
}

/** AES-256-GCM under a non-exportable Android Keystore key, no user authentication (the kit opens wallets in the background). */
internal class KeystoreSealer(private val alias: String) : WalletFilePasswords.Sealer {

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun hasKey(): Boolean = keyStore().containsAlias(alias)

    override fun createKey() {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }

    private fun key(): SecretKey = keyStore().getKey(alias, null) as? SecretKey
        ?: throw IllegalStateException("the Keystore key is missing")

    override fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val body = cipher.doFinal(plain)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV length" }
        return iv + body
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "entry too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
