package one.monero.moneroone.ui.screens.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.icu.text.ListFormatter
import android.os.LocaleList
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.locale.pluralTr
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.seed.SeedDates
import one.monero.moneroone.core.wallet.seed.SeedFormat
import one.monero.moneroone.core.wallet.seed.SeedPhraseValidator.Status
import one.monero.moneroone.ui.components.GlassSegmentedPicker
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.screens.onboarding.SeedEntryState.Effect
import one.monero.moneroone.ui.screens.onboarding.SeedEntryState.Focus
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoFamily
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.TabularFigures
import java.util.Locale

/**
 * The seed input of the restore screen's seed step (seed-entry spec 2.1, iOS
 * SeedPhraseEntryView): word count and Paste, the numbered word grid, and the
 * status line with Clear. The step's title, hint, birthday and Continue stay
 * in RestoreWalletScreen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeedPhraseEntry(
    entry: SeedEntryState,
    requesters: List<FocusRequester>,
    statusRequester: BringIntoViewRequester,
    onEffect: (Effect) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // The cells use a password input type, which can make a password manager
    // offer to save the words. Autofill skips this window's Compose content
    // while the seed entry shows.
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { view.importantForAutofill = previous }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SeedEntryControls(
            format = entry.format,
            onFormat = { onEffect(entry.setFormat(it)) },
            onPaste = { onEffect(entry.paste(clipboardText(context))) }
        )
        SeedWordGrid(entry, requesters, onEffect)
        SeedStatusRow(
            entry,
            onClear = { onEffect(entry.clearAll()) },
            modifier = Modifier.bringIntoViewRequester(statusRequester)
        )
    }
}

/**
 * Up to three list words for the focused cell, directly above the keyboard,
 * and a trailing button that hides the keyboard (iOS SeedSuggestionBar: the
 * toolbar's capsule items). The words spread over the bar, one word centered.
 * It keeps its height while empty, so the grid above it does not jump.
 */
