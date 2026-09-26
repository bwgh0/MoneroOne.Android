package one.monero.moneroone.ui.screens.send

import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.horizontalsystems.monerokit.MoneroKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.core.wallet.SendState
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.AuthGateDialog
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ShrinkToFitText
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.TabularFigures
import one.monero.moneroone.ui.theme.WarningYellow

private enum class SendPhase { ADDRESS, AMOUNT, REVIEW, SENDING, SUCCESS, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    walletViewModel: WalletViewModel,
    initialAddress: String? = null,
    initialAmount: String? = null,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onSent: () -> Unit
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val sendState by walletViewModel.sendState.collectAsState()

    // Pre-fill arrives via QR / deep link and is untrusted: it must pass the
    // same checks as manual entry or be dropped, so the flow can never skip
    // the phase where the dropped value would have been entered.
    val prefilledAddress = remember {
        initialAddress?.trim()?.takeIf { isValidMoneroAddress(it) }
    }
    val prefilledAmount = remember {
        initialAmount?.trim()?.takeIf { walletViewModel.parseXmr(it) > 0 }
    }
    // Only skip past the amount step once the balance is actually known to cover
    // it; an unloaded balance reads as 0, which must not silently drop a legitimate
    // amount nor let an over-balance one reach review unchecked.
    val prefillCoveredByBalance = remember {
        val parsed = prefilledAmount?.let { walletViewModel.parseXmr(it) } ?: 0L
        parsed > 0 && parsed <= walletState.balance.unlocked
    }

    // Determine starting phase based on pre-fill
    val startPhase = when {
        prefilledAddress != null && prefillCoveredByBalance -> SendPhase.REVIEW
        prefilledAddress != null -> SendPhase.AMOUNT
        else -> SendPhase.ADDRESS
    }

    var phase by remember { mutableStateOf(startPhase) }
    var navigatingForward by remember { mutableStateOf(true) }

