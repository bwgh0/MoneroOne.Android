package one.monero.moneroone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.theme.CardBackgroundDark
import one.monero.moneroone.ui.theme.CardBackgroundLight
import one.monero.moneroone.ui.theme.CardBorderDark
import one.monero.moneroone.ui.theme.CardBorderLight
import one.monero.moneroone.ui.theme.MoneroOrange

/**
 * Glass-style segmented picker similar to iOS GlassSegmentedPicker
 */
@Composable
fun <T> GlassSegmentedPicker(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelSelector: (T) -> String = { it.toString() },
    cornerRadius: Dp = 12.dp
) {
    val isDarkTheme = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val innerShape = RoundedCornerShape(cornerRadius - 4.dp)

    val backgroundColor = if (isDarkTheme) CardBackgroundDark else CardBackgroundLight
    val borderColor = if (isDarkTheme) CardBorderDark else CardBorderLight

    val density = LocalDensity.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(4.dp)
    ) {
        var rowSize by remember { mutableStateOf(IntSize.Zero) }

        // Calculate segment width
        val segmentWidth = with(density) {
            if (rowSize.width > 0 && options.isNotEmpty()) {
                (rowSize.width / options.size).toDp()
            } else {
                0.dp
            }
        }

        // Animated selection indicator offset
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "indicatorOffset"
        )

        // Selection indicator
        if (segmentWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(innerShape)
                    .background(MoneroOrange)
            )
        }

        // Options row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { rowSize = it }
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption
                val textColor = if (isSelected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelSelector(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}
