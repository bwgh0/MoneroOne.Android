package one.monero.moneroone.ui.screens.settings

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.util.SeedClipboard
import one.monero.moneroone.core.wallet.SeedType
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.KeypadKey
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.GlassSegmentedPicker
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.pinLockoutMessage
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoFamily
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme

private const val PIN_LENGTH = 6

/** iOS WalletError.walletMismatch wording. */
private const val WALLET_CHANGED_MESSAGE = "Active wallet changed — please retry"
private const val NO_SEED_MESSAGE = "No seed phrase found for this wallet"

/** iOS WalletError.seedMismatch wording. */
private const val SEED_MISMATCH_MESSAGE = "Seed phrase doesn't match current wallet"

/** What the screen may show once the PIN is verified. */
private sealed interface SeedReveal {
    class Show(val words: List<String>, val type: SeedType, val electrumWords: List<String>?) : SeedReveal
    class Refuse(val message: String) : SeedReveal
}

/**
 * The one place that decides what this screen reveals: only the phrase of the
 * wallet it was opened for. Put any further reveal check here. Reads encrypted
 * storage and converts BIP39 to the legacy words: call it off Main.
 */
private fun seedRevealFor(walletViewModel: WalletViewModel, boundWalletId: String?): SeedReveal {
    val walletId = boundWalletId ?: return SeedReveal.Refuse(NO_SEED_MESSAGE)
    val words = walletViewModel.getSeedPhrase(walletId)
    val type = walletViewModel.getSeedType(walletId)
    val electrumWords = if (type == SeedType.BIP39_24) walletViewModel.getElectrumSeedPhrase(walletId) else null
    // Checked after the reads, so a switch while they ran is reported as one.
    if (walletViewModel.activeWallet.value?.id != walletId) return SeedReveal.Refuse(WALLET_CHANGED_MESSAGE)
    if (words.isNullOrEmpty() || type == null) return SeedReveal.Refuse(NO_SEED_MESSAGE)
    // The open wallet file does not derive from these words: a backup of them
    // would not back up the wallet's funds (iOS seedMismatch).
    if (!walletViewModel.seedMatchesWalletFile(walletId)) return SeedReveal.Refuse(SEED_MISMATCH_MESSAGE)
    return SeedReveal.Show(words, type, electrumWords)
}

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
    var electrumSeedWords by remember { mutableStateOf<List<String>?>(null) }
    var seedType by remember { mutableStateOf<SeedType?>(null) }
    var showElectrum by remember { mutableStateOf(false) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Bind this screen to the wallet that was active when it opened. If the
    // active wallet changes mid-view (switch/delete), close immediately so we
    // can never show another wallet's seed (iOS a31683d).
    val activeWallet by walletViewModel.activeWallet.collectAsState()
    val boundWalletId = remember { activeWallet?.id }
    // Two paths can close the screen at once (the switch above, a refused reveal): pop once.
    var closed by remember { mutableStateOf(false) }
    fun close() {
        if (closed) return
        closed = true
        onBack()
    }
    LaunchedEffect(activeWallet?.id) {
        if (activeWallet?.id != boundWalletId) {
            close()
        }
    }

    val screenReaderActive = remember {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        accessibilityManager.isTouchExplorationEnabled
    }
    var screenReaderWarningAccepted by remember { mutableStateOf(false) }

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
                val entered = pin
                scope.launch {
                    // Rate-limited with the unlock screen: on Android this PIN
                    // is the only barrier in front of the seed.
                    val verified = walletViewModel.verifyPinForAction(entered)
                    if (verified) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val reveal = withContext(Dispatchers.Default) {
                            seedRevealFor(walletViewModel, boundWalletId)
                        }
                        when (reveal) {
                            is SeedReveal.Show -> {
                                seedWords = reveal.words
                                seedType = reveal.type
                                electrumSeedWords = reveal.electrumWords
                                isUnlocked = true
                            }
                            is SeedReveal.Refuse -> {
                                Toast.makeText(context, reveal.message, Toast.LENGTH_LONG).show()
                                close()
                            }
                        }
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val lockedFor = walletViewModel.getRemainingLockoutMs()
                        pinError = if (lockedFor > 0) pinLockoutMessage(lockedFor) else "Invalid PIN"
                        shakeAnimation = true
                        pin = ""
                    }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    } else if (screenReaderActive && !screenReaderWarningAccepted) {
        // Screen readers speak visible text, so the seed must not render until the user opts in
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
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                            text = "Screen Reader Is On",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your screen reader will read your recovery phrase out loud. Anyone who hears it can steal your funds. Use headphones or make sure nobody can hear your device before continuing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                onClick = { screenReaderWarningAccepted = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Show Recovery Phrase")
            }

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
                    style = MaterialTheme.typography.headlineSmall
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Format selector for BIP39 wallets
            if (seedType == SeedType.BIP39_24 && electrumSeedWords != null) {
                GlassSegmentedPicker(
                    options = listOf(false, true),
                    selectedOption = showElectrum,
                    onOptionSelected = { electrum ->
                        showElectrum = electrum
                        copiedToClipboard = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    labelSelector = { electrum -> if (electrum) "25 Words (Legacy)" else "24 Words (BIP39)" }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            val displayWords = if (showElectrum && electrumSeedWords != null) electrumSeedWords!! else seedWords
            val wordCount = displayWords.size
            val formatLabel = if (showElectrum) "Legacy (Electrum)" else if (seedType == SeedType.BIP39_24) "BIP39" else "Legacy (Electrum)"

            // Seed words display (iOS: fill container, radius 16, elevated cells)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                shadow = false,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recovery Phrase",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "$wordCount words · $formatLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayWords.forEachIndexed { index, word ->
                            SeedWordChip(index = index + 1, word = word)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copy button
            PrimaryButton(
                onClick = {
                    SeedClipboard.copy(context, displayWords.joinToString(" "))
                    copiedToClipboard = true
                    Toast.makeText(
                        context,
                        "Copied! Will clear in ${SeedClipboard.LIFETIME_SECONDS} seconds",
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = if (copiedToClipboard) "Copied!" else "Copy Seed Phrase")
            }

            if (copiedToClipboard) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Clipboard will auto-clear in ${SeedClipboard.LIFETIME_SECONDS} seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (isFilled) MoneroOrange else MoneroTheme.colors.gray.copy(alpha = 0.3f)
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
                            KeypadKey(onPress = onBackspace) { onClick ->
                                IconButton(
                                    onClick = onClick,
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
                        }
                        else -> {
                            KeypadKey(onPress = { onDigitPress(button) }) { onClick ->
                                GlassButton(
                                    onClick = onClick,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = button,
                                            style = MaterialTheme.typography.headlineMedium,
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
}

@Composable
private fun SeedWordChip(index: Int, word: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
