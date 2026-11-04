package one.monero.moneroone.core.service

import one.monero.moneroone.core.locale.tr
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.horizontalsystems.monerokit.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import one.monero.moneroone.MainActivity
import androidx.core.content.ContextCompat
import one.monero.moneroone.R
import one.monero.moneroone.core.wallet.WalletManager
import one.monero.moneroone.core.wallet.WalletStore
import timber.log.Timber

class WalletSyncService : Service() {

    companion object {
        // v2: hidden from the lock screen. Channel settings cannot be changed
        // after creation, so the id is versioned instead.
        const val CHANNEL_ID = "wallet_sync_v2"
        const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "one.monero.moneroone.STOP_SYNC"

        /**
         * Foreground starts sent whose onStartCommand has not run yet. A service
         * stopped before it calls startForeground crashes the app
         * (ForegroundServiceDidNotStartInTimeException).
         */
        private val pendingStarts = PendingStarts()

        fun start(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
            try {
                context.startForegroundService(intent)
                pendingStarts.sent()
            } catch (e: IllegalStateException) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException
                // when the app may not start a foreground service now.
                Timber.w(e, "WalletSyncService: start not allowed")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
            if (pendingStarts.any()) {
                // The app came back before the service ran (a quick trip to the
                // background). stopService now would crash the app; a stop action
                // runs after the pending start has called startForeground.
                try {
                    context.startService(intent.setAction(ACTION_STOP))
                } catch (e: IllegalStateException) {
                    // stopService would still crash the app here. The pending start
                    // stops the service right after its startForeground instead.
                    Timber.w(e, "WalletSyncService: stop action not allowed, stopping after the pending start")
                    pendingStarts.stopWhenStarted()
                }
                return
            }
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The startId of the last onStartCommand. stopSelf(lastStartId) leaves a foreground
     * start that is still pending alone: stopping before it calls startForeground
     * crashes the app (ForegroundServiceDidNotStartInTimeException).
     */
    private var lastStartId = 0

    /** Uptime when the app was last in the foreground: the dataSync time limit counts from it. */
    private var lastForegroundUptime = 0L

    private val timeLimitGuard = Runnable { onTimeLimitGuard() }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            lastForegroundUptime = SystemClock.uptimeMillis()
            armTimeLimitGuard()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        Timber.d("WalletSyncService: created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP) {
            // From the notification's Stop button or from stop(): a plain start,
            // not a foreground one. stopSelf(startId) leaves a later start alone.
            Timber.d("WalletSyncService: stop action received")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // True when stop() ran while this start was pending.
        val stopAfterStart = intent != null && pendingStarts.ran()

        // Every branch calls startForeground before it stops: a foreground start
        // that stops first crashes the app (ForegroundServiceDidNotStartInTimeException).
        val inForeground = try {
            startForeground(NOTIFICATION_ID, buildNotification(WalletManager.syncStateFlow.value))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): a start
            // from the background, or the Android 15+ dataSync time limit is used up.
            Timber.w(e, "WalletSyncService: foreground start not allowed, stopping")
            false
        }

        // A null intent is a system restart after the process died. The new
        // process has no open wallet, so stop.
        if (intent == null || !inForeground || stopAfterStart) {
            if (intent == null) Timber.d("WalletSyncService: restart without a wallet, stopping")
            if (stopAfterStart) Timber.d("WalletSyncService: stop asked for while starting, stopping")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // start() runs when the app leaves the foreground or from the Sync screen.
        lastForegroundUptime = SystemClock.uptimeMillis()
        armTimeLimitGuard()

        scope.launch {
            WalletManager.syncStateFlow.collect { state ->
                val notification = buildNotification(state)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, notification)
            }
        }

        Timber.d("WalletSyncService: started foreground")
        // Not sticky: a restarted service has no open wallet to sync.
        return START_NOT_STICKY
    }

    /**
     * Android 15+ limits dataSync foreground services to 6 hours a day. At the
     * limit the system calls this, and the service must stop within a few
     * seconds, or the app crashes (ForegroundServiceDidNotStopInTimeException).
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("WalletSyncService: dataSync time limit reached, stopping")
        // Not onTimeout's startId: the system passes the last start sent, which can
        // still be pending. stopForeground alone meets the time limit.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    /**
     * Some Android 15+ devices never call onTimeout, and the app then crashes at
     * the limit. So the service also stops itself a minute before it.
     */
    private fun armTimeLimitGuard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        handler.removeCallbacks(timeLimitGuard)
        handler.postAtTime(timeLimitGuard, SyncTimeLimit.stopAt(lastForegroundUptime))
    }

    private fun onTimeLimitGuard() {
        val now = SystemClock.uptimeMillis()
        val appInForeground = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (appInForeground) lastForegroundUptime = now
        if (!SyncTimeLimit.shouldStop(now, lastForegroundUptime, appInForeground)) {
            armTimeLimitGuard()
            return
        }
        Timber.w("WalletSyncService: dataSync time limit is near, stopping")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeLimitGuard)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        scope.cancel()
        Timber.d("WalletSyncService: destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(syncState: SyncState): Notification {
        // Tap opens app
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, WalletSyncService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.monero_orange))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, tr("Stop"), stopPending)

        // Identify which wallet is syncing (multi-wallet).
        val activeWallet = WalletStore.activeWalletInfo(this)
        val walletLabel = activeWallet?.let { "${it.emoji} ${it.name}" }

        when (syncState) {
            is SyncState.Connecting -> {
                builder.setContentTitle(tr("Connecting..."))
                builder.setContentText(walletLabel?.let { tr("%s — connecting to Monero network", it) }
                    ?: tr("Connecting to Monero network"))
                builder.setProgress(0, 0, true)
            }
            is SyncState.Syncing -> {
                val pct = ((syncState.progress ?: 0.0) * 100).toInt()
                val blocks = syncState.remainingBlocks
                builder.setContentTitle(walletLabel?.let { tr("%s — syncing %s%%", it, pct) } ?: tr("Syncing %s%%", pct))
                builder.setContentText(
                    if (blocks != null && blocks > 0) tr("%s blocks remaining", blocks)
                    else tr("Syncing wallet...")
                )
                builder.setProgress(100, pct, false)
            }
            is SyncState.Synced -> {
                builder.setContentTitle(tr("Synced"))
                builder.setContentText(walletLabel?.let { tr("%s is up to date", it) } ?: tr("Wallet is up to date"))
            }
            is SyncState.NotSynced -> {
                builder.setContentTitle(tr("Not synced"))
                builder.setContentText(syncState.error.message ?: tr("Sync error"))
            }
        }

        return builder.build()
    }
}
