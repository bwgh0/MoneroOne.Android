package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.core.locale.tr
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
import one.monero.moneroone.ui.components.AnimatedWalletIcon
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
                // iOS: no bar title; the hero carries the one title.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // iOS AddWalletView: one column 24 apart, the copy inset 32, the
        // buttons 16 from the edges and 32 above the bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedWalletIcon(size = 120.dp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr("Add Wallet"),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr("Create a new wallet or restore an existing one from your seed phrase."),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // iOS Add Wallet: every action is a glass button with a brand label.
            PrimaryButton(
                onClick = onCreateWallet,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(tr("Create New Wallet"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(
                onClick = onRestoreWallet,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.ReplayCircleFilled, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(tr("Restore Wallet"))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
