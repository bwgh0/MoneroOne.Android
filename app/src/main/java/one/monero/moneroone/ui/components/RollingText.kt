package one.monero.moneroone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * Text whose digits roll vertically when the value changes, the Compose
 * counterpart of iOS `.contentTransition(.numericText())` +
 * `.monospacedDigit()` on the balance card and the send amount.
 *
 * Each character animates on its own: digits slide up when they grow and
 * down when they shrink, everything else cross-fades. Tabular figures keep
 * the width stable so neighbouring glyphs do not shuffle mid-roll.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    val tabular = style.copy(fontFeatureSettings = "tnum")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, ch ->
            AnimatedContent(
                targetState = ch,
                modifier = Modifier.clipToBounds(),
                transitionSpec = {
                    val spec = tween<Float>(Motion.DIGIT_MS, easing = FastOutSlowInEasing)
                    val offset = tween<androidx.compose.ui.unit.IntOffset>(Motion.DIGIT_MS, easing = FastOutSlowInEasing)
                    if (targetState.isDigit() && initialState.isDigit()) {
                        val up = targetState > initialState
                        (slideInVertically(offset) { h -> if (up) h else -h } + fadeIn(spec))
                            .togetherWith(slideOutVertically(offset) { h -> if (up) -h else h } + fadeOut(spec))
                    } else {
                        fadeIn(spec).togetherWith(fadeOut(spec))
                    }.using(SizeTransform(clip = false))
                },
                label = "roll$index"
            ) { c ->
                androidx.compose.material3.Text(
                    text = c.toString(),
                    style = tabular,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
