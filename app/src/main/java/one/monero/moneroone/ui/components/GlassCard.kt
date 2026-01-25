package one.monero.moneroone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val backgroundColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.7f)
    }

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    val baseModifier = modifier
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = 1.dp,
            color = borderColor,
            shape = shape
        )

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
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

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val backgroundColor = if (isDarkTheme) {
        Color.White.copy(alpha = if (enabled) 0.1f else 0.05f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.8f else 0.4f)
    }

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = if (enabled) 0.15f else 0.08f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.6f else 0.3f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 20.dp,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(colors = colors)
        )

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
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
