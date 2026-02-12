package one.monero.moneroone.ui.screens.chart

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.monero.moneroone.data.model.ChartUiState
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.PriceDataPoint
import one.monero.moneroone.data.repository.PriceRepository
import one.monero.moneroone.data.util.emaSmooth
import timber.log.Timber

class ChartViewModel(application: Application) : AndroidViewModel(application) {

    private val priceRepository = PriceRepository()
    private val prefs = application.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
    private var chartLoadJob: Job? = null
    private var priceLoadJob: Job? = null

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private var rawChartData: List<PriceDataPoint> = emptyList()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.WEEK)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(Currency.USD)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()

    // Conversion rate from USD to selected currency (for chart data conversion)
    private val _usdToSelectedRate = MutableStateFlow(1.0)
    val usdToSelectedRate: StateFlow<Double> = _usdToSelectedRate.asStateFlow()

    init {
        // Load saved currency from preferences
        val savedCode = prefs.getString("selected_currency", Currency.USD.code)
        _selectedCurrency.value = Currency.entries.find { it.code == savedCode } ?: Currency.USD
        loadData()
        load24hChange()
    }

    fun selectTimeRange(range: TimeRange) {
        if (_selectedTimeRange.value == range) return // Already selected
        _selectedTimeRange.value = range
        clearSelection()
        // Show loading but keep existing data visible (like iOS)
        _uiState.update { it.copy(isLoading = true) }
        loadChartData()
    }

    fun selectCurrency(currency: Currency) {
        if (_selectedCurrency.value == currency) return
        _selectedCurrency.value = currency
        // Persist to SharedPreferences (same key as WalletViewModel)
        prefs.edit().putString("selected_currency", currency.code).apply()
        // Reload all data with new currency (this sets the conversion rate from the response)
        loadData()
        load24hChange()
    }

    fun selectPoint(point: PriceDataPoint?) {
        _uiState.update { it.copy(selectedPoint = point) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPoint = null) }
    }

    private fun applySmoothing(data: List<PriceDataPoint>): List<PriceDataPoint> {
        return emaSmooth(data, 10)
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        loadCurrentPrice()
        loadChartData()
    }

    /**
     * Fetches 1D chart data to calculate the true 24h price change.
     * This value stays stable regardless of which time range the user selects in the chart.
     */
    private fun load24hChange() {
        viewModelScope.launch {
            priceRepository.fetchChartData(TimeRange.DAY, _selectedCurrency.value).fold(
                onSuccess = { data ->
                    val open = data.firstOrNull()?.price
                    val close = data.lastOrNull()?.price
                    if (open != null && close != null && open > 0) {
                        _uiState.update { it.copy(priceChange24h = ((close - open) / open) * 100) }
                    }
                },
                onFailure = { /* Silently fail, badge just won't show */ }
            )
        }
    }

    private fun loadCurrentPrice() {
        // Cancel any pending price fetch to prevent race conditions
        priceLoadJob?.cancel()

        priceLoadJob = viewModelScope.launch {
            val currency = _selectedCurrency.value
            priceRepository.fetchCurrentPrice(currency).fold(
                onSuccess = { result ->
                    // Only update if this is still the selected currency
                    if (_selectedCurrency.value == currency) {
                        // Set the conversion rate from the API response
                        _usdToSelectedRate.value = result.usdToSelectedRate
                        Timber.d("Conversion rate for ${currency.code}: ${result.usdToSelectedRate}")
                        _uiState.update { state ->
                            state.copy(
                                currentPrice = result.price,
                                priceChange = result.change24h,
                                error = null
                            )
                        }
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "Failed to fetch current price, retrying...")
                    // Retry once after a short delay (handles rate limiting from CurrencyScreen)
                    kotlinx.coroutines.delay(1000)
                    priceRepository.fetchCurrentPrice(currency).fold(
                        onSuccess = { result ->
                            // Only update if this is still the selected currency
                            if (_selectedCurrency.value == currency) {
                                _usdToSelectedRate.value = result.usdToSelectedRate
                                _uiState.update { state ->
                                    state.copy(
                                        currentPrice = result.price,
                                        priceChange = result.change24h,
                                        error = null
                                    )
                                }
                            }
                        },
                        onFailure = { e2 ->
                            Timber.e(e2, "Retry failed for current price")
                            _uiState.update { it.copy(error = e2.message) }
                        }
                    )
                }
            )
        }
    }

    private fun loadChartData() {
        // Cancel any pending chart load to prevent race conditions
        chartLoadJob?.cancel()

        chartLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val range = _selectedTimeRange.value
            val currency = _selectedCurrency.value
            val result = priceRepository.fetchChartData(range, currency)

            // Only update if this is still the selected range/currency (in case user switched while loading)
            if (_selectedTimeRange.value != range || _selectedCurrency.value != currency) return@launch

            result.fold(
                onSuccess = { data ->
                    rawChartData = data
                    val displayData = applySmoothing(data)

                    val high = displayData.maxOfOrNull { it.price }
                    val low = displayData.minOfOrNull { it.price }
                    val open = displayData.firstOrNull()?.price
                    val close = displayData.lastOrNull()?.price

                    _uiState.update { state ->
                        state.copy(
                            chartData = displayData,
                            isLoading = false,
                            error = null,
                            high = high,
                            low = low,
                            open = open,
                            close = close,
                            priceChange = if (open != null && close != null && open > 0) {
                                ((close - open) / open) * 100
                            } else state.priceChange
                        )
                    }
                },
                onFailure = { e ->
                    // Don't show error for cancellation - this is expected when user switches ranges
                    if (e is CancellationException) {
                        return@launch
                    }
                    // Silently fail like iOS - just log and keep existing data
                    Timber.e(e, "Failed to fetch chart data")
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    fun getChartPriceChange(): Double? {
        val state = _uiState.value
        val open = state.open ?: return null
        val close = state.close ?: return null
        if (open == 0.0) return null
        return ((close - open) / open) * 100
    }
}
