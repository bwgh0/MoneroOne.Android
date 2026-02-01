package one.monero.moneroone.ui.screens.settings

import android.content.Context
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.repository.PriceRepository
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CurrencyScreen(
    onBack: () -> Unit,
    onCurrencySelected: (Currency) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    val priceRepository = remember { PriceRepository() }

    var selectedCurrency by remember {
        val savedCode = prefs.getString("selected_currency", Currency.USD.code)
        mutableStateOf(Currency.entries.find { it.code == savedCode } ?: Currency.USD)
    }

    val prices = remember { mutableStateMapOf<Currency, Double?>() }

    // Fetch current prices for all currencies
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Currency.entries.forEach { currency ->
                val result = priceRepository.fetchCurrentPrice(currency)
                result.onSuccess { price ->
                    prices[currency] = price.price
                }
            }
        }
    }

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

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Currency.entries.forEach { currency ->
                CurrencyItem(
                    currency = currency,
                    price = prices[currency],
                    isSelected = currency == selectedCurrency,
                    onClick = {
                        selectedCurrency = currency
                        prefs.edit().putString("selected_currency", currency.code).apply()
                        onCurrencySelected(currency)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CurrencyItem(
    currency: Currency,
    price: Double?,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currency.code.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (price != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1 XMR = ${formatPrice(price, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
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
