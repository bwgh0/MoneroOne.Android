package one.monero.moneroone.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.PinEntryField
import one.monero.moneroone.ui.theme.ErrorRed

private enum class ChangePinStep {
    ENTER_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

@Composable
fun ChangePinScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(ChangePinStep.ENTER_CURRENT) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (step) {
        ChangePinStep.ENTER_CURRENT -> "Enter Current PIN"
        ChangePinStep.ENTER_NEW -> "Enter New PIN"
        ChangePinStep.CONFIRM_NEW -> "Confirm New PIN"
    }

    val subtitle = when (step) {
        ChangePinStep.ENTER_CURRENT -> "Enter your current PIN to continue"
        ChangePinStep.ENTER_NEW -> "Choose a new 6-digit PIN"
        ChangePinStep.CONFIRM_NEW -> "Re-enter your new PIN to confirm"
    }

    val currentValue = when (step) {
        ChangePinStep.ENTER_CURRENT -> currentPin
        ChangePinStep.ENTER_NEW -> newPin
        ChangePinStep.CONFIRM_NEW -> confirmPin
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Change PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Centered content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            PinEntryField(
                value = currentValue,
                onValueChange = { value ->
                    error = null
                    when (step) {
                        ChangePinStep.ENTER_CURRENT -> currentPin = value
                        ChangePinStep.ENTER_NEW -> newPin = value
                        ChangePinStep.CONFIRM_NEW -> confirmPin = value
                    }
                },
                isError = error != null,
                onComplete = { pin ->
                    when (step) {
                        ChangePinStep.ENTER_CURRENT -> {
                            if (!walletViewModel.verifyPinOnly(pin)) {
                                error = "Incorrect PIN"
                                currentPin = ""
                            } else {
                                step = ChangePinStep.ENTER_NEW
                                error = null
                            }
                        }
                        ChangePinStep.ENTER_NEW -> {
                            step = ChangePinStep.CONFIRM_NEW
                            error = null
                        }
                        ChangePinStep.CONFIRM_NEW -> {
                            if (pin != newPin) {
                                error = "PINs don't match"
                                confirmPin = ""
                            } else {
                                walletViewModel.changePin(currentPin, newPin)
                                onSuccess()
                            }
                        }
                    }
                }
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = error ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
