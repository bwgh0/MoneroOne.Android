package one.monero.moneroone.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.alert.PriceAlertManager
import one.monero.moneroone.core.alert.PriceAlertWorker
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.data.model.AlertCondition
import one.monero.moneroone.data.model.PriceAlert
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.GlassSegmentedPicker
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.MoneroOrange
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun AddPriceAlertScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { PriceAlertManager(context) }
    val currentPrice by walletViewModel.currentPrice.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()

    var condition by remember { mutableStateOf(AlertCondition.ABOVE) }
    var targetPrice by remember { mutableStateOf("") }

    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    try {
        format.currency = java.util.Currency.getInstance(selectedCurrency.code.uppercase())
    } catch (_: Exception) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Price Alert",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current price display
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Current XMR Price",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentPrice?.let { format.format(it.price) } ?: "Loading...",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Condition picker
        Text(
            text = "Alert when price goes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        GlassSegmentedPicker(
            options = AlertCondition.entries.toList(),
            selectedOption = condition,
            onOptionSelected = { condition = it },
            modifier = Modifier.fillMaxWidth(),
            labelSelector = { if (it == AlertCondition.ABOVE) "Above" else "Below" }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Target price input
        Text(
            text = "Target Price",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        OutlinedTextField(
            value = targetPrice,
            onValueChange = {
                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                    targetPrice = it
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.00") },
            prefix = {
                Text(
                    text = selectedCurrency.symbol,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MoneroOrange,
                cursorColor = MoneroOrange,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(
            onClick = {
                val price = targetPrice.toDoubleOrNull() ?: return@PrimaryButton
                val alert = PriceAlert(
                    id = UUID.randomUUID().toString(),
                    condition = condition,
                    targetPrice = price,
                    currencyCode = selectedCurrency.code
                )
                manager.addAlert(alert)
                PriceAlertWorker.schedule(context)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = targetPrice.toDoubleOrNull() != null && targetPrice.toDoubleOrNull()!! > 0,
            color = MoneroOrange
        ) {
            Text(
                text = "Create Alert",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
