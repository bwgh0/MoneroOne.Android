package one.monero.moneroone.ui.screens.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange

private const val PIN_LENGTH = 6

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
    var shakeAnimation by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

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

    fun onDigitPress(digit: String) {
        if (currentValue.length < PIN_LENGTH) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            error = null
            when (step) {
                ChangePinStep.ENTER_CURRENT -> {
                    currentPin += digit
                    if (currentPin.length == PIN_LENGTH) {
                        if (!walletViewModel.verifyPinOnly(currentPin)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            error = "Incorrect PIN"
                            shakeAnimation = true
                            currentPin = ""
                        } else {
                            step = ChangePinStep.ENTER_NEW
                        }
                    }
                }
                ChangePinStep.ENTER_NEW -> {
                    newPin += digit
                    if (newPin.length == PIN_LENGTH) {
                        step = ChangePinStep.CONFIRM_NEW
                    }
                }
                ChangePinStep.CONFIRM_NEW -> {
                    confirmPin += digit
                    if (confirmPin.length == PIN_LENGTH) {
                        if (confirmPin != newPin) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            error = "PINs don't match"
                            shakeAnimation = true
                            confirmPin = ""
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            walletViewModel.changePin(currentPin, newPin)
                            onSuccess()
                        }
                    }
                }
            }
        }
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        error = null
        when (step) {
            ChangePinStep.ENTER_CURRENT -> if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
            ChangePinStep.ENTER_NEW -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
            ChangePinStep.CONFIRM_NEW -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
        }
    }

    LaunchedEffect(shakeAnimation) {
        if (shakeAnimation) {
            delay(500)
            shakeAnimation = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button row
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
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // PIN dots
        PinDots(
            enteredLength = currentValue.length,
            totalLength = PIN_LENGTH,
            shake = shakeAnimation
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Number pad
        NumberPad(
            onDigitPress = ::onDigitPress,
            onBackspace = ::onBackspace
        )

        Spacer(modifier = Modifier.height(32.dp))
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
    onBackspace: () -> Unit
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back")
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
                            GlassButton(
                                onClick = { onDigitPress(button) },
                                modifier = Modifier.size(80.dp),
                                cornerRadius = 40.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = button,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
