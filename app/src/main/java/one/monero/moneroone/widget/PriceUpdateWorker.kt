package one.monero.moneroone.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.repository.PriceRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PriceUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "price_widget_update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("Price widget worker scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Price widget worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val currencyCode = prefs.getString("selected_currency", Currency.USD.code) ?: Currency.USD.code
        val currency = Currency.entries.find { it.code == currencyCode } ?: Currency.USD

        val priceRepository = PriceRepository()
        priceRepository.fetchCurrentPrice(currency).onSuccess { result ->
            WidgetDataStore.savePrice(
                context,
                result.price,
                result.change24h,
                currency.code,
                currency.symbol
            )
            PriceWidget.updateAll(context)
        }.onFailure { e ->
            Timber.e(e, "Failed to fetch price for widget update")
        }

        return Result.success()
    }
}
