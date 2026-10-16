package one.monero.moneroone.ui.screens.keystone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.KeystoneUrTypes
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.truncateMiddle

private enum class KeystoneSignStage {
    PREPARE_OUTPUTS,
    SHOW_OUTPUTS,
    SCAN_KEY_IMAGES,
    PREPARE_UNSIGNED,
    SHOW_UNSIGNED,
    SCAN_SIGNED,
    READY_TO_BROADCAST,
    BROADCASTING,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoneSignScreen(
    walletViewModel: WalletViewModel,
    address: String,
    amountAtomic: Long,
    sweepAll: Boolean,
    onBack: () -> Unit,
    onSent: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(KeystoneSignStage.PREPARE_OUTPUTS) }
    var outputs by remember { mutableStateOf<ByteArray?>(null) }
    var unsignedTx by remember { mutableStateOf<ByteArray?>(null) }
    var signedTx by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fail(t: Throwable) {
        error = t.message ?: t.javaClass.simpleName
    }

    LaunchedEffect(Unit) {
        try {
            outputs = walletViewModel.keystoneExportOutputs()
            stage = KeystoneSignStage.SHOW_OUTPUTS
        } catch (t: Throwable) {
            fail(t)
        }
    }

    BackHandler(enabled = stage != KeystoneSignStage.BROADCASTING) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign with Keystone") },
                navigationIcon = {
                    if (stage != KeystoneSignStage.BROADCASTING) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (sweepAll) "Send all XMR" else walletViewModel.formatXmr(amountAtomic) + " XMR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        truncateMiddle(address),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Verify the destination, amount, and fee on Keystone before approving. Monero One never receives the private spend key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when (stage) {
                KeystoneSignStage.PREPARE_OUTPUTS,
                KeystoneSignStage.PREPARE_UNSIGNED,
                KeystoneSignStage.BROADCASTING -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (stage) {
                            KeystoneSignStage.PREPARE_OUTPUTS -> "Preparing wallet outputs..."
                            KeystoneSignStage.PREPARE_UNSIGNED -> "Creating unsigned transaction..."
                            else -> "Broadcasting signed transaction..."
                        }
                    )
                    Spacer(Modifier.weight(1f))
                }

                KeystoneSignStage.SHOW_OUTPUTS -> {
                    Text(
                        "1. Scan outputs with Keystone",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On Keystone open the Monero / Feather offline signing flow and scan this animated QR.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    AnimatedKeystoneUr(
                        type = KeystoneUrTypes.OUTPUT,
                        data = checkNotNull(outputs),
                        modifier = Modifier.size(320.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        onClick = { error = null; stage = KeystoneSignStage.SCAN_KEY_IMAGES },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Scan Key Images")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Key-image import lets the watch-only wallet know which outputs were already spent. Monero wallet2 requires the selected node to be trusted for this import step, so prefer your own or a node you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                KeystoneSignStage.SCAN_KEY_IMAGES -> {
                    Text(
                        "Scan the key-image QR shown by Keystone",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        KeystoneQrScanner(
                            expectedType = KeystoneUrTypes.KEY_IMAGE,
                            onDecoded = { payload ->
                                if (payload !is KeystoneScanPayload.Ur) return@KeystoneQrScanner
                                stage = KeystoneSignStage.PREPARE_UNSIGNED
                                scope.launch {
                                    try {
                                        walletViewModel.keystoneImportKeyImages(payload.bytes)
                                        unsignedTx = walletViewModel.keystoneCreateUnsignedTransaction(
                                            address = address,
                                            amount = amountAtomic,
                                            sweepAll = sweepAll
                                        )
                                        stage = KeystoneSignStage.SHOW_UNSIGNED
                                    } catch (t: Throwable) {
                                        fail(t)
                                        stage = KeystoneSignStage.SHOW_OUTPUTS
                                    }
                                }
                            },
                            onError = { error = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                KeystoneSignStage.SHOW_UNSIGNED -> {
                    Text(
                        "2. Sign transaction on Keystone",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan this unsigned transaction with Keystone, verify the address, amount, and fee, then sign. Keystone will show a signed-transaction QR above its Done button.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    AnimatedKeystoneUr(
                        type = KeystoneUrTypes.TX_UNSIGNED,
                        data = checkNotNull(unsignedTx),
                        modifier = Modifier.size(320.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        onClick = { error = null; stage = KeystoneSignStage.SCAN_SIGNED },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Scan Keystone Signed QR")
                    }
                }

                KeystoneSignStage.SCAN_SIGNED -> {
                    Text(
                        "3. Scan the signed QR from Keystone",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Keystone's screen with the Done button contains the signed transaction QR. Keep that screen open and do not press Done until Monero One confirms the QR was received.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        KeystoneQrScanner(
                            expectedType = KeystoneUrTypes.TX_SIGNED,
                            onDecoded = { payload ->
                                if (payload !is KeystoneScanPayload.Ur) return@KeystoneQrScanner
                                signedTx = payload.bytes
                                error = null
                                stage = KeystoneSignStage.READY_TO_BROADCAST
                            },
                            onError = { error = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                KeystoneSignStage.READY_TO_BROADCAST -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Signed transaction received",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The signed QR is now on this phone. You can press Done on Keystone. Review the destination and amount above, then broadcast from Monero One.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(
                        onClick = {
                            val payload = signedTx
                            if (payload == null) {
                                error = "Signed transaction is missing. Scan it again."
                            } else {
                                error = null
                                stage = KeystoneSignStage.BROADCASTING
                                scope.launch {
                                    try {
                                        check(walletViewModel.keystoneSubmitSignedTransaction(payload)) {
                                            "Monero wallet rejected the signed transaction"
                                        }
                                        stage = KeystoneSignStage.SUCCESS
                                    } catch (t: Throwable) {
                                        fail(t)
                                        stage = KeystoneSignStage.READY_TO_BROADCAST
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Broadcast / Send XMR")
                    }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            signedTx = null
                            error = null
                            stage = KeystoneSignStage.SCAN_SIGNED
                        }
                    ) {
                        Text("Scan again")
                    }
                    Spacer(Modifier.weight(1f))
                }

                KeystoneSignStage.SUCCESS -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Transaction broadcast",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(onClick = onSent, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
