package one.monero.moneroone.core.service

import one.monero.moneroone.core.locale.tr
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
import java.util.concurrent.atomic.AtomicInteger

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
        private val pendingStarts = AtomicInteger(0)

        fun start(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
            try {
                context.startForegroundService(intent)
                pendingStarts.incrementAndGet()
            } catch (e: IllegalStateException) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException
                // when the app may not start a foreground service now.
                Timber.w(e, "WalletSyncService: start not allowed")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
            if (pendingStarts.get() > 0) {
                // The app came back before the service ran (a quick trip to the
                // background). stopService now would crash the app; a stop action
                // runs after the pending start has called startForeground.
                try {
                    context.startService(intent.setAction(ACTION_STOP))
                    return
                } catch (e: IllegalStateException) {
                    Timber.w(e, "WalletSyncService: stop action not allowed")
                }
            }
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Timber.d("WalletSyncService: created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // From the notification's Stop button or from stop(): a plain start,
            // not a foreground one. stopSelf(startId) leaves a later start alone.
            Timber.d("WalletSyncService: stop action received")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent != null) pendingStarts.updateAndGet { (it - 1).coerceAtLeast(0) }

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
        if (intent == null || !inForeground) {
            if (intent == null) Timber.d("WalletSyncService: restart without a wallet, stopping")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
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
