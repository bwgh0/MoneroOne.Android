package one.monero.moneroone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.theme.GradientOrangeEnd
import one.monero.moneroone.ui.theme.GradientOrangeStart
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroOrangePressed
import one.monero.moneroone.ui.theme.MoneroTheme

/** Every button, chip, pill, tab bar and segmented control (tokens.json radius.full). */
val CapsuleShape = RoundedCornerShape(percent = 50)

/** Visible height of a prominent or primary capsule (tokens.json size.buttonHeight). */
val ButtonHeight = 56.dp

/** Compact capsules: sheet toolbars, inline actions (tokens.json size.buttonHeightCompact). */
val ButtonHeightCompact = 44.dp

private val ButtonContentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/**
 * The light-mode card elevation, iOS `0 4 12 rgba(0,0,0,0.08)` (tokens.json
 * elevation.card). Android multiplies these colors by the theme's shadow
 * alphas (spot about 0.19, ambient about 0.04), so the spot gives the ~4%
 * darkening under the card and the strong ambient color the faint ~2% halo
 * iOS shows on the other edges. Dark mode has no shadow: the fill carries depth.
 */
@Composable
fun Modifier.cardShadow(shape: Shape, enabled: Boolean = true): Modifier =
    if (enabled && !MoneroTheme.isDark) {
        this.shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.5f),
            spotColor = Color.Black.copy(alpha = 0.22f)
        )
    } else {
        this
    }

/**
 * The light-mode "raised" elevation, iOS `0 2 8 rgba(0,0,0,0.08)` (tokens.json
 * elevation.raised): small stat cards, chips and segmented thumbs that float.
 */
@Composable
fun Modifier.raisedShadow(shape: Shape): Modifier =
    if (!MoneroTheme.isDark) {
        this.shadow(
            elevation = 4.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.4f),
            spotColor = Color.Black.copy(alpha = 0.20f)
        )
    } else {
        this
    }

/**
 * The floating tab bar elevation, iOS `0 10 30 rgba(0,0,0,0.12)` (tokens.json
 * elevation.float). On the black dark-mode page a shadow cannot show, so it
 * is drawn in light mode only.
 */
@Composable
fun Modifier.floatShadow(shape: Shape): Modifier =
    if (!MoneroTheme.isDark) {
        this.shadow(
            elevation = 16.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.6f),
            spotColor = Color.Black.copy(alpha = 0.30f)
        )
    } else {
        this
    }

/**
 * Cells, chips and discs that sit on a card or sheet: the fill (#F2F2F7) on
 * the white light-mode card, the elevated fill (#2C2C2E) on the dark one.
 */
val CellFill: Color
    @Composable @ReadOnlyComposable
    get() = if (MoneroTheme.isDark) MoneroTheme.colors.fillElevated else MoneroTheme.colors.fill

/** The tokens.json `press` curve: scale 0.98, critically damped, fast. */
@Composable
private fun pressScale(interactionSource: MutableInteractionSource, enabled: Boolean = true): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1500f),
        label = "pressScale"
    )
    return scale
}

/**
 * Card: radius 20, surface fill, no border, the card shadow in light mode
 * only. Settings groups and table cards pass `cornerRadius = 16.dp`; cards on
 * a grouped (gray) page pass `shadow = false`, as iOS grouped lists have none.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    shadow: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, enabled = onClick != null)

    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .cardShadow(shape, enabled = shadow)
        .clip(shape)
        .background(color)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.onSurface),
            onClick = onClick
        )
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        content = content
    )
}

/**
 * Glass-look capsule for tiles and keys (Send / Receive, keypad): surface
 * fill with the card shadow in light mode. The caller lays out the content.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CapsuleShape,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, enabled)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .cardShadow(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.onSurface),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Gradient card for balance display with orange gradient
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    colors: List<Color> = listOf(GradientOrangeStart, GradientOrangeEnd),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, enabled = onClick != null)

    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(8.dp, shape, clip = false)
        .clip(shape)
        .background(
            brush = Brush.linearGradient(colors = colors)
        )

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White),
            onClick = onClick
        )
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        content = content
    )
}

/** Label row shared by the capsule buttons: callout semibold, 8dp icon gap. */
@Composable
private fun ButtonLabel(
    contentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = contentColor)) {
            Row(
                modifier = Modifier.padding(ButtonContentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Filled brand capsule with a white label (iOS `glassProminentButtonStyle()`
 * tinted brand). One per flow entry: Create New Wallet and its equivalents.
 */
@Composable
fun ProminentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = pressScale(interactionSource, enabled)
    val fill = when {
        !enabled -> MoneroOrange.copy(alpha = 0.4f)
        isPressed -> MoneroOrangePressed
        else -> MoneroOrange
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = ButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CapsuleShape)
            .background(fill)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        ButtonLabel(contentColor = Color.White, content = content)
    }
}

/**
 * The iOS glass button (`glassButtonStyle()`): surface capsule with the card
 * shadow in light mode, brand label and optional leading icon. Disabled
 * labels turn gray. Pass [contentColor] for the few label-color or green
 * cases iOS has (Welcome Restore, Receive). On a card or sheet, pass
 * `colorScheme.surfaceContainerHigh` as [containerColor] so the capsule still
 * shows on the dark #1C1C1E surface.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MoneroOrange,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, enabled)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = ButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .cardShadow(CapsuleShape)
            .clip(CapsuleShape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = contentColor),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        ButtonLabel(
            contentColor = if (enabled) contentColor else MoneroTheme.colors.gray,
            content = content
        )
    }
}

/**
 * Secondary emphasis: capsule tinted 0.15 of its hue, label in the hue
 * (tokens.json components.buttonTinted).
 */
@Composable
fun TintedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MoneroOrange,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource, enabled)
    val hue = if (enabled) color else MoneroTheme.colors.gray

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = ButtonHeightCompact)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CapsuleShape)
            .background(hue.copy(alpha = 0.15f))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = hue),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        ButtonLabel(contentColor = hue, content = content)
    }
}
