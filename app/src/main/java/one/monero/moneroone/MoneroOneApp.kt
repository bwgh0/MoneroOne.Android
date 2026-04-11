package one.monero.moneroone

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
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
        private const val KEY_BACKGROUND_SYNC = "background_sync_enabled"
    }

    // ActivityLifecycleCallbacks fires immediately — no delay like ProcessLifecycleOwner
    private val activityCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BACKGROUND_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    // ProcessLifecycleOwner is still used for background sync (stop/start)
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_BACKGROUND_SYNC, false) && WalletManager.kit != null) {
                Timber.d("Starting background sync service")
                WalletSyncService.start(this@MoneroOneApp)
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            WalletSyncService.stop(this@MoneroOneApp)
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

        // Activity callbacks for auto-lock timestamp (fires immediately)
        registerActivityLifecycleCallbacks(activityCallbacks)

        // Process observer for background sync only
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

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
