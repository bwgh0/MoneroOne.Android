package one.monero.moneroone

import one.monero.moneroone.core.locale.tr
import android.app.Activity
import android.app.Application
import android.app.Notification
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
import one.monero.moneroone.core.wallet.PinHash
import one.monero.moneroone.core.wallet.WalletManager
import one.monero.moneroone.core.whatsnew.WhatsNewGate
import one.monero.moneroone.widget.PriceWidget
import one.monero.moneroone.widget.WalletWidget
import timber.log.Timber
import kotlin.concurrent.thread

class MoneroOneApp : Application() {

    companion object {
        private const val PREFS_NAME = "monero_wallet"
        private const val KEY_BACKGROUND_TIMESTAMP = "background_timestamp"
        private const val KEY_BACKGROUND_SYNC = "background_sync_enabled"
    }

    // ActivityLifecycleCallbacks fires immediately — no delay like ProcessLifecycleOwner
    private val activityCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity) {
            // A rotation, a theme switch or a font size change pauses the
            // activity only to make it again. The app stays on screen, so
            // this pause starts no auto-lock time.
            if (activity.isChangingConfigurations) return
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
        one.monero.moneroone.core.network.TorNetwork.initialize(this)
        one.monero.moneroone.core.util.SoundFeedback.initialize(this)
        one.monero.moneroone.core.locale.AppStrings.initialize(this)
        // What's New: decide fresh install or update before onboarding can create a wallet.
        WhatsNewGate.create(this).launched(hadWalletFiles = WhatsNewGate.hasWalletFiles(this))
        // Seed word lists load lazily from assets; this only stores the asset manager.
        one.monero.moneroone.core.wallet.seed.SeedWordLists.initialize(this)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()

        // The PIN check runs natively in the kit's library; load it now, off
        // Main, so the first unlock after a cold start does not wait for it.
        thread(name = "pin-hash-preload", isDaemon = true) { PinHash.preload() }

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

    /** Creates the channels, or renames them in the app language after a language change. */
    fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // createNotificationChannel never updates lockscreenVisibility on an
        // existing channel, so the hidden-from-lock-screen variants ship under
        // new ids and the originals are removed from installs that have them.
        nm.deleteNotificationChannel("wallet_sync")
        nm.deleteNotificationChannel("price_alerts")

        val syncChannel = NotificationChannel(
            WalletSyncService.CHANNEL_ID,
            tr("Wallet Sync"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = tr("Shows wallet sync progress while running in the background")
            setShowBadge(false)
            // Keep wallet activity off the lock screen — a visible sync/price
            // notification tells a shoulder-surfer this device holds Monero.
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(syncChannel)

        val alertChannel = NotificationChannel(
            PriceAlertWorker.CHANNEL_ID,
            tr("Price Alerts"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = tr("Get notified when XMR crosses a price you set.")
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(alertChannel)
    }
}
