package one.monero.moneroone.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.SystemFill

/** Radius of text fields, option cards, banners and QR frames (tokens.json radius.field). */
val FieldShape = RoundedCornerShape(12.dp)

/**
 * iOS switch colors: green track when on, white thumbs, a light neutral track
 * when off (#787880 at 0.16 light / 0.32 dark), no border. Never orange.
 */
@Composable
fun moneroSwitchColors(): SwitchColors {
    val offTrack = SystemFill
    val green = SuccessGreen
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = green,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = offTrack,
        uncheckedBorderColor = Color.Transparent,
        uncheckedIconColor = Color.Transparent,
        disabledCheckedThumbColor = Color.White,
        disabledCheckedTrackColor = green.copy(alpha = 0.4f),
        disabledCheckedBorderColor = Color.Transparent,
        disabledCheckedIconColor = Color.Transparent,
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.7f),
        disabledUncheckedTrackColor = offTrack.copy(alpha = offTrack.alpha * 0.5f),
        disabledUncheckedBorderColor = Color.Transparent,
        disabledUncheckedIconColor = Color.Transparent
    )
}

/**
 * The one switch style (tokens.json components.switch). The thumb keeps its
 * full size when off, as on iOS: Material shrinks it unless the thumb has content.
 */
@Composable
fun MoneroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = { Box(Modifier.size(SwitchDefaults.IconSize)) },
        enabled = enabled,
        colors = moneroSwitchColors()
    )
}

/**
 * Text field colors (tokens.json components.field): fill container, no
 * border or indicator line, brand caret, secondary labels. Fields that sit
 * inside a card or sheet pass that surface's contrasting fill as [containerColor].
 */
@Composable
fun moneroTextFieldColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledTextColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
): TextFieldColors {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MoneroTheme.colors.labelTertiary
    val red = ErrorRed
    return TextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = disabledTextColor,
        errorTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor,
        errorContainerColor = containerColor,
        cursorColor = MoneroOrange,
        errorCursorColor = red,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLeadingIconColor = secondary,
        unfocusedLeadingIconColor = secondary,
        disabledLeadingIconColor = secondary,
        errorLeadingIconColor = secondary,
        focusedTrailingIconColor = secondary,
        unfocusedTrailingIconColor = secondary,
        disabledTrailingIconColor = secondary,
        errorTrailingIconColor = red,
        focusedLabelColor = secondary,
        unfocusedLabelColor = secondary,
        disabledLabelColor = secondary,
        errorLabelColor = red,
        focusedPlaceholderColor = tertiary,
        unfocusedPlaceholderColor = tertiary,
        disabledPlaceholderColor = tertiary,
        errorPlaceholderColor = tertiary,
        focusedSupportingTextColor = secondary,
        unfocusedSupportingTextColor = secondary,
        disabledSupportingTextColor = secondary,
        errorSupportingTextColor = red,
        focusedPrefixColor = secondary,
        unfocusedPrefixColor = secondary,
        disabledPrefixColor = secondary,
        errorPrefixColor = secondary,
        focusedSuffixColor = secondary,
        unfocusedSuffixColor = secondary,
        disabledSuffixColor = secondary,
        errorSuffixColor = secondary
    )
}

/**
 * The shared text field: radius 12, fill color, no border, brand caret.
 * Errors show in the label, caret and supporting text, which turn red.
 */
@Composable
fun MoneroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    colors: TextFieldColors = moneroTextFieldColors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = FieldShape,
        colors = colors
    )
}

/** Pull-to-refresh spinner in the brand orange on the card surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneroRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        color = MoneroOrange
    )
}

/**
 * One line of text that steps its font size down until it fits its width,
 * never below [minScale] of the style's size: the iOS `minimumScaleFactor`.
 * The size is measured before drawing, so the text never flashes at full size.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minScale: Float = 0.7f
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth) {
            var candidate = style
            val floor = style.fontSize.value * minScale
            while (maxWidth != Constraints.Infinity &&
                candidate.fontSize.value * 0.95f >= floor &&
                measurer.measure(text, candidate, maxLines = 1, softWrap = false).size.width > maxWidth
            ) {
                candidate = candidate.copy(
                    fontSize = candidate.fontSize * 0.95f,
                    lineHeight = if (candidate.lineHeight.isSpecified) candidate.lineHeight * 0.95f else candidate.lineHeight
                )
            }
            candidate
        }
        Text(
            text = text,
            style = fitted,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
