package one.monero.moneroone.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.monero.moneroone.R

@Composable
fun MoneroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    // Day PNG has no padding around logo; night PNG has ~8.5% transparent
    // padding. Use a smaller scale on day so the circle clip doesn't cut
    // into logo content, matching night's visual proportions.
    val scaleFactor = if (isSystemInDarkTheme()) 1.2f else 1.0f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.monero_logo),
            contentDescription = "Monero",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .scale(scaleFactor)
        )
    }
}
