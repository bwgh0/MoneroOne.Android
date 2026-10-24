package one.monero.moneroone.ui.screens.wallet

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.openalias.DnssecStatus
import one.monero.moneroone.core.openalias.OpenAliasName
import one.monero.moneroone.core.openalias.OpenAliasSummary
import one.monero.moneroone.core.openalias.PinState
import one.monero.moneroone.core.whatsnew.WhatsNewGlyph
import one.monero.moneroone.paymentlinks.EnvelopeArt
import one.monero.moneroone.paymentlinks.EnvelopeGeometry
import one.monero.moneroone.paymentlinks.PackingPose
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.raisedShadow
import one.monero.moneroone.ui.screens.send.ContactDestinationText
import one.monero.moneroone.ui.screens.send.OpenAliasRecipientCard
import one.monero.moneroone.ui.screens.send.OpenAliasResolvedLines
import one.monero.moneroone.ui.theme.MonoFamily
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The pictures of the What's New cards (iOS WhatsNewScenes.swift). Each one is laid out at its
 * native width at the default text size, then scaled to fit the card, so it looks the same on
 * every phone and at every font scale. Phase A: still pictures only (the iOS link and Transfer
 * loops come later).
 */

/** A picture's native width in dp (iOS WhatsNewScenePicture.nativeWidth). */
private fun WhatsNewGlyph.nativeWidth(): Dp = when (this) {
    WhatsNewGlyph.TRANSFER -> 370.dp
    WhatsNewGlyph.LINK -> LINK_W.dp
    WhatsNewGlyph.CONTACTS -> 320.dp
    WhatsNewGlyph.AT, WhatsNewGlyph.KEY -> 300.dp
}

/** The picture of one feature, centered in the space it gets, scaled to fit it. */
@Composable
internal fun WhatsNewScenePicture(glyph: WhatsNewGlyph, modifier: Modifier = Modifier) {
    if (glyph == WhatsNewGlyph.LINK) {
        LinkStill(modifier)
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        ScaledPicture(nativeWidth = glyph.nativeWidth()) {
            when (glyph) {
                WhatsNewGlyph.TRANSFER -> TransferStill()
                WhatsNewGlyph.CONTACTS -> ContactsStill()
                WhatsNewGlyph.AT -> NameStill()
                WhatsNewGlyph.KEY -> SeedStill()
                WhatsNewGlyph.LINK -> Unit
            }
        }
    }
}

/**
 * iOS ScaledPictureLayout: measures [content] at [nativeWidth] with the font scale at 1 (iOS
 * `.dynamicTypeSize(.large)`), then draws it scaled by min(box width x [fill] / native width,
 * box height / native height), centered.
 */
