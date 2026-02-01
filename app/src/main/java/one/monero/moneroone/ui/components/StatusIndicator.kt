package one.monero.moneroone.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    progress: Double? = null,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        when (status) {
            SyncStatus.Synced -> {
                StatusDot(color = SuccessGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
            SyncStatus.Syncing -> {
                LoadingIndicator(size = 12.dp)
                Spacer(modifier = Modifier.width(8.dp))
                val progressPercent = ((progress ?: 0.0) * 100).toInt()
                Text(
                    text = "Syncing $progressPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
            SyncStatus.Connecting -> {
                LoadingIndicator(size = 12.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connecting...",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
            SyncStatus.NotConnected -> {
                StatusDot(color = ErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
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
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .graphicsLayer { this.alpha = alpha }
                .background(color)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun LoadingIndicator(
    size: Dp = 12.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = strokeWidth,
        color = color
    )
}
