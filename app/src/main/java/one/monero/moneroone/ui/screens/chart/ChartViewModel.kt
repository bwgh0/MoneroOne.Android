package one.monero.moneroone.ui.screens.chart

import androidx.lifecycle.ViewModel
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
import timber.log.Timber

class ChartViewModel : ViewModel() {

    private val priceRepository = PriceRepository()
    private var chartLoadJob: Job? = null

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.WEEK)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(Currency.USD)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()

    init {
        loadData()
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
        _selectedCurrency.value = currency
        loadCurrentPrice()
    }

    fun selectPoint(point: PriceDataPoint?) {
        _uiState.update { it.copy(selectedPoint = point) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPoint = null) }
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        loadCurrentPrice()
        loadChartData()
    }

    private fun loadCurrentPrice() {
        viewModelScope.launch {
            val result = priceRepository.fetchCurrentPrice(_selectedCurrency.value)
            result.fold(
                onSuccess = { price ->
                    _uiState.update { state ->
                        state.copy(
                            currentPrice = price.price,
                            priceChange = price.change24h,
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "Failed to fetch current price")
                    _uiState.update { it.copy(error = e.message) }
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
            val result = priceRepository.fetchChartData(range)

            // Only update if this is still the selected range (in case user switched while loading)
            if (_selectedTimeRange.value != range) return@launch

            result.fold(
                onSuccess = { data ->
                    val high = data.maxOfOrNull { it.price }
                    val low = data.minOfOrNull { it.price }
                    val open = data.firstOrNull()?.price
                    val close = data.lastOrNull()?.price

                    _uiState.update { state ->
                        state.copy(
                            chartData = data,
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
