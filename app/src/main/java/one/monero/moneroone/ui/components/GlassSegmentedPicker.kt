package one.monero.moneroone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Segmented picker after iOS GlassSegmentedPicker (tokens.json
 * components.segmented): a capsule track in the fill color and a sliding
 * elevated-fill capsule for the selection, with the raised shadow in light mode.
 * Labels are subheadline medium: the selected one in the label color and
 * semibold, the others secondary. Nothing here is orange.
 */
@Composable
fun <T> GlassSegmentedPicker(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelSelector: (T) -> String = { it.toString() }
) {
    val density = LocalDensity.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    // The thumb is the elevated fill (#FFFFFF light / #2C2C2E dark): in dark
    // mode the card and fill colors are both #1C1C1E, so a card-colored thumb
    // would vanish on the track.
    val thumbColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        var rowSize by remember { mutableStateOf(IntSize.Zero) }

        val segmentWidth = with(density) {
            if (rowSize.width > 0 && options.isNotEmpty()) {
                (rowSize.width / options.size).toDp()
            } else {
                0.dp
            }
        }

        // tokens.json motion.springs.selector: iOS spring(response 0.3, damping 0.7)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 438.6f),
            label = "indicatorOffset"
        )

        if (segmentWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .raisedShadow(CapsuleShape)
                    .clip(CapsuleShape)
                    .background(thumbColor)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { rowSize = it }
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelSelector(option),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
