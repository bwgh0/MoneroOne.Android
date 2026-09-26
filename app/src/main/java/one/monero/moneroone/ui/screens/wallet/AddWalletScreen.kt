package one.monero.moneroone.ui.screens.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ReplayCircleFilled
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import one.monero.moneroone.ui.components.MoneroHeroLogo
import one.monero.moneroone.ui.components.PrimaryButton

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
                title = { Text("Add Wallet", style = MaterialTheme.typography.titleMedium) },
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

            MoneroHeroLogo(size = 120.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Add a Wallet",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create a fresh wallet or restore one from its seed phrase. It will use your existing PIN.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            // iOS Add Wallet: every action is a glass button with a brand label.
            PrimaryButton(
                onClick = onCreateWallet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Create New Wallet")
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(
                onClick = onRestoreWallet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ReplayCircleFilled, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Restore Wallet")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
