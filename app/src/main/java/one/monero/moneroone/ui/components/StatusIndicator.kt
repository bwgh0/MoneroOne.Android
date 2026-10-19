package one.monero.moneroone.ui.components

import one.monero.moneroone.core.locale.tr
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.PendingOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlin.math.ceil

enum class SyncStatus {
    Synced,
    Syncing,
    Connecting,
    NotConnected
}

enum class TransactionStatus {
    Pending,
    Locked,
    Confirmed,
    Failed
}

/**
 * Maps SyncState to a 0-5 stage index and display text, matching iOS ConnectionStepIndicator.
 *
 * Stage 0: Not connected
 * Stage 1: Reaching node
 * Stage 2: Connecting
 * Stage 3: Loading blocks
 * Stage 4: Scanning blocks
 * Stage 5: Synced
 */
private fun syncStateToStage(syncState: SyncState): Pair<Int, String> = when (syncState) {
    is SyncState.NotSynced -> 0 to "Not connected"
    is SyncState.Connecting -> if (syncState.waiting) {
        2 to "Connecting..."
    } else {
        1 to "Reaching node..."
    }
    is SyncState.Syncing -> {
        val progress = syncState.progress ?: 0.0
        if (progress < 0.05) {
            3 to "Loading blocks..."
        } else {
            4 to "Scanning ${(progress * 100).toInt()}%..."
        }
    }
    is SyncState.Synced -> 5 to "Synced"
}

/** The red sync error: a node that rejected the RPC login says so (iOS friendlyErrorMessage). */
internal fun syncErrorText(nodeLoginFailed: Boolean): String =
    if (nodeLoginFailed) tr("Node login failed. Check the username and password.") else tr("Not connected")

private const val TOTAL_STEPS = 6

/** Sync and status dots (tokens.json size.statusDot). */
val StatusDotSize = 8.dp

/**
 * Balance card sync status, in the iOS BalanceCard order: synced, then no
 * network (the ladder waits at stage 0, iOS ConnectionStage.noNetwork), then
 * the ladder, then the error. Text is caption in the secondary color; the
 * error text is red, as on iOS.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    progress: Double? = null,
    modifier: Modifier = Modifier,
    syncState: SyncState? = null,
    isOnline: Boolean = true,
    /** The sync stopped on a node that rejected the RPC login. */
    nodeLoginFailed: Boolean = false
) {
    if (status == SyncStatus.Synced) {
        // Simple synced display: green dot + "Synced"
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = SuccessGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tr("Synced"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (!isOnline) {
        ConnectionStepIndicator(currentStage = 0, statusText = tr("No network"), modifier = modifier)
    } else if (syncState != null && syncState !is SyncState.Synced && syncState !is SyncState.NotSynced) {
        // If we have a full SyncState, use the step indicator
        val (currentStage, statusText) = syncStateToStage(syncState)
        ConnectionStepIndicator(currentStage = currentStage, statusText = statusText, modifier = modifier)
    } else if (status == SyncStatus.NotConnected) {
        // A wrapped error keeps the dot on its first line: the dot is centred in a box one line
        // tall (the line box of bodySmall), so one line sits exactly as with centred alignment.
        val textStyle = MaterialTheme.typography.bodySmall
        val firstLineHeight = with(LocalDensity.current) {
            if (textStyle.lineHeight.isSp) ceil(textStyle.lineHeight.toPx()).toDp() else StatusDotSize
        }
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.height(firstLineHeight), contentAlignment = Alignment.Center) {
                StatusDot(color = ErrorRed)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = syncErrorText(nodeLoginFailed),
                style = textStyle,
                color = ErrorRed
            )
        }
    } else {
        // Fallback: show step indicator for Connecting/Syncing via syncState if available
        val fallbackState = when (status) {
            SyncStatus.Connecting -> SyncState.Connecting(waiting = false)
            SyncStatus.Syncing -> SyncState.Syncing(progress = progress)
            else -> SyncState.NotSynced(error = Throwable(tr("Unknown")))
        }
        val (currentStage, statusText) = syncStateToStage(fallbackState)
        ConnectionStepIndicator(currentStage = currentStage, statusText = statusText, modifier = modifier)
    }
}

@Composable
private fun ConnectionStepIndicator(
    currentStage: Int,
    statusText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        // Dot row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until TOTAL_STEPS) {
                if (i > 0) {
                    // Connecting line between dots
                    val lineColor = if (i <= currentStage) MoneroOrange
                        else MoneroTheme.colors.gray.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(lineColor)
                    )
                }

                when {
                    // Final stage (synced) gets green dot
                    i == TOTAL_STEPS - 1 && currentStage == TOTAL_STEPS - 1 -> {
                        StatusDot(color = SuccessGreen)
                    }
                    // Completed stages: filled orange
                    i < currentStage -> {
                        StatusDot(color = MoneroOrange)
                    }
                    // Current/active stage: pulsing orange
                    i == currentStage -> {
                        PulsingDot(color = MoneroOrange)
                    }
                    // Pending stages: outlined gray
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(StatusDotSize)
                                .clip(CircleShape)
                                .border(1.5.dp, MoneroTheme.colors.gray.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Status text below dots
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PulsingDot(
    color: Color,
    size: Dp = StatusDotSize
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun TransactionStatusIndicator(
    status: TransactionStatus,
    modifier: Modifier = Modifier
) {
    // A new status word cross-fades in (the confirmation ring's beat).
    androidx.compose.animation.Crossfade(
        targetState = status,
        animationSpec = Motion.crossFade(),
        modifier = modifier,
        label = "status"
    ) { shown ->
        val (text, color) = when (shown) {
            TransactionStatus.Pending -> tr("Pending") to PendingOrange
            TransactionStatus.Locked -> tr("Locked") to MoneroOrange
            TransactionStatus.Confirmed -> tr("Confirmed") to SuccessGreen
            TransactionStatus.Failed -> tr("Failed") to ErrorRed
        }
        StatusLabel(text = text, dotColor = color)
    }
}

/** A row's status spot: an 8 dot and a caption2 word, in the dot's hue unless [textColor] says (yellow keeps the label color). */
@Composable
fun StatusLabel(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = dotColor
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = dotColor)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    size: Dp = StatusDotSize,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false
) {
    if (pulsing) {
        PulsingDot(color = color, size = size)
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}
