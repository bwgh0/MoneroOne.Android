package one.monero.moneroone.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.SeedType
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateWalletScreen(
    walletViewModel: WalletViewModel,
    onWalletCreated: () -> Unit,
    onBack: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var generatedSeed by remember { mutableStateOf<List<String>>(emptyList()) }
    var seedConfirmed by remember { mutableStateOf(false) }

    // Generate seed immediately on screen entry
    LaunchedEffect(Unit) {
        if (generatedSeed.isEmpty()) {
            generatedSeed = walletViewModel.generateNewSeed(SeedType.BIP39_24)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) currentStep-- else onBack()
                    }) {
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
            // Progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { step ->
                    Box(
                        modifier = Modifier
                            .size(if (step == currentStep) 12.dp else 8.dp)
                            .background(
                                color = if (step <= currentStep) MoneroOrange else Color.Gray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    if (step < 1) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            when (currentStep) {
                0 -> SeedDisplay(
                    seed = generatedSeed,
                    onContinue = { currentStep = 1 }
                )
                1 -> SeedConfirmation(
                    seed = generatedSeed,
                    onConfirmed = {
                        seedConfirmed = true
                        walletViewModel.createWallet(generatedSeed, SeedType.BIP39_24)
                        onWalletCreated()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedDisplay(
    seed: List<String>,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MoneroLogo(size = 80.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Write down your seed phrase",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = WarningYellow.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningYellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "This is the ONLY way to recover your wallet. Store it safely offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Seed words grid
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                seed.forEachIndexed { index, word ->
                    SeedWordChip(number = index + 1, word = word)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MoneroOrange,
                contentColor = Color.White
            )
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SeedWordChip(
    number: Int,
    word: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$number.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SeedConfirmation(
    seed: List<String>,
    onConfirmed: () -> Unit
) {
    var confirmChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Confirm Backup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Please confirm that you have safely stored your seed phrase.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { confirmChecked = !confirmChecked }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (confirmChecked) MoneroOrange else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .then(
                            if (!confirmChecked) {
                                Modifier.background(
                                    color = Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (confirmChecked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "I have written down and securely stored my ${seed.size}-word seed phrase",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirmed,
            enabled = confirmChecked,
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
            Text("Create Wallet", style = MaterialTheme.typography.titleMedium)
        }
    }
}
