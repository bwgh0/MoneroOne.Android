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
    val change24h: Double?
)

// UI state
data class ChartUiState(
    val currentPrice: Double? = null,
    val priceChange: Double? = null,
    val chartData: List<PriceDataPoint> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedPoint: PriceDataPoint? = null,
    val high: Double? = null,
    val low: Double? = null,
    val open: Double? = null,
    val close: Double? = null
)

// Supported currencies
enum class Currency(val code: String, val symbol: String, val flag: String, val displayName: String) {
    USD("usd", "$", "🇺🇸", "US Dollar"),
    EUR("eur", "€", "🇪🇺", "Euro"),
    GBP("gbp", "£", "🇬🇧", "British Pound"),
    CAD("cad", "C$", "🇨🇦", "Canadian Dollar"),
    AUD("aud", "A$", "🇦🇺", "Australian Dollar"),
    JPY("jpy", "¥", "🇯🇵", "Japanese Yen"),
    CNY("cny", "¥", "🇨🇳", "Chinese Yuan")
}
