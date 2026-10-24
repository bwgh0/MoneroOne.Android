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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import one.monero.moneroone.paymentlinks.ENVELOPE_ARRIVE
import one.monero.moneroone.paymentlinks.PACE
import one.monero.moneroone.paymentlinks.SWEEP_MS
import one.monero.moneroone.paymentlinks.TOOL_AWAY_X
import one.monero.moneroone.paymentlinks.TOOL_AWAY_Y
import one.monero.moneroone.paymentlinks.TOOL_HOVER
import one.monero.moneroone.paymentlinks.TOOL_LIFT
import one.monero.moneroone.paymentlinks.TOOL_SINK
import one.monero.moneroone.paymentlinks.TOOL_TILT
import one.monero.moneroone.paymentlinks.WAX_DROP_SCALE
import one.monero.moneroone.paymentlinks.pacedSettle
import one.monero.moneroone.paymentlinks.pacedStepBack
import one.monero.moneroone.paymentlinks.pacedTween
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.rememberReduceMotion
import kotlin.math.roundToLong
import one.monero.moneroone.ui.screens.wallet.WhatsNewLinkLoop.Step as LoopStep
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import one.monero.moneroone.ui.components.RollingText
import one.monero.moneroone.ui.screens.send.formatFiatAmount
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The pictures of the What's New cards (iOS WhatsNewScenes.swift). Each one is laid out at its
 * native width at the default text size, then scaled to fit the card, so it looks the same on
 * every phone and at every font scale. The link plays the send's loop (iOS WhatsNewLinkLoop) and
 * the Transfer picture its own (iOS WhatsNewTransferLoop).
 */

/** A picture's native width in dp (iOS WhatsNewScenePicture.nativeWidth). */
private fun WhatsNewGlyph.nativeWidth(): Dp = when (this) {
    WhatsNewGlyph.TRANSFER -> 370.dp
    WhatsNewGlyph.LINK -> LINK_W.dp
    WhatsNewGlyph.CONTACTS -> 320.dp
    WhatsNewGlyph.AT, WhatsNewGlyph.KEY -> 300.dp
}

