package one.monero.moneroone.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.CurrentPrice
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CurrencyScreen(
    currentPrice: CurrentPrice?,
    selectedCurrency: Currency,
    isLoading: Boolean,
    onBack: () -> Unit,
    onCurrencySelected: (Currency) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Currency",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select your preferred display currency for prices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Currency list (no prices per row - matches iOS)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Currency.entries.forEach { currency ->
                CurrencyItem(
                    currency = currency,
                    isSelected = currency == selectedCurrency,
                    onClick = { onCurrencySelected(currency) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Current Price section
        Text(
            text = "CURRENT PRICE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isLoading) {
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MoneroOrange
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Fetching price...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else if (currentPrice != null) {
                    // Price row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1 XMR",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatPrice(currentPrice.price, selectedCurrency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    // 24h change row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "24h Change",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        val change = currentPrice.change24h
                        if (change != null) {
                            val isPositive = change >= 0
                            val changeColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFE53935)
                            val arrow = if (isPositive) "\u2197" else "\u2198" // ↗ or ↘
                            Text(
                                text = "$arrow ${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", change)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = changeColor
                            )
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    // Last updated row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Updated",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatRelativeTime(currentPrice.lastUpdated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Error state (no price and not loading)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\u26A0",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unable to fetch price",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer
        Text(
            text = "Prices from CoinGecko",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CurrencyItem(
    currency: Currency,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag emoji
            Text(
                text = currency.flag,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Currency info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currency.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currency.code.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Check mark if selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MoneroOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatPrice(price: Double, currency: Currency): String {
    val format = NumberFormat.getCurrencyInstance(
        when (currency) {
            Currency.USD -> Locale.US
            Currency.EUR -> Locale.GERMANY
            Currency.GBP -> Locale.UK
            Currency.CAD -> Locale.CANADA
            Currency.AUD -> Locale("en", "AU")
            Currency.JPY -> Locale.JAPAN
            Currency.CNY -> Locale.CHINA
        }
    )

    return try {
        format.currency = java.util.Currency.getInstance(currency.code.uppercase())
        format.format(price)
    } catch (e: Exception) {
        "${currency.symbol}${String.format(Locale.US, "%.2f", price)}"
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60

    return when {
        diffSeconds < 60 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes} min ago"
        diffHours < 24 -> "${diffHours} hr ago"
        else -> "${diffHours / 24} days ago"
    }
}
