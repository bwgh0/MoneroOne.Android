package one.monero.moneroone.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ReplayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.components.MoneroHeroLogo
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ProminentButton
import one.monero.moneroone.ui.theme.MoneroOrange

@Composable
fun WelcomeScreen(
    onCreateWallet: () -> Unit,
    onRestoreWallet: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Hero art (the glossy render lives only here and on Add Wallet)
            MoneroHeroLogo(size = 120.dp)

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Monero One",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Simple. Private. Secure.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            // Create Wallet: the one filled button, as on iOS
            ProminentButton(
                onClick = onCreateWallet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = "Create New Wallet")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Wallet: glass with a label-color title, as on iOS
            PrimaryButton(
                onClick = onRestoreWallet,
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.ReplayCircleFilled, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = "Restore Wallet")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AnimatedMoneroLogo(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 * 0.9f

        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MoneroOrange.copy(alpha = pulseAlpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = radius * 1.5f
            ),
            radius = radius * 1.5f,
            center = Offset(centerX, centerY)
        )

        // Main circle
        drawCircle(
            color = MoneroOrange,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 4.dp.toPx())
        )

        // Inner fill
        drawCircle(
            color = MoneroOrange.copy(alpha = 0.1f),
            radius = radius - 2.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // Monero "M" shape
        val mPath = Path().apply {
            val mWidth = radius * 1.2f
            val mHeight = radius * 0.8f
            val startX = centerX - mWidth / 2
            val startY = centerY + mHeight / 2

            moveTo(startX, startY)
            lineTo(startX, centerY - mHeight / 3)
            lineTo(centerX, centerY + mHeight / 6)
            lineTo(centerX + mWidth / 2, centerY - mHeight / 3)
            lineTo(centerX + mWidth / 2, startY)
        }

        drawPath(
            path = mPath,
            color = MoneroOrange,
            style = Stroke(width = 6.dp.toPx())
        )
    }
}
