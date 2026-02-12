package one.monero.moneroone.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import one.monero.moneroone.data.model.CMCChartResponse
import one.monero.moneroone.data.model.CoinGeckoResponse
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.CurrentPrice
import one.monero.moneroone.data.model.PriceDataPoint
import one.monero.moneroone.data.util.lttbDownsample
import one.monero.moneroone.ui.screens.chart.TimeRange
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class PriceRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3"
        // CoinMarketCap API for chart data (matches iOS implementation)
        private const val CMC_CHART_URL = "https://api.coinmarketcap.com/data-api/v3.3/cryptocurrency/detail/chart"
        private const val MONERO_CMC_ID = 328
    }

    /**
     * Fetch prices for all supported currencies in a single API call.
     * Returns a map of Currency to price.
     */
    suspend fun fetchAllPrices(): Result<Map<Currency, Double>> = withContext(Dispatchers.IO) {
        try {
            val currencies = Currency.entries.joinToString(",") { it.code }
            val url = "$COINGECKO_BASE_URL/simple/price?ids=monero&vs_currencies=$currencies&include_24hr_change=true"

            val response = fetchUrl(url)
            val parsed = json.decodeFromString<CoinGeckoResponse>(response)

            val priceData = parsed.monero
                ?: return@withContext Result.failure(Exception("No price data available"))

            val priceMap = mutableMapOf<Currency, Double>()
            Currency.entries.forEach { currency ->
                val price = when (currency) {
                    Currency.USD -> priceData.usd
                    Currency.EUR -> priceData.eur
                    Currency.GBP -> priceData.gbp
                    Currency.CAD -> priceData.cad
                    Currency.AUD -> priceData.aud
                    Currency.JPY -> priceData.jpy
                    Currency.CNY -> priceData.cny
                }
                price?.let { priceMap[currency] = it }
            }

            Result.success(priceMap)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch all prices")
            Result.failure(e)
        }
    }

    suspend fun fetchCurrentPrice(currency: Currency): Result<CurrentPrice> = withContext(Dispatchers.IO) {
        try {
            val currencies = Currency.entries.joinToString(",") { it.code }
            val url = "$COINGECKO_BASE_URL/simple/price?ids=monero&vs_currencies=$currencies&include_24hr_change=true"

            val response = fetchUrl(url)
            val parsed = json.decodeFromString<CoinGeckoResponse>(response)

            val priceData = parsed.monero
                ?: return@withContext Result.failure(Exception("No price data available"))

            val selectedPrice = when (currency) {
                Currency.USD -> priceData.usd
                Currency.EUR -> priceData.eur
                Currency.GBP -> priceData.gbp
                Currency.CAD -> priceData.cad
                Currency.AUD -> priceData.aud
                Currency.JPY -> priceData.jpy
                Currency.CNY -> priceData.cny
            } ?: return@withContext Result.failure(Exception("Price not available for ${currency.code}"))

            val usdPrice = priceData.usd ?: selectedPrice  // fallback if USD unavailable

            // Calculate conversion rate from the prices we already have
            val usdToSelectedRate = if (currency == Currency.USD || usdPrice == 0.0) {
                1.0
            } else {
                selectedPrice / usdPrice
            }

            val change = when (currency) {
                Currency.USD -> priceData.usd24hChange
                Currency.EUR -> priceData.eur24hChange
                Currency.GBP -> priceData.gbp24hChange
                Currency.CAD -> priceData.cad24hChange
                Currency.AUD -> priceData.aud24hChange
                Currency.JPY -> priceData.jpy24hChange
                Currency.CNY -> priceData.cny24hChange
            }

            Result.success(CurrentPrice(selectedPrice, change, usdToSelectedRate))
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch current price")
            Result.failure(e)
        }
    }

    suspend fun fetchChartData(range: TimeRange, currency: Currency = Currency.USD): Result<List<PriceDataPoint>> = withContext(Dispatchers.IO) {
        try {
            // CoinMarketCap range and interval parameters (matches iOS)
            val (rangeParam, interval) = when (range) {
                TimeRange.DAY -> "1D" to "5m"
                TimeRange.WEEK -> "7D" to "15m"
                TimeRange.MONTH -> "1M" to "1h"
                TimeRange.YEAR -> "1Y" to "1d"
                TimeRange.ALL -> "ALL" to "7d"
            }

            // IMPORTANT: Always fetch chart data in USD (convertId=2781)
            // CMC API doesn't reliably return converted data, so we fetch in USD
            // and apply conversion rate client-side (matching iOS implementation)
            val url = "$CMC_CHART_URL?id=$MONERO_CMC_ID&range=$rangeParam&interval=$interval&convertId=2781"

            val response = fetchCmcUrl(url)
            val parsed = json.decodeFromString<CMCChartResponse>(response)

            val points = parsed.data?.points
                ?: return@withContext Result.failure(Exception("No chart data available"))

            // CMC returns array of {s: "timestamp_seconds", v: [price, volume, marketCap]}
            val dataPoints = points.mapNotNull { point ->
                val timestamp = point.s?.toLongOrNull()?.times(1000) ?: return@mapNotNull null // Convert to ms
                val price = point.v?.firstOrNull() ?: return@mapNotNull null
                PriceDataPoint(timestamp, price)
            }.sortedBy { it.timestamp }

            if (dataPoints.isEmpty()) {
                return@withContext Result.failure(Exception("No chart data available"))
            }

            // Downsample based on time range
            val maxPoints = when (range) {
                TimeRange.DAY -> 96      // ~15 min intervals
                TimeRange.WEEK -> 168    // ~1 hour intervals
                TimeRange.MONTH -> 180   // ~4 hour intervals
                TimeRange.YEAR -> 365    // ~1 day intervals
                TimeRange.ALL -> 500     // More points for full history
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
            connection.setRequestProperty("User-Agent", "MoneroOne/1.0")
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

    // CoinMarketCap requires specific headers (matches iOS implementation)
    private fun fetchCmcUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MoneroOne/1.0 Android")
            connection.setRequestProperty("origin", "https://coinmarketcap.com")
            connection.setRequestProperty("platform", "web")
            connection.setRequestProperty("referer", "https://coinmarketcap.com/")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
