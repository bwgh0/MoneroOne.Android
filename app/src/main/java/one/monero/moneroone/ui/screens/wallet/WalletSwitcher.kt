package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.core.locale.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.key
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.SeedValidation
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ShrinkToFitText
import one.monero.moneroone.ui.components.cardShadow
import one.monero.moneroone.ui.components.moneroTextFieldColors
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoFamily
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.truncateMiddle
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 98 x 56, as iOS measures it: a 74pt label frame plus the 12pt side
    // insets of the glass button style. No ring while the rows are open, as on iOS.
    Box(
        modifier = modifier
            .width(98.dp)
            .cardShadow(CapsuleShape)
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = wallet?.emoji ?: "💰", fontSize = 22.sp)
            Text(
                text = wallet?.name ?: tr("Wallet"),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * pencil = Edit Wallet (name, icon, Delete Wallet for every wallet), swipe left =
 * delete (inactive rows only), drag the handle on the
 * left = reorder, persisted through [onMove]. Balances follow Fiat Mode through
 * [liveBalanceText] and [formatBalance].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagerRows(
    wallets: List<WalletInfo>,
    activeWallet: WalletInfo?,
    liveBalanceText: String,
    /** A wallet's balance as the row shows it, unit included ("1.0000 XMR", or fiat in Fiat Mode). */
    formatBalance: (Long) -> String,
    onSwitch: (WalletInfo) -> Unit,
    onDelete: (WalletInfo) -> Unit,
    onRename: (WalletInfo, String, String) -> Unit,
    onMove: (movedId: String, beforeId: String?) -> Unit,
    onAddWallet: () -> Unit,
    /** Opens Transfer. The button shows when the active wallet can send ([switcherShowsTransfer]). */
    onTransfer: () -> Unit = {}
) {
    var renameTarget by remember { mutableStateOf<WalletInfo?>(null) }
    var deleteCandidate by remember { mutableStateOf<WalletInfo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }

    // Reorder state, all of it by wallet id, so a row's motion follows its wallet and not
    // its slot. The lifted row follows the finger; each row it passes slides by the lifted
    // row's height plus the gap. On the drop every row starts from where it is on screen
    // and springs into its new slot, so nothing jumps.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<String, Float>() }
    val motions = remember { HashMap<String, RowMotion>() }
    // The order of the drop, shown until the store's new list arrives, so the rows never
    // flash back to the old order for a frame.
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    val shown = pendingOrder?.let { order ->
        val byId = wallets.associateBy { it.id }
        if (order.size == wallets.size && order.all { it in byId }) order.map { byId.getValue(it) } else null
    } ?: wallets
    LaunchedEffect(wallets) {
        pendingOrder = null
        val ids = wallets.mapTo(HashSet()) { it.id }
        heights.keys.retainAll(ids)
        motions.keys.retainAll(ids)
    }
    // pointerInput lambdas outlive recompositions: read the list through state.
    val latestShown by rememberUpdatedState(shown)

    fun motionOf(id: String) = motions.getOrPut(id) { RowMotion() }
    fun heightsOf(list: List<WalletInfo>) = list.map { heights[it.id] ?: 0f }
    fun animateRow(id: String, to: Float) {
        val m = motionOf(id)
        if (m.target == to && m.job?.isActive == true) return
        if (m.target == to && m.offset == to) return
        m.target = to
        m.job?.cancel()
        m.job = scope.launch {
            animate(m.offset, to, m.velocity, Motion.snappy()) { value, velocity ->
                m.offset = value
                m.velocity = velocity
            }
            m.velocity = 0f
        }
    }
    fun snapRow(id: String, to: Float) {
        val m = motionOf(id)
        m.job?.cancel()
        m.offset = to
        m.target = to
        m.velocity = 0f
    }
    fun updateShifts() {
        val list = latestShown
        val from = list.indexOfFirst { it.id == draggingId }
        if (from < 0) return
        val rowHeights = heightsOf(list)
        val to = reorderDropIndex(rowHeights, gapPx, from, dragOffset)
        val step = rowHeights[from] + gapPx
        list.forEachIndexed { j, w ->
            if (j == from) return@forEachIndexed
            animateRow(w.id, when {
                j in (from + 1)..to -> -step
                j in to until from -> step
                else -> 0f
            })
        }
    }
    fun startDrag(id: String) {
        if (draggingId != null) return
        // A row still springing into place is picked up where it is.
        dragOffset = motionOf(id).offset
        snapRow(id, 0f)
        draggingId = id
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun endDrag(commit: Boolean) {
        val list = latestShown
        val id = draggingId ?: return
        val from = list.indexOfFirst { it.id == id }
        draggingId = null
        if (from < 0) { dragOffset = 0f; return }
        val rowHeights = heightsOf(list)
        val to = if (commit) reorderDropIndex(rowHeights, gapPx, from, dragOffset) else from
        val oldTops = reorderRowTops(rowHeights, gapPx)
        val onScreen = list.mapIndexed { j, w -> oldTops[j] + if (j == from) dragOffset else motionOf(w.id).offset }
        val newList = list.toMutableList().apply { add(to, removeAt(from)) }
        val newTops = reorderRowTops(heightsOf(newList), gapPx)
        newList.forEachIndexed { k, w ->
            snapRow(w.id, onScreen[list.indexOf(w)] - newTops[k])
            animateRow(w.id, 0f)
        }
        dragOffset = 0f
        settlingId = id
        scope.launch {
            motionOf(id).job?.join()
            if (settlingId == id) settlingId = null
        }
        if (to != from) {
            pendingOrder = newList.map { it.id }
            timber.log.Timber.d("reorder: ${list[from].name} -> position $to")
            onMove(id, newList.getOrNull(to + 1)?.id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEachIndexed { index, wallet ->
            key(wallet.id) {
                val isActive = wallet.id == activeWallet?.id
                val isDragging = draggingId == wallet.id
                val motion = motionOf(wallet.id)
                val lift by animateFloatAsState(if (isDragging) 1f else 0f, Motion.snappy(), label = "lift")
                WalletRow(
                    wallet = wallet,
                    isActive = isActive,
                    isLifted = isDragging,
                    // An unknown balance is "— XMR" (also in Fiat Mode), never zero, as on iOS.
                    balanceText = if (isActive) liveBalanceText else wallet.cachedBalance?.let(formatBalance) ?: "— XMR",
                    modifier = Modifier
                        .onSizeChanged { heights[wallet.id] = it.height.toFloat() }
                        .zIndex(if (isDragging || settlingId == wallet.id) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else motion.offset
                            val s = 1f + 0.02f * lift
                            scaleX = s
                            scaleY = s
                            shadowElevation = 14.dp.toPx() * lift
                            shape = CapsuleShape
                        },
                    onClick = { onSwitch(wallet) },
                    onReorderStart = { startDrag(wallet.id) },
                    onReorderDrag = { dy ->
                        if (draggingId == wallet.id) {
                            dragOffset += dy
                            updateShifts()
                        }
                    },
                    onReorderEnd = { commit -> if (draggingId == wallet.id) endDrag(commit) },
                    onRenameRequest = { renameTarget = wallet },
                    onMoveUp = if (index > 0) ({ onMove(wallet.id, shown[index - 1].id) }) else null,
                    onMoveDown = if (index < shown.lastIndex) ({ onMove(wallet.id, shown.getOrNull(index + 2)?.id) }) else null,
                    // The active wallet is deleted from Edit Wallet, not by a swipe.
                    onDeleteRequest = if (isActive) null else ({ deleteCandidate = wallet })
                )
            }
        }

        WalletActionsRow(showTransfer = switcherShowsTransfer(activeWallet), onTransfer = onTransfer, onAddWallet = onAddWallet)
    }

    renameTarget?.let { target ->
        EditWalletSheet(
            wallet = target,
            onDismiss = { renameTarget = null },
            onSave = { name, emoji ->
                onRename(target, name, emoji)
                renameTarget = null
            },
            onDelete = {
                // The confirmation opens over the sheet, as on iOS: Cancel
                // returns to Edit Wallet, Delete closes both.
                deleteCandidate = target
            }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(tr("Delete Wallet?")) },
            text = {
                Text(
                    tr("This removes the wallet from this device. You can recover it with the seed phrase.")
                )
            },
            confirmButton = {
                TextButton(
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
                        renameTarget = null
                    }
                ) {
                    Text(tr("Delete"), color = ErrorRed)
                }
            },
            dismissButton = {
                DismissTextButton(onClick = { deleteCandidate = null }) { Text(tr("Cancel")) }
            }
        )
    }
}

/** A row's reorder offset, kept by wallet id so it follows the wallet when the list reorders. */
private class RowMotion {
    var offset by mutableFloatStateOf(0f)
    var target = 0f
    var velocity = 0f
    var job: Job? = null
}

/** The top of each row in a column of [heights] spaced [gap] apart. */
internal fun reorderRowTops(heights: List<Float>, gap: Float): List<Float> {
    var y = 0f
    return heights.map { h -> y.also { y += h + gap } }
}

/**
 * Where the lifted row at [from], moved by [dragOffset], drops in a column of rows of [heights]
 * spaced [gap] apart. Each row's own measured height counts: the lifted row takes a row's slot
 * once its leading edge passes that row's middle.
 */
internal fun reorderDropIndex(heights: List<Float>, gap: Float, from: Int, dragOffset: Float): Int {
    if (from !in heights.indices) return from
    val tops = reorderRowTops(heights, gap)
    val top = tops[from] + dragOffset
    val bottom = top + heights[from]
    var to = from
    for (j in from + 1..heights.lastIndex) {
        if (bottom > tops[j] + heights[j] / 2f) to = j else break
    }
    if (to == from) {
        for (j in from - 1 downTo 0) {
            if (top < tops[j] + heights[j] / 2f) to = j else break
        }
    }
    return to
}

internal enum class RowSwipe { Closed, Open }

/**
 * The swipe to the Delete zone (iOS SwipeTrack, allowsFullSwipe false), shared by the wallet rows
 * and the Contacts sheet. A flick or a pull past 40% of the zone width opens it; a full swipe only
 * opens it, and a tap on the zone asks to delete. Only offset and alpha animate.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class RowSwipeState(val revealPx: Float, flingPx: Float, private val scope: CoroutineScope) {
    val anchored = AnchoredDraggableState(
        initialValue = RowSwipe.Closed,
        anchors = DraggableAnchors {
            RowSwipe.Closed at 0f
            RowSwipe.Open at -revealPx
        },
        positionalThreshold = { distance -> distance * 0.4f },
        velocityThreshold = { flingPx },
        snapAnimationSpec = Motion.snappy(),
        decayAnimationSpec = exponentialDecay()
    )
    private var job: Job? = null

    /** The zone takes taps once the row is almost open. */
    val zoneEnabled by derivedStateOf { anchored.offset <= -revealPx * 0.9f }

    /** Open, or on its way: a tap on the row closes it and does nothing else. */
    val isOpen: Boolean get() = anchored.targetValue != RowSwipe.Closed || anchored.offset < -0.5f

    /** The row's offset; read it in a layout lambda (`Modifier.offset { }`). */
    fun offset(): IntOffset = IntOffset(anchored.offset.roundToInt(), 0)

    fun animate(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    fun close() = animate { anchored.animateTo(RowSwipe.Closed) }

    fun cancel() {
        job?.cancel()
    }
}

@Composable
internal fun rememberRowSwipeState(): RowSwipeState {
    val density = LocalDensity.current
    val revealPx = with(density) { 88.dp.toPx() }
    val flingPx = with(density) { 125.dp.toPx() }
    val scope = rememberCoroutineScope()
    return remember(revealPx, flingPx) { RowSwipeState(revealPx, flingPx, scope) }
}

/**
 * A row over its Delete zone. The card follows the finger from the touch slop on. The axis that
 * moved more by the touch slop decides: a mostly horizontal swipe, diagonal too, is a swipe; a
 * mostly vertical one scrolls the list. [content] offsets its card by [RowSwipeState.offset] and
 * closes an open row on a tap. A null [onDelete], or [locked], turns the swipe off and closes the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeToDeleteRow(
    swipe: RowSwipeState,
    deleteLabel: String,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val swipeEnabled = onDelete != null
    LaunchedEffect(swipeEnabled, locked) {
        if ((!swipeEnabled || locked) && swipe.anchored.currentValue != RowSwipe.Closed) swipe.close()
    }
    // The leading edge moves toward the start: left in LTR, right in RTL.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(swipe, swipeEnabled, locked, direction) {
                if (!swipeEnabled || locked) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker().apply { addPointerInputChange(down) }
                    var total = Offset.Zero
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) {
                                val velocity = tracker.calculateVelocity().x * direction
                                swipe.animate { swipe.anchored.settle(velocity) }
                            }
                            break
                        }
                        if (!dragging && change.isConsumed) break
                        tracker.addPointerInputChange(change)
                        val delta = change.positionChange()
                        if (dragging) {
                            change.consume()
                            swipe.anchored.dispatchRawDelta(delta.x * direction)
                            continue
                        }
                        total += delta
                        if (abs(total.x) < slop && abs(total.y) < slop) {
                            // The list scroll or the reorder drag may take it first.
                            awaitPointerEvent(PointerEventPass.Final)
                            if (change.isConsumed) break
                            continue
                        }
                        // Past the slop, the axis that moved more wins.
                        if (abs(total.x) <= abs(total.y)) break
                        dragging = true
                        swipe.cancel()
                        change.consume()
                        swipe.anchored.dispatchRawDelta((total.x - sign(total.x) * slop) * direction)
                    }
                }
            }
    ) {
        if (swipeEnabled) {
            // The Delete zone behind the row, no layout participation (iOS SwipeTrack): the 52
            // red disc with the white trash, "Delete" in caption2 under it.
            WalletDeleteZone(
                label = deleteLabel,
                enabled = swipe.zoneEnabled,
                onClick = {
                    swipe.close()
                    onDelete?.invoke()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { alpha = (-swipe.anchored.offset / swipe.revealPx).coerceIn(0f, 1f) }
            )
        }
        content()
    }
}

/**
 * One wallet row (iOS WalletRow): emoji, name, orange balance, address under
 * it, Edit Wallet pencil, and a check when it is the active wallet. A swipe
 * reveals the Delete zone ([SwipeToDeleteRow]); the row never re-lays-out
 * (iOS aa8e835 lesson).
 */
@Composable
private fun WalletRow(
    wallet: WalletInfo,
    isActive: Boolean,
    balanceText: String,
    modifier: Modifier = Modifier,
    isLifted: Boolean = false,
    onClick: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderDrag: (Float) -> Unit,
    onReorderEnd: (commit: Boolean) -> Unit,
    onRenameRequest: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDeleteRequest: (() -> Unit)?
) {
    val swipe = rememberRowSwipeState()
    SwipeToDeleteRow(
        swipe = swipe,
        deleteLabel = tr("Delete wallet"),
        onDelete = onDeleteRequest,
        modifier = modifier,
        locked = isLifted
    ) {
        WalletRowCard(
            emoji = wallet.emoji,
            name = wallet.name,
            address = wallet.cachedPrimaryAddress?.takeIf { SeedValidation.isPlausiblePrimaryAddress(it) },
            balanceText = balanceText,
            isActive = isActive,
            modifier = Modifier
                .fillMaxWidth()
                .offset { swipe.offset() }
                .clearAndSetSemantics {
                    contentDescription = "${wallet.name}, $balanceText"
                    selected = isActive
                    onClick(label = tr(if (isActive) "Close" else "Switch wallet")) { onClick(); true }
                    customActions = buildList {
                        add(CustomAccessibilityAction(tr("Edit")) { onRenameRequest(); true })
                        onDeleteRequest?.let { add(CustomAccessibilityAction(tr("Delete")) { it(); true }) }
                        onMoveUp?.let { add(CustomAccessibilityAction(tr("Move up")) { it(); true }) }
                        onMoveDown?.let { add(CustomAccessibilityAction(tr("Move down")) { it(); true }) }
                    }
                },
            onClick = {
                // A tap on an open row closes it and never switches.
                if (swipe.isOpen) {
                    swipe.close()
                } else {
                    onClick()
                }
            },
            // Room for the drag handle, which sits on top of the card (below).
            leading = { Spacer(Modifier.width(HandleSlot)) }
        ) {
            IconButton(onClick = onRenameRequest) {
                // iOS pencil.circle.fill in the secondary color at 0.5; the pencil is cut out.
                FilledCircleGlyph(
                    icon = Icons.Default.Edit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    contentDescription = tr("Edit Wallet")
                )
            }
            WalletRowCheck(isActive)
        }

        // The drag handle: a touch here lifts the row at once and moves it with the finger.
        // It sits over the card's leading edge, outside the card's cleared semantics.
        val start by rememberUpdatedState(onReorderStart)
        val drag by rememberUpdatedState(onReorderDrag)
        val end by rememberUpdatedState(onReorderEnd)
        Box(Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { swipe.offset() }
                    .fillMaxHeight()
                    .width(16.dp + HandleSlot)
                    // Hidden from TalkBack, as on iOS: the row has Move up / Move down, so each
                    // wallet stays one stop.
                    .clearAndSetSemantics {}
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            start()
                            var ended = false
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        ended = true
                                        end(true)
                                        break
                                    }
                                    val dy = change.positionChange().y
                                    change.consume()
                                    if (dy != 0f) drag(dy)
                                }
                            } finally {
                                // A cancelled gesture puts the row back where it was.
                                if (!ended) end(false)
                            }
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

/** The drag handle's slot before the emoji: the 24dp glyph and a 14dp gap, as on iOS. */
private val HandleSlot = 38.dp

/**
 * The wallet switcher's row card (iOS WalletRow): the emoji circle, the name, an optional
 * orange balance and the main address 8…8, then [trailing]. The active row carries the 1.5dp
 * brand ring at 0.7. The switcher, the claim sheet's "Claim to" row and its wallet list share
 * it; the claim sheet passes a fill one step up ([containerColor], [circleColor]) so a row
 * never matches the sheet in dark mode.
 */
@Composable
fun WalletRowCard(
    emoji: String,
    name: String,
    address: String?,
    modifier: Modifier = Modifier,
    balanceText: String? = null,
    isActive: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    circleColor: Color = CellFill,
    onClick: (() -> Unit)? = null,
    /** Below 1, a row that cannot be picked: the emoji, the name and the balance fade, the card stays. */
    contentAlpha: Float = 1f,
    /** In place of the address, at full strength: why a faded row cannot be picked. */
    note: String? = null,
    /** In place of [balanceText]'s text: a balance drawn by the caller, such as one that rolls. */
    balance: (@Composable () -> Unit)? = null,
    /** Before the emoji: the switcher's room for its drag handle. */
    leading: @Composable RowScope.() -> Unit = {},
    /** Last, so a caller's trailing lambda fills this slot, not [balance]. */
    trailing: @Composable RowScope.() -> Unit = {}
) {
    GlassCard(
        modifier = modifier.then(
            if (isActive) Modifier.border(1.5.dp, MoneroOrange.copy(alpha = 0.7f), CapsuleShape)
            else Modifier
        ),
        // A capsule, as the iOS glass button; the ring follows its edge.
        shape = CapsuleShape,
        color = containerColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Box(modifier = Modifier.alpha(contentAlpha)) {
                EmojiCircle(emoji = emoji, size = 44.dp, color = circleColor)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(contentAlpha)
                )
                if (balance != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(modifier = Modifier.alpha(contentAlpha)) { balance() }
                } else balanceText?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    // A balance never loses digits: it shrinks, then wraps.
                    ShrinkToFitText(
                        text = it,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MoneroOrange,
                        minScale = 0.6f,
                        wrapAtFloor = true,
                        modifier = Modifier.alpha(contentAlpha)
                    )
                }
                if (note != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else address?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = truncateMiddle(it),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            trailing()
        }
    }
}

/** The switcher's mark in a row's last slot (iOS WalletRowCheck): a green check circle on the
 * current wallet, an empty 24dp ring (2dp, secondary at 0.3) on the others. */
@Composable
fun WalletRowCheck(isActive: Boolean) {
    if (isActive) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = tr("Current wallet"),
            tint = SuccessGreen,
            modifier = Modifier.size(24.dp)
        )
    } else {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
        )
    }
}

