package one.monero.moneroone.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// CoinGecko simple price response (legacy fallback)
@Serializable
data class CoinGeckoResponse(
    val monero: MoneroPriceData? = null
)

// Monero One price API response
@Serializable
data class MoneroOnePriceResponse(
    val quotes: Map<String, MoneroOnePriceQuote>,
    val timestamp: Long? = null
)

@Serializable
data class MoneroOnePriceQuote(
    val price: Double,
    @SerialName("change24h") val change24h: Double? = null
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
    @SerialName("try") val tryVal: Double? = null,
    val rub: Double? = null,
    val chf: Double? = null,
    val brl: Double? = null,
    val inr: Double? = null,
    val krw: Double? = null,
    val mxn: Double? = null,
    val pln: Double? = null,
    val uah: Double? = null,
    @SerialName("usd_24h_change") val usd24hChange: Double? = null,
    @SerialName("eur_24h_change") val eur24hChange: Double? = null,
    @SerialName("gbp_24h_change") val gbp24hChange: Double? = null,
    @SerialName("cad_24h_change") val cad24hChange: Double? = null,
    @SerialName("aud_24h_change") val aud24hChange: Double? = null,
    @SerialName("jpy_24h_change") val jpy24hChange: Double? = null,
    @SerialName("cny_24h_change") val cny24hChange: Double? = null,
    @SerialName("try_24h_change") val try24hChange: Double? = null,
    @SerialName("rub_24h_change") val rub24hChange: Double? = null,
    @SerialName("chf_24h_change") val chf24hChange: Double? = null,
    @SerialName("brl_24h_change") val brl24hChange: Double? = null,
    @SerialName("inr_24h_change") val inr24hChange: Double? = null,
    @SerialName("krw_24h_change") val krw24hChange: Double? = null,
    @SerialName("mxn_24h_change") val mxn24hChange: Double? = null,
    @SerialName("pln_24h_change") val pln24hChange: Double? = null,
    @SerialName("uah_24h_change") val uah24hChange: Double? = null
) {
    fun getPriceFor(currency: Currency): Double? = when (currency) {
        Currency.USD -> usd; Currency.EUR -> eur; Currency.GBP -> gbp
        Currency.CAD -> cad; Currency.AUD -> aud; Currency.JPY -> jpy
        Currency.CNY -> cny; Currency.TRY -> tryVal; Currency.RUB -> rub
        Currency.CHF -> chf; Currency.BRL -> brl; Currency.INR -> inr
        Currency.KRW -> krw; Currency.MXN -> mxn; Currency.PLN -> pln
        Currency.UAH -> uah
    }
    fun getChangeFor(currency: Currency): Double? = when (currency) {
        Currency.USD -> usd24hChange; Currency.EUR -> eur24hChange; Currency.GBP -> gbp24hChange
        Currency.CAD -> cad24hChange; Currency.AUD -> aud24hChange; Currency.JPY -> jpy24hChange
        Currency.CNY -> cny24hChange; Currency.TRY -> try24hChange; Currency.RUB -> rub24hChange
        Currency.CHF -> chf24hChange; Currency.BRL -> brl24hChange; Currency.INR -> inr24hChange
        Currency.KRW -> krw24hChange; Currency.MXN -> mxn24hChange; Currency.PLN -> pln24hChange
        Currency.UAH -> uah24hChange
    }
}

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
    CNY("cny", "¥", "🇨🇳", "Chinese Yuan", 2787),
    TRY("try", "₺", "🇹🇷", "Turkish Lira", 2810),
    RUB("rub", "₽", "🇷🇺", "Russian Ruble", 2806),
    CHF("chf", "Fr", "🇨🇭", "Swiss Franc", 2785),
    BRL("brl", "R$", "🇧🇷", "Brazilian Real", 2783),
    INR("inr", "₹", "🇮🇳", "Indian Rupee", 2796),
    KRW("krw", "₩", "🇰🇷", "South Korean Won", 2798),
    MXN("mxn", "MX$", "🇲🇽", "Mexican Peso", 2799),
    PLN("pln", "zł", "🇵🇱", "Polish Zloty", 2805),
    UAH("uah", "₴", "🇺🇦", "Ukrainian Hryvnia", 2824)
}
