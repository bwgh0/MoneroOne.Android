package one.monero.moneroone.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.MoneroOrange
import io.horizontalsystems.monerokit.util.RestoreHeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreWalletScreen(
    walletViewModel: WalletViewModel,
    onWalletRestored: () -> Unit,
    onBack: () -> Unit
) {
    var seedPhrase by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val walletState by walletViewModel.walletState.collectAsState()

    val dateFormatter = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restore Wallet") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter your seed phrase",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "24 words (BIP39) or 25 words (Monero legacy)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Seed phrase input
            OutlinedTextField(
                value = seedPhrase,
                onValueChange = {
                    seedPhrase = it.lowercase()
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = { Text("Seed Phrase") },
                placeholder = { Text("Separate words with spaces") },
                supportingText = {
                    val wordCount = seedPhrase.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                    Text("$wordCount words")
                },
                isError = errorMessage != null,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Restore height input
            OutlinedTextField(
                value = selectedDate?.let { dateFormatter.format(Date(it)) } ?: "",
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                label = { Text("Wallet Birthday (Optional)") },
                placeholder = { Text("Select date when wallet was created") },
                supportingText = { Text("Leave empty to scan from beginning (slower)") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select date",
                            tint = MoneroOrange
                        )
                    }
                },
                readOnly = true,
                enabled = false,
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

            if (walletState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = walletState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val words = seedPhrase.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    when {
                        words.isEmpty() -> {
                            errorMessage = "Please enter your seed phrase"
                        }
                        words.size !in listOf(24, 25) -> {
                            errorMessage = "Seed phrase must be 24 or 25 words"
                        }
                        else -> {
                            // Convert selected date to restore height
                            val restoreHeightValue = selectedDate?.let { dateToRestoreHeight(it) }?.toString()
                            walletViewModel.restoreWallet(
                                seed = words,
                                restoreHeight = restoreHeightValue,
                                restoreDateMillis = selectedDate
                            )
                            onWalletRestored()
                        }
                    }
                },
                enabled = !walletState.isInitializing && seedPhrase.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White,
                    disabledContainerColor = MoneroOrange.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = if (walletState.isInitializing) "Restoring..." else "Restore Wallet",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = MoneroOrange)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedDate = null
                        showDatePicker = false
                    }
                ) {
                    Text("Clear")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun dateToRestoreHeight(dateMillis: Long): Long {
    return RestoreHeight.getInstance().getHeight(Date(dateMillis))
}
