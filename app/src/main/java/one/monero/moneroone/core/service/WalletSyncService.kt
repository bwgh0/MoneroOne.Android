package one.monero.moneroone.core.service

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
import one.monero.moneroone.R
import one.monero.moneroone.core.wallet.WalletManager
import timber.log.Timber

class WalletSyncService : Service() {

    companion object {
        const val CHANNEL_ID = "wallet_sync"
        const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "one.monero.moneroone.STOP_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WalletSyncService::class.java)
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
            Timber.d("WalletSyncService: stop action received")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(WalletManager.syncStateFlow.value))

        scope.launch {
            WalletManager.syncStateFlow.collect { state ->
                val notification = buildNotification(state)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, notification)
            }
        }

        Timber.d("WalletSyncService: started foreground")
        return START_STICKY
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Stop", stopPending)

        when (syncState) {
            is SyncState.Connecting -> {
                builder.setContentTitle("Connecting...")
                builder.setContentText("Connecting to Monero network")
                builder.setProgress(0, 0, true)
            }
            is SyncState.Syncing -> {
                val pct = ((syncState.progress ?: 0.0) * 100).toInt()
                val blocks = syncState.remainingBlocks
                builder.setContentTitle("Syncing $pct%")
                builder.setContentText(
                    if (blocks != null && blocks > 0) "$blocks blocks remaining"
                    else "Syncing wallet..."
                )
                builder.setProgress(100, pct, false)
            }
            is SyncState.Synced -> {
                builder.setContentTitle("Synced")
                builder.setContentText("Wallet is up to date")
            }
            is SyncState.NotSynced -> {
                builder.setContentTitle("Not synced")
                builder.setContentText(syncState.error.message ?: "Sync error")
            }
        }

        return builder.build()
    }
}
