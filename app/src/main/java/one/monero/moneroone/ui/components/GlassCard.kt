package one.monero.moneroone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.theme.GradientOrangeEnd
import one.monero.moneroone.ui.theme.GradientOrangeStart

/**
 * M3 Expressive Card with spring animations and modern styling
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // Use MaterialTheme colors to respect explicit darkTheme parameter
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val elevation = if (isDarkTheme) 0.dp else 2.dp

    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (!isDarkTheme) {
                Modifier.shadow(elevation, shape, clip = false)
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = 1.dp,
            color = borderColor,
            shape = shape
        )

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.primary),
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
 * Glass-style button with spring press animation
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Use MaterialTheme colors to match GlassCard and respect explicit darkTheme parameter
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (enabled) 1f else 0.6f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
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
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

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

/**
 * Primary action button with filled style
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.6f
            }
            .shadow(if (enabled) 4.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(color)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Secondary/outline button style
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Use MaterialTheme to determine if dark theme
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val backgroundColor = if (isDarkTheme) {
        Color.Transparent
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.6f
            }
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = borderColor.copy(alpha = if (enabled) 1f else 0.5f),
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = borderColor),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
