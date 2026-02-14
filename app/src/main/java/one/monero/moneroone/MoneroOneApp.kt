package one.monero.moneroone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import one.monero.moneroone.core.alert.PriceAlertManager
import one.monero.moneroone.core.alert.PriceAlertWorker
import one.monero.moneroone.core.service.WalletSyncService
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.core.wallet.WalletManager
import one.monero.moneroone.widget.PriceWidget
import one.monero.moneroone.widget.WalletWidget
import timber.log.Timber

class MoneroOneApp : Application() {

    companion object {
        private const val PREFS_NAME = "monero_wallet"
        private const val KEY_BACKGROUND_TIMESTAMP = "background_timestamp"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_SHOULD_LOCK = "should_lock"
        private const val KEY_BACKGROUND_SYNC = "background_sync_enabled"
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // App going to background - record timestamp
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_BACKGROUND_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Timber.d("App went to background, recorded timestamp")

            // Start background sync service if enabled and wallet is initialized
            if (prefs.getBoolean(KEY_BACKGROUND_SYNC, false) && WalletManager.kit != null) {
                Timber.d("Starting background sync service")
                WalletSyncService.start(this@MoneroOneApp)
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            // App coming to foreground - stop the service (ViewModel handles sync now)
            WalletSyncService.stop(this@MoneroOneApp)

            // Check if we should lock
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
                    // Set flag that WalletViewModel will check
                    prefs.edit()
                        .putBoolean(KEY_SHOULD_LOCK, true)
                        .apply()
                }
            }

            // Clear the background timestamp
            prefs.edit()
                .remove(KEY_BACKGROUND_TIMESTAMP)
                .apply()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()

        // Initialize network monitor
        NetworkMonitor.init(this)

        // Register lifecycle observer for auto-lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Schedule price alert worker if there are enabled alerts
        if (PriceAlertManager(this).hasEnabledAlerts()) {
            PriceAlertWorker.schedule(this)
        }

        // Refresh all widgets on startup
        PriceWidget.updateAll(this)
        WalletWidget.updateAll(this)

        Timber.d("MoneroOne Application started")
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val syncChannel = NotificationChannel(
            WalletSyncService.CHANNEL_ID,
            "Wallet Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows wallet sync progress while running in the background"
            setShowBadge(false)
        }
        nm.createNotificationChannel(syncChannel)

        val alertChannel = NotificationChannel(
            PriceAlertWorker.CHANNEL_ID,
            "Price Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when XMR price hits your target"
        }
        nm.createNotificationChannel(alertChannel)
    }
}
