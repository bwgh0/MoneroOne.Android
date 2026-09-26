package one.monero.moneroone.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.ErrorRed

/**
 * Re-authentication gate for irreversible actions (currently: broadcasting a
 * transaction). Prefers a strong biometric when the user has enabled it, and
 * always offers the wallet PIN as the fallback.
 *
 * The unlock rate limiter is deliberately shared: [WalletViewModel.verifyPin]
 * enforces lockout, so repeated wrong PINs here back off exactly as they do on
 * the unlock screen.
 */
@Composable
fun AuthGateDialog(
    walletViewModel: WalletViewModel,
    title: String,
    subtitle: String,
    onAuthenticated: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricAttempted by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        val manager = BiometricManager.from(context)
        val supported = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val enabled = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .getBoolean("biometrics_enabled", false)
        supported && enabled
    }

    fun promptBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }
                // Errors and failures fall through to the PIN field rather than
                // cancelling: the user must still be able to send if the sensor fails.
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use PIN")
                // Must match the class checked in biometricAvailable — the default
                // otherwise admits Class 2 sensors we did not vet.
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    LaunchedEffect(Unit) {
        if (biometricAvailable && !biometricAttempted) {
            biometricAttempted = true
            promptBiometric()
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                PinEntryField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    isError = error != null,
                    onComplete = { entered ->
                        scope.launch {
                            if (walletViewModel.verifyPinForAction(entered)) {
                                onAuthenticated()
                            } else {
                                val lockedFor = walletViewModel.getRemainingLockoutMs()
                                error = if (lockedFor > 0) {
                                    "Too many attempts. Try again in ${(lockedFor + 999) / 1000}s"
                                } else {
                                    "Incorrect PIN"
                                }
                                pin = ""
                            }
                        }
                    }
                )
                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (biometricAvailable) {
                TextButton(onClick = { promptBiometric() }) { Text("Use biometrics") }
            }
        },
        dismissButton = {
            DismissTextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
