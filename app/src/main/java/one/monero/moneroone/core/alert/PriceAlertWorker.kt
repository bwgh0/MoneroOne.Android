package one.monero.moneroone.core.alert

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import one.monero.moneroone.MainActivity
import one.monero.moneroone.R
import one.monero.moneroone.data.model.AlertCondition
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.repository.PriceRepository
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PriceAlertWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "price_alert_check"
        const val CHANNEL_ID = "price_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("Price alert worker scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Price alert worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val manager = PriceAlertManager(context)

        if (!manager.hasEnabledAlerts()) {
            Timber.d("No enabled alerts, skipping check")
            return Result.success()
        }

        val priceRepository = PriceRepository()
        val pricesResult = priceRepository.fetchAllPrices()

        pricesResult.onSuccess { prices ->
            val triggered = manager.checkAlerts(prices)
            triggered.forEach { alert ->
                sendNotification(alert, prices)
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to fetch prices for alert check")
        }

        return Result.success()
    }

    private fun sendNotification(alert: one.monero.moneroone.data.model.PriceAlert, prices: Map<Currency, Double>) {
        val currency = Currency.entries.find { it.code == alert.currencyCode } ?: return
        val currentPrice = prices[currency] ?: return

        val conditionText = when (alert.condition) {
            AlertCondition.ABOVE -> "above"
            AlertCondition.BELOW -> "below"
        }

        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        try {
            format.currency = java.util.Currency.getInstance(currency.code.uppercase())
        } catch (_: Exception) {}

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, alert.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.monero_logo)
            .setContentTitle("XMR Price Alert")
            .setContentText("Monero is $conditionText ${format.format(alert.targetPrice)} (now ${format.format(currentPrice)})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(alert.id.hashCode(), notification)
    }
}
