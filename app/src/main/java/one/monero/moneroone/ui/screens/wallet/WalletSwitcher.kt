package one.monero.moneroone.ui.screens.wallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlin.math.roundToInt

/** Curated emoji set for wallet icons (grid picker, iOS parity in spirit). */
private val WALLET_EMOJI = listOf(
    "💰", "🪙", "💎", "💸", "🤑", "🏦", "🐖", "💳", "🧧", "👛",
    "🔒", "🛡️", "🔑", "🗝️", "🕶️", "🥷", "👻", "🤖", "👾", "🎃",
    "📈", "📉", "💼", "🚀", "🌙", "⭐", "✨", "🔥", "⚡", "🌊",
    "🍀", "🌳", "🌵", "❄️", "🌋", "🏔️", "🏝️", "🏠", "🏰", "⛺",
    "🎯", "🎲", "🎮", "🎁", "🎩", "👑", "🧲", "🧪", "⚙️", "🧭",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🦁", "🐺",
    "🦊", "🐸", "🐢", "🦉", "🦅", "🐉", "🐋", "🦈", "🐙", "🦇"
)

/**
 * Collapsed switcher chip shown in the WalletScreen header: emoji over name.
 */
@Composable
fun WalletSwitcherButton(
    wallet: WalletInfo?,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(74.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(text = wallet?.emoji ?: "💰", fontSize = 22.sp)
            Text(
                text = wallet?.name ?: "Wallet",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.widthIn(max = 66.dp)
            )
        }
    }
}

/**
 * Expanded wallet manager: active wallet card + rows for the other wallets
 * (rendered from CACHED data only) + Add Wallet. Inline in WalletScreen,
 * not a sheet (iOS parity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagerRows(
    wallets: List<WalletInfo>,
    activeWallet: WalletInfo?,
    liveBalanceText: String,
    formatXmr: (Long) -> String,
    onSwitch: (WalletInfo) -> Unit,
    onDelete: (WalletInfo) -> Unit,
    onRename: (WalletInfo, String, String) -> Unit,
    onAddWallet: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<WalletInfo?>(null) }
    var deleteCandidate by remember { mutableStateOf<WalletInfo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var isSwitching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        activeWallet?.let { active ->
            ActiveWalletCard(
                wallet = active,
                balanceText = liveBalanceText,
                onRename = { renameTarget = active }
            )
        }

        wallets.filter { it.id != activeWallet?.id }.forEach { wallet ->
            WalletRow(
                wallet = wallet,
                formatXmr = formatXmr,
                onClick = {
                    if (!isSwitching) {
                        isSwitching = true
                        onSwitch(wallet)
                    }
                },
                onDeleteRequest = { deleteCandidate = wallet }
            )
        }

        AddWalletRow(onClick = onAddWallet)
    }

    renameTarget?.let { target ->
        RenameWalletSheet(
            wallet = target,
            onDismiss = { renameTarget = null },
            onSave = { name, emoji ->
                onRename(target, name, emoji)
                renameTarget = null
            }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Remove \"${candidate.name}\"?") },
            text = {
                Text(
                    "This removes the wallet from this device only. " +
                        "It still exists on the blockchain and can be recovered with its seed phrase."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Double-tap guard: deletion races kit teardown.
                        if (!isDeleting) {
                            isDeleting = true
                            onDelete(candidate)
                            scope.launch {
                                delay(600)
                                isDeleting = false
                            }
                        }
                        deleteCandidate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveWalletCard(
    wallet: WalletInfo,
    balanceText: String,
    onRename: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiCircle(emoji = wallet.emoji, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wallet.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MoneroOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                wallet.cachedPrimaryAddress?.takeIf { it.length > 16 }?.let { address ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${address.take(8)}…${address.takeLast(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename wallet",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active wallet",
                tint = SuccessGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Inactive wallet row rendered from CACHED balance/address only.
 * Swipe-to-delete reveals a trash button in an overlay — only offset/alpha
 * animate, the row never re-lays-out (iOS aa8e835 lesson).
 */
@Composable
private fun WalletRow(
    wallet: WalletInfo,
    formatXmr: (Long) -> String,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 72.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = offsetX, label = "swipe")
    val revealFraction = (-animatedOffset / revealPx).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Trash overlay behind the row — no layout participation.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .alpha(revealFraction),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {
                    offsetX = 0f
                    onDeleteRequest()
                },
                enabled = revealFraction > 0.9f
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete wallet",
                    tint = ErrorRed
                )
            }
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-revealPx, 0f)
                    },
                    onDragStopped = {
                        offsetX = if (offsetX < -revealPx * 0.5f) -revealPx else 0f
                    }
                ),
            onClick = {
                if (offsetX != 0f) {
                    offsetX = 0f
                } else {
                    onClick()
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiCircle(emoji = wallet.emoji, size = 40.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${formatXmr(wallet.cachedBalance ?: 0L)} XMR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        wallet.cachedPrimaryAddress?.takeIf { it.length > 16 }?.let { address ->
                            Text(
                                text = "  ${address.take(8)}…",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddWalletRow(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MoneroOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Add Wallet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MoneroOrange
            )
        }
    }
}

@Composable
fun EmojiCircle(emoji: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameWalletSheet(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit,
    title: String = "Rename Wallet"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(wallet.name) }
    var emoji by remember { mutableStateOf(wallet.emoji) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { showEmojiPicker = !showEmojiPicker },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 36.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to change",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (showEmojiPicker) {
                Spacer(modifier = Modifier.height(12.dp))
                EmojiPickerGrid(
                    selected = emoji,
                    onSelect = {
                        emoji = it
                        showEmojiPicker = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Wallet name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSave(name.trim(), emoji) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneroOrange,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = MoneroOrange.copy(alpha = 0.4f)
                )
            ) {
                Text("Save", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun EmojiPickerGrid(
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(WALLET_EMOJI) { candidate ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (candidate == selected) MoneroOrange.copy(alpha = 0.25f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { onSelect(candidate) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = candidate, fontSize = 20.sp)
            }
        }
    }
}
