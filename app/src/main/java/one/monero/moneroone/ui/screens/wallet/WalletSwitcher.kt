package one.monero.moneroone.ui.screens.wallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlin.math.abs
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
 * Wallet switcher chip in the WalletScreen header: emoji over name. It stays
 * a chip while the list is open (an orange ring marks the open state); the
 * active wallet keeps its own slot in the list below instead of being pulled
 * up into the chip, so the list never reshuffles on a switch.
 */
@Composable
fun WalletSwitcherButton(
    wallet: WalletInfo?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ring by animateColorAsState(
        targetValue = if (expanded) MoneroOrange.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = Motion.snappy(),
        label = "switcherRing"
    )
    Box(
        modifier = modifier
            .width(74.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.5.dp, ring, RoundedCornerShape(12.dp))
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
 * Expanded wallet manager: EVERY wallet in store order (insertion order until
 * the user drags), the active one marked in place with a check and an orange
 * ring, then Add Wallet. Inline in WalletScreen, not a sheet.
 *
 * Gestures per row: tap = switch (or close when it is the active one),
 * pencil = rename, swipe left = delete (inactive rows), long-press + drag =
 * reorder, persisted through [onMove].
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
    onMove: (movedId: String, beforeId: String?) -> Unit,
    onAddWallet: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<WalletInfo?>(null) }
    var deleteCandidate by remember { mutableStateOf<WalletInfo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }

    // Reorder state: the lifted row follows the finger, the rows it passes
    // slide out of the way by one step, the drop commits the new order.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var suppressClick by remember { mutableStateOf(false) }
    // pointerInput lambdas outlive recompositions: read the list and the
    // drop target through state, never through captured locals.
    val latestWallets by rememberUpdatedState(wallets)
    fun dropTarget(from: Int): Int {
        val stepNow = rowHeightPx + gapPx
        return if (stepNow <= 0f) from
        else (from + (dragOffset / stepNow).roundToInt()).coerceIn(0, latestWallets.lastIndex)
    }
    val step = rowHeightPx + gapPx
    val targetIndex = dragIndex?.let { dropTarget(it) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        wallets.forEachIndexed { index, wallet ->
            val isActive = wallet.id == activeWallet?.id
            val isDragging = dragIndex == index
            val from = dragIndex
            val shift = when {
                from == null || targetIndex == null || isDragging -> 0f
                index > from && index <= targetIndex -> -step
                index < from && index >= targetIndex -> step
                else -> 0f
            }
            val animatedShift by animateFloatAsState(shift, Motion.snappy(), label = "shift$index")
            val lift by animateFloatAsState(if (isDragging) 1f else 0f, Motion.snappy(), label = "lift$index")

            key(wallet.id) {
                WalletRow(
                    wallet = wallet,
                    isActive = isActive,
                    balanceText = if (isActive) liveBalanceText else "${formatXmr(wallet.cachedBalance ?: 0L)} XMR",
                    modifier = Modifier
                        .onSizeChanged { if (rowHeightPx == 0f) rowHeightPx = it.height.toFloat() }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else animatedShift
                            val s = 1f + 0.02f * lift
                            scaleX = s
                            scaleY = s
                            shadowElevation = 14.dp.toPx() * lift
                            shape = RoundedCornerShape(16.dp)
                        }
                        .pointerInput(index, wallets.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragIndex = index
                                    dragOffset = 0f
                                    suppressClick = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta.y
                                },
                                onDragEnd = {
                                    val from = dragIndex
                                    val to = from?.let { dropTarget(it) }
                                    dragIndex = null
                                    dragOffset = 0f
                                    val list = latestWallets
                                    if (from != null && to != null && to != from && from in list.indices) {
                                        val remaining = list.filterIndexed { i, _ -> i != from }
                                        timber.log.Timber.d("reorder: ${list[from].name} -> position $to")
                                        onMove(list[from].id, remaining.getOrNull(to)?.id)
                                    }
                                    scope.launch { delay(300); suppressClick = false }
                                },
                                onDragCancel = {
                                    dragIndex = null
                                    dragOffset = 0f
                                    scope.launch { delay(300); suppressClick = false }
                                }
                            )
                        },
                    onClick = { if (!suppressClick) onSwitch(wallet) },
                    onRenameRequest = { renameTarget = wallet },
                    // The active wallet is deleted from Settings, not by a swipe.
                    onDeleteRequest = if (isActive) null else ({ deleteCandidate = wallet })
                )
            }
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

/**
 * One wallet row (iOS WalletRow): emoji, name, orange balance, address under
 * it, rename pencil, and a check when it is the active wallet. Swipe-to-delete
 * reveals a trash button in an overlay — only offset/alpha animate, the row
 * never re-lays-out (iOS aa8e835 lesson). The drag only engages on a clear
 * leftward swipe past 24dp so wobbly taps still click.
 */
@Composable
private fun WalletRow(
    wallet: WalletInfo,
    isActive: Boolean,
    balanceText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: (() -> Unit)?
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 72.dp.toPx() }
    val dragStartPx = with(density) { 24.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = offsetX, label = "swipe")
    val revealFraction = (-animatedOffset / revealPx).coerceIn(0f, 1f)
    val swipeEnabled = onDeleteRequest != null

    Box(modifier = modifier.fillMaxWidth()) {
        if (swipeEnabled) {
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
                        onDeleteRequest?.invoke()
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
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .then(
                    if (isActive) Modifier.border(1.5.dp, MoneroOrange.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    else Modifier
                )
                .pointerInput(swipeEnabled, revealPx, dragStartPx) {
                    if (!swipeEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startOffset = offsetX
                        var dragging = false
                        var acc = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.isConsumed) break   // the list or the reorder drag took it
                            val dx = change.positionChange().x
                            if (!dragging) {
                                acc += dx
                                val dy = abs(change.position.y - down.position.y)
                                if (abs(acc) > dragStartPx) {
                                    val towardsReveal = acc < 0f || startOffset < 0f
                                    if (towardsReveal && abs(acc) > dy) {
                                        dragging = true
                                        offsetX = (startOffset + acc).coerceIn(-revealPx, 0f)
                                        change.consume()
                                    } else {
                                        break   // rightward or mostly vertical: leave it to click / scroll
                                    }
                                }
                            } else {
                                offsetX = (offsetX + dx).coerceIn(-revealPx, 0f)
                                change.consume()
                            }
                        }
                        if (dragging) {
                            offsetX = if (offsetX < -revealPx * 0.5f) -revealPx else 0f
                        }
                    }
                },
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
                    .then(if (isActive) Modifier.background(MoneroOrange.copy(alpha = 0.06f)) else Modifier)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }

                IconButton(onClick = onRenameRequest) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename wallet",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active wallet",
                        tint = SuccessGreen,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(28.dp))
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
