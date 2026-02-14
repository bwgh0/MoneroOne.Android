package one.monero.moneroone.ui.screens.unlock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import one.monero.moneroone.R
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange

private const val PIN_LENGTH = 6

@Composable
fun UnlockScreen(
    walletViewModel: WalletViewModel,
    onUnlocked: () -> Unit,
    onResetWallet: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeAnimation by remember { mutableStateOf(false) }
    var attempts by remember { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val biometricAvailable = remember {
        val biometricManager = BiometricManager.from(context)
        val deviceSupports = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val userEnabled = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .getBoolean("biometrics_enabled", false)
        deviceSupports && userEnabled
    }

    fun onDigitPress(digit: String) {
        if (pin.length < PIN_LENGTH) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pin += digit
            errorMessage = null

            if (pin.length == PIN_LENGTH) {
                if (walletViewModel.verifyPin(pin)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlocked()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    attempts++
                    errorMessage = "Incorrect PIN"
                    shakeAnimation = true
                    pin = ""
                }
            }
        }
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pin = pin.dropLast(1)
            errorMessage = null
        }
    }

    fun showBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // For biometric unlock, we trust the OS authentication
                    walletViewModel.unlockWithBiometrics()
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User can fall back to PIN
                }

                override fun onAuthenticationFailed() {
                    // User can try again or use PIN
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MoneroOne")
            .setSubtitle("Use biometrics to unlock your wallet")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(shakeAnimation) {
        if (shakeAnimation) {
            delay(500)
            shakeAnimation = false
        }
    }

    // Try biometric on first load if available (with delay to ensure UI is ready)
    LaunchedEffect(Unit) {
        if (biometricAvailable) {
            delay(300) // Small delay for UI to be fully ready
            showBiometricPrompt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.5f))

        // Monero logo
        MoneroLogo(size = 80.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Monero One",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // PIN dots
        PinDots(
            enteredLength = pin.length,
            totalLength = PIN_LENGTH,
            shake = shakeAnimation
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Number pad
        NumberPad(
            onDigitPress = ::onDigitPress,
            onBackspace = ::onBackspace,
            onBiometric = if (biometricAvailable) ::showBiometricPrompt else null
        )

        TextButton(onClick = { showResetDialog = true }) {
            Text(
                text = "Forgot PIN?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Remove Wallet from Device?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This removes wallet data from this device only. " +
                        "Your wallet still exists on the blockchain and can be recovered with your seed phrase.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetWallet()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PinDots(
    enteredLength: Int,
    totalLength: Int,
    shake: Boolean
) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(totalLength) { index ->
            val isFilled = index < enteredLength
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.2f else 1f,
                animationSpec = tween(100),
                label = "scale$index"
            )

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) MoneroOrange else Color.Gray.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun NumberPad(
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (onBiometric != null) "bio" else "", "0", "back")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { button ->
                    when (button) {
                        "" -> Spacer(modifier = Modifier.size(80.dp))
                        "bio" -> {
                            IconButton(
                                onClick = { onBiometric?.invoke() },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric unlock",
                                    modifier = Modifier.size(32.dp),
                                    tint = MoneroOrange
                                )
                            }
                        }
                        "back" -> {
                            IconButton(
                                onClick = onBackspace,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        else -> {
                            NumberButton(
                                digit = button,
                                onClick = { onDigitPress(button) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    digit: String,
    onClick: () -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        cornerRadius = 40.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
