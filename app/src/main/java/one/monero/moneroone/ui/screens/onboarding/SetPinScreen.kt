package one.monero.moneroone.ui.screens.onboarding

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange

private const val PIN_LENGTH = 6

@Composable
fun SetPinScreen(
    walletViewModel: WalletViewModel,
    onPinSet: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) } // 0 = set, 1 = confirm
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeAnimation by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val title = if (currentStep == 0) "Set a PIN to secure your wallet" else "Confirm Your PIN"
    val subtitle = if (currentStep == 0) {
        null
    } else {
        "Enter your PIN again to confirm"
    }

    val currentPin = if (currentStep == 0) pin else confirmPin

    fun onDigitPress(digit: String) {
        if (currentPin.length < PIN_LENGTH) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (currentStep == 0) {
                pin += digit
                if (pin.length == PIN_LENGTH) {
                    currentStep = 1
                }
            } else {
                confirmPin += digit
                if (confirmPin.length == PIN_LENGTH) {
                    if (confirmPin == pin) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        walletViewModel.setPin(pin)
                        onPinSet()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        errorMessage = "PINs don't match"
                        shakeAnimation = true
                        confirmPin = ""
                    }
                }
            }
        }
    }

    fun onBackspace() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (currentStep == 0 && pin.isNotEmpty()) {
            pin = pin.dropLast(1)
        } else if (currentStep == 1 && confirmPin.isNotEmpty()) {
            confirmPin = confirmPin.dropLast(1)
        }
        errorMessage = null
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
        Spacer(modifier = Modifier.weight(0.5f))

        MoneroLogo(size = 80.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // PIN dots
        PinDots(
            enteredLength = currentPin.length,
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
    val shakeOffset by animateFloatAsState(
        targetValue = if (shake) 1f else 0f,
        animationSpec = tween(100),
        label = "shake"
    )

    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp),
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
