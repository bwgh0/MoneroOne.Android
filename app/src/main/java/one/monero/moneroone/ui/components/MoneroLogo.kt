package one.monero.moneroone.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.monero.moneroone.R

/**
 * Reusable Monero logo component with proper circular masking.
 * The logo is displayed larger than the container and clipped to create a tight crop.
 */
@Composable
fun MoneroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    // Scale factor for tighter crop - source image is pre-cropped to remove glow
    val imageSize = size * 2.2f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.monero_logo),
            contentDescription = "Monero",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(imageSize)
        )
    }
}
