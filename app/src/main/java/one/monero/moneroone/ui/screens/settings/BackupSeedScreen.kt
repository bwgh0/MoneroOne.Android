package one.monero.moneroone.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange

private const val PIN_LENGTH = 6
private const val CLIPBOARD_CLEAR_DELAY_MS = 5 * 60 * 1000L // 5 minutes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSeedScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var shakeAnimation by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }
    var seedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Clear clipboard after delay if seed was copied
    DisposableEffect(copiedToClipboard) {
        if (copiedToClipboard) {
            val job = scope.launch {
                delay(CLIPBOARD_CLEAR_DELAY_MS)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            onDispose { job.cancel() }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(shakeAnimation) {
        if (shakeAnimation) {
            delay(500)
            shakeAnimation = false
        }
    }

    fun onDigitPress(digit: String) {
        if (pin.length < PIN_LENGTH) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pinError = null
            pin += digit
            if (pin.length == PIN_LENGTH) {
                val verified = walletViewModel.verifyPinOnly(pin)
                if (verified) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isUnlocked = true
                    seedWords = walletViewModel.getSeedPhrase() ?: emptyList()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pinError = "Invalid PIN"
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
            pinError = null
        }
    }

    if (!isUnlocked) {
        // Full-screen PIN entry with custom number pad
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
                text = "Enter PIN to View Seed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your PIN is required to access your recovery phrase",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN dots
            PinDotsBackup(
                enteredLength = pin.length,
                totalLength = PIN_LENGTH,
                shake = shakeAnimation
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            AnimatedVisibility(
                visible = pinError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = pinError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Number pad
            NumberPadBackup(
                onDigitPress = ::onDigitPress,
                onBackspace = ::onBackspace
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    } else {
        // Seed display screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
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
                    text = "Backup Seed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Warning banner
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Keep Your Seed Safe",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Never share your recovery phrase. Anyone with these words can access your funds. Store securely offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seed words display
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Recovery Phrase",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seedWords.forEachIndexed { index, word ->
                            SeedWordChip(index = index + 1, word = word)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copy button
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Seed Phrase", seedWords.joinToString(" "))
                    clipboard.setPrimaryClip(clip)
                    copiedToClipboard = true
                    Toast.makeText(context, "Copied! Will clear in 5 minutes", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (copiedToClipboard) "Copied!" else "Copy Seed Phrase",
                    fontWeight = FontWeight.Medium
                )
            }

            if (copiedToClipboard) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Clipboard will auto-clear in 5 minutes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PinDotsBackup(
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
private fun NumberPadBackup(
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

@Composable
private fun SeedWordChip(index: Int, word: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.bodySmall,
                color = MoneroOrange
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
