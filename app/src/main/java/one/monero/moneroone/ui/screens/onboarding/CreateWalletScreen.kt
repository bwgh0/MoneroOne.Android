package one.monero.moneroone.ui.screens.onboarding

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.util.SeedClipboard
import one.monero.moneroone.core.wallet.CreateFlowStart
import one.monero.moneroone.core.wallet.SeedType
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.AddWalletFlowEffect
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.components.OnNavEntryEnd
import one.monero.moneroone.ui.components.isRecreatingForConfigChange
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateWalletScreen(
    walletViewModel: WalletViewModel,
    flowId: String,
    onWalletCreated: () -> Unit,
    onBack: () -> Unit,
    isAddingWallet: Boolean = false
) {
    // Step and flags survive an Activity recreation; the words never enter
    // saved state. The ViewModel holds them for the life of the flow.
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var seedIssued by rememberSaveable { mutableStateOf(false) }
    var seedConfirmed by rememberSaveable { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    val pendingSeed by walletViewModel.pendingSeed.collectAsState()
    val walletState by walletViewModel.walletState.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val screenReaderActive = remember {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityManager.isTouchExplorationEnabled
    }
    var screenReaderWarningAccepted by rememberSaveable { mutableStateOf(false) }

    // Decided once per composition. A recreated screen finds its phrase in the
    // ViewModel and shows the same words. A flow whose phrase is gone (process
    // death) must never show a new phrase in its place: it leaves and starts
    // over where it began, as iOS does after a kill.
    val start = remember { walletViewModel.createFlowStart(flowId, savedIssued = seedIssued) }
    LaunchedEffect(flowId) {
        when (start) {
            CreateFlowStart.ISSUE -> {
                walletViewModel.generateNewSeed(SeedType.BIP39_24, flowId)
                seedIssued = true
            }
            CreateFlowStart.RESUME -> seedIssued = true
            CreateFlowStart.RESTART -> onBack()
            // The add below finishes the flow.
            CreateFlowStart.COMPLETED -> Unit
        }
    }

    // The add runs in the ViewModel. Its outcome, not the coroutine that
    // started it, finishes the flow, so a screen that the Activity recreates
    // during the add still moves on once the wallet exists.
    val addOutcomes by walletViewModel.addOutcomes.collectAsState()
    val addsInFlight by walletViewModel.addsInFlight.collectAsState()
    val walletAdded = addOutcomes[flowId] == true
    LaunchedEffect(walletAdded) {
        if (walletAdded) onWalletCreated()
    }

    // The lock screen and leaving the screen drop the phrase; the disposal
    // that recreates the Activity keeps it.
    val activity = LocalActivity.current
    DisposableEffect(flowId) {
        onDispose {
            if (!activity.isRecreatingForConfigChange()) walletViewModel.discardPendingSeed(flowId)
        }
    }
    // The flow's entry left the back stack for good: nothing of it may stay,
    // also when a recreation during a transition skipped the disposal above.
    OnNavEntryEnd("create-flow") {
        walletViewModel.discardPendingSeed(flowId)
        walletViewModel.clearAddOutcome(flowId)
    }

    // Suppress auto-lock while the add-wallet flow is open (iOS parity).
    if (isAddingWallet) AddWalletFlowEffect(walletViewModel)

    if (start == CreateFlowStart.RESTART || start == CreateFlowStart.COMPLETED) return

    // This flow's phrase only.
    val seed = pendingSeed?.words?.takeIf { walletViewModel.holdsPendingSeed(flowId) }.orEmpty()

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
                repeat(3) { step ->
                    Box(
                        modifier = Modifier
                            .size(if (step == currentStep) 12.dp else 8.dp)
                            .background(
                                color = if (step <= currentStep) MoneroOrange else Color.Gray.copy(alpha = 0.3f),
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
                        seed = seed,
                        onContinue = { currentStep = 1 }
                    )
                }
                1 -> SeedConfirmation(
                    seed = seed,
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
                        // addsInFlight also covers an add that the screen started
                        // before an Activity recreation.
                        isBusy = isCreating || walletState.isInitializing || flowId in addsInFlight,
                        onDone = { name, emoji ->
                            if (isCreating || walletState.isInitializing || walletAdded || flowId in addsInFlight) {
                                return@NameWalletStep
                            }
                            // The ViewModel keeps the phrase until the add succeeds; it is
                            // gone only when something else ended the flow: start over.
                            if (seed.isEmpty()) {
                                onBack()
                                return@NameWalletStep
                            }
                            isCreating = true
                            scope.launch {
                                try {
                                    walletViewModel.createWallet(seed, SeedType.BIP39_24, flowId, name, emoji)
                                } finally {
                                    isCreating = false
                                }
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

        Spacer(modifier = Modifier.height(16.dp))

        // Copy seed button
        val context = LocalContext.current
        var copied by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(
            onClick = {
                SeedClipboard.copy(context, seed.joinToString(" "))
                copied = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MoneroOrange),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MoneroOrange)
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (copied) "Copied!" else "Copy to Clipboard",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Button(
            onClick = onReveal,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MoneroOrange,
                contentColor = Color.White
            )
        ) {
            Text("Show Seed Phrase", style = MaterialTheme.typography.titleMedium)
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
    var confirmChecked by rememberSaveable { mutableStateOf(false) }

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
