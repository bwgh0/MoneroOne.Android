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
import kotlin.random.Random

class PriceUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "price_widget_update"

        fun schedule(context: Context) {
            // Flex window plus a random phase so fetches don't hit monero.one
            // on an exact 30-minute clock an observer could fingerprint.
            val request = PeriodicWorkRequestBuilder<PriceUpdateWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setInitialDelay(Random.nextLong(TimeUnit.MINUTES.toMillis(10)), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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
        // No fetch until a wallet exists. Avoids leaking IP to monero.one
        // before the user has generated/restored a key.
        if (prefs.getString("wallet_id", null) == null) {
            return Result.success()
        }

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
