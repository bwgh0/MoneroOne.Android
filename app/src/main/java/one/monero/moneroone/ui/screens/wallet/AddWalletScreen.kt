package one.monero.moneroone.ui.screens.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.AddWalletFlowEffect
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.theme.MoneroOrange

/**
 * Entry of the add-wallet flow (2nd+ wallet): create or restore. PIN setup is
 * SKIPPED — the app already has one. Auto-lock is suppressed while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    walletViewModel: WalletViewModel,
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
    onBack: () -> Unit
) {
    AddWalletFlowEffect(walletViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Wallet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            MoneroLogo(size = 120.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Add a Wallet",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create a fresh wallet or restore one from its seed phrase. It will use your existing PIN.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onCreateWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White
                )
            ) {
                Text("Create New Wallet", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRestoreWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MoneroOrange)
            ) {
                Text("Restore Wallet", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