@Composable
internal fun ScaledPicture(
    nativeWidth: Dp,
    modifier: Modifier = Modifier,
    fill: Float = 1f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    Layout(
        content = {
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(Modifier.width(nativeWidth)) { content() }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val nativePx = nativeWidth.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixedWidth(nativePx))
        val boxW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else nativePx.toFloat()
        val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY
        val scale = min(boxW * fill / nativePx, boxH / placeable.height.coerceAtLeast(1)).coerceAtLeast(0f)
        val w = constraints.constrainWidth((nativePx * scale).roundToInt())
        val h = constraints.constrainHeight((placeable.height * scale).roundToInt())
        layout(w, h) {
            placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** A part of a picture (iOS scenePart): the part fill, a hairline, the raised shadow in light. */
@Composable
private fun Modifier.scenePart(shape: Shape): Modifier = this
    .raisedShadow(shape)
    .background(MaterialTheme.colorScheme.surfaceContainer, shape)
    .border(0.5.dp, MoneroTheme.colors.separator, shape)

private fun sampleAddress(head: String, tail: String) = head + "1".repeat(79) + tail

private fun xmr(value: Double, digits: Int): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    return format.format(value) + " XMR"
}

// MARK: Transfer (the received step of the iOS loop)

@Composable
private fun TransferStill() {
    val brand = MoneroTheme.colors.brand
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SceneWalletRow(
                    emoji = "💰",
                    name = tr("Everyday Spending"),
                    address = sampleAddress("4AdUndXH", "5ZDxJ8Bq"),
                    balance = xmr(1034.503917, 6),
                    isActive = true
                )
                SceneWalletRow(
                    emoji = "🏔️",
                    name = tr("Long-Term Savings Wallet"),
                    address = sampleAddress("86yLe8Lm", "QHv5ezC1"),
                    balance = xmr(250.25, 4),
                    isActive = false
                )
            }
            Row(
                modifier = Modifier
                    .raisedShadow(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CapsuleShape)
                    .background(brand.copy(alpha = 0.15f), CapsuleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = brand, modifier = Modifier.size(16.dp))
                Text(
                    text = xmr(250.0, 4),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = brand,
                    maxLines = 1
                )
            }
        }
        Row(
            modifier = Modifier
                .width(179.dp)
                .scenePart(CapsuleShape)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledCircleGlyph(Icons.Default.SwapHoriz, brand)
            Text(
                text = tr("Transfer"),
                style = MaterialTheme.typography.labelLarge,
                color = brand,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SceneWalletRow(emoji: String, name: String, address: String, balance: String, isActive: Boolean) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    WalletRowCard(
        emoji = emoji,
        name = name,
        address = address,
        balanceText = balance,
        isActive = isActive,
        modifier = if (isActive) Modifier else Modifier.border(0.5.dp, MoneroTheme.colors.separator, CapsuleShape),
        leading = {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = secondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        },
        trailing = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                FilledCircleGlyph(Icons.Default.Edit, secondary.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(12.dp))
            WalletRowCheck(isActive)
        }
    )
}

// MARK: Contacts

@Composable
private fun ContactsStill() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneContactRow(
            "👩🏽",
            Contact("1", "Ana Souza", "ana@example.com", Contact.KIND_OPEN_ALIAS)
        )
        SceneContactRow(
            "👨🏻",
            Contact("2", "Kenji Mori", sampleAddress("88WNmfKy", "kJ7SPNG4"))
        )
        SceneContactRow(
            "👩🏼‍🦰",
            Contact("3", "Lena Fischer", "lena@example.org", Contact.KIND_OPEN_ALIAS)
        )
    }
}

@Composable
private fun SceneContactRow(emoji: String, contact: Contact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scenePart(CapsuleShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(CellFill, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 30.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ContactDestinationText(contact)
        }
    }
}

// MARK: Send to a name

private val nameSummary = OpenAliasSummary(
    name = OpenAliasName("ana@example.com", "ana.example.com"),
    address = sampleAddress("4AdUndXH", "5ZDxJ8Bq"),
    dnssec = DnssecStatus.VALID,
    pin = PinState.None,
    recipientName = "Ana Souza"
)

@Composable
private fun NameStill() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = tr("Recipient"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .scenePart(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            OpenAliasRecipientCard(nameSummary, onClear = {})
        }
        OpenAliasResolvedLines(nameSummary)
    }
}

// MARK: Seed phrase

