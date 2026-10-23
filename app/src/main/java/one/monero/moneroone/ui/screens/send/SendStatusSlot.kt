package one.monero.moneroone.ui.screens.send

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.SendGate
import one.monero.moneroone.core.wallet.SendWait
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.SyncStatus
import one.monero.moneroone.ui.components.SyncStatusIndicator
import one.monero.moneroone.ui.components.syncErrorText
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroTheme

/**
 * Review's wait for the main wallet ([SendGate]), on the wallet's state as the balance card shows it.
 * [states] feeds the status slot; a build waits in [awaitReady] and, after a failure, asks [retriesFailure]
 * whether to run again.
 */
@Stable
class SendWaitGate(
    private val syncState: () -> SyncState,
    private val nodeLoginFailed: () -> Boolean
) {
    val states: Flow<SendWait> = SendGate.states(syncState, nodeLoginFailed)

    fun now(): SendWait = SendGate.now(syncState(), nodeLoginFailed())

    fun readyNow(): Boolean = now() == SendWait.Ready

    suspend fun awaitReady() {
        states.first { it == SendWait.Ready }
    }

    /**
     * A build failed. True when it runs again: the wallet stopped meanwhile (the build waits for it in
     * [awaitReady]), or the network came back or changed moments ago and the node is not reached yet (the build
     * pauses [SendGate.SETTLE_RETRY_MS] first). False: a real failure, which the review shows.
     */
    suspend fun retriesFailure(): Boolean {
        if (!readyNow()) return true
        if (!SendGate.networkSettling()) return false
        delay(SendGate.SETTLE_RETRY_MS)
        return true
    }
}

@Composable
internal fun rememberSendWaitGate(walletViewModel: WalletViewModel): SendWaitGate = remember(walletViewModel) {
    SendWaitGate(
        syncState = { walletViewModel.walletState.value.syncState },
        nodeLoginFailed = { walletViewModel.walletState.value.nodeLoginFailed }
    )
}

@Composable
internal fun SendWaitGate.collectAsState(): State<SendWait> = remember(this) { states }.collectAsState(initial = remember(this) { now() })

/** A wait shorter than this shows no status, so a quick build does not flash it. */
private const val SLOT_DELAY_MS = 500L

/** tokens.json crossFade: easeInOut, 0.35 s. */
private const val CROSS_FADE_MS = 350

private sealed interface SlotContent {
    data object None : SlotContent
    data class Ladder(val syncState: SyncState) : SlotContent
    data object Synced : SlotContent
    data object Offline : SlotContent
    data class NotConnected(val nodeLoginFailed: Boolean) : SlotContent
    data class Failed(val message: String) : SlotContent
}

/**
 * The one status slot directly above Review's Send (mockup send-wait-sync): the balance card's sync ladder while
 * the wallet connects, loads or scans, green "Synced" while the transaction builds, or a banner for a real
 * failure: gray offline, red node or build error, each with Retry. It shows only once the wait (for the wallet,
 * or for the build of a wallet that was synced on arrival) passes [SLOT_DELAY_MS]; a build failure shows at once.
 * It fades out when the values fill in.
 */
@Composable
internal fun SendStatusSlot(
    wait: SendWait,
    /** The review has no transaction yet: it waits for the wallet or builds. */
    pending: Boolean,
    /** The build failed: this message, in the red banner. */
    error: String?,
    /** Retry on a wait banner (offline, node not reached). */
    onRetryWait: () -> Unit,
    /** Retry on a build failure. */
    onRetryBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waiting = pending && error == null
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        if (waiting) {
            delay(SLOT_DELAY_MS)
            shown = true
        }
    }
    LaunchedEffect(pending, error) { if (!pending || error != null) shown = false }

    val content = when {
        error != null -> SlotContent.Failed(error)
        !pending || !shown -> SlotContent.None
        else -> when (wait) {
            SendWait.Ready -> SlotContent.Synced
            SendWait.Offline -> SlotContent.Offline
            is SendWait.NotConnected -> SlotContent.NotConnected(wait.nodeLoginFailed)
            is SendWait.Syncing -> SlotContent.Ladder(wait.syncState)
        }
    }
    val fade = tween<Float>(CROSS_FADE_MS, easing = Motion.EaseInOut)
    AnimatedContent(
        targetState = content,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        contentKey = { it::class },
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.fillMaxWidth(),
        label = "sendStatusSlot"
    ) { shownContent ->
        when (shownContent) {
            SlotContent.None -> Box(Modifier.fillMaxWidth())
            is SlotContent.Ladder -> Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                SyncStatusIndicator(
                    status = SyncStatus.Syncing,
                    syncState = shownContent.syncState,
                    horizontalAlignment = Alignment.CenterHorizontally
                )
            }
            SlotContent.Synced -> Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                SyncStatusIndicator(status = SyncStatus.Synced)
            }
            SlotContent.Offline -> SlotBanner(
                icon = Icons.Default.WifiOff,
                tint = MoneroTheme.colors.gray,
                text = tr("No internet connection"),
                onRetry = onRetryWait
            )
            is SlotContent.NotConnected -> SlotBanner(
                icon = Icons.Default.Error,
                tint = ErrorRed,
                text = if (shownContent.nodeLoginFailed) syncErrorText(nodeLoginFailed = true)
                else tr("Could not reach the node. Check your connection and try again."),
                onRetry = onRetryWait
            )
            is SlotContent.Failed -> SlotBanner(
                icon = Icons.Default.Error,
                tint = ErrorRed,
                text = shownContent.message,
                onRetry = onRetryBuild
            )
        }
    }
}

/** The app's banner (radius 12, 10% tint of [tint], label-color text) with a Retry at its end in [tint]. */
@Composable
private fun SlotBanner(icon: ImageVector, tint: Color, text: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 16.dp)
        )
        // The 48 dp target reaches into the banner's padding; the label ends 16 dp from the edge.
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(tr("Retry"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

/**
 * A value still to come: a gray bar in a line of [style], as iOS `.redacted(reason: .placeholder)`. The line keeps
 * the value's height, so nothing moves when the value fills in.
 */
@Composable
internal fun ValuePlaceholder(width: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    val color = MoneroTheme.colors.gray.copy(alpha = 0.28f)
    // The bar is about three quarters of the font size tall (11 on a 15 line, 9 on a 12 line).
    val barHeight = with(LocalDensity.current) { style.fontSize.toDp() } * 0.74f
    Text(
        text = " ",
        style = style,
        modifier = modifier
            .width(width)
            .clearAndSetSemantics {}
            .drawBehind {
                val h = barHeight.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, (size.height - h) / 2),
                    size = Size(size.width, h),
                    cornerRadius = CornerRadius(h / 2)
                )
            }
    )
}

/** A review value that fills in where its placeholder was: a cross-fade, anchored at the end of its row. */
@Composable
internal fun FillIn(ready: Boolean, placeholder: @Composable () -> Unit, value: @Composable () -> Unit) {
    val fade = tween<Float>(CROSS_FADE_MS, easing = Motion.EaseInOut)
    AnimatedContent(
        targetState = ready,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
        contentAlignment = Alignment.TopEnd,
        label = "fillIn"
    ) { filled -> if (filled) value() else placeholder() }
}

/** TalkBack on the gray Send while Review waits for the wallet. Labels only: nothing visible changes. */
internal fun Modifier.sendWaitHint(waiting: Boolean): Modifier =
    if (!waiting) this else semantics { stateDescription = tr("Waiting for your wallet to sync") }
