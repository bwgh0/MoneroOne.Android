package one.monero.moneroone.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.PendingOrange
import one.monero.moneroone.ui.theme.SuccessGreen

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

private const val TOTAL_STEPS = 6

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    progress: Double? = null,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    syncState: SyncState? = null
) {
    // If we have a full SyncState, use the step indicator
    if (syncState != null && syncState !is SyncState.Synced && syncState !is SyncState.NotSynced) {
        ConnectionStepIndicator(syncState = syncState, modifier = modifier)
    } else if (status == SyncStatus.Synced) {
        // Simple synced display: green dot + "Synced"
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = SuccessGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Synced",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    } else if (status == SyncStatus.NotConnected) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = ErrorRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    } else {
        // Fallback: show step indicator for Connecting/Syncing via syncState if available
        val fallbackState = when (status) {
            SyncStatus.Connecting -> SyncState.Connecting(waiting = false)
            SyncStatus.Syncing -> SyncState.Syncing(progress = progress)
            else -> SyncState.NotSynced(error = Throwable("Unknown"))
        }
        ConnectionStepIndicator(syncState = fallbackState, modifier = modifier)
    }
}

@Composable
private fun ConnectionStepIndicator(
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    val (currentStage, statusText) = syncStateToStage(syncState)

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
                        else Color.Gray.copy(alpha = 0.3f)
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Status text below dots
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PulsingDot(
    color: Color,
    size: Dp = 8.dp
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
    val (text, color) = when (status) {
        TransactionStatus.Pending -> "Pending" to PendingOrange
        TransactionStatus.Locked -> "Locked" to MoneroOrange
        TransactionStatus.Confirmed -> "Confirmed" to SuccessGreen
        TransactionStatus.Failed -> "Failed" to ErrorRed
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = color, size = 6.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    size: Dp = 8.dp,
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
