package one.monero.moneroone.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.whatsnew.NotesVersion
import one.monero.moneroone.core.whatsnew.WhatsNewEntry
import one.monero.moneroone.core.whatsnew.WhatsNewGlyph
import one.monero.moneroone.core.whatsnew.WhatsNewHighlight
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.MoneroTheme

/**
 * The What's New banner in the home header, under the greeting, in the slot
 * of the offline banner (iOS WhatsNewBanner): radius 12, green at 10%, one
 * hit target tall. A tap anywhere but the X opens the notes; the X hides the
 * banner for good. TalkBack: "What's New in Monero One 1.1, button", then
 * "Close, button". At a large font the text wraps and the banner grows taller.
 */
@Composable
fun WhatsNewBanner(
    version: NotesVersion,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val green = MoneroTheme.colors.positive
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(green.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = green,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = keepName(tr("What's New in Monero One %s", version.toString())),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        val close = tr("Close")
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The notes of one big update, Apple's "What's New in Pages" sheet as a
 * ModalBottomSheet like ClaimSheet: centered title and version, three
 * highlight rows, the other improvements, one Continue. The notes scroll and
 * Continue stays at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(entry: WhatsNewEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = keepName(tr("What's New in Monero One")),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr("Version %s", entry.version.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    entry.highlights.forEach { HighlightRow(it) }
                }
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = tr("Other Improvements"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.improvements.forEach { line ->
                        Row {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MoneroTheme.colors.labelTertiary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .width(8.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tr(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            PrimaryButton(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 32.dp)
            ) {
                Text(tr("Continue"))
            }
        }
    }
}

/** One highlight: a 40dp brand disc, the title, then the text. TalkBack reads it as one stop. */
@Composable
private fun HighlightRow(highlight: WhatsNewHighlight) {
    val brand = MoneroTheme.colors.brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(brand.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = highlight.glyph.icon,
                contentDescription = null,
                tint = brand,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr(highlight.title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = keepName(tr(highlight.text)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val WhatsNewGlyph.icon: ImageVector
    get() = when (this) {
        WhatsNewGlyph.TRANSFER -> Icons.Default.SwapHoriz
        WhatsNewGlyph.LINK -> Icons.Default.Link
        WhatsNewGlyph.CONTACTS -> Icons.Outlined.AccountCircle
        WhatsNewGlyph.AT -> Icons.Default.AlternateEmail
        WhatsNewGlyph.KEY -> Icons.Default.Key
    }

/** "Monero One" never breaks across lines; the name stays in Latin letters in every language. */
private fun keepName(text: String): String = text.replace("Monero One", "Monero One")