/**
 * An SF Symbols `*.circle.fill` glyph at title3: a 20dp disc in [color] with [icon] cut out of it
 * (drawn in [cutout], the fill under the glyph).
 */
@Composable
internal fun FilledCircleGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentDescription: String? = null,
    cutout: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = cutout,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** The swipe's Delete zone (iOS SwipeTrack): a 52 red disc with the white trash, "Delete" in caption2
 * under it, 6 apart, 12 from the edge. TalkBack hears [label] as one button. */
@Composable
internal fun WalletDeleteZone(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(ErrorRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = tr("Delete"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Transfer can send back to this wallet, so one wallet is enough. A wallet that cannot send
 * (view-only) has no Transfer, and Add Wallet fills the row (iOS walletManager.canSend).
 */
internal fun switcherShowsTransfer(activeWallet: WalletInfo?): Boolean =
    activeWallet != null && !activeWallet.isViewOnly

/**
 * The switcher's last row: Transfer and Add Wallet side by side, 12 apart, as every button pair
 * (iOS WalletManagerRows). Without Transfer, Add Wallet fills the row.
 * At the largest text sizes the two stack, 12 apart, so neither label is cut (iOS accessibility sizes).
 */
@Composable
private fun WalletActionsRow(showTransfer: Boolean, onTransfer: () -> Unit, onAddWallet: () -> Unit) {
    val hint = tr("Moves XMR to another of your wallets, or back to this one")
    val transfer = @Composable { modifier: Modifier ->
        WalletActionCard(
            icon = Icons.Default.SwapHoriz,
            label = tr("Transfer"),
            onClick = onTransfer,
            // TalkBack reads the visible "Transfer"; the action label explains it.
            modifier = modifier.semantics { onClick(label = hint) { onTransfer(); true } }
        )
    }
    val addWallet = @Composable { modifier: Modifier ->
        WalletActionCard(icon = Icons.Default.Add, label = tr("Add Wallet"), onClick = onAddWallet, modifier = modifier)
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (showTransfer) transfer(Modifier.fillMaxWidth())
            addWallet(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (showTransfer) transfer(Modifier.weight(1f))
            addWallet(Modifier.weight(1f))
        }
    }
}

/** A switcher action (iOS glass capsule): the brand filled-circle glyph and the label (callout
 * semibold, brand), centered, 8 apart. */
@Composable
private fun WalletActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryButton(onClick = onClick, modifier = modifier) {
        FilledCircleGlyph(icon = icon, color = MoneroOrange)
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmojiCircle(emoji: String, size: androidx.compose.ui.unit.Dp, color: Color = CellFill) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/**
 * Edit Wallet (iOS EditWalletSheet): the icon, the name, Save, and Delete
 * Wallet last, for every wallet (a swipe deletes only inactive ones).
 * [onDelete] opens the caller's confirmation over the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWalletSheet(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit,
    onDelete: () -> Unit,
    title: String = tr("Edit Wallet")
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(wallet.name) }
    var emoji by remember { mutableStateOf(wallet.emoji) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Content color is explicit: in dark mode the card color equals the fill
    // color (#1C1C1E), and Material's contentColorFor would pick the gray
    // secondary label for it.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val canSave = name.trim().isNotEmpty()
        // Cancel, the title and Save in one bar, as in the New Contact sheet and on iOS.
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            DismissTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text(tr("Cancel")) }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center).semantics { heading() }
            )
            TextButton(
                onClick = { onSave(name.trim(), emoji) },
                enabled = canSave,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(tr("Save"), color = if (canSave) MoneroOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CellFill)
                    .clickable { showEmojiPicker = !showEmojiPicker }
                    .emojiCircleSemantics(emoji),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 36.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tr("Tap to change icon"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Spacer(modifier = Modifier.height(24.dp))

            MoneroTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Wallet name")) },
                singleLine = true,
                colors = moneroTextFieldColors(containerColor = CellFill),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Delete Wallet"), color = ErrorRed)
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
                        if (candidate == selected) MoneroOrange.copy(alpha = 0.15f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { onSelect(candidate) }
                    // iOS: the emoji's name, a button, selected when chosen.
                    .clearAndSetSemantics {
                        contentDescription = emojiSpokenName(candidate)
                        role = Role.Button
                        this.selected = candidate == selected
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = candidate, fontSize = 20.sp)
            }
        }
    }
}

/**
 * TalkBack for the emoji circle that opens the picker, as VoiceOver reads
 * iOS's EmojiPickerCircle: "Icon, money bag", a button.
 */
fun Modifier.emojiCircleSemantics(emoji: String): Modifier = clearAndSetSemantics {
    contentDescription = tr("Icon, %s", emojiSpokenName(emoji))
    role = Role.Button
}

/**
 * An emoji's Unicode name, lowercased ("money bag"), as iOS speaks it
 * (EmojiPickerSheet.spokenName): one name per code point, variation
 * selectors left out, "icon" when there is no name.
 */
fun emojiSpokenName(emoji: String): String {
    val names = mutableListOf<String>()
    var index = 0
    while (index < emoji.length) {
        val codePoint = emoji.codePointAt(index)
        index += Character.charCount(codePoint)
        val name = Character.getName(codePoint)?.lowercase(java.util.Locale.ROOT) ?: continue
        if (!name.startsWith("variation selector")) names += name
    }
    return if (names.isEmpty()) "icon" else names.joinToString(" ")
}
