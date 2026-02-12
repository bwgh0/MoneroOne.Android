package one.monero.moneroone.ui.screens.wallet

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.data.model.PriceDataPoint
import one.monero.moneroone.data.util.calculateChartIndex
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.GlassSegmentedPicker
import one.monero.moneroone.ui.screens.chart.ChartViewModel
import one.monero.moneroone.ui.screens.chart.TimeRange
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import one.monero.moneroone.data.model.Currency as AppCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioChartScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    chartViewModel: ChartViewModel
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val chartUiState by chartViewModel.uiState.collectAsState()
    val selectedRange by chartViewModel.selectedTimeRange.collectAsState()
    val selectedCurrency by chartViewModel.selectedCurrency.collectAsState()
    val conversionRate by chartViewModel.usdToSelectedRate.collectAsState()

    // Calculate balance in XMR
    val balanceXmr = walletState.balance.all.toDouble() / 1_000_000_000_000.0

    // Convert chart data to portfolio value (apply conversion rate for currency)
    val portfolioData = remember(chartUiState.chartData, balanceXmr, conversionRate) {
        chartUiState.chartData.map { point ->
            PriceDataPoint(
                timestamp = point.timestamp,
                price = point.price * balanceXmr * conversionRate
            )
        }
    }

    val selectedPoint = chartUiState.selectedPoint?.let { point ->
        PriceDataPoint(
            timestamp = point.timestamp,
            price = point.price * balanceXmr * conversionRate
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Portfolio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Portfolio value display card
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
                    val isShowingSelection = selectedPoint != null

                    Text(
                        text = if (isShowingSelection) formatDate(selectedPoint!!.timestamp, selectedRange) else "Current Value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // currentPrice is already in selected currency from CoinGecko
                    // chart data (close) is in USD, so we apply conversion rate
                    val displayValue = selectedPoint?.price
                        ?: (chartUiState.currentPrice?.times(balanceXmr))
                        ?: (chartUiState.close?.times(balanceXmr)?.times(conversionRate))

                    if (displayValue != null) {
                        Text(
                            text = formatCurrency(displayValue, selectedCurrency),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (chartUiState.isLoading) {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${walletViewModel.formatXmr(walletState.balance.all)} XMR",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val priceChange = chartUiState.priceChange
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
                onOptionSelected = { chartViewModel.selectTimeRange(it) },
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
                if (chartUiState.isLoading && portfolioData.isEmpty()) {
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
                } else if (portfolioData.isNotEmpty()) {
                    PortfolioChart(
                        data = portfolioData,
                        selectedPoint = selectedPoint,
                        currency = selectedCurrency,
                        onPointSelected = { point ->
                            // Convert back to price point for the view model
                            if (point != null && balanceXmr > 0) {
                                chartViewModel.selectPoint(
                                    PriceDataPoint(
                                        timestamp = point.timestamp,
                                        price = point.price / balanceXmr
                                    )
                                )
                            } else {
                                chartViewModel.selectPoint(null)
                            }
                        },
                        timeRange = selectedRange,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else if (chartUiState.error != null) {
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

            // Portfolio stats (High/Low)
            if (portfolioData.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val high = portfolioData.maxOfOrNull { it.price }
                        val low = portfolioData.minOfOrNull { it.price }

                        StatItem(
                            label = "${selectedRange.label} High",
                            value = high?.let { formatCurrency(it, selectedCurrency) } ?: "-",
                            valueColor = SuccessGreen
                        )
                        StatItem(
                            label = "${selectedRange.label} Low",
                            value = low?.let { formatCurrency(it, selectedCurrency) } ?: "-",
                            valueColor = ErrorRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PortfolioChart(
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

    val rightMargin = 55f
    val bottomMargin = 20f

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    chartWidth = size.width.toFloat() - rightMargin
                    val index = calculateChartIndex(offset.x, chartWidth, data.size)
                    data.getOrNull(index)?.let { onPointSelected(it) }
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    val index = calculateChartIndex(change.position.x, chartWidth, data.size)
                    data.getOrNull(index)?.let { onPointSelected(it) }
                },
                onDragEnd = { onPointSelected(null) },
                onDragCancel = { onPointSelected(null) }
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

        // Draw Y-axis labels
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
            val clampedY = y.coerceIn(textLayout.size.height / 2f, height - textLayout.size.height / 2f)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(width + 6f, clampedY - textLayout.size.height / 2)
            )
            drawLine(
                color = axisColor.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw X-axis labels
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

        // Create paths
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

        areaPath.lineTo(width, height)
        areaPath.lineTo(0f, height)
        areaPath.close()

        // Draw area fill
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

        // Draw selected point
        selectedPoint?.let { selected ->
            val index = data.indexOfFirst { it.timestamp == selected.timestamp }
            if (index >= 0) {
                val x = index * pointWidth
                val y = height - verticalPadding - ((selected.price - min) / range * (height - 2 * verticalPadding)).toFloat()

                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                drawCircle(
                    color = Color.White,
                    radius = 10f,
                    center = Offset(x, y)
                )

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
    val date = Date(timestamp)
    val pattern = when (range) {
        TimeRange.DAY -> "h:mm a"
        TimeRange.WEEK -> "EEE h:mm a"
        TimeRange.MONTH -> "MMM d, h:mm a"
        TimeRange.YEAR, TimeRange.ALL -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.US).format(date)
}
