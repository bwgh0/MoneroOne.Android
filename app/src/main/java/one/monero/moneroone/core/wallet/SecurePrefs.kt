package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The app's one EncryptedSharedPreferences file ("secure_wallet_data"): seeds,
 * per-wallet PIN hashes, PIN rate-limit counters and node RPC credentials.
 * Opened once per process; the master key lives in the Android Keystore.
 */
object SecurePrefs {

    @Volatile
    private var instance: SharedPreferences? = null

    fun open(context: Context): SharedPreferences {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                appContext,
                "secure_wallet_data",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { instance = it }
        }
    }
}
