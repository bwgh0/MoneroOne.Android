package one.monero.moneroone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
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

/** Height of a single-line text field (tokens.json size.fieldHeight). */
val FieldHeight = 54.dp

/** Field label to field (tokens.json space.named.labelGap); the supporting line keeps the same gap below. */
private val FieldLabelGap = 8.dp

/**
 * Material lays the input line in a box at least 24dp tall, so 15 above and
 * below makes a single-line field exactly [FieldHeight]: 15 + 24 + 15. Text
 * sits 16 from the edges, as on iOS (`.padding()`).
 */
private val FieldContentPadding = PaddingValues(
    horizontal = 16.dp,
    vertical = (FieldHeight - 24.dp) / 2
)

/**
 * The shared text field (tokens.json components.field): 54 tall for a single
 * line, radius 12, fill color, no border, brand caret, body text. The label
 * sits 8 above the field in subheadline and the secondary label color, never
 * inside it; supporting text sits 8 below in caption. The modifier sizes the
 * whole block: a caller's height (the seed phrase box) goes to the field, the
 * part that grows. Errors turn the label, caret and supporting text red.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // TextFieldColors resolves its state colors internally; pick them the same way.
    fun stateColor(focusedColor: Color, unfocusedColor: Color, disabledColor: Color, errorColor: Color) =
        when {
            !enabled -> disabledColor
            isError -> errorColor
            focused -> focusedColor
            else -> unfocusedColor
        }
    val textColor = textStyle.color.takeOrElse {
        stateColor(colors.focusedTextColor, colors.unfocusedTextColor, colors.disabledTextColor, colors.errorTextColor)
    }
    val labelColor = stateColor(
        colors.focusedLabelColor, colors.unfocusedLabelColor, colors.disabledLabelColor, colors.errorLabelColor
    )
    val supportingColor = stateColor(
        colors.focusedSupportingTextColor, colors.unfocusedSupportingTextColor,
        colors.disabledSupportingTextColor, colors.errorSupportingTextColor
    )
    val containerColor = stateColor(
        colors.focusedContainerColor, colors.unfocusedContainerColor,
        colors.disabledContainerColor, colors.errorContainerColor
    )
    val labelStyle = MaterialTheme.typography.bodyMedium
    val supportingStyle = MaterialTheme.typography.bodySmall

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .then(if (isError) Modifier.semantics { error("Invalid input") } else Modifier)
                .defaultMinSize(minWidth = TextFieldDefaults.MinWidth),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox = { innerTextField ->
                // The label and the supporting text live inside the text field
                // node, as Material's do, so TalkBack reads them with it.
                FieldLayout(
                    label = label?.let { content ->
                        {
                            CompositionLocalProvider(LocalContentColor provides labelColor) {
                                ProvideTextStyle(labelStyle.copy(color = labelColor), content)
                            }
                        }
                    },
                    field = {
                        TextFieldDefaults.DecorationBox(
                            value = value,
                            innerTextField = innerTextField,
                            enabled = enabled,
                            singleLine = singleLine,
                            visualTransformation = visualTransformation,
                            interactionSource = interactionSource,
                            isError = isError,
                            placeholder = placeholder,
                            leadingIcon = leadingIcon,
                            trailingIcon = trailingIcon,
                            prefix = prefix,
                            suffix = suffix,
                            colors = colors,
                            contentPadding = FieldContentPadding,
                            container = { Box(Modifier.background(containerColor, FieldShape)) }
                        )
                    },
                    supporting = supportingText?.let { content ->
                        {
                            CompositionLocalProvider(LocalContentColor provides supportingColor) {
                                ProvideTextStyle(supportingStyle.copy(color = supportingColor), content)
                            }
                        }
                    }
                )
            }
        )
    }
}

private val EmptySlot: @Composable () -> Unit = {}

/**
 * Label, field and supporting text in one column: label 8 above the field,
 * supporting text 8 below. The field is at least [FieldHeight] tall and takes
 * any extra height the caller's modifier gives the block, so a multi-line
 * field keeps the height its caller sets.
 */
@Composable
private fun FieldLayout(
    label: (@Composable () -> Unit)?,
    field: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)?
) {
    Layout(
        contents = listOf(label ?: EmptySlot, field, supporting ?: EmptySlot)
    ) { (labelMeasurables, fieldMeasurables, supportingMeasurables), constraints ->
        val gap = FieldLabelGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelPlaceable = labelMeasurables.firstOrNull()?.measure(loose)
        val supportingPlaceable = supportingMeasurables.firstOrNull()?.measure(loose)
        val above = labelPlaceable?.let { it.height + gap } ?: 0
        val below = supportingPlaceable?.let { it.height + gap } ?: 0

        val minFieldHeight = maxOf(FieldHeight.roundToPx(), constraints.minHeight - above - below)
        val maxFieldHeight = if (constraints.hasBoundedHeight) {
            maxOf(minFieldHeight, constraints.maxHeight - above - below)
        } else {
            Constraints.Infinity
        }
        val minFieldWidth = maxOf(
            constraints.minWidth,
            labelPlaceable?.width ?: 0,
            supportingPlaceable?.width ?: 0
        ).coerceAtMost(constraints.maxWidth)
        val fieldPlaceable = fieldMeasurables.first().measure(
            Constraints(
                minWidth = minFieldWidth,
                maxWidth = constraints.maxWidth,
                minHeight = minFieldHeight,
                maxHeight = maxFieldHeight
            )
        )

        val width = constraints.constrainWidth(fieldPlaceable.width)
        val height = constraints.constrainHeight(above + fieldPlaceable.height + below)
        layout(width, height) {
            labelPlaceable?.placeRelative(0, 0)
            fieldPlaceable.placeRelative(0, above)
            supportingPlaceable?.placeRelative(0, above + fieldPlaceable.height + gap)
        }
    }
}

/**
 * A dismissive text action such as Cancel, Close or Skip for Now: no fill, a
 * callout-semibold label in the secondary label color (tokens.json
 * components.buttonText). Confirming actions stay brand orange; destructive
 * ones take an ErrorRed label (components.buttonDestructive).
 */
@Composable
fun DismissTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        content = content
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
