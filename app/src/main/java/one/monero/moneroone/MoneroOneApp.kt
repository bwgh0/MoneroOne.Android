package one.monero.moneroone

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber

class MoneroOneApp : Application() {

    companion object {
        private const val PREFS_NAME = "monero_wallet"
        private const val KEY_BACKGROUND_TIMESTAMP = "background_timestamp"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_SHOULD_LOCK = "should_lock"
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // App going to background - record timestamp
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_BACKGROUND_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Timber.d("App went to background, recorded timestamp")
        }

        override fun onStart(owner: LifecycleOwner) {
            // App coming to foreground - check if we should lock
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val backgroundTimestamp = prefs.getLong(KEY_BACKGROUND_TIMESTAMP, 0)
            val timeoutSeconds = prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, 60) // Default 1 minute

            if (backgroundTimestamp > 0 && timeoutSeconds >= 0) {
                val elapsedSeconds = (System.currentTimeMillis() - backgroundTimestamp) / 1000

                val shouldLock = when {
                    timeoutSeconds == 0 -> true // IMMEDIATE
                    timeoutSeconds == -1 -> false // NEVER
                    else -> elapsedSeconds >= timeoutSeconds
                }

                if (shouldLock) {
                    Timber.d("Auto-lock triggered: elapsed=${elapsedSeconds}s, timeout=${timeoutSeconds}s")
                    // Set flag that WalletViewModel will check - use commit() for synchronous write
                    prefs.edit()
                        .putBoolean(KEY_SHOULD_LOCK, true)
                        .commit()
                }
            }

            // Clear the background timestamp - use commit() to ensure synchronous removal
            prefs.edit()
                .remove(KEY_BACKGROUND_TIMESTAMP)
                .commit()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Register lifecycle observer for auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        Timber.d("MoneroOne Application started")
    }
}
