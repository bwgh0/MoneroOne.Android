package one.monero.moneroone.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.CurrentPrice
import one.monero.moneroone.data.model.MoneroOnePriceResponse
import one.monero.moneroone.data.model.PriceDataPoint
import one.monero.moneroone.data.model.CMCChartResponse
import one.monero.moneroone.data.util.lttbDownsample
import one.monero.moneroone.ui.screens.chart.TimeRange
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class PriceRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MONERO_ONE_API = "https://monero.one/api/v1"
    }

    /**
     * Fetch prices for all supported currencies via monero.one API.
     */
    suspend fun fetchAllPrices(): Result<Map<Currency, Double>> = withContext(Dispatchers.IO) {
        try {
            val response = fetchUrl("$MONERO_ONE_API/price")
            val parsed = json.decodeFromString<MoneroOnePriceResponse>(response)

            val priceMap = mutableMapOf<Currency, Double>()
            Currency.entries.forEach { currency ->
                parsed.quotes[currency.code]?.let { quote ->
                    priceMap[currency] = quote.price
                }
            }

            Result.success(priceMap)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch all prices")
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentPrice(currency: Currency): Result<CurrentPrice> = withContext(Dispatchers.IO) {
        try {
            val response = fetchUrl("$MONERO_ONE_API/price")
            val parsed = json.decodeFromString<MoneroOnePriceResponse>(response)

            val quote = parsed.quotes[currency.code]
                ?: return@withContext Result.failure(Exception("Price not available for ${currency.code}"))

            val usdQuote = parsed.quotes["usd"]
            val usdPrice = usdQuote?.price ?: quote.price

            val usdToSelectedRate = if (currency == Currency.USD || usdPrice == 0.0) {
                1.0
            } else {
                quote.price / usdPrice
            }

            Result.success(CurrentPrice(quote.price, quote.change24h, usdToSelectedRate))
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch current price")
            Result.failure(e)
        }
    }

    suspend fun fetchChartData(range: TimeRange, currency: Currency = Currency.USD): Result<List<PriceDataPoint>> = withContext(Dispatchers.IO) {
        try {
            val rangeParam = when (range) {
                TimeRange.DAY -> "1D"
                TimeRange.WEEK -> "7D"
                TimeRange.MONTH -> "1M"
                TimeRange.YEAR -> "1Y"
                TimeRange.ALL -> "ALL"
            }

            val response = fetchUrl("$MONERO_ONE_API/chart?range=$rangeParam")
            val parsed = json.decodeFromString<CMCChartResponse>(response)

            val points = parsed.data?.points
                ?: return@withContext Result.failure(Exception("No chart data available"))

            val dataPoints = points.mapNotNull { point ->
                val timestamp = point.s?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
                val price = point.v?.firstOrNull() ?: return@mapNotNull null
                PriceDataPoint(timestamp, price)
            }.sortedBy { it.timestamp }

            if (dataPoints.isEmpty()) {
                return@withContext Result.failure(Exception("No chart data available"))
            }

            val maxPoints = when (range) {
                TimeRange.DAY -> 96
                TimeRange.WEEK -> 168
                TimeRange.MONTH -> 180
                TimeRange.YEAR -> 365
                TimeRange.ALL -> 500
            }
            val downsampled = lttbDownsample(dataPoints, maxPoints)

            Result.success(downsampled)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch chart data")
            Result.failure(e)
        }
    }

    private fun fetchUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MoneroOne/1.0 Android")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