@Composable
private fun SeedStill() {
    val words = seedSceneWords(LocalConfiguration.current.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 2) {
                        val index = row * 2 + col
                        if (index < words.size) {
                            SceneSeedCell(21 + index, words[index], Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
            Text(
                text = tr("Valid seed phrase"),
                style = MaterialTheme.typography.bodyMedium,
                color = SuccessGreen
            )
        }
    }
}

@Composable
private fun SceneSeedCell(number: Int, word: String, modifier: Modifier) {
    val numberStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .scenePart(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The number sits right-aligned in the width of "25.", as on iOS.
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(text = "25.", style = numberStyle, color = androidx.compose.ui.graphics.Color.Transparent)
            Text(
                text = "$number.",
                style = numberStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        Text(
            text = word,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** iOS SeedSceneWords: five words of the seed word list in the app language. */
private fun seedSceneWords(locale: Locale): List<String> {
    val tag = locale.toLanguageTag()
    return when (locale.language) {
        "de" -> listOf("Skizze", "Exodus", "Umgang", "Ölberg", "Garten")
        "es" -> listOf("pétalo", "doble", "pulmón", "natal", "fiable")
        "fr" -> listOf("stock", "foin", "trier", "rasoir", "infime")
        "it" -> listOf("sudare", "esame", "uffa", "riflesso", "geologia")
        "ja" -> listOf("にっすう", "けとる", "のはら", "ついか", "こんれい")
        "nl" -> listOf("varaan", "gogme", "weert", "secessie", "kipschnitzel")
        "pt" -> listOf("tchecoslovaco", "fiquei", "ursulina", "ptialina", "honroso")
        "ru" -> listOf("флейта", "картина", "шампунь", "социолог", "льгота")
        "zh" -> if (tag.contains("Hant") || tag.endsWith("TW") || tag.endsWith("HK")) {
            listOf("tuesday", "gnome", "vexed", "sapling", "jaded")
        } else {
            listOf("幕", "树", "抬", "嘴", "胜")
        }
        else -> listOf("tuesday", "gnome", "vexed", "sapling", "jaded")
    }
}

// MARK: Send with a link (the sealed envelope)

/** iOS LinkScene: the envelope 178 wide, with 140 envelope units of room above and below. */
private const val LINK_W = 178f
private const val LINK_HEADROOM = 140f
private const val LINK_H = LINK_W * (EnvelopeGeometry.H + 2 * LINK_HEADROOM) / EnvelopeGeometry.W
private const val LINK_FILL = 178f / 186f

/** The end of the send's packing: the flap shut, the seal pressed, the foil amount shown. */
private val sealedPose = PackingPose(
    envelopeY = 0f,
    envelopeAlpha = 1f,
    noteLayerY = 0f,
    noteY = 0f,
    noteShape = 1f,
    noteAlpha = 0f,
    noteTint = 0f,
    flapDegrees = 0f,
    sealAlpha = 1f,
    sealScale = 1f,
    sealMark = 1f,
    sealDome = 0f,
    toolAlpha = 0f,
    toolX = 0f,
    toolY = 0f,
    toolTilt = 0f,
    toolHeight = 1f,
    sweep = -1f,
    foilReveal = 1.35f,
)

@Composable
private fun LinkStill(modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boxW = maxWidth.value
        val boxH = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        val k = min(boxW * LINK_FILL / LINK_W, boxH / LINK_H)
        var widthPx by remember { mutableIntStateOf(0) }
        // Envelope units to pixels: the art renders at this scale, as the send hero's does.
        val unit = widthPx / EnvelopeGeometry.W
        val art by produceState<EnvelopeArt?>(null, widthPx) {
            if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderForPacking(unit) }
        }
        val foil by produceState<Bitmap?>(null, widthPx) {
            if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderFoil(context, unit, "0.5000", "XMR") }
        }
        val tint = SuccessGreen.toArgb()
        Canvas(
            Modifier
                .size((LINK_W * k).dp, (LINK_H * k).dp)
                .onSizeChanged { widthPx = it.width }
                .clearAndSetSemantics {}
        ) {
            val drawn = art ?: return@Canvas
            val e = size.width / EnvelopeGeometry.W
            // Frame units to pixels: the envelope sits in the hero frame at ENV_SCALE.
            val frame = e / EnvelopeGeometry.ENV_SCALE
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(-EnvelopeGeometry.ENV_LEFT * frame, LINK_HEADROOM * e - EnvelopeGeometry.ENV_TOP * frame)
                native.scale(frame, frame)
                drawn.drawPacking(native, sealedPose, null, foil, tint)
                native.restore()
            }
        }
    }
}
