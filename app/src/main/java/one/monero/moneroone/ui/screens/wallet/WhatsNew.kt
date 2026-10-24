package one.monero.moneroone.ui.screens.wallet

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.role
import one.monero.moneroone.core.review.ReviewPromptGate
import one.monero.moneroone.core.review.openPlayListing
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.MoneroLogo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
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
 * The notes of one big update, as on iOS (Flighty's layout): the balance card's Monero logo and
 * the version, a large title, one card with a picture per feature and its title and line under
 * it, the other improvements, then Share with Friends and Rate on Google Play. An X pinned at the
 * top right closes it. A ModalBottomSheet like ClaimSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(entry: WhatsNewEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    // At the large text sizes (iOS accessibility sizes) the cards go and the features sit closer.
    val largeText = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    // iOS presents it as a large sheet with one detent: no grabber, and the top stays clear of
    // the status bar. The sheet draws edge to edge, so a full-height one put its handle under the
    // camera; the 0.92 height (as the Contacts sheet) keeps it below the status bar.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = CLOSE_INSET, bottom = 24.dp)
            ) {
                // The balance card's logo and the version, lined up with the X.
                Row(
                    modifier = Modifier
                        .heightIn(min = CLOSE_SIZE)
                        .padding(end = CLOSE_SIZE),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MoneroLogo(
                        size = 40.dp,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                    Text(
                        text = tr("Version %s", entry.version.toString()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = keepName(tr("What's New in Monero One")),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .semantics { heading() }
                )
                Column(
                    modifier = Modifier.padding(top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(if (largeText) 24.dp else 40.dp)
                ) {
                    entry.highlights.forEach { FeatureRow(it, showsCard = !largeText) }
                }
                if (entry.improvements.isNotEmpty()) {
                    Text(
                        text = tr("Other Improvements"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = 40.dp)
                            .semantics { heading() }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinkButton(Icons.AutoMirrored.Filled.Send, tr("Share with Friends")) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, SHARE_URL)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                    // A person who rates here never gets the rating ask after a send (as
                    // Settings > Rate Monero One).
                    LinkButton(Icons.Default.Star, tr("Rate on Google Play")) {
                        ReviewPromptGate(prefs).ratedFromSettings()
                        openPlayListing(context)
                    }
                }
            }
            GlassButton(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = CLOSE_INSET, end = CLOSE_INSET)
                    .size(CLOSE_SIZE)
                    .semantics {
                        contentDescription = tr("Close")
                        role = Role.Button
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp)
                )
            }
        }
    }
}

private val CLOSE_SIZE = 44.dp
private val CLOSE_INSET = 16.dp
private const val LARGE_TEXT_SCALE = 1.6f
private const val SHARE_URL = "https://monero.one/download"

/** One feature: its card, then the title and the line, inset 12. TalkBack reads the text as one stop. */
@Composable
private fun FeatureRow(highlight: WhatsNewHighlight, showsCard: Boolean) {
    Column {
        if (showsCard) {
            FeatureCard(highlight.glyph)
            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tr(highlight.title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = keepName(tr(highlight.text)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A feature's card (iOS WhatsNewFeatureCard): 16:10, the cell fill, one brand glow, the picture
 * blurred large and faint behind (dark only), and the sharp picture on top with 16 around it.
 */
@Composable
private fun FeatureCard(glyph: WhatsNewGlyph) {
    val brand = MoneroTheme.colors.brand
    val isDark = MoneroTheme.isDark
    val glow = if (isDark) 0.12f else 0.08f
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(shape)
            .background(CellFill)
            .drawBehind {
                // An elliptical gradient that meets the card's edges (iOS EllipticalGradient).
                val r = size.width / 2f
                val brush = Brush.radialGradient(
                    0.4f to brand.copy(alpha = glow),
                    0.58f to brand.copy(alpha = glow * 0.75f),
                    0.74f to brand.copy(alpha = glow * 0.42f),
                    0.88f to brand.copy(alpha = glow * 0.15f),
                    1f to brand.copy(alpha = 0f),
                    center = center,
                    radius = r
                )
                scale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                    drawRect(brush, topLeft = Offset(0f, center.y - r), size = Size(size.width, size.width))
                }
            }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        // Modifier.blur needs Android 12; below it the card keeps the glow alone.
        if (isDark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WhatsNewScenePicture(
                glyph = glyph,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.6f }
                    .blur(28.dp, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer {
                        scaleX = 1.6f
                        scaleY = 1.6f
                    }
            )
        }
        WhatsNewScenePicture(
            glyph = glyph,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }
}

/** A link at the end: a brand glyph and a callout semibold brand label, centered. */
@Composable
private fun LinkButton(icon: ImageVector, title: String, onClick: () -> Unit) {
    val brand = MoneroTheme.colors.brand
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CapsuleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = brand, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = brand,
            textAlign = TextAlign.Center
        )
    }
}

/** "Monero One" never breaks across lines; the name stays in Latin letters in every language. */
private fun keepName(text: String): String = text.replace("Monero One", "Monero One")
