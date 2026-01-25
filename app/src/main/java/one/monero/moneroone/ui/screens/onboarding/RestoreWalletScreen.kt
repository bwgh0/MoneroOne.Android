package one.monero.moneroone.ui.screens.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.MoneroOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreWalletScreen(
    walletViewModel: WalletViewModel,
    onWalletRestored: () -> Unit,
    onBack: () -> Unit
) {
    var seedPhrase by remember { mutableStateOf("") }
    var restoreHeight by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val walletState by walletViewModel.walletState.collectAsState()

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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enter Your Seed Phrase",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your 12, 24, or 25 word seed phrase to restore your wallet",
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
                placeholder = { Text("Enter words separated by spaces...") },
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
                value = restoreHeight,
                onValueChange = { restoreHeight = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Restore Height (Optional)") },
                placeholder = { Text("Block height or date (YYYY-MM-DD)") },
                supportingText = { Text("Leave empty to scan from beginning (slower)") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
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
                        words.size !in listOf(12, 24, 25) -> {
                            errorMessage = "Seed phrase must be 12, 24, or 25 words (got ${words.size})"
                        }
                        else -> {
                            walletViewModel.restoreWallet(
                                seed = words,
                                restoreHeight = restoreHeight.ifBlank { null }
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
}
