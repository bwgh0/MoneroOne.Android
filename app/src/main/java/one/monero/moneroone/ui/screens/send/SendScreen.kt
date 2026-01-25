package one.monero.moneroone.ui.screens.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onSent: () -> Unit
) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val walletState by walletViewModel.walletState.collectAsState()
    val maxAmount = walletViewModel.formatXmr(walletState.balance.unlocked)

    val estimatedFee = remember(address, amount) {
        if (address.isNotBlank() && amount.isNotBlank()) {
            try {
                val amountLong = walletViewModel.parseXmr(amount)
                if (amountLong > 0) {
                    walletViewModel.estimateFee(address, amountLong)
                } else 0L
            } catch (e: Exception) {
                0L
            }
        } else 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send XMR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Recipient address
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recipient Address") },
                placeholder = { Text("4...") },
                trailingIcon = {
                    IconButton(onClick = onScanQr) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = MoneroOrange
                        )
                    }
                },
                isError = errorMessage != null && address.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    // Only allow valid decimal input
                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                        amount = it
                        errorMessage = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                placeholder = { Text("0.0000") },
                suffix = { Text("XMR") },
                trailingIcon = {
                    TextButton(
                        onClick = { amount = maxAmount }
                    ) {
                        Text("MAX", color = MoneroOrange)
                    }
                },
                supportingText = {
                    Text("Available: $maxAmount XMR")
                },
                isError = errorMessage != null && amount.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transaction summary
            if (address.isNotBlank() && amount.isNotBlank() && estimatedFee > 0) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Transaction Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SummaryRow(
                            label = "Amount",
                            value = "$amount XMR"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SummaryRow(
                            label = "Network Fee",
                            value = "${walletViewModel.formatXmr(estimatedFee)} XMR"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(8.dp))

                        val totalAmount = walletViewModel.parseXmr(amount) + estimatedFee
                        SummaryRow(
                            label = "Total",
                            value = "${walletViewModel.formatXmr(totalAmount)} XMR",
                            isTotal = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Send button
            Button(
                onClick = {
                    when {
                        address.isBlank() -> errorMessage = "Please enter a recipient address"
                        !address.startsWith("4") && !address.startsWith("8") -> {
                            errorMessage = "Invalid Monero address"
                        }
                        amount.isBlank() -> errorMessage = "Please enter an amount"
                        walletViewModel.parseXmr(amount) <= 0 -> {
                            errorMessage = "Amount must be greater than 0"
                        }
                        walletViewModel.parseXmr(amount) > walletState.balance.unlocked -> {
                            errorMessage = "Insufficient balance"
                        }
                        else -> {
                            walletViewModel.send(
                                address = address,
                                amount = walletViewModel.parseXmr(amount)
                            )
                            onSent()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White
                ),
                enabled = address.isNotBlank() && amount.isNotBlank()
            ) {
                Text(
                    text = "Send XMR",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isTotal) 1f else 0.7f)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