    // Send flow state
    var address by remember { mutableStateOf(prefilledAddress ?: "") }
    var amount by remember { mutableStateOf(prefilledAmount ?: "") }
    var isSweepAll by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf("") }
    var sendInProgress by remember { mutableStateOf(false) }
    // Re-authentication is required between confirming and broadcasting: an
    // unlocked, unattended phone must not be able to drain the wallet.
    var showAuthGate by remember { mutableStateOf(false) }
    var amountPrefilledFromQR by remember { mutableStateOf(prefilledAmount != null) }

    // Fee state (estimated on REVIEW phase)
    var estimatedFee by remember { mutableLongStateOf(0L) }
    var feeLoading by remember { mutableStateOf(false) }
    var feeError by remember { mutableStateOf<String?>(null) }

    // React to send state changes from ViewModel
    LaunchedEffect(sendState) {
        when (sendState) {
            is SendState.Sending -> phase = SendPhase.SENDING
            is SendState.Success -> { phase = SendPhase.SUCCESS; sendInProgress = false }
            is SendState.Error -> { phase = SendPhase.ERROR; sendInProgress = false }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { walletViewModel.resetSendState() }
    }

    fun goForward(to: SendPhase) {
        navigatingForward = true
        phase = to
    }

    fun goBack(to: SendPhase) {
        navigatingForward = false
        phase = to
    }

    // Handle system back button/gesture
    BackHandler {
        when (phase) {
            SendPhase.AMOUNT -> goBack(SendPhase.ADDRESS)
            SendPhase.REVIEW -> goBack(SendPhase.AMOUNT)
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (phase) {
                            SendPhase.ADDRESS -> "Send XMR"
                            SendPhase.AMOUNT -> if (address.length > 20) "Send to ${address.take(8)}...${address.takeLast(4)}" else "Amount"
                            SendPhase.REVIEW -> "Review"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (phase in listOf(SendPhase.ADDRESS, SendPhase.AMOUNT, SendPhase.REVIEW)) {
                        IconButton(onClick = {
                            when (phase) {
                                SendPhase.ADDRESS -> onBack()
                                SendPhase.AMOUNT -> goBack(SendPhase.ADDRESS)
                                SendPhase.REVIEW -> goBack(SendPhase.AMOUNT)
                                else -> {}
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                val direction = if (navigatingForward) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                    initialOffsetX = { it * direction }
                ) + fadeIn(animationSpec = tween(200)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                            targetOffsetX = { -it * direction }
                        ) + fadeOut(animationSpec = tween(150))
                    )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "sendPhase"
        ) { currentPhase ->
            when (currentPhase) {
                SendPhase.ADDRESS -> AddressPhase(
                    address = address,
                    onAddressChange = { address = it },
                    onScanQr = onScanQr,
                    onContinue = { goForward(SendPhase.AMOUNT) }
                )
                SendPhase.AMOUNT -> {
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    AmountPhase(
                        amount = amount,
                        onAmountChange = { amount = it; isSweepAll = false; amountPrefilledFromQR = false },
                        isSweepAll = isSweepAll,
                        onMaxTap = {
                            amount = walletViewModel.formatXmr(walletState.balance.unlocked)
                            isSweepAll = true
                            amountPrefilledFromQR = false
                        },
                        availableBalance = walletViewModel.formatXmr(walletState.balance.unlocked),
                        unlockedBalance = walletState.balance.unlocked,
                        parseXmr = walletViewModel::parseXmr,
                        amountPrefilledFromQR = amountPrefilledFromQR,
                        xmrPrice = currentPrice?.price,
                        currencySymbol = selectedCurrency.symbol,
                        memo = memo,
                        onMemoChange = { memo = it },
                        onContinue = { goForward(SendPhase.REVIEW) }
                    )
                }
                SendPhase.REVIEW -> {
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    ReviewPhase(
                        address = address,
                        amount = amount,
                        isSweepAll = isSweepAll,
                        amountPrefilledFromQR = amountPrefilledFromQR,
                        onUpgradeToSweepAll = { isSweepAll = true },
                        estimatedFee = estimatedFee,
                        feeLoading = feeLoading,
                        feeError = feeError,
                        onEstimateFee = { fee, loading, error ->
                            estimatedFee = fee; feeLoading = loading; feeError = error
                        },
                        unlockedBalance = walletState.balance.unlocked,
                        formatXmr = walletViewModel::formatXmr,
                        parseXmr = walletViewModel::parseXmr,
                        estimateFeeFn = { amt, sweep ->
                            walletViewModel.estimateFee(address, amt, isSweepAll = sweep)
                        },
                        xmrPrice = currentPrice?.price,
                        currencySymbol = selectedCurrency.symbol,
                        sendInProgress = sendInProgress,
                        onConfirm = {
                        sendInProgress = true
                        showAuthGate = true
                    }
                )
                }
                SendPhase.SENDING -> SendingPhase()
                SendPhase.SUCCESS -> SuccessPhase(
                    txHash = (sendState as? SendState.Success)?.txHash ?: "",
                    onDone = onSent
                )
                SendPhase.ERROR -> ErrorPhase(
                    message = (sendState as? SendState.Error)?.message ?: "Transaction failed",
                    onRetry = {
                        walletViewModel.resetSendState()
                        goBack(SendPhase.REVIEW)
                    },
                    onClose = {
                        walletViewModel.resetSendState()
                        onBack()
                    }
                )
            }
        }
    }

    if (showAuthGate) {
        AuthGateDialog(
            walletViewModel = walletViewModel,
            title = "Confirm transaction",
            subtitle = "Authenticate to send ${if (isSweepAll) "your full balance" else "$amount XMR"}",
            onAuthenticated = {
                showAuthGate = false
                walletViewModel.send(
                    address,
                    walletViewModel.parseXmr(amount),
                    isSweepAll = isSweepAll
                )
            },
            onCancel = {
                showAuthGate = false
                sendInProgress = false
            }
        )
    }
}

// ============================================================
// PHASE 1: ADDRESS
// ============================================================

@Composable
private fun AddressPhase(
    address: String,
    onAddressChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onContinue: () -> Unit
) {
    val isValid = address.isNotEmpty() && isValidMoneroAddress(address)
    val clipboardManager = LocalClipboardManager.current

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "addressAlpha"
    )
    val offsetY by animateFloatAsState(
        if (visible) 0f else 20f,
        animationSpec = tween(400),
        label = "addressOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Recipient Address",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Addresses are mono caption, as on iOS.
        MoneroTextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter XMR address", style = MonoCaption) },
            textStyle = MonoCaption,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            singleLine = true
        )

        // Inline validation indicator
        if (address.isNotEmpty()) {
            val valid = isValidMoneroAddress(address)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(
                    if (valid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (valid) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    if (valid) "Valid address" else "Invalid address",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) SuccessGreen else ErrorRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan QR + Paste buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryButton(
                onClick = onScanQr,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Scan QR")
            }
            PrimaryButton(
                onClick = {
                    clipboardManager.getText()?.text?.let { onAddressChange(it.trim()) }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Paste")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        PrimaryButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) {
            Text("Continue")
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ============================================================
// PHASE 2: AMOUNT
// ============================================================

@Composable
private fun AmountPhase(
    amount: String,
    onAmountChange: (String) -> Unit,
    isSweepAll: Boolean,
    onMaxTap: () -> Unit,
    availableBalance: String,
    unlockedBalance: Long,
    parseXmr: (String) -> Long,
    amountPrefilledFromQR: Boolean,
    xmrPrice: Double?,
    currencySymbol: String,
    memo: String,
    onMemoChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val parsedAmount = parseXmr(amount)
    val canContinue = parsedAmount > 0 && parsedAmount <= unlockedBalance
    val clipboardManager = LocalClipboardManager.current

    // Fiat mode toggle
    var isFiatMode by remember { mutableStateOf(false) }
    var fiatString by remember { mutableStateOf("") }
    var showMemo by remember { mutableStateOf(memo.isNotEmpty()) }

    // Sync fiat ↔ XMR when toggling or typing
    fun syncFiatFromXmr() {
        val price = xmrPrice ?: return
        val xmrVal = amount.toDoubleOrNull() ?: 0.0
        fiatString = if (xmrVal > 0) "%.2f".format(xmrVal * price) else ""
    }

    fun syncXmrFromFiat() {
        val price = xmrPrice ?: return
        if (price <= 0) return
        val fiatVal = fiatString.toDoubleOrNull() ?: 0.0
        val xmr = fiatVal / price
        onAmountChange(if (xmr > 0) "%.12f".format(xmr).trimEnd('0').trimEnd('.') else "")
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "amountAlpha")
    val offsetY by animateFloatAsState(if (visible) 0f else 20f, tween(400), label = "amountOffset")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // QR warning
        if (amountPrefilledFromQR) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(14.dp))
                Text("Amount pre-filled from QR code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Large amount display
        if (isFiatMode) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Shrinks like iOS (minimumScaleFactor 0.4) instead of clipping.
                ShrinkToFitText(
                    text = fiatString.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayLarge,
                    minScale = 0.4f,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                )
            }
            // XMR conversion below
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "≈ ${amount.ifEmpty { "0" }} XMR",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                // Shrinks like iOS (minimumScaleFactor 0.4) instead of clipping.
                ShrinkToFitText(
                    text = amount.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayLarge,
                    minScale = 0.4f,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "XMR",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fiat toggle capsule
        if (xmrPrice != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.1f))
                    .clickable {
                        if (isFiatMode) {
                            // Switching back to XMR mode — sync XMR from fiat
                            syncXmrFromFiat()
                            isFiatMode = false
                        } else {
                            // Switching to fiat mode — sync fiat from XMR
                            syncFiatFromXmr()
                            isFiatMode = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFiatMode) {
                        "≈ ${amount.ifEmpty { "0" }} XMR"
                    } else {
                        val fiatVal = (amount.toDoubleOrNull() ?: 0.0) * xmrPrice
                        "≈ ${currencySymbol}${"%.2f".format(fiatVal)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("⇅", style = MaterialTheme.typography.bodyMedium, color = MoneroOrange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Available balance + Paste/Max buttons
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Available: $availableBalance XMR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f))
                    .clickable {
                        clipboardManager.getText()?.text?.let { clip ->
                            val filtered = clip.filter { it.isDigit() || it == '.' }
                            if (filtered.isNotEmpty()) {
                                if (isFiatMode) { fiatString = filtered; syncXmrFromFiat() }
                                else onAmountChange(filtered)
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(14.dp))
                Text("Paste", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f))
                    .clickable {
                        isFiatMode = false
                        onMaxTap()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Max", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
            }
        }

        // Add memo
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMemo = !showMemo }
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add memo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (showMemo) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = showMemo) {
            MoneroTextField(
                value = memo,
                onValueChange = onMemoChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Numeric keypad
        NumericKeypad(
            onKey = { key ->
                if (isFiatMode) {
                    when (key) {
                        "⌫" -> { if (fiatString.isNotEmpty()) { fiatString = fiatString.dropLast(1); syncXmrFromFiat() } }
                        "." -> { if (!fiatString.contains(".")) { fiatString = if (fiatString.isEmpty()) "0." else "$fiatString."; syncXmrFromFiat() } }
                        else -> {
                            val newFiat = if (fiatString == "0" && key != ".") key else fiatString + key
                            val parts = newFiat.split(".")
                            if (parts.size <= 1 || parts[1].length <= 2) { fiatString = newFiat; syncXmrFromFiat() }
                        }
                    }
                } else {
                    when (key) {
                        "⌫" -> if (amount.isNotEmpty()) onAmountChange(amount.dropLast(1))
                        "." -> {
                            if (!amount.contains(".")) onAmountChange(if (amount.isEmpty()) "0." else "$amount.")
                        }
                        else -> {
                            val newAmount = if (amount == "0" && key != ".") key else amount + key
                            val parts = newAmount.split(".")
                            if (parts.size <= 1 || parts[1].length <= 12) onAmountChange(newAmount)
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Continue button (below keypad, matching iOS)
        PrimaryButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = canContinue
        ) {
            Text("Continue")
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ============================================================
// PHASE 3: REVIEW
// ============================================================

@Composable
private fun ReviewPhase(
    address: String,
    amount: String,
    isSweepAll: Boolean,
    amountPrefilledFromQR: Boolean,
    onUpgradeToSweepAll: () -> Unit,
    estimatedFee: Long,
    feeLoading: Boolean,
    feeError: String?,
    onEstimateFee: (Long, Boolean, String?) -> Unit,
    unlockedBalance: Long,
    formatXmr: (Long) -> String,
    parseXmr: (String) -> Long,
    estimateFeeFn: suspend (Long, Boolean) -> Long,
    xmrPrice: Double?,
    currencySymbol: String,
    sendInProgress: Boolean,
    onConfirm: () -> Unit
) {
    val parsedAmount = parseXmr(amount)

    // Estimate fee when entering review
    LaunchedEffect(Unit) {
        onEstimateFee(0L, true, null)
        try {
            val fee = withContext(Dispatchers.IO) {
                estimateFeeFn(parsedAmount, isSweepAll)
            }
            onEstimateFee(fee, false, null)

            // Auto-upgrade to sweep-all if amount + fee > balance. An amount
            // the user did not type must never silently become the full
            // balance — that upgrade requires an explicit tap instead.
            if (!isSweepAll && !amountPrefilledFromQR && parsedAmount + fee > unlockedBalance) {
                onUpgradeToSweepAll()
            }
        } catch (e: Exception) {
            onEstimateFee(0L, false, e.message ?: "Fee estimation failed")
        }
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "reviewAlpha")
    val offsetY by animateFloatAsState(if (visible) 0f else 20f, tween(400), label = "reviewOffset")

    val feeReady = !feeLoading && feeError == null && estimatedFee > 0
    val needsSweepAllChoice = amountPrefilledFromQR && !isSweepAll && feeReady &&
        parsedAmount + estimatedFee > unlockedBalance

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // QR warning
        if (amountPrefilledFromQR && !isSweepAll) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(14.dp))
                Text("Amount pre-filled from QR code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Transaction card: iOS review table (radius 16, fill, no shadow)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            shadow = false,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Recipient
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MoneroOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Recipient", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = address.take(12) + "..." + address.takeLast(8),
                            style = MonoCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Amount (centered)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSweepAll) {
                        Text("All Funds", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (feeReady) {
                            val sendAmount = (unlockedBalance - estimatedFee).coerceAtLeast(0)
                            Text(
                                "${formatXmr(sendAmount)} XMR",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            xmrPrice?.let { price ->
                                val fiat = atomicToXmr(sendAmount) * price
                                Text(
                                    "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (feeLoading) {
                            CircularProgressIndicator(color = MoneroOrange, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        Text(
                            "${formatXmr(parsedAmount)} XMR",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        xmrPrice?.let { price ->
                            val fiat = atomicToXmr(parsedAmount) * price
                            Text(
                                "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Fee row
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Network Fee", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    when {
                        feeLoading -> CircularProgressIndicator(color = MoneroOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        feeError != null -> Text("Error", style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                        else -> Column(horizontalAlignment = Alignment.End) {
                            Text("${formatXmr(estimatedFee)} XMR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            xmrPrice?.let { price ->
                                val fiat = atomicToXmr(estimatedFee) * price
                                Text(
                                    "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Total row
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    if (feeReady) {
                        val total = if (isSweepAll) unlockedBalance else parsedAmount + estimatedFee
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${formatXmr(total)} XMR",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MoneroOrange
                            )
                            xmrPrice?.let { price ->
                                val fiat = atomicToXmr(total) * price
                                Text(
                                    "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pre-filled amount + fee no longer fits the balance
        if (needsSweepAllChoice) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(18.dp))
                        Text(
                            "Amount plus network fee exceeds your available balance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onUpgradeToSweepAll,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Send all funds instead", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Send button
        @OptIn(ExperimentalComposeUiApi::class)
        PrimaryButton(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { event ->
                    (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0
                },
            enabled = feeReady && !sendInProgress && !needsSweepAllChoice
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Send")
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ============================================================
// PHASE 4: STATUS SCREENS
// ============================================================

@Composable
private fun SendingPhase() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            GradientSpinner()

            Spacer(modifier = Modifier.height(40.dp))

            Text("Sending Transaction...", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Please wait while your transaction is being broadcast",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SuccessPhase(txHash: String, onDone: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Animated checkmark
    var showCheck by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); showCheck = true }
    val checkScale by animateFloatAsState(
        if (showCheck) 1f else 0.3f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "checkScale"
    )
    val checkAlpha by animateFloatAsState(if (showCheck) 1f else 0f, tween(300), label = "checkAlpha")

    // Expanding ring
    val ringScale by animateFloatAsState(
        if (showCheck) 2f else 1f,
        tween(800, easing = LinearEasing),
        label = "ringScale"
    )
    val ringAlpha by animateFloatAsState(if (showCheck) 0f else 1f, tween(800), label = "ringAlpha")

    // Read the adaptive green here: drawBehind is not a composable scope.
    val successGreen = SuccessGreen
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Expanding ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                        .drawBehind {
                            drawCircle(
                                color = successGreen,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                )
                // Checkmark
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkAlpha }
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Success", tint = SuccessGreen, modifier = Modifier.size(80.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text("Sent!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SuccessGreen)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Your transaction has been submitted to the network",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (txHash.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))

                Text("Transaction ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (copied) "Copied!" else txHash.take(10) + "..." + txHash.takeLast(6),
                        style = MonoCaption,
                        color = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(txHash))
                            copied = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            PrimaryButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ErrorPhase(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape).background(ErrorRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Error, contentDescription = "Error", tint = ErrorRed, modifier = Modifier.size(80.dp))
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text("Transaction Failed", style = MaterialTheme.typography.headlineSmall, color = ErrorRed)

            Spacer(modifier = Modifier.height(12.dp))

            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(48.dp))

            // iOS: Retry is the glass action, Close a secondary text button under it.
            PrimaryButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Retry")
            }
            Spacer(modifier = Modifier.height(12.dp))
            DismissTextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

// ============================================================
// SHARED COMPONENTS
// ============================================================

@Composable
private fun NumericKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    GlassButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                modifier = Modifier.align(Alignment.Center).size(24.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularFigures),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "outerRotation"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(2140, easing = LinearEasing)),
        label = "innerRotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        // Pulsing background
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(CircleShape)
                .background(MoneroOrange.copy(alpha = 0.08f))
        )

        // Outer ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .rotate(outerRotation)
                .drawBehind {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                MoneroOrange.copy(alpha = 0f),
                                MoneroOrange,
                                MoneroOrange.copy(alpha = 0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
        )

        // Inner ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .rotate(innerRotation)
                .drawBehind {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                MoneroOrange.copy(alpha = 0f),
                                MoneroOrange.copy(alpha = 0.6f),
                                MoneroOrange.copy(alpha = 0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
        )

        // Center icon
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = MoneroOrange,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun atomicToXmr(atomic: Long): Double {
    return atomic.toDouble() / 1_000_000_000_000.0
}

private fun isValidMoneroAddress(address: String): Boolean {
    if (address.length !in listOf(95, 106)) return false
    if (!address.startsWith("4") && !address.startsWith("8")) return false
    return try {
        MoneroKit.validateAddress(address)
        true
    } catch (e: Exception) {
        false
    }
}