/** The picture of one feature, centered in the space it gets, scaled to fit it. [animated]: the link and Transfer play their loops. */
@Composable
internal fun WhatsNewScenePicture(glyph: WhatsNewGlyph, modifier: Modifier = Modifier, animated: Boolean = false) {
    if (glyph == WhatsNewGlyph.LINK) {
        LinkScene(animated, modifier)
        return
    }
    if (glyph == WhatsNewGlyph.TRANSFER) {
        TransferScene(animated, modifier)
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        ScaledPicture(nativeWidth = glyph.nativeWidth()) {
            when (glyph) {
                WhatsNewGlyph.CONTACTS -> ContactsStill()
                WhatsNewGlyph.AT -> NameStill()
                WhatsNewGlyph.KEY -> SeedStill()
                WhatsNewGlyph.LINK, WhatsNewGlyph.TRANSFER -> Unit
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

// MARK: Transfer

/**
 * The pictures' amounts in Fiat Mode with a usable price (iOS PriceService.showFiatFirst and
 * formatFiatValue): the live price and the selected currency's symbol. Null shows XMR.
 */
@Immutable
data class WhatsNewFiat(val price: Double, val symbol: String) {
    companion object {
        /** The fiat for the pictures, or null (XMR) outside Fiat Mode or without a usable price. */
        fun of(fiatMode: Boolean, price: Double?, symbol: String): WhatsNewFiat? =
            if (fiatMode && price != null && price.isFinite() && price > 0.0) WhatsNewFiat(price, symbol) else null
    }
}

/** The fiat the sheet gives its pictures; null shows XMR. */
internal val LocalWhatsNewFiat = compositionLocalOf<WhatsNewFiat?> { null }

/**
 * iOS WhatsNewTransferLoop: Transfer is pressed, the amount shows between the wallets, the From
 * balance rolls down, the To balance rolls up, a rest on the result, then a fade, so the balances
 * go back unseen.
 */
internal object WhatsNewTransferLoop {
    enum class Step {
        /** The picture as it stands: nothing has moved. It fades back in. */
        START,
        /** Transfer is pressed. */
        PRESS,
        /** Transfer is let go and the amount shows between the wallets. */
        AMOUNT,
        /** The amount leaves the From wallet: its balance rolls down. */
        SENT,
        /** The amount reaches the To wallet: its balance rolls up. */
        RECEIVED,
        /** The picture fades out, so the balances go back unseen. */
        FADE,
    }

    val FROM_BALANCE = BigDecimal("1284.503917")
    val TO_BALANCE = BigDecimal("0.25")
    val AMOUNT = BigDecimal("250")

    /** The frame that stands still with animations off: the result, the last frame before the fade. */
    val stillStep = Step.RECEIVED

    /** The Transfer button's press (motion.press, larger than 0.98 so it reads at the picture's scale). */
    const val PRESS_SCALE = 0.96f

    /** The amount's size while it is hidden. */
    const val AMOUNT_HIDDEN_SCALE = 0.8f

    /** The settle spring (response 0.4, damping 0.8): the amount comes in, the button comes back. */
    const val SETTLE_STIFFNESS = 246.7f

    fun fromBalance(step: Step): BigDecimal = when (step) {
        Step.START, Step.PRESS, Step.AMOUNT -> FROM_BALANCE
        Step.SENT, Step.RECEIVED, Step.FADE -> FROM_BALANCE - AMOUNT
    }

    fun toBalance(step: Step): BigDecimal = when (step) {
        Step.START, Step.PRESS, Step.AMOUNT, Step.SENT -> TO_BALANCE
        Step.RECEIVED, Step.FADE -> TO_BALANCE + AMOUNT
    }

    fun showsAmount(step: Step): Boolean = step == Step.AMOUNT || step == Step.SENT || step == Step.RECEIVED

    fun buttonScale(step: Step): Float = if (step == Step.PRESS) PRESS_SCALE else 1f

    fun pictureOpacity(step: Step): Float = if (step == Step.FADE) 0f else 1f

    /**
     * The wait before the move to [step]: the rest on the start frame before the press, the time
     * to read the amount, the gap between the two rolls, and the rest on the result before the fade.
     */
    fun delay(step: Step): Double = when (step) {
        Step.START, Step.AMOUNT -> 0.0
        Step.PRESS -> 1.4
        Step.SENT -> 0.6
        Step.RECEIVED -> 0.3
        Step.FADE -> 2.0
    }

    /** The length of the move to [step]: the fades (crossFade), the press, the settle spring, the roll (rollDigits). */
    fun duration(step: Step): Double = when (step) {
        Step.START, Step.FADE -> 0.35
        Step.PRESS -> 0.1
        Step.AMOUNT -> 0.4
        Step.SENT, Step.RECEIVED -> 0.2
    }

    /** When the move to [step] begins, from the start of a cycle. */
    fun begin(step: Step): Double {
        var time = 0.0
        for (earlier in Step.entries) {
            time += delay(earlier)
            if (earlier == step) break
            time += duration(earlier)
        }
        return time
    }

    /** One cycle, rests included. */
    val cycleDuration: Double get() = Step.entries.sumOf { delay(it) + duration(it) }

    /** The step shown [elapsed] seconds after the loop began. A time on a step's begin shows that step. */
    fun step(elapsed: Double): Step {
        val time = (maxOf(0.0, elapsed) + 1e-6) % cycleDuration
        return Step.entries.last { begin(it) <= time }
    }

    /**
     * An amount as the picture shows it: in fiat with [fiat], as the Activity rows and the wallet
     * switcher; else in XMR with 4 to 12 decimals (iOS XMRFormatter).
     */
    fun amountText(xmr: BigDecimal, fiat: WhatsNewFiat?): String {
        if (fiat != null) return formatFiatAmount(xmr.toDouble() * fiat.price, fiat.symbol)
        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 4
            maximumFractionDigits = 12
            roundingMode = RoundingMode.DOWN
        }
        return format.format(xmr) + " XMR"
    }
}

private typealias TransferStep = WhatsNewTransferLoop.Step

/** The Transfer loop's moving parts, driven at each step's begin (iOS TransferSceneLoop). */
@Stable
private class TransferLoopMotion {
    /** The step whose balances show; a balance rolls when it changes. */
    var step by mutableStateOf(TransferStep.START)
        private set

    /** Each swap back to the start draws the balances anew, without a roll (iOS `.identity`). */
    var resets by mutableIntStateOf(0)
        private set

    val opacity = Animatable(1f)
    val buttonScale = Animatable(1f)
    val amountScale = Animatable(WhatsNewTransferLoop.AMOUNT_HIDDEN_SCALE)
    val amountAlpha = Animatable(0f)

    /** Off screen: the first frame, at once. The loop starts again from there, without a jump. */
    suspend fun reset() {
        toStart()
        opacity.snapTo(1f)
    }

    /** The loop, cycle after cycle, until it is cancelled. */
    suspend fun run() = coroutineScope {
        val cycleMs = (WhatsNewTransferLoop.cycleDuration * 1000).roundToLong()
        while (isActive) {
            val cycle = launch { playCycle() }
            delay(cycleMs)
            cycle.cancelAndJoin()
        }
    }

    private suspend fun toStart() {
        if (step != TransferStep.START) {
            step = TransferStep.START
            resets++
        }
        buttonScale.snapTo(1f)
        amountScale.snapTo(WhatsNewTransferLoop.AMOUNT_HIDDEN_SCALE)
        amountAlpha.snapTo(0f)
    }

    private suspend fun playCycle() = coroutineScope {
        // Back to the start while the picture is clear (after the fade), or at once the first time.
        if (opacity.value < 1f) opacity.snapTo(0f)
        toStart()
        fun at(move: TransferStep, block: suspend CoroutineScope.() -> Unit) = launch {
            val ms = (WhatsNewTransferLoop.begin(move) * 1000).roundToLong()
            if (ms > 0) delay(ms)
            coroutineScope { block() }
        }
        fun ms(move: TransferStep) = (WhatsNewTransferLoop.duration(move) * 1000).roundToInt()
        val fade = tween<Float>(ms(TransferStep.FADE), easing = Motion.EaseInOut)
        val settle = spring<Float>(dampingRatio = 0.8f, stiffness = WhatsNewTransferLoop.SETTLE_STIFFNESS)
        at(TransferStep.START) { opacity.animateTo(1f, tween(ms(TransferStep.START), easing = Motion.EaseInOut)) }
        at(TransferStep.PRESS) {
            step = TransferStep.PRESS
            buttonScale.animateTo(WhatsNewTransferLoop.PRESS_SCALE, tween(ms(TransferStep.PRESS), easing = Motion.EaseInOut))
        }
        at(TransferStep.AMOUNT) {
            step = TransferStep.AMOUNT
            launch { buttonScale.animateTo(1f, settle) }
            launch { amountScale.animateTo(1f, settle) }
            amountAlpha.animateTo(1f, settle)
        }
        at(TransferStep.SENT) { step = TransferStep.SENT }
        at(TransferStep.RECEIVED) { step = TransferStep.RECEIVED }
        at(TransferStep.FADE) {
            step = TransferStep.FADE
            launch { amountScale.animateTo(WhatsNewTransferLoop.AMOUNT_HIDDEN_SCALE, fade) }
            launch { amountAlpha.animateTo(0f, fade) }
            opacity.animateTo(0f, fade)
        }
    }
}

/**
 * iOS TransferScene: two wallet switcher rows (the current one with its brand ring) and the
 * Transfer button. When [animated], it plays the Transfer loop while it is on screen and the app
 * is in front; with animations off (or the blurred copy behind) it shows the loop's result, still.
 */
@Composable
private fun TransferScene(animated: Boolean, modifier: Modifier) {
    val playing = animated && !rememberReduceMotion()
    val loop = remember(playing) { if (playing) TransferLoopMotion() else null }
    var onScreen by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    if (loop != null) {
        val running = onScreen && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        LaunchedEffect(loop, running) {
            if (running) loop.run() else loop.reset()
        }
    }
    val visibility = if (loop != null) {
        Modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            onScreen = bounds.width > 0f && bounds.height > 0f
        }
    } else {
        Modifier
    }
    Box(modifier.then(visibility), contentAlignment = Alignment.Center) {
        ScaledPicture(nativeWidth = WhatsNewGlyph.TRANSFER.nativeWidth()) {
            if (loop == null) {
                TransferFrame(WhatsNewTransferLoop.stillStep, resets = 0, { 1f }, { 1f }, { 1f }, { 1f })
            } else {
                TransferFrame(
                    loop.step,
                    loop.resets,
                    opacity = { loop.opacity.value },
                    buttonScale = { loop.buttonScale.value },
                    amountScale = { loop.amountScale.value },
                    amountAlpha = { loop.amountAlpha.value }
                )
            }
        }
    }
}

/** One frame of the Transfer picture; the moving values are read at draw time. */
@Composable
private fun TransferFrame(
    step: TransferStep,
    resets: Int,
    opacity: () -> Float,
    buttonScale: () -> Float,
    amountScale: () -> Float,
    amountAlpha: () -> Float
) {
    val brand = MoneroTheme.colors.brand
    val fiat = LocalWhatsNewFiat.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = opacity().coerceIn(0f, 1f)
                // Each part fades on its own, so the shadows past the picture's bounds stay whole.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The From balance falls (it rolls up, iOS numericText(value:)); the To balance rises.
                SceneWalletRow(
                    emoji = "💰",
                    name = tr("Everyday Spending"),
                    address = sampleAddress("4AdUndXH", "5ZDxJ8Bq"),
                    balanceText = WhatsNewTransferLoop.amountText(WhatsNewTransferLoop.fromBalance(step), fiat),
                    isActive = true,
                    resets = resets,
                    countsDown = true
                )
                SceneWalletRow(
                    emoji = "🏔️",
                    name = tr("Long-Term Savings Wallet"),
                    address = sampleAddress("86yLe8Lm", "QHv5ezC1"),
                    balanceText = WhatsNewTransferLoop.amountText(WhatsNewTransferLoop.toBalance(step), fiat),
                    isActive = false,
                    resets = resets,
                    countsDown = false
                )
            }
            // Across the gap between From and To.
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        val scale = amountScale()
                        scaleX = scale
                        scaleY = scale
                        alpha = amountAlpha().coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .raisedShadow(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CapsuleShape)
                    .background(brand.copy(alpha = 0.15f), CapsuleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = brand, modifier = Modifier.size(16.dp))
                Text(
                    text = WhatsNewTransferLoop.amountText(WhatsNewTransferLoop.AMOUNT, fiat),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = brand,
                    maxLines = 1
                )
            }
        }
        Row(
            modifier = Modifier
                .graphicsLayer {
                    val scale = buttonScale()
                    scaleX = scale
                    scaleY = scale
                }
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
private fun SceneWalletRow(
    emoji: String,
    name: String,
    address: String,
    balanceText: String,
    isActive: Boolean,
    resets: Int = 0,
    countsDown: Boolean = false
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val brand = MoneroTheme.colors.brand
    WalletRowCard(
        emoji = emoji,
        name = name,
        address = address,
        balanceText = balanceText,
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
        },
        balance = {
            // The balance rolls when it changes; back at the start it is drawn anew, without a roll.
            key(resets) {
                RollingText(
                    text = balanceText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = brand,
                    minScale = 0.6f,
                    countsDown = countsDown
                )
            }
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

// MARK: Send with a link (the send's packing, in a loop)

/** iOS LinkScene: the envelope 178 wide, with 140 envelope units of room above and below. */
internal const val LINK_W = 178f
internal const val LINK_HEADROOM = 140f
internal const val LINK_H = LINK_W * (EnvelopeGeometry.H + 2 * LINK_HEADROOM) / EnvelopeGeometry.W
internal const val LINK_FILL = 178f / 186f

/** The link picture's scale (dp per dp of its native size) in a box of [boxW] x [boxH] dp. */
internal fun linkPictureScale(boxW: Float, boxH: Float): Float = min(boxW * LINK_FILL / LINK_W, boxH / LINK_H)

/**
 * iOS WhatsNewLinkLoop: the send's packing on the card, in a loop. The note alone, the open
 * envelope comes up around it, the note slides in, the flap folds down, the wax drops, the stamp
 * presses the seal on and goes, one light sweep shows the foil; a rest, a fade, and everything
 * goes back unseen. The hero's beats (LinkPacking, its times at pace 1, times its pace), springs
 * and curves. No check, no haptics, no sounds. Times in seconds.
 */
internal object WhatsNewLinkLoop {
    enum class Step {
        /** The note alone, risen where the send's check turns into it. It fades back in. */
        START,
        /** The open envelope comes up around the note. */
        ARRIVE,
        /** The note slides into the envelope. */
        NOTE_IN,
        /** The flap folds down; the envelope lifts to the middle. */
        CLOSE,
        /** The flap passes 90 degrees: its outside shows (drawn from the flap's angle). */
        FLAP_OVER,
        /** A plain drop of wax. */
        DROP,
        /** The stamp comes in from the upper right and hovers over the wax. */
        HOVER,
        /** It presses in. */
        PRESS,
        /** It meets the wax: the wax spreads. */
        CONTACT,
        /** It lifts. */
        LIFT,
        /** The ring and the mark show in the wax. */
        MARK,
        /** It goes up and to the right, and fades. */
        AWAY,
        /** One light sweep: the foil shows where it has passed. */
        SWEEP,
        /** The picture fades out, so everything goes back unseen. */
        FADE,
    }

    /** The frame that stands still when animations are off: the sealed envelope with its foil. */
    val stillStep = Step.SWEEP

    /** The fade out and back in (crossFade). */
    const val FADE_S = 0.35
    /** The rest on the note before the envelope comes up. */
    const val REST_ON_NOTE_S = 0.6
    /** The hero's light sweep. Its time is not paced. */
    const val SWEEP_S = SWEEP_MS / 1000.0
    /** The rest on the sealed envelope before the fade. */
    const val REST_ON_RESULT_S = 2.0
    /** The hero's wax starts at this scale. */
    const val DROP_START_SCALE = 0.3f

    /** The time from the begin of the step before to the begin of [step]. */
    fun wait(step: Step): Double {
        val pace = PACE.toDouble()
        return when (step) {
            Step.START -> 0.0
            Step.ARRIVE -> FADE_S + REST_ON_NOTE_S
            Step.NOTE_IN -> 0.3 * pace
            Step.CLOSE -> 0.35 * pace
            Step.FLAP_OVER -> 0.12 * pace
            Step.DROP -> 0.23 * pace
            Step.HOVER -> 0.15 * pace
            Step.PRESS -> 0.4 * pace
            Step.CONTACT -> 0.09 * pace
            Step.LIFT -> 0.16 * pace
            Step.MARK -> 0.03 * pace
            Step.AWAY -> 0.07 * pace
            Step.SWEEP -> 0.1 * pace
            Step.FADE -> SWEEP_S + REST_ON_RESULT_S
        }
    }

    /** When [step] begins, from the start of a cycle. */
    fun begin(step: Step): Double {
        var time = 0.0
        for (earlier in Step.entries) {
            time += wait(earlier)
            if (earlier == step) break
        }
        return time
    }

    /** One cycle, rests included: it ends when the fade has finished. */
    val cycleDuration: Double get() = begin(Step.FADE) + FADE_S

    /** The step shown [elapsed] seconds after the loop began. A time on a step's begin shows that step. */
    fun step(elapsed: Double): Step {
        val time = (maxOf(0.0, elapsed) + 1e-6) % cycleDuration
        return Step.entries.last { begin(it) <= time }
    }

    /** The picture's opacity once the moves up to [step] have finished. */
    fun opacity(step: Step): Float = if (step == Step.FADE) 0f else 1f

    /** The hero's pose once the moves up to [step] have finished: each step sets what the hero sets at that beat. */
    fun pose(step: Step): PackingPose {
        var pose = PackingPose(
            envelopeY = EnvelopeGeometry.DROP + ENVELOPE_ARRIVE,
            envelopeAlpha = 0f,
            noteLayerY = EnvelopeGeometry.DROP,
            noteY = -EnvelopeGeometry.CARD_RISE,
            noteShape = 1f,
            noteAlpha = 1f,
            noteTint = 0f,
            flapDegrees = 180f,
            sealAlpha = 0f,
            sealScale = DROP_START_SCALE,
            sealMark = 0f,
            sealDome = 1f,
            toolAlpha = 0f,
            toolX = TOOL_AWAY_X,
            toolY = TOOL_AWAY_Y,
            toolTilt = TOOL_TILT,
            toolHeight = 1f,
            sweep = -1f,
            foilReveal = -0.35f,
        )
        for (move in Step.entries) {
            pose = when (move) {
                Step.START, Step.FLAP_OVER, Step.FADE -> pose
                Step.ARRIVE -> pose.copy(envelopeAlpha = 1f, envelopeY = EnvelopeGeometry.DROP)
                Step.NOTE_IN -> pose.copy(noteY = 0f)
                Step.CLOSE -> pose.copy(flapDegrees = 0f, envelopeY = 0f, noteLayerY = 0f)
                Step.DROP -> pose.copy(sealAlpha = 1f, sealScale = WAX_DROP_SCALE)
                Step.HOVER -> pose.copy(toolAlpha = 1f, toolX = 0f, toolY = TOOL_HOVER, toolTilt = 0f)
                Step.PRESS -> pose.copy(toolY = TOOL_SINK, toolHeight = 0f)
                Step.CONTACT -> pose.copy(sealScale = 1f, sealDome = 0f)
                Step.LIFT -> pose.copy(toolY = TOOL_SINK - TOOL_LIFT, toolHeight = 1f)
                Step.MARK -> pose.copy(sealMark = 1f)
                Step.AWAY -> pose.copy(toolAlpha = 0f, toolX = TOOL_AWAY_X, toolY = TOOL_AWAY_Y, toolTilt = TOOL_TILT)
                Step.SWEEP -> pose.copy(foilReveal = 1.35f, sweep = -1f)
            }
            if (move == step) break
        }
        return pose
    }
}

/** The loop's moving parts, driven at each step's begin as the hero's `play()` drives them. */
@Stable
private class LinkLoopMotion {
    private val start = WhatsNewLinkLoop.pose(WhatsNewLinkLoop.Step.START)

    val opacity = Animatable(1f)
    private val envelopeY = Animatable(start.envelopeY)
    private val envelopeAlpha = Animatable(start.envelopeAlpha)
    private val noteLayerY = Animatable(start.noteLayerY)
    private val noteY = Animatable(start.noteY)
    private val flap = Animatable(start.flapDegrees)
    private val sealAlpha = Animatable(start.sealAlpha)
    private val sealScale = Animatable(start.sealScale)
    private val sealMark = Animatable(start.sealMark)
    private val sealDome = Animatable(start.sealDome)
    private val toolAlpha = Animatable(start.toolAlpha)
    private val toolX = Animatable(start.toolX)
    private val toolY = Animatable(start.toolY)
    private val toolTilt = Animatable(start.toolTilt)
    private val toolHeight = Animatable(start.toolHeight)
    private val sweep = Animatable(start.sweep)
    private val foilReveal = Animatable(start.foilReveal)

    /** Off screen: the first frame, at once. The loop starts again from there. */
    suspend fun reset() {
        toStart()
        opacity.snapTo(1f)
    }

    /** The loop, cycle after cycle, until it is cancelled. */
    suspend fun run() = coroutineScope {
        val cycleMs = (WhatsNewLinkLoop.cycleDuration * 1000).roundToLong()
        while (isActive) {
            val cycle = launch { playCycle() }
            delay(cycleMs)
            cycle.cancelAndJoin()
        }
    }

    private suspend fun toStart() {
        envelopeY.snapTo(start.envelopeY); envelopeAlpha.snapTo(start.envelopeAlpha)
        noteLayerY.snapTo(start.noteLayerY); noteY.snapTo(start.noteY); flap.snapTo(start.flapDegrees)
        sealAlpha.snapTo(start.sealAlpha); sealScale.snapTo(start.sealScale)
        sealMark.snapTo(start.sealMark); sealDome.snapTo(start.sealDome)
        toolAlpha.snapTo(start.toolAlpha); toolX.snapTo(start.toolX); toolY.snapTo(start.toolY)
        toolTilt.snapTo(start.toolTilt); toolHeight.snapTo(start.toolHeight)
        sweep.snapTo(start.sweep); foilReveal.snapTo(start.foilReveal)
    }

    private suspend fun playCycle() = coroutineScope {
        // Back to the start while the picture is clear (after the fade), or at once the first time.
        if (opacity.value < 1f) opacity.snapTo(0f)
        toStart()
        fun at(step: WhatsNewLinkLoop.Step, block: suspend () -> Unit) = launch {
            val ms = (WhatsNewLinkLoop.begin(step) * 1000).roundToLong()
            if (ms > 0) delay(ms)
            block()
        }
        val fade = tween<Float>((WhatsNewLinkLoop.FADE_S * 1000).roundToInt(), easing = Motion.EaseInOut)
        at(LoopStep.START) { opacity.animateTo(1f, fade) }
        at(LoopStep.ARRIVE) { envelopeAlpha.animateTo(1f, pacedTween(300, Motion.EaseOut)) }
        at(LoopStep.ARRIVE) { envelopeY.animateTo(EnvelopeGeometry.DROP, pacedSettle()) }
        at(LoopStep.NOTE_IN) { noteY.animateTo(0f, pacedSettle()) }
        at(LoopStep.CLOSE) { flap.animateTo(0f, pacedStepBack()) }
        at(LoopStep.CLOSE) { envelopeY.animateTo(0f, pacedStepBack()) }
        at(LoopStep.CLOSE) { noteLayerY.animateTo(0f, pacedStepBack()) }
        at(LoopStep.DROP) { sealAlpha.animateTo(1f, pacedTween(90, LinearEasing)) }
        at(LoopStep.DROP) { sealScale.animateTo(WAX_DROP_SCALE, pacedSettle()) }
        at(LoopStep.HOVER) { toolAlpha.animateTo(1f, pacedTween(150, LinearEasing)) }
        at(LoopStep.HOVER) { toolX.animateTo(0f, pacedStepBack()) }
        at(LoopStep.HOVER) { toolY.animateTo(TOOL_HOVER, pacedStepBack()) }
        at(LoopStep.HOVER) { toolTilt.animateTo(0f, pacedStepBack()) }
        at(LoopStep.PRESS) { toolY.animateTo(TOOL_SINK, pacedTween(100, Motion.EaseIn)) }
        at(LoopStep.PRESS) { toolHeight.animateTo(0f, pacedTween(100, Motion.EaseIn)) }
        at(LoopStep.CONTACT) { sealScale.animateTo(1f, pacedTween(160, Motion.EaseOut)) }
        at(LoopStep.CONTACT) { sealDome.animateTo(0f, pacedTween(160, Motion.EaseOut)) }
        at(LoopStep.LIFT) { toolY.animateTo(TOOL_SINK - TOOL_LIFT, pacedTween(100, Motion.EaseIn)) }
        at(LoopStep.LIFT) { toolHeight.animateTo(1f, pacedTween(100, Motion.EaseIn)) }
        at(LoopStep.MARK) { sealMark.animateTo(1f, pacedTween(120, Motion.EaseOut)) }
        at(LoopStep.AWAY) { toolX.animateTo(TOOL_AWAY_X, pacedStepBack()) }
        at(LoopStep.AWAY) { toolY.animateTo(TOOL_AWAY_Y, pacedStepBack()) }
        at(LoopStep.AWAY) { toolTilt.animateTo(TOOL_TILT, pacedStepBack()) }
        at(LoopStep.AWAY) { toolAlpha.animateTo(0f, pacedTween(250, Motion.EaseIn)) }
        at(LoopStep.SWEEP) { foilReveal.animateTo(1.35f, tween(SWEEP_MS, easing = Motion.EaseInOut)) }
        at(LoopStep.SWEEP) {
            sweep.snapTo(-0.35f)
            sweep.animateTo(1.35f, tween(SWEEP_MS, easing = Motion.EaseInOut))
            sweep.snapTo(-1f)
        }
        at(LoopStep.FADE) { opacity.animateTo(0f, fade) }
    }

    fun pose() = start.copy(
        envelopeY = envelopeY.value,
        envelopeAlpha = envelopeAlpha.value.coerceIn(0f, 1f),
        noteLayerY = noteLayerY.value,
        noteY = noteY.value,
        flapDegrees = flap.value,
        sealAlpha = sealAlpha.value.coerceIn(0f, 1f),
        sealScale = sealScale.value,
        sealMark = sealMark.value.coerceIn(0f, 1f),
        sealDome = sealDome.value.coerceIn(0f, 1f),
        toolAlpha = toolAlpha.value.coerceIn(0f, 1f),
        toolX = toolX.value,
        toolY = toolY.value,
        toolTilt = toolTilt.value,
        toolHeight = toolHeight.value,
        sweep = sweep.value,
        foilReveal = foilReveal.value,
    )
}

/**
 * iOS LinkScene: the envelope of the Link ready step with "0.5000 XMR" in foil, 178 wide, with
 * room above and below for the note, the open envelope and the stamp. When [animated], it plays
 * the send's loop while it is on screen and the app is in front; with animations off (or the
 * blurred copy behind) it shows the sealed envelope, still. The picture draws past its own bounds
 * (the card's edge cuts only the stamp and the rising envelope).
 */
@Composable
private fun LinkScene(animated: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val playing = animated && !rememberReduceMotion()
    val loop = remember(playing) { if (playing) LinkLoopMotion() else null }
    var onScreen by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visibility = if (loop != null) {
        Modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            onScreen = bounds.width > 0f && bounds.height > 0f
        }
    } else {
        Modifier
    }
    BoxWithConstraints(modifier.then(visibility), contentAlignment = Alignment.Center) {
        val boxW = maxWidth.value
        val boxH = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        val k = linkPictureScale(boxW, boxH)
        var widthPx by remember { mutableIntStateOf(0) }
        // Envelope units to pixels: the art renders at this scale, as the send hero's does.
        val unit = widthPx / EnvelopeGeometry.W
        val art by produceState<EnvelopeArt?>(null, widthPx) {
            if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderForPacking(unit) }
        }
        val foil by produceState<Bitmap?>(null, widthPx) {
            if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderFoil(context, unit, "0.5000", "XMR") }
        }
        val label = tr("Payment Link").uppercase(Locale.getDefault())
        val note by produceState<Bitmap?>(null, widthPx, playing, label) {
            if (widthPx > 0 && playing) {
                value = withContext(Dispatchers.Default) { EnvelopeArt.renderCard(context, unit, "0.5000", "XMR", label) }
            }
        }
        if (loop != null) {
            val running = onScreen && lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
                art != null && foil != null && note != null
            LaunchedEffect(loop, running) {
                if (running) loop.run() else loop.reset()
            }
        }
        val still = remember { WhatsNewLinkLoop.pose(WhatsNewLinkLoop.stillStep) }
        val tint = SuccessGreen.toArgb()
        Canvas(
            Modifier
                .size((LINK_W * k).dp, (LINK_H * k).dp)
                .onSizeChanged { widthPx = it.width }
                .clearAndSetSemantics {}
        ) {
            val drawn = art ?: return@Canvas
            val opacity = loop?.opacity?.value ?: 1f
            if (opacity <= 0f) return@Canvas
            val pose = loop?.pose() ?: still
            val e = size.width / EnvelopeGeometry.W
            // Frame units to pixels: the envelope sits in the hero frame at ENV_SCALE.
            val frame = e / EnvelopeGeometry.ENV_SCALE
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // One layer for the fade, wider than the picture's bounds: the note, the open flap and
                // the stamp draw past them.
                val count = if (opacity < 1f) {
                    native.saveLayerAlpha(
                        -size.width, -size.height, 2 * size.width, 2 * size.height,
                        (opacity * 255).roundToInt()
                    )
                } else {
                    native.save()
                }
                native.translate(-EnvelopeGeometry.ENV_LEFT * frame, LINK_HEADROOM * e - EnvelopeGeometry.ENV_TOP * frame)
                native.scale(frame, frame)
                drawn.drawPacking(native, pose, if (loop != null) note else null, foil, tint)
                native.restoreToCount(count)
            }
        }
    }
}
