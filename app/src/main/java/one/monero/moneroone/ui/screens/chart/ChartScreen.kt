package one.monero.moneroone.ui.screens.chart

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import one.monero.moneroone.data.model.Currency as AppCurrency
import one.monero.moneroone.data.model.PriceDataPoint
import one.monero.moneroone.data.util.calculateChartIndex
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.GlassSegmentedPicker
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

enum class TimeRange(val label: String, val days: Int) {
    DAY("24H", 1),
    WEEK("1W", 7),
    MONTH("1M", 30),
    YEAR("1Y", 365),
    ALL("All", 1825)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    viewModel: ChartViewModel = viewModel(),
    onPriceAlertsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val conversionRate by viewModel.usdToSelectedRate.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                viewModel.refresh()
                delay(1500)
                isRefreshing = false
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Monero Price",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onPriceAlertsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Price Alerts",
                    tint = MoneroOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Price display card
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            ) {
                val selectedPoint = uiState.selectedPoint
                val isShowingSelection = selectedPoint != null

                Text(
                    text = if (isShowingSelection) formatDate(selectedPoint!!.timestamp, selectedRange) else "Current Price",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Show selected point price, or current price, or fall back to latest chart price
                // Apply conversion rate to chart data (which is in USD) for non-USD currencies
                // Note: currentPrice is already in the selected currency from CoinGecko
                val displayPrice = when {
                    selectedPoint != null -> selectedPoint.price * conversionRate
                    uiState.currentPrice != null -> uiState.currentPrice
                    uiState.close != null -> uiState.close!! * conversionRate
                    else -> null
                }
                if (displayPrice != null) {
                    Text(
                        text = formatCurrency(displayPrice, selectedCurrency),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else if (uiState.isLoading) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val priceChange = uiState.priceChange
                if (priceChange != null) {
                    val changeColor = if (priceChange >= 0) SuccessGreen else ErrorRed
                    val changePrefix = if (priceChange >= 0) "+" else ""

                    Text(
                        text = "$changePrefix${String.format(Locale.US, "%.2f", priceChange)}% (${selectedRange.label})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time range selector
        GlassSegmentedPicker(
            options = TimeRange.entries.toList(),
            selectedOption = selectedRange,
            onOptionSelected = { viewModel.selectTimeRange(it) },
            modifier = Modifier.fillMaxWidth(),
            labelSelector = { it.label }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Chart area
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            if (uiState.isLoading && uiState.chartData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MoneroOrange,
                        strokeWidth = 3.dp
                    )
                }
            } else if (uiState.chartData.isNotEmpty()) {
                // Apply conversion rate to chart data for display
                val convertedChartData = remember(uiState.chartData, conversionRate) {
                    uiState.chartData.map { point ->
                        PriceDataPoint(point.timestamp, point.price * conversionRate)
                    }
                }
                val convertedSelectedPoint = uiState.selectedPoint?.let { point ->
                    PriceDataPoint(point.timestamp, point.price * conversionRate)
                }
                PriceChart(
                    data = convertedChartData,
                    selectedPoint = convertedSelectedPoint,
                    currency = selectedCurrency,
                    onPointSelected = { convertedPoint ->
                        // Convert back to USD for the view model
                        if (convertedPoint != null && conversionRate > 0) {
                            viewModel.selectPoint(
                                PriceDataPoint(
                                    convertedPoint.timestamp,
                                    convertedPoint.price / conversionRate
                                )
                            )
                        } else {
                            viewModel.selectPoint(null)
                        }
                    },
                    timeRange = selectedRange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unable to load chart",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Price stats (High/Low/Open/Close)
        if (uiState.chartData.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = "${selectedRange.label} High",
                            value = uiState.high?.let { formatCurrency(it * conversionRate, selectedCurrency) } ?: "-",
                            valueColor = SuccessGreen
                        )
                        StatItem(
                            label = "${selectedRange.label} Low",
                            value = uiState.low?.let { formatCurrency(it * conversionRate, selectedCurrency) } ?: "-",
                            valueColor = ErrorRed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = "Open",
                            value = uiState.open?.let { formatCurrency(it * conversionRate, selectedCurrency) } ?: "-"
                        )
                        StatItem(
                            label = "Close",
                            value = uiState.close?.let { formatCurrency(it * conversionRate, selectedCurrency) } ?: "-"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    } // PullToRefreshBox
}

@Composable
private fun PriceChart(
    data: List<PriceDataPoint>,
    selectedPoint: PriceDataPoint?,
    currency: AppCurrency,
    onPointSelected: (PriceDataPoint?) -> Unit,
    timeRange: TimeRange,
    modifier: Modifier = Modifier
) {
    val lineColor = MoneroOrange
    val areaTopColor = MoneroOrange.copy(alpha = 0.4f)
    val areaBottomColor = MoneroOrange.copy(alpha = 0.0f)
    val axisColor = Color.Gray.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)

    var chartWidth by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Margins for axis labels
    val rightMargin = 45f
    val bottomMargin = 20f

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    chartWidth = size.width.toFloat() - rightMargin
                    val index = calculateChartIndex(offset.x, chartWidth, data.size)
                    data.getOrNull(index)?.let { onPointSelected(it) }
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    val index = calculateChartIndex(change.position.x, chartWidth, data.size)
                    data.getOrNull(index)?.let { onPointSelected(it) }
                },
                onDragEnd = {
                    isDragging = false
                    onPointSelected(null)
                },
                onDragCancel = {
                    isDragging = false
                    onPointSelected(null)
                }
            )
        }
    ) {
        if (data.isEmpty()) return@Canvas

        val width = size.width - rightMargin
        val height = size.height - bottomMargin
        chartWidth = width

        val min = data.minOfOrNull { it.price } ?: 0.0
        val max = data.maxOfOrNull { it.price } ?: 0.0
        val range = (max - min).coerceAtLeast(0.01)
        val verticalPadding = height * 0.05f

        val pointWidth = width / (data.size - 1).coerceAtLeast(1)

        // Draw Y-axis labels (price) on the RIGHT side - only 3 labels to avoid cramping
        val yLabelCount = 2
        val currencySymbol = when (currency) {
            AppCurrency.USD -> "$"
            AppCurrency.EUR -> "€"
            AppCurrency.GBP -> "£"
            AppCurrency.CAD -> "C$"
            AppCurrency.AUD -> "A$"
            AppCurrency.JPY -> "¥"
            AppCurrency.CNY -> "¥"
        }
        for (i in 0..yLabelCount) {
            val price = min + (range * i / yLabelCount)
            val y = height - verticalPadding - ((price - min) / range * (height - 2 * verticalPadding)).toFloat()
            val label = "$currencySymbol${String.format(Locale.US, "%.0f", price)}"
            val textLayout = textMeasurer.measure(label, labelStyle)
            // Clamp y position so labels don't go outside chart area
            val clampedY = y.coerceIn(textLayout.size.height / 2f, height - textLayout.size.height / 2f)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(width + 6f, clampedY - textLayout.size.height / 2)
            )
            // Draw horizontal grid line
            drawLine(
                color = axisColor.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw X-axis labels (dates) - only middle labels, skip first and last to avoid cramping
        val xLabelCount = 3
        val datePattern = when (timeRange) {
            TimeRange.DAY -> "ha"
            TimeRange.WEEK -> "EEE"
            TimeRange.MONTH -> "M/d"
            TimeRange.YEAR, TimeRange.ALL -> "MMM"
        }
        val dateFormat = SimpleDateFormat(datePattern, Locale.US)
        for (i in 1..xLabelCount) {
            val dataIndex = (data.size - 1) * i / (xLabelCount + 1)
            val point = data.getOrNull(dataIndex) ?: continue
            val x = dataIndex * pointWidth
            val label = dateFormat.format(Date(point.timestamp))
            val textLayout = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(x - textLayout.size.width / 2, height + 4f)
            )
        }

        // Create path for the line
        val linePath = Path()
        val areaPath = Path()

        data.forEachIndexed { index, point ->
            val x = index * pointWidth
            val y = height - verticalPadding - ((point.price - min) / range * (height - 2 * verticalPadding)).toFloat()

            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, height)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }

        // Complete area path
        areaPath.lineTo(width, height)
        areaPath.lineTo(0f, height)
        areaPath.close()

        // Draw area fill with gradient
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(areaTopColor, areaBottomColor)
            )
        )

        // Draw line
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw selected point indicator
        selectedPoint?.let { selected ->
            val index = data.indexOfFirst { it.timestamp == selected.timestamp }
            if (index >= 0) {
                val x = index * pointWidth
                val y = height - verticalPadding - ((selected.price - min) / range * (height - 2 * verticalPadding)).toFloat()

                // Draw dashed vertical line
                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                // Draw outer circle (white border)
                drawCircle(
                    color = Color.White,
                    radius = 10f,
                    center = Offset(x, y)
                )

                // Draw inner circle (orange)
                drawCircle(
                    color = lineColor,
                    radius = 6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

private fun formatCurrency(amount: Double, currency: AppCurrency): String {
    val locale = when (currency) {
        AppCurrency.USD -> Locale.US
        AppCurrency.EUR -> Locale.GERMANY
        AppCurrency.GBP -> Locale.UK
        AppCurrency.CAD -> Locale.CANADA
        AppCurrency.AUD -> Locale("en", "AU")
        AppCurrency.JPY -> Locale.JAPAN
        AppCurrency.CNY -> Locale.CHINA
    }
    val format = NumberFormat.getCurrencyInstance(locale)
    try {
        format.currency = Currency.getInstance(currency.code.uppercase())
    } catch (e: Exception) {
        // Fallback formatting
    }
    return format.format(amount)
}

private fun formatDate(timestamp: Long, range: TimeRange): String {
    val date = Date(timestamp)  // CoinGecko returns milliseconds already
    val pattern = when (range) {
        TimeRange.DAY -> "h:mm a"
        TimeRange.WEEK -> "EEE h:mm a"
        TimeRange.MONTH -> "MMM d, h:mm a"
        TimeRange.YEAR, TimeRange.ALL -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.US).format(date)
}
