package one.monero.moneroone.ui.screens.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.random.Random

enum class TimeRange(val label: String, val days: Int) {
    DAY("24H", 1),
    WEEK("7D", 7),
    MONTH("30D", 30),
    YEAR("1Y", 365)
}

@Composable
fun ChartScreen() {
    var selectedRange by remember { mutableStateOf(TimeRange.WEEK) }
    var currentPrice by remember { mutableStateOf(150.0) }
    var priceChange by remember { mutableStateOf(2.5) }
    var priceData by remember { mutableStateOf<List<Double>>(emptyList()) }

    // Simulate loading price data
    LaunchedEffect(selectedRange) {
        withContext(Dispatchers.IO) {
            val prices = generateMockPriceData(selectedRange.days)
            currentPrice = prices.last()
            priceChange = ((prices.last() - prices.first()) / prices.first()) * 100
            priceData = prices
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "XMR Price",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Price display
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Current Price",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatCurrency(currentPrice),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val changeColor = if (priceChange >= 0) SuccessGreen else ErrorRed
                val changePrefix = if (priceChange >= 0) "+" else ""

                Text(
                    text = "$changePrefix${String.format("%.2f", priceChange)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time range selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { selectedRange = range },
                    label = { Text(range.label) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MoneroOrange,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simple chart placeholder (Vico requires more setup)
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Simple price range display instead of full chart
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (priceData.isNotEmpty()) {
                        val min = priceData.minOrNull() ?: 0.0
                        val max = priceData.maxOrNull() ?: 0.0

                        Text(
                            text = "Price Range",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "High: ${formatCurrency(max)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Low: ${formatCurrency(min)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ErrorRed
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Market stats
        Text(
            text = "Market Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                StatRow("Market Cap", "$2.8B")
                Spacer(modifier = Modifier.height(12.dp))
                StatRow("24h Volume", "$95M")
                Spacer(modifier = Modifier.height(12.dp))
                StatRow("Circulating Supply", "18.4M XMR")
                Spacer(modifier = Modifier.height(12.dp))
                StatRow("All-Time High", "$542.33")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.currency = Currency.getInstance("USD")
    return format.format(amount)
}

private fun generateMockPriceData(days: Int): List<Double> {
    val dataPoints = days * 24 // Hourly data
    var price = 145.0
    return List(dataPoints) {
        price += (Random.nextDouble() - 0.48) * 2 // Slight upward bias
        price = price.coerceIn(100.0, 200.0)
        price
    }
}
