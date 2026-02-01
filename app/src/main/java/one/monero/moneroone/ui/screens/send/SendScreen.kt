package one.monero.moneroone.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.MoneroOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    walletViewModel: WalletViewModel,
    initialAddress: String? = null,
    initialAmount: String? = null,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onSent: () -> Unit
) {
    var address by remember(initialAddress) { mutableStateOf(initialAddress ?: "") }
    var amount by remember(initialAmount) { mutableStateOf(initialAmount ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val walletState by walletViewModel.walletState.collectAsState()
    val maxAmount = walletViewModel.formatXmr(walletState.balance.unlocked)

    var estimatedFee by remember { mutableStateOf(0L) }

    LaunchedEffect(address, amount) {
        estimatedFee = if (address.isNotBlank() && amount.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val amountLong = walletViewModel.parseXmr(amount)
                    if (amountLong > 0) {
                        walletViewModel.estimateFee(address, amountLong)
                    } else 0L
                } catch (e: Exception) {
                    0L
                }
            }
        } else 0L
    }

    val isValidInput = address.isNotBlank() && amount.isNotBlank() && errorMessage == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Send XMR",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Recipient address
            Text(
                text = "Recipient Address",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
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
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Amount
            Text(
                text = "Amount",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                        amount = it
                        errorMessage = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0.0000") },
                suffix = {
                    Text(
                        text = "XMR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    TextButton(onClick = { amount = maxAmount }) {
                        Text(
                            text = "MAX",
                            color = MoneroOrange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                supportingText = {
                    Text(
                        text = "Available: $maxAmount XMR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = errorMessage != null && amount.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )

            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transaction summary
            AnimatedVisibility(
                visible = isValidInput && estimatedFee > 0,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
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

                        Spacer(modifier = Modifier.height(12.dp))

                        SummaryRow(
                            label = "Network Fee",
                            value = "${walletViewModel.formatXmr(estimatedFee)} XMR"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Spacer(modifier = Modifier.height(12.dp))

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
            PrimaryButton(
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
                            isSending = true
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
                enabled = isValidInput && !isSending,
                color = MoneroOrange
            ) {
                Text(
                    text = if (isSending) "Sending..." else "Send XMR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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
