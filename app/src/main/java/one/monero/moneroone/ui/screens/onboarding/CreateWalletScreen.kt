package one.monero.moneroone.ui.screens.onboarding

import android.content.Context
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.SeedType
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.TintedButton
import one.monero.moneroone.ui.theme.MonoFamily
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateWalletScreen(
    walletViewModel: WalletViewModel,
    onWalletCreated: () -> Unit,
    onBack: () -> Unit,
    isAddingWallet: Boolean = false
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var generatedSeed by remember { mutableStateOf<List<String>>(emptyList()) }
    var seedConfirmed by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    val walletState by walletViewModel.walletState.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val screenReaderActive = remember {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityManager.isTouchExplorationEnabled
    }
    var screenReaderWarningAccepted by remember { mutableStateOf(false) }

    // Generate seed immediately on screen entry
    LaunchedEffect(Unit) {
        if (generatedSeed.isEmpty()) {
            generatedSeed = walletViewModel.generateNewSeed(SeedType.BIP39_24)
        }
    }

    // Suppress auto-lock while the add-wallet flow is open (iOS parity).
    if (isAddingWallet) {
        DisposableEffect(Unit) {
            walletViewModel.setAddWalletFlowActive(true)
            onDispose { walletViewModel.setAddWalletFlowActive(false) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet", style = MaterialTheme.typography.titleMedium) },
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
                repeat(3) { step ->
                    Box(
                        modifier = Modifier
                            .size(if (step == currentStep) 12.dp else 8.dp)
                            .background(
                                color = if (step <= currentStep) MoneroOrange else MoneroTheme.colors.gray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    if (step < 2) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            when (currentStep) {
                // Screen readers speak visible text, so the seed must not render until the user opts in
                0 -> if (screenReaderActive && !screenReaderWarningAccepted) {
                    ScreenReaderSeedWarning(
                        onReveal = { screenReaderWarningAccepted = true }
                    )
                } else {
                    SeedDisplay(
                        seed = generatedSeed,
                        onContinue = { currentStep = 1 }
                    )
                }
                1 -> SeedConfirmation(
                    seed = generatedSeed,
                    onConfirmed = {
                        seedConfirmed = true
                        currentStep = 2
                    }
                )
                2 -> {
                    // Naming at the END, prefilled with the next default name.
                    NameWalletStep(
                        defaultName = walletViewModel.nextWalletName(),
                        buttonLabel = "Create Wallet",
                        isBusy = isCreating,
                        onDone = { name, emoji ->
                            if (isCreating) return@NameWalletStep
                            isCreating = true
                            scope.launch {
                                val ok = walletViewModel.createWallet(
                                    generatedSeed, SeedType.BIP39_24, name, emoji
                                )
                                isCreating = false
                                if (ok) onWalletCreated()
                            }
                        }
                    )
                    if (walletState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = walletState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Room under the last button so its capsule and shadow clear the edge.
            Spacer(modifier = Modifier.height(24.dp))
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

        // Seed words grid (iOS: fill container, radius 16, elevated cells)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            shadow = false,
            color = MaterialTheme.colorScheme.surfaceVariant
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

        Spacer(modifier = Modifier.height(16.dp))

        // Copy seed button
        val clipboardManager = LocalClipboardManager.current
        var copied by remember { mutableStateOf(false) }
        TintedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(seed.joinToString(" ")))
                copied = true
            }
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(if (copied) "Copied!" else "Copy to Clipboard")
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ScreenReaderSeedWarning(
    onReveal: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MoneroLogo(size = 80.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Screen reader is on",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
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
                    text = "Your screen reader will read your seed phrase out loud. Anyone who hears it can steal your funds. Use headphones or make sure nobody can hear your device before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            onClick = onReveal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Seed Phrase")
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MonoFamily,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MoneroTheme.colors.gray.copy(alpha = 0.2f),
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

        PrimaryButton(
            onClick = onConfirmed,
            enabled = confirmChecked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Wallet")
        }
    }
}
