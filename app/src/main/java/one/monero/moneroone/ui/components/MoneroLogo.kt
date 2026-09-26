package one.monero.moneroone.ui.components

import androidx.compose.foundation.Image
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

/**
 * The official flat Monero mark (vector, #FF6600 / #4C4C4C). By day the M is
 * white (drawable/monero_mark); by night it is see-through, so the dark
 * background shows through it (drawable-night/monero_mark). Every logo in
 * the app uses it except the glossy hero art ([MoneroHeroLogo]).
 */
@Composable
fun MoneroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Image(
        painter = painterResource(id = R.drawable.monero_mark),
        contentDescription = "Monero",
        modifier = modifier.size(size)
    )
}

/**
 * Glossy hero art from the iOS app (light and night variants, converted to
 * sRGB). Hero use only: Welcome and Add Wallet. Like iOS, the art is scaled
 * 1.15 and clipped to a circle so only the coin shows, not its plate.
 */
@Composable
fun MoneroHeroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.monero_hero),
            contentDescription = "Monero",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .scale(1.15f)
        )
    }
}