@Composable
internal fun SeedSuggestionBar(entry: SeedEntryState, onEffect: (Effect) -> Unit, modifier: Modifier = Modifier) {
    val words = entry.suggestions
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // A long word shortens (ellipsis) before the bar overflows; chips keep 8 apart.
        val chipMax = if (words.isEmpty()) maxWidth else (maxWidth - SuggestionHeight - 8.dp * words.size) / words.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            words.forEachIndexed { i, word ->
                if (i > 0 || words.size == 1) Spacer(modifier = Modifier.weight(1f))
                GlassButton(
                    onClick = { onEffect(entry.acceptSuggestion(word)) },
                    modifier = Modifier.height(SuggestionHeight).widthIn(max = chipMax)
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            GlassButton(
                onClick = { onEffect(Effect(SeedEntryState.Focus.Hide)) },
                modifier = Modifier.size(SuggestionHeight)
            ) {
                Icon(
                    Icons.Default.KeyboardHide,
                    contentDescription = tr("Hide keyboard"),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private val SuggestionHeight = 44.dp

@Composable
private fun SeedEntryControls(format: SeedFormat, onFormat: (SeedFormat) -> Unit, onPaste: () -> Unit) {
    val numberOfWords = tr("Number of words")
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Side by side when the picker labels fit next to Paste, else stacked (iOS ViewThatFits).
        val fontScale = LocalDensity.current.fontScale
        val stacked = maxWidth < 340.dp * fontScale
        // At the largest font sizes the segments show the number only ("16 words"
        // does not fit there); TalkBack still reads "16 words" (iOS does the same).
        val numbersOnly = fontScale >= 1.5f
        val picker = @Composable { pickerModifier: Modifier ->
            GlassSegmentedPicker(
                options = SeedFormat.entries,
                selectedOption = format,
                onOptionSelected = onFormat,
                modifier = pickerModifier.semantics { contentDescription = numberOfWords },
                accessibilityLabel = { pluralTr("%s words", it.wordCount) },
                labelSelector = { if (numbersOnly) it.wordCount.toString() else pluralTr("%s words", it.wordCount) }
            )
        }
        val paste = @Composable { pasteModifier: Modifier ->
            // A minimum, not a fixed height: the label grows with the font size.
            PrimaryButton(onClick = onPaste, modifier = pasteModifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(tr("Paste"))
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                picker(Modifier.fillMaxWidth())
                paste(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                picker(Modifier.weight(1f))
                paste(Modifier)
            }
        }
    }
}

/**
 * Equal columns, filled left to right then down: 2 on a phone, more on wide
 * screens, 1 at the largest font sizes. Not lazy: every visible cell's field
 * exists, so focus can move to any of them.
 */
@Composable
private fun SeedWordGrid(entry: SeedEntryState, requesters: List<FocusRequester>, onEffect: (Effect) -> Unit) {
    val count = entry.wordCount
    val gridLabel = tr("Seed Phrase")
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().semantics { contentDescription = gridLabel }) {
        val columns = if (LocalDensity.current.fontScale >= 1.5f) {
            1
        } else {
            ((maxWidth + CellSpacing) / (MinColumnWidth + CellSpacing)).toInt().coerceIn(1, 4)
        }
        Column(verticalArrangement = Arrangement.spacedBy(CellSpacing)) {
            for (start in 0 until count step columns) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing)) {
                    for (index in start until start + columns) {
                        if (index < count) {
                            SeedWordCell(entry, index, requesters[index], onEffect, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One numbered cell: "N." in secondary, the word in mono. Focused cells get
 * a brand stroke; invalid ones a red stroke, red text and an error glyph, so
 * the state never rests on color alone.
 */
@Composable
private fun SeedWordCell(
    entry: SeedEntryState,
    index: Int,
    requester: FocusRequester,
    onEffect: (Effect) -> Unit,
    modifier: Modifier
) {
    val value = entry.cell(index)
    val isInvalid = index in entry.review.invalidCells
    val isFocused = entry.focusedIndex == index
    val isLast = index == entry.wordCount - 1
    val shape = RoundedCornerShape(12.dp)
    val stroke = when {
        isInvalid -> ErrorRed
        isFocused -> MoneroOrange
        else -> Color.Transparent
    }
    val wordLabel = tr("Word %s", index + 1)
    val emptyLabel = tr("Empty")
    val invalidLabel = tr("Not in the word list")
    var wasFocused by remember { mutableStateOf(false) }
    val numberStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularFigures)

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, stroke, shape)
            // A tap anywhere in the cell focuses its field (no semantics: the field is the element).
            .pointerInput(requester) { detectTapGestures { runCatching { requester.requestFocus() } } }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Right-aligned in the width of "25.". TalkBack hears "Word N" from the field instead.
        Box(modifier = Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.CenterEnd) {
            Text("25.", style = numberStyle, modifier = Modifier.alpha(0f))
            Text("${index + 1}.", style = numberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SeedWordInput(onDeleteWhenEmpty = { emptyBackspace(entry, index, onEffect) }) {
            BasicTextField(
                value = value,
                onValueChange = { onEffect(entry.onValueChange(index, it)) },
                modifier = Modifier
                    .weight(1f)
                    // As tall as the cell: the scroll view keeps the focused field
                    // in view (focus moves, the keyboard opens), so the whole cell shows.
                    .heightIn(min = 48.dp)
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (it.isFocused != wasFocused) {
                            wasFocused = it.isFocused
                            entry.onFocusChanged(index, it.isFocused)
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Backspace -> emptyBackspace(entry, index, onEffect)
                            Key.Enter, Key.NumPadEnter -> {
                                onEffect(entry.commit(index))
                                true
                            }
                            Key.Tab -> if (event.isShiftPressed) {
                                false
                            } else {
                                onEffect(entry.commit(index))
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics {
                        contentDescription = wordLabel
                        if (value.text.isEmpty()) stateDescription = emptyLabel
                        if (isInvalid) error(invalidLabel)
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MonoFamily,
                    color = if (isInvalid) ErrorRed else MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MoneroOrange),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onEffect(entry.commit(index)) },
                    onDone = { onEffect(entry.commit(index)) }
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                        innerTextField()
                    }
                }
            )
        }
        if (isInvalid) {
            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SeedStatusRow(entry: SeedEntryState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val status = entry.review.status
    val locale = Locale.getDefault()
    val error = ErrorRed
    val (text, color) = if (entry.showsCountError) {
        tr("Please enter 16, 24, or 25 words") to error
    } else {
        when (status) {
            is Status.Progress -> tr("%s of %s words", status.filled, status.total) to MaterialTheme.colorScheme.onSurfaceVariant
            is Status.CheckWords -> {
                val words = if (status.numbers.size == 1) {
                    tr("Check word %s", status.numbers[0])
                } else {
                    tr("Check words %s", ListFormatter.getInstance(locale).format(status.numbers))
                }
                words to error
            }
            Status.InvalidPhrase -> tr("Invalid seed phrase. Please check your words and try again.") to error
            Status.Passphrase -> tr("This Polyseed uses a passphrase. Monero One does not support Polyseed passphrases yet.") to error
            is Status.Valid -> tr("Valid seed phrase") to SuccessGreen
        }
    }
    val glyph = when {
        color == error -> Icons.Default.Error
        status is Status.Valid && !entry.showsCountError -> Icons.Default.CheckCircle
        else -> null
    }
    val birthday = (status as? Status.Valid)?.polyseedBirthdayUnix?.takeIf { !entry.showsCountError }
    val detail = birthday?.let {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMyyyy")
        tr("Wallet created around %s", SeedDates.monthYear(it, pattern, locale))
    }

    // One live region: TalkBack reads status changes, the birthday line with them.
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                glyph?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) }
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
            }
            if (entry.hasText) {
                TextButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = MoneroOrange)) {
                    Text(tr("Clear"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        detail?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun emptyBackspace(entry: SeedEntryState, index: Int, onEffect: (Effect) -> Unit): Boolean {
    val effect = entry.deleteBackwardInEmptyCell(index)
    if (effect.focus == Focus.Keep) return false
    onEffect(effect)
    return true
}

/** The clipboard's text, read when the person taps Paste. */
private fun clipboardText(context: Context): String {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
}

/**
 * Sets the keyboard for the seed word field inside [content]:
 * - The keyboard does not learn the words (IME_FLAG_NO_PERSONALIZED_LEARNING,
 *   the flag behind incognito typing).
 * - Only the app's suggestion row shows: see [seedWordInputType].
 *
 * [onDeleteWhenEmpty] sees backspace in an empty field, which changes no
 * text: keyboards report it as a delete key event or as
 * deleteSurroundingText. It returns true when it handled the key.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SeedWordInput(onDeleteWhenEmpty: (() -> Boolean)? = null, content: @Composable () -> Unit) {
    val onDelete by rememberUpdatedState(onDeleteWhenEmpty)
    val context = LocalContext.current
    val inputMethods = remember(context) { context.getSystemService(InputMethodManager::class.java) }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val seedField = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    // Read each time a cell gets focus, so a language change applies from the next cell.
                    val subtype = runCatching { inputMethods?.currentInputMethodSubtype }.getOrNull()
                    outAttributes.inputType = seedWordInputType(
                        outAttributes.inputType,
                        keepTextType = keepsTextInputType(subtype?.isAsciiCapable, typingLanguages(subtype))
                    )
                    if (onDelete == null) return connection
                    return object : InputConnectionWrapper(connection, false) {
                        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
                            (beforeLength > 0 && afterLength == 0 && onDelete?.invoke() == true) ||
                                super.deleteSurroundingText(beforeLength, afterLength)

                        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
                            (beforeLength > 0 && afterLength == 0 && onDelete?.invoke() == true) ||
                                super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)

                        override fun sendKeyEvent(event: KeyEvent): Boolean =
                            (event.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && onDelete?.invoke() == true) ||
                                super.sendKeyEvent(event)
                    }
                }
            }
            nextHandler.startInputMethod(seedField)
        },
        content = content
    )
}

/**
 * The input type of a seed word field. [inputType] is the type the text field
 * asked for.
 *
 * VISIBLE_PASSWORD plus NO_SUGGESTIONS hides the keyboard's own suggestion
 * strip (Gboard included), so only the app's row shows. Glide typing stops
 * in these fields. VISIBLE_PASSWORD, not PASSWORD: with PASSWORD, TalkBack
 * reads each letter as "dot". NO_SUGGESTIONS stops autocorrect (turning
 * autocorrect off is not enough for AOSP LatinIME), but Gboard still shows
 * its strip with NO_SUGGESTIONS alone.
 *
 * [keepTextType] (see [keepsTextInputType]) keeps the text field's type plus
 * NO_SUGGESTIONS, and the keyboard's strip shows.
 */
internal fun seedWordInputType(inputType: Int, keepTextType: Boolean): Int =
    if (keepTextType) {
        inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    } else {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

/**
 * True when a seed field keeps the plain text type, so that the keyboard
 * can type a non-Latin word list:
 * - The keyboard language cannot type ASCII ([asciiCapable] is false).
 *   Keyboards can force a Latin layout in password fields.
 * - One of [languages] is Japanese, Chinese or Korean. These words need the
 *   keyboard's own composing text or candidate strip. In a password field,
 *   Gboard offers only Latin layouts for Japanese.
 *
 * Gboard reports no keyboard language to apps, so [languages] also holds
 * the app's and the device's languages. Gboard keeps its Russian layout in
 * password fields, so Russian needs neither check.
 */
internal fun keepsTextInputType(asciiCapable: Boolean?, languages: List<String>): Boolean =
    asciiCapable == false ||
        languages.any { it.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT) in ComposingLanguages }

private val ComposingLanguages = setOf("ja", "zh", "ko")

/** The keyboard's language when it reports one, then the app's and the device's languages. */
private fun typingLanguages(subtype: InputMethodSubtype?): List<String> = buildList {
    subtype?.let { add(it.languageTag.ifEmpty { @Suppress("DEPRECATION") it.locale }) }
    for (locales in listOf(LocaleList.getDefault(), Resources.getSystem().configuration.locales)) {
        for (i in 0 until locales.size()) add(locales[i].language)
    }
}

private val CellSpacing = 8.dp

/** iOS SeedGridLayout minColumnWidth. */
private val MinColumnWidth = 150.dp
