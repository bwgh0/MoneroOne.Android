package one.monero.moneroone.ui.screens.keystone

import androidx.activity.compose.BackHandler
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
import one.monero.moneroone.ui.components.PrimaryButton

private enum class KeystoneKeyImageStage {
    PREPARING,
    SHOW_OUTPUTS,
    SCAN_KEY_IMAGES,
    IMPORTING,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoneKeyImageSyncScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(KeystoneKeyImageStage.PREPARING) }
    var outputs by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            outputs = walletViewModel.keystoneExportOutputs()
            stage = KeystoneKeyImageStage.SHOW_OUTPUTS
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
    }

    BackHandler(enabled = stage != KeystoneKeyImageStage.IMPORTING) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Keystone Spend Status") },
                navigationIcon = {
                    if (stage != KeystoneKeyImageStage.IMPORTING) {
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
            when (stage) {
                KeystoneKeyImageStage.PREPARING,
                KeystoneKeyImageStage.IMPORTING -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (stage == KeystoneKeyImageStage.PREPARING) {
                            "Preparing wallet outputs..."
                        } else {
                            "Importing key images and updating spent outputs..."
                        },
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.weight(1f))
                }

                KeystoneKeyImageStage.SHOW_OUTPUTS -> {
                    Text(
                        "1. Scan outputs with Keystone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open Keystone's Monero / Feather offline signing flow and scan this QR. This does not send a transaction.",
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
                        onClick = {
                            error = null
                            stage = KeystoneKeyImageStage.SCAN_KEY_IMAGES
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Scan Key Images")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This is the hardware-wallet equivalent of Feather's key-image sync. It lets the watch-only wallet learn which outputs are spent without exposing the spend key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                KeystoneKeyImageStage.SCAN_KEY_IMAGES -> {
                    Text(
                        "2. Scan the key-image QR from Keystone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Keep Keystone on its key-image QR until Monero One finishes scanning it.",
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
                            expectedType = KeystoneUrTypes.KEY_IMAGE,
                            onDecoded = { payload ->
                                if (payload !is KeystoneScanPayload.Ur) return@KeystoneQrScanner
                                stage = KeystoneKeyImageStage.IMPORTING
                                scope.launch {
                                    try {
                                        walletViewModel.keystoneImportKeyImages(payload.bytes)
                                        error = null
                                        stage = KeystoneKeyImageStage.SUCCESS
                                    } catch (t: Throwable) {
                                        error = t.message ?: t.javaClass.simpleName
                                        stage = KeystoneKeyImageStage.SHOW_OUTPUTS
                                    }
                                }
                            },
                            onError = { error = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                KeystoneKeyImageStage.SUCCESS -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Spend status updated",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Monero One imported Keystone's key images and refreshed the wallet balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
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
