package one.monero.moneroone.ui.screens.receive

import one.monero.moneroone.core.locale.tr
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.ReceiveAddressLogic
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.core.wallet.addressesOf
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.truncateMiddle
import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import one.monero.moneroone.ui.components.FocusableQrPlate
import one.monero.moneroone.ui.components.QrFocusContainer
import one.monero.moneroone.ui.components.QrFocusItem
import one.monero.moneroone.ui.components.QrRecedeEdge
import one.monero.moneroone.ui.components.qrFocusRecede
import one.monero.moneroone.ui.components.rememberSaveQrToPhotos
import one.monero.moneroone.ui.screens.send.SendAmountStart
import one.monero.moneroone.ui.components.rememberShareQr
import one.monero.moneroone.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onSelectAddress: (() -> Unit)? = null
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val activeWallet by walletViewModel.activeWallet.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    val selectedAddressIndex = walletState.receiveIndex
    val scope = rememberCoroutineScope()
    var creating by remember(activeWallet?.id) { mutableStateOf(false) }

    // Re-read when screen resumes (e.g., returning from AddressPickerScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                walletViewModel.refreshAddresses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only the addresses published for the active wallet: a kit still held for
    // the wallet just left must never show its addresses under this name.
    val addresses = walletState.addressesOf(activeWallet?.id)
    val keysUnavailable = addresses?.blocked == true
    val sub = addresses?.shownAddress(selectedAddressIndex)
    val address = sub?.address.orEmpty()
    val addressLabel = ReceiveAddressLogic.name(sub?.addressIndex ?: 0,
        activeWallet?.addressLabels?.get(sub?.addressIndex) ?: sub?.label.orEmpty())
    val addressRows = remember(addresses, walletState.transactions, activeWallet?.addressLabels) {
        ReceiveAddressLogic.rows(addresses?.list.orEmpty(), walletState.transactions, activeWallet?.addressLabels.orEmpty())
    }
    val canCreate = addresses?.complete == true && !keysUnavailable && !creating &&
        ReceiveAddressLogic.unusedAfterLastUsed(addressRows) < ReceiveAddressLogic.STOP_THRESHOLD
    val canShareAddress = address.isNotBlank() && !keysUnavailable

    // Saved with the screen's back stack entry, so the amount stays through a
    // trip to the address picker, as on iOS.
    var requestAmount by rememberSaveable { mutableStateOf("") }
    val currentPrice by walletViewModel.currentPrice.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
    val fiatMode by walletViewModel.fiatMode.collectAsState()
    val xmrPrice = currentPrice?.price
    // Fiat when Fiat Mode is on and a usable price exists, as on Send. Set once when the screen
    // appears, so a swap to XMR stays, and a price that comes later does not change the unit.
    var isFiatMode by rememberSaveable {
        mutableStateOf(SendAmountStart.startsInFiat(fiatMode, xmrPrice, requestAmount))
    }
    var fiatAmount by rememberSaveable { mutableStateOf("") }
    val currencySymbol = selectedCurrency.symbol

    // A monero: URI rather than the bare address, so other wallets and the
    // camera offer to open it (iOS ReceiveView.qrContent). Copy still copies
    // the bare address.
    val requestedXmr = remember(requestAmount) { requestAmount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } }
    val qrContent = remember(address, requestedXmr, canShareAddress) {
        when {
            !canShareAddress -> ""
            requestedXmr != null -> "monero:$address?tx_amount=${requestedXmr.stripTrailingZeros().toPlainString()}"
            else -> "monero:$address"
        }
    }
    val freshAddress = remember { walletViewModel.freshReceiveAddress }
    val saveQr = rememberSaveQrToPhotos()
    val shareQr = rememberShareQr()
    var qrMenu by remember { mutableStateOf(false) }

    // Copy and Share act only on a shown address (iOS disables both without one).
    val copyAddress = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(tr("Monero Address"), address)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, tr("Copied to clipboard"), Toast.LENGTH_SHORT).show()
    }
    // The code's image and a line to send with it (iOS shareItems).
    val shareAddress = {
        shareQr(qrContent,
            if (requestedXmr != null) tr("Send me %s XMR at this address:\n\n%s", requestAmount, address)
            else tr("Send me Monero (XMR) at this address:\n\n%s", address))
    }

    QrFocusContainer { focus ->
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tr("Receive XMR"),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // The code on its plate; a tap grows it into focus mode (iOS FocusableQRPlate).
            if (keysUnavailable) {
                Box(
                    Modifier.size(280.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    KeysUnavailableMessage(Modifier.padding(horizontal = 8.dp))
                }
            } else if (qrContent.isNotEmpty()) {
                Box {
                    FocusableQrPlate(
                        item = QrFocusItem(qrContent, addressLabel, requestedXmr),
                        side = 280.dp,
                        focus = focus,
                        label = tr("QR code for receiving Monero"),
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            qrMenu = true
                        },
                        actions = listOf(
                            CustomAccessibilityAction(tr("Save to Photos")) { saveQr(qrContent); true },
                            CustomAccessibilityAction(tr("Share QR Code")) { shareQr(qrContent, null); true }
                        )
                    )
                    DropdownMenu(expanded = qrMenu, onDismissRequest = { qrMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Save to Photos")) },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = { qrMenu = false; saveQr(qrContent) }
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Share QR Code")) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { qrMenu = false; shareQr(qrContent, null) }
                        )
                    }
                }
            } else {
                Box(
                    Modifier.size(280.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MoneroOrange)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Everything under the code steps back toward the bottom in focus mode.
            Column(
                Modifier.fillMaxWidth().qrFocusRecede(focus, QrRecedeEdge.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // "Request Amount (optional)" label row with currency toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Request Amount (optional)"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (xmrPrice != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(MoneroOrange.copy(alpha = 0.15f))
                            .clickable {
                                if (isFiatMode) {
                                    isFiatMode = false
                                } else {
                                    val xmrVal = requestAmount.toDoubleOrNull() ?: 0.0
                                    fiatAmount = if (xmrVal > 0) "%.2f".format(Locale.US, xmrVal * xmrPrice) else ""
                                    isFiatMode = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("⇅", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
                        Text(
                            text = if (isFiatMode) "XMR" else selectedCurrency.code.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MoneroOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount input field
            if (isFiatMode) {
                MoneroTextField(
                    value = fiatAmount,
                    onValueChange = { input ->
                        val it = input.replace(',', '.')
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            fiatAmount = it
                            val price = xmrPrice
                            if (price != null && price > 0) {
                                val fiatVal = it.toDoubleOrNull() ?: 0.0
                                val xmr = fiatVal / price
                                requestAmount = if (xmr > 0) "%.12f".format(Locale.US, xmr).trimEnd('0').trimEnd('.') else ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.00") },
                    prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (fiatAmount.isNotBlank()) {
                            IconButton(onClick = { fiatAmount = ""; requestAmount = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = tr("Clear"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            } else {
                MoneroTextField(
                    value = requestAmount,
                    onValueChange = { input ->
                        val it = input.replace(',', '.')
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) requestAmount = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.0") },
                    suffix = { Text("XMR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (requestAmount.isNotBlank()) {
                            IconButton(onClick = { requestAmount = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = tr("Clear"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address card (truncated, matching iOS): radius 12 on the fill
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                shadow = false,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = onSelectAddress != null) { onSelectAddress?.invoke() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = addressLabel,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (address.length > 20) truncateMiddle(address)
                                else address.ifBlank { if (keysUnavailable) "" else tr("Loading...") },
                            style = MonoCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    addressRows.firstOrNull { it.index == sub?.addressIndex }?.let { row ->
                        Text(row.description, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp).width(100.dp))
                    }
                    if (onSelectAddress != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = tr("Select Address"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = {
                        creating = true
                        scope.launch {
                            try {
                                if (!walletViewModel.createSubaddress()) Toast.makeText(context, tr("Unable to create address"), Toast.LENGTH_SHORT).show()
                            } finally { creating = false }
                        }
                    }, enabled = canCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    if (creating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("New Address"))
                }
                }
            }
            ReceiveFooter(
                // The choice, not the fallback: while a chosen subaddress is not
                // listed yet the card shows the main address, and iOS keeps its
                // warning off then.
                mainAddress = selectedAddressIndex == 0,
                freshAddress = freshAddress,
                limitNote = ReceiveAddressLogic.creationWarning(addressRows)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Copy / Share buttons (tall glass cards with icon above text, matching iOS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(90.dp)
                        .alpha(if (canShareAddress) 1f else DISABLED_ALPHA),
                    enabled = canShareAddress,
                    onClick = copyAddress
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(tr("Copy"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                GlassButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(90.dp)
                        .alpha(if (canShareAddress) 1f else DISABLED_ALPHA),
                    enabled = canShareAddress,
                    onClick = shareAddress
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(tr("Share"), style = MaterialTheme.typography.labelLarge, color = MoneroOrange)
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
    }
}

/** Copy and Share while no address can be shown (iOS dims disabled buttons the same way). */
private const val DISABLED_ALPHA = 0.4f

/** The notes under the address card: what happens next (iOS ReceiveView's footer). */
@Composable
private fun ReceiveFooter(mainAddress: Boolean, freshAddress: Boolean, limitNote: String?) {
    if (!mainAddress && !freshAddress && limitNote == null) return
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (mainAddress) {
            val spoken = tr("Privacy warning: Main address links all transactions. Use subaddresses for privacy.")
            FooterLine(Icons.Default.Warning, WarningYellow,
                tr("Main address links all transactions. Use subaddresses for privacy."),
                MaterialTheme.colorScheme.onSurface,
                Modifier.clearAndSetSemantics { contentDescription = spoken })
        }
        if (freshAddress) {
            val secondary = MaterialTheme.colorScheme.onSurfaceVariant
            FooterLine(Icons.Default.Sync, secondary, tr("New address after each payment"), secondary)
        }
        limitNote?.let { FooterLine(Icons.Default.Warning, WarningYellow, it, MaterialTheme.colorScheme.onSurface) }
    }
}

/** A caption with its glyph on the first line. */
@Composable
private fun FooterLine(icon: ImageVector, iconTint: Color, text: String, textColor: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 2.dp).size(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

/**
 * Shown in place of the QR code when the wallet's keys failed a check (iOS
 * ReceiveView keysUnavailable): no receive address may be shown.
 */
@Composable
internal fun KeysUnavailableMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = tr("Wallet keys unavailable. No receive address can be shown.")
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = tr("Wallet keys unavailable"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = tr("The wallet couldn't load its keys, so no receive address can be shown. Do not send funds to any address from this app until this is resolved. Force-quit and reopen the app; if this persists, restore the wallet from its seed."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
