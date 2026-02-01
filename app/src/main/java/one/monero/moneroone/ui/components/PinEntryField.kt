package one.monero.moneroone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.theme.MoneroOrange

/**
 * PIN entry field with animated dots matching iOS design.
 * Shows 6 dots that fill in as digits are entered.
 */
@Composable
fun PinEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    pinLength: Int = 6,
    dotSize: Dp = 16.dp,
    dotSpacing: Dp = 16.dp,
    filledColor: Color = MoneroOrange,
    emptyColor: Color = MaterialTheme.colorScheme.outlineVariant,
    nextColor: Color = MoneroOrange,
    isError: Boolean = false,
    onComplete: ((String) -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-focus on composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Trigger onComplete when PIN is full
    LaunchedEffect(value) {
        if (value.length == pinLength) {
            onComplete?.invoke(value)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Hidden text field for keyboard input
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.length <= pinLength && newValue.all { it.isDigit() }) {
                    onValueChange(newValue)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp) // Make it virtually invisible but focusable
        )

        // Visible dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(dotSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pinLength) { index ->
                PinDot(
                    isFilled = index < value.length,
                    isNext = index == value.length,
                    isError = isError,
                    size = dotSize,
                    filledColor = filledColor,
                    emptyColor = emptyColor,
                    nextColor = nextColor
                )
            }
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isNext: Boolean,
    isError: Boolean,
    size: Dp,
    filledColor: Color,
    emptyColor: Color,
    nextColor: Color
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isFilled -> 1f
            isNext -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dotScale"
    )

    val color = when {
        isError -> MaterialTheme.colorScheme.error
        isFilled -> filledColor
        else -> Color.Transparent
    }

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isNext -> nextColor
        isFilled -> filledColor
        else -> emptyColor
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = CircleShape
            )
    )
}
