package one.monero.moneroone.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// CoinGecko simple price response
@Serializable
data class CoinGeckoResponse(
    val monero: MoneroPriceData? = null
)

@Serializable
data class MoneroPriceData(
    val usd: Double? = null,
    val eur: Double? = null,
    val gbp: Double? = null,
    val cad: Double? = null,
    val aud: Double? = null,
    val jpy: Double? = null,
    val cny: Double? = null,
    @SerialName("usd_24h_change") val usd24hChange: Double? = null,
    @SerialName("eur_24h_change") val eur24hChange: Double? = null,
    @SerialName("gbp_24h_change") val gbp24hChange: Double? = null,
    @SerialName("cad_24h_change") val cad24hChange: Double? = null,
    @SerialName("aud_24h_change") val aud24hChange: Double? = null,
    @SerialName("jpy_24h_change") val jpy24hChange: Double? = null,
    @SerialName("cny_24h_change") val cny24hChange: Double? = null
)

// CoinGecko market chart response
@Serializable
data class CoinGeckoChartResponse(
    val prices: List<List<Double>>? = null
)

// CoinMarketCap chart response (matches iOS implementation exactly)
@Serializable
data class CMCChartResponse(
    val data: CMCChartData? = null
)

@Serializable
data class CMCChartData(
    val points: List<CMCPoint>? = null // Array of points
)

@Serializable
data class CMCPoint(
    val s: String? = null,        // timestamp as string (seconds)
    val v: List<Double>? = null   // [price, volume, marketCap]
)

// App models
data class PriceDataPoint(
    val timestamp: Long,
    val price: Double
)

data class CurrentPrice(
    val price: Double,
    val change24h: Double?,
    val usdToSelectedRate: Double = 1.0,  // Conversion rate calculated from CoinGecko response
    val lastUpdated: Long = System.currentTimeMillis()
)

// UI state
data class ChartUiState(
    val currentPrice: Double? = null,
    val priceChange: Double? = null,
    val priceChange24h: Double? = null,
    val chartData: List<PriceDataPoint> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedPoint: PriceDataPoint? = null,
    val high: Double? = null,
    val low: Double? = null,
    val open: Double? = null,
    val close: Double? = null
)

// Supported currencies with CoinMarketCap IDs for chart data
enum class Currency(
    val code: String,
    val symbol: String,
    val flag: String,
    val displayName: String,
    val cmcId: Int
) {
    USD("usd", "$", "🇺🇸", "US Dollar", 2781),
    EUR("eur", "€", "🇪🇺", "Euro", 2790),
    GBP("gbp", "£", "🇬🇧", "British Pound", 2791),
    CAD("cad", "C$", "🇨🇦", "Canadian Dollar", 2784),
    AUD("aud", "A$", "🇦🇺", "Australian Dollar", 2782),
    JPY("jpy", "¥", "🇯🇵", "Japanese Yen", 2797),
    CNY("cny", "¥", "🇨🇳", "Chinese Yuan", 2787)
}
