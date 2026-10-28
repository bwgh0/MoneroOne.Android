package one.monero.moneroone.ui.screens.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import one.monero.moneroone.ui.components.GlowEllipse
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.ShineBand
import one.monero.moneroone.ui.components.successHaptic
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.monero.moneroone.R
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.locale.trRes
import one.monero.moneroone.core.username.*
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.components.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

/**
 * The orange edge of a claim (iOS UsernameCard.ClaimEdge). It laps the card from [start]. From
 * [close] on, its tail eases to a stop and its head runs on until it is a full ring.
 * Times are [System.nanoTime], the frame clock's time base.
 */
@Immutable
data class ClaimEdge(val start: Long, val close: Long? = null) {
    companion object {
        /** One lap per turn of the brand spinner's outer ring (`spinnerOuter`), in seconds. */
        const val LAP = 1.5
        /** The ring closes like Send's circle draws itself (`draw`), in seconds. */
        const val CLOSING = 0.4
        const val LENGTH = 0.24
    }

    /**
     * The tail and the head at [now], in laps from 12 o'clock, clockwise. The curves are the iOS
     * Core Animation ones: in the close, the tail eases to a stop over half the close's travel,
     * and the head keeps its speed at first and stops one lap ahead of the tail.
     */
    fun span(now: Long): Pair<Double, Double> {
        val speed = 1 / LAP
        val close = close ?: run {
            val tail = (max(0L, now - start) / 1e9 * speed) % 1.0
            return tail to tail + LENGTH
        }
        val base = (max(0L, close - start) / 1e9 * speed) % 1.0
        val carry = speed * CLOSING
        val u = ((now - close) / 1e9 / CLOSING).coerceIn(0.0, 1.0)
        val tail = base + carry / 2 * (1 - (1 - u) * (1 - u))
        val headFrom = base + LENGTH
        val headTo = base + carry / 2 + 1
        val y1 = carry / (3 * (headTo - headFrom))
        val y = 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u + u * u * u
        return tail to headFrom + (headTo - headFrom) * y
    }

    /** The ring is whole: the close has run its full time. */
    fun closed(now: Long): Boolean = close != null && now - close >= (CLOSING * 1e9).toLong()
}

/** The moment's values (iOS UsernameCard.celebrate): the pop, the ring's fade, the glow and one shine. */
@Stable
private class UsernameMoment {
    val scale = Animatable(1f)
    val ring = Animatable(1f)
    val glow = Animatable(0f)
    val glowWidth = Animatable(0.75f)
    val shine = Animatable(0f)

    /** The moment, once. Reduce Motion: the ring goes and the glow only fades in. */
    suspend fun celebrate(reduceMotion: Boolean) = coroutineScope {
        if (reduceMotion) {
            ring.snapTo(0f)
            glow.animateTo(0.2f, Motion.crossFade())
            return@coroutineScope
        }
        // The pop: the card grows a little (`stepForward`) and settles (`celebrate`).
        launch {
            withTimeoutOrNull(150) { scale.animateTo(1.04f, Motion.stepForward()) }
            scale.animateTo(1f, Motion.celebrate())
        }
        launch { ring.animateTo(0f, Motion.crossFade()) }
        launch { shine.animateTo(1f, tween(1200, easing = Motion.EaseInOut)) }
        // `heroGlow`: it blooms to the art's low state, then rests at its high state on the float's curve.
        coroutineScope {
            launch { glowWidth.animateTo(1.1f, Motion.celebrate()) }
            glow.animateTo(0.6f, Motion.celebrate())
        }
        launch { glowWidth.animateTo(0.75f, tween(2500, easing = Motion.EaseInOut)) }
        glow.animateTo(0.2f, tween(2500, easing = Motion.EaseInOut))
    }
}

/** The card's outline from 12 o'clock, clockwise: the path the claim edge runs on. */
private fun cardRing(size: Size, r: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w / 2, 0f)
    lineTo(w - r, 0f)
    arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
    lineTo(w, h - r)
    arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
    lineTo(r, h)
    arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    close()
}

/**
 * The approved Inset + Core card, shared by the reveal and the detail sheet (iOS UsernameCard).
 * [edge] is the claim edge, while the claim runs and until the ring has closed. [celebrates] is
 * the claim's moment: the card pops, the ring fades, a glow blooms under the card and one shine
 * sweeps across it. [awaitsClaim] (the confirm step) builds the glow and the shine hidden before
 * the claim, so neither the claim nor the pop builds them. [capture] records the card for the
 * share image (see [rememberUsernameCardShare]), without the glow.
 */
@Composable
fun UsernameCard(handle: String, externalAddress: String, modifier: Modifier = Modifier, still: Boolean = false,
                 edge: ClaimEdge? = null, celebrates: Boolean = false, awaitsClaim: Boolean = false,
                 capture: Modifier = Modifier) {
    val orange = MoneroOrange
    val reduceMotion = rememberReduceMotion()
    val tilt = rememberUsernameTilt(!still && !reduceMotion)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ink = if (dark) Color(0xFFECEDEF) else Color(0xFF363C40)
    val secondary = if (dark) Color(0xFFADB4B9) else Color(0xFF687176)
    val lip = if (dark) Color(0xFFA5410B) else Color(0xFFB74C17)
    val moment = remember { UsernameMoment() }
    LaunchedEffect(celebrates) { if (celebrates) moment.celebrate(reduceMotion) }
    // The edge's clock: the frame time, read only where the ring draws, so a frame redraws and
    // never recomposes. It stops once the ring is whole.
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(edge, reduceMotion) {
        if (edge == null || reduceMotion) return@LaunchedEffect
        while (true) {
            val now = withFrameNanos { it }
            frame.longValue = now
            if (edge.closed(now)) break
        }
    }
    // Reduce Motion: no lap. The edge stands still, then the full ring fades in.
    val wholeRing by animateFloatAsState(if (edge?.close != null) 1f else 0f, Motion.crossFade(), label = "ring")
    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = moment.scale.value; scaleY = moment.scale.value }) {
        // `heroGlow`, on the card's bottom edge and behind it.
        if (edge != null || awaitsClaim) {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val w = maxWidth * 0.7f
                val h = maxHeight * 0.15f
                Box(Modifier.align(Alignment.BottomCenter).offset(y = h / 2).size(w, h), contentAlignment = Alignment.Center) {
                    GlowEllipse(w, h, orange) {
                        scaleX = moment.glowWidth.value
                        alpha = moment.glow.value
                    }
                }
            }
        }
    BoxWithConstraints(capture.fillMaxWidth().aspectRatio(1000f / 600f).graphicsLayer {
        rotationY = tilt.x * 3; rotationX = -tilt.y * 2
    }.clearAndSetSemantics {
        contentDescription = tr("%s, Monero One username card. %s", handle, externalAddress)
    }.drawWithCache {
        val s = size.width / 1000f
        val ring = cardRing(size, 30 * s)
        val measure = PathMeasure().apply { setPath(ring, false) }
        val length = measure.length
        val stroke = Stroke(max(1.5.dp.toPx(), 4 * s))
        val segment = Path()
        onDrawWithContent {
            drawContent()
            val e = edge ?: return@onDrawWithContent
            when {
                celebrates -> drawPath(ring, orange, alpha = moment.ring.value, style = stroke)
                reduceMotion -> {
                    drawPath(ring, orange, alpha = wholeRing, style = stroke)
                    if (e.close == null) {
                        segment.reset()
                        measure.getSegment(0f, (ClaimEdge.LENGTH * length).toFloat(), segment, true)
                        drawPath(segment, orange, style = stroke)
                    }
                }
                else -> {
                    val now = frame.longValue.takeIf { it > 0 } ?: System.nanoTime()
                    val (tail, head) = e.span(now)
                    if (head - tail >= 0.999) {
                        drawPath(ring, orange, style = stroke)
                    } else {
                        val from = tail % 1.0
                        val to = from + (head - tail)
                        segment.reset()
                        measure.getSegment((from * length).toFloat(), (min(to, 1.0) * length).toFloat(), segment, true)
                        if (to > 1) measure.getSegment(0f, ((to - 1) * length).toFloat(), segment, true)
                        drawPath(segment, orange, style = stroke)
                    }
                }
            }
        }
    }) {
        val s = maxWidth.value / 1000f
        val cardWidth = maxWidth
        val cardHeight = maxHeight
        // Type follows the card width, not the text size, as on iOS: the card is a picture of a card.
        val density = LocalDensity.current
        fun cardSp(n: Float) = with(density) { (n * s).dp.toSp() }
        val shape = RoundedCornerShape((30*s).dp)
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            // Core's darker underside: a lip just below the bottom edge, over the shadow (iOS 4f1966e).
            val r = CornerRadius((30*s).dp.toPx())
            val card = Path().apply { addRoundRect(RoundRect(size.toRect(), r)) }
            val below = Path().apply { addRoundRect(RoundRect(size.toRect().translate(0f, (3*s).dp.toPx()), r)) }
            drawPath(Path.combine(PathOperation.Difference, below, card), lip)
        }.shadow((14*s).dp, shape).clip(shape)
            .background(Brush.linearGradient(if (dark) listOf(Color(0xFF343B3F), Color(0xFF262C30), Color(0xFF1A2025))
                else listOf(Color(0xFFF0F2F2), Color(0xFFE4E7E8), Color(0xFFD5DADD))))
            .border((1.5f*s).dp, Color.White.copy(alpha = if (dark) 0.12f else 0.6f), shape)) {
            Box(Modifier.fillMaxSize().padding((44*s).dp).clip(RoundedCornerShape((18*s).dp))
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (dark) 0.1f else 0.025f), Color.White.copy(alpha = if (dark) 0.01f else 0.14f))))
                .border((2*s).dp, Color.Black.copy(alpha = if (dark) 0.25f else 0.1f), RoundedCornerShape((18*s).dp)))
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width*(0.3f+tilt.x*0.2f), size.height*(0.18f+tilt.y*0.2f)), radius = size.width*0.7f))
            }
            Image(painterResource(R.drawable.monero_mark), contentDescription = null,
                modifier = Modifier.offset((87*s).dp, (87*s).dp).size((48*s).dp))
            Text("MONERO ONE", color = secondary, fontSize = cardSp(17f), fontWeight = FontWeight.Medium, letterSpacing = cardSp(2f),
                modifier = Modifier.offset((88*s).dp, (93*s).dp).width((824*s).dp), textAlign = TextAlign.End)
            Text(buildAnnotatedString { withStyle(SpanStyle(color = MoneroOrange)) { append("@") }; withStyle(SpanStyle(color = ink)) { append(handle) } },
                // Inter's widest capitals need this fixed size to stay inside the inset at 15 characters.
                fontSize = cardSp(51f), fontWeight = FontWeight.SemiBold, letterSpacing = cardSp(-1.8f), maxLines = 1,
                modifier = Modifier.offset((88*s).dp, (248*s).dp))
            Text(externalAddress, color = secondary, fontSize = cardSp(38f), letterSpacing = cardSp(-0.5f), maxLines = 1,
                modifier = Modifier.offset((88*s).dp, (328*s).dp))
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / 1000f
                drawLine(orange, Offset(88*scale, 510*scale), Offset(116*scale, 510*scale), 4*scale)
                drawLine(orange, Offset(884*scale, 510*scale), Offset(912*scale, 510*scale), 4*scale)
                val edge = Path().apply {
                    moveTo(0f, size.height - 22*scale)
                    quadraticTo(0f, size.height - 2*scale, 30*scale, size.height - 2*scale)
                    lineTo(size.width - 30*scale, size.height - 2*scale)
                    quadraticTo(size.width, size.height - 2*scale, size.width, size.height - 22*scale)
                }
                drawPath(edge, orange, style = Stroke(5*scale))
            }
            // `heroShine`: one white band, clipped to the card. It waits off the card from the
            // confirm step, so neither the claim nor the pop builds it.
            if ((edge != null || awaitsClaim) && !reduceMotion) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    ShineBand(cardWidth, offset = { moment.shine.value * 1.35f - 0.35f }, height = cardHeight)
                }
            }
        }
    }
    }
}

/**
 * Records the card as drawn for the share image, with a margin around it so the lip and the
 * shadow show: 40 on a 1000-wide card, as iOS renders 1000 x 600 with padding 40.
 * Pass the modifier to [UsernameCard]; the function returns the share bitmap.
 */
@Composable
internal fun rememberUsernameCardShare(): Pair<Modifier, suspend () -> android.graphics.Bitmap> {
    val layer = rememberGraphicsLayer()
    val shareLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(layer, shareLayer, density, dark) {
        var card = Size.Zero
        var margin = 0f
        val capture = Modifier.drawWithContent {
            margin = (size.width * 0.04f).roundToInt().toFloat()
            card = size
            layer.record(IntSize((size.width + 2 * margin).roundToInt(), (size.height + 2 * margin).roundToInt())) {
                translate(margin, margin) { this@drawWithContent.drawContent() }
            }
            translate(-margin, -margin) { drawLayer(layer) }
        }
        capture to suspend {
            // The card's Modifier.shadow does not reach the bitmap, so the share image draws the
            // iOS card shadow under it: black 15% (35% dark), radius 14, y 12 on a 1000-wide card.
            val s = card.width / 1000f
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = (if (dark) Color(0xFF262C30) else Color(0xFFE4E7E8)).toArgb()
                setShadowLayer(14 * s, 0f, 12 * s, Color.Black.copy(alpha = if (dark) 0.35f else 0.15f).toArgb())
            }
            shareLayer.record(density, LayoutDirection.Ltr, layer.size) {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(margin, margin, margin + card.width, margin + card.height, 30 * s, 30 * s, paint) }
                drawLayer(layer)
            }
            shareLayer.toImageBitmap().asAndroidBitmap()
        }
    }
}

/** The flow's pushed steps. Welcome is the root; Confirm holds the claim, its moment and its result. */
private enum class UsernameStep { WELCOME, CHOOSE, CONFIRM }

private val UsernameStage.step: UsernameStep get() = when (this) {
    UsernameStage.WELCOME -> UsernameStep.WELCOME
    UsernameStage.CHOOSE -> UsernameStep.CHOOSE
    else -> UsernameStep.CONFIRM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameSheet(wallet: WalletInfo, session: Long, wallets: WalletViewModel, model: UsernameViewModel,
                  binding: UsernameBinding?, onDismiss: () -> Unit) {
    val state by model.state.collectAsState()
    val store by model.store.state.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current
    val (cardCapture, shareCard) = rememberUsernameCardShare()
    // The claim's moment (iOS UsernameFlowView.land). [landed]: 1 the ring closes while the card
    // moves into its reveal place, 2 the pop. [arrived]: 1 the title, 2 the buttons, 3 the moment's end.
    var edge by remember { mutableStateOf<ClaimEdge?>(null) }
    var landed by remember { mutableIntStateOf(0) }
    var arrived by remember { mutableIntStateOf(0) }
    val flow = binding == null
    val result = flow && state.stage == UsernameStage.REVEALED
    val claiming = flow && (state.stage == UsernameStage.CLAIMING || (result && edge != null && landed == 0))
    val landing = flow && landed == 1
    // A result with no edge (the sheet came back after its moment) shows as it ends.
    val revealed = result && (landed == 2 || edge == null)
    val shownArrived = if (result && edge == null) 3 else arrived
    val busy = claiming || landing
    // No Done while the claim and its moment run. It comes back with Home's update, after the shine.
    val showsDone = !busy && !(revealed && shownArrived < 3)
    LaunchedEffect(state.stage, edge?.start) {
        val claim = edge ?: return@LaunchedEffect
        when (state.stage) {
            UsernameStage.CLAIMING -> Unit
            UsernameStage.REVEALED -> if (landed == 0) {
                // The build-up: the edge runs one full lap at least, also when the registry answers at once.
                val lap = (ClaimEdge.LAP * 1e9).toLong() - (System.nanoTime() - claim.start)
                if (lap > 0) delay(lap / 1_000_000)
                edge = claim.copy(close = System.nanoTime())
                landed = 1
                delay((ClaimEdge.CLOSING * 1000).toLong())
                // `celebrate`, as Send's success.
                landed = 2
                successHaptic(view)
                delay(200)
                arrived = 1
                delay(150)
                arrived = 2
                // The shine runs 1.2 s from the pop; with Reduce Motion the last fade ends sooner.
                delay(if (reduceMotion) 400 else 850)
                arrived = 3
                model.releaseHome()
            }
            // An error or no free claim: no moment.
            else -> { edge = null; landed = 0; arrived = 0 }
        }
    }
    // A sheet that closes mid-claim must not leave Home's update held.
    DisposableEffect(Unit) { onDispose { model.releaseHome() } }
    // One lambda, read through State: a lambda that captured [busy] made a new sheet state when it
    // changed, and the sheet slid in again (as ClaimSheet learned).
    val keepOpen by rememberUpdatedState(busy)
    val confirmChange = remember { { value: SheetValue -> value != SheetValue.Hidden || !keepOpen } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmChange)
    val scope = rememberCoroutineScope()
    // Done and Not Now slide the sheet down, as a swipe does, before it leaves.
    val close: () -> Unit = {
        scope.launch { sheet.hide() }.invokeOnCompletion { if (!sheet.isVisible) onDismiss() }
    }
    // A large sheet, as on iOS: it stops below the status bar and keeps one height for every step.
    ModalBottomSheet(onDismissRequest = { if (!keepOpen) onDismiss() }, sheetState = sheet,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
        // Dark: the card fill (#1C1C1E), as iOS sheets, so the sheet edge shows on the black page.
        containerColor = if (MoneroTheme.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        if (binding != null) {
            UsernamePage {
                UsernameBar(title = null, onBack = null, showsDone = true, onDone = close)
                Text(tr("Your Username"), style = MaterialTheme.typography.titleLarge)
                UsernameCard(binding.displayHandle, binding.externalAddress, capture = cardCapture)
                UsernameWalletNote(wallet)
                UsernameActions(binding, shareCard)
            }
            return@ModalBottomSheet
        }
        val pendingClaim = store.pending != null
        // System back goes back one step, as the back arrow does; the sheet stays. It sits in the
        // sheet's content, so it reaches the sheet dialog's back dispatcher after the sheet's own callback.
        val canGoBack = when (state.stage) {
            UsernameStage.CHOOSE -> true
            UsernameStage.CONFIRM -> !pendingClaim
            else -> false
        }
        BackHandler(enabled = canGoBack) { model.back() }
        // Choose and Confirm push from the trailing edge, as Send's steps do; back slides them out.
        AnimatedContent(
            targetState = state.stage.step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(Motion.stepForward()) { it } togetherWith slideOutHorizontally(Motion.stepForward()) { -it })
                        .using(SizeTransform(clip = true) { _, _ -> Motion.stepForward() })
                } else {
                    (slideInHorizontally(Motion.stepBack()) { -it } togetherWith slideOutHorizontally(Motion.stepBack()) { it })
                        .using(SizeTransform(clip = true) { _, _ -> Motion.stepBack() })
                }
            },
            label = "usernameStep"
        ) { step ->
            // A page that slides out keeps the stage it showed, not the one it leaves for.
            val stage = if (step == state.stage.step) state.stage else when (step) {
                UsernameStep.WELCOME -> UsernameStage.WELCOME
                UsernameStep.CHOOSE -> UsernameStage.CHOOSE
                UsernameStep.CONFIRM -> UsernameStage.CONFIRM
            }
            UsernamePage {
                UsernameBar(
                    title = if (step == UsernameStep.WELCOME) null else tr("Your Username"),
                    onBack = if (step == UsernameStep.CHOOSE || (stage == UsernameStage.CONFIRM && !pendingClaim)) model::back else null,
                    showsDone = showsDone, onDone = close)
                when (step) {
                    UsernameStep.WELCOME -> {
                        Icon(Icons.Default.AlternateEmail, null, tint = MoneroOrange, modifier = Modifier.padding(top = 24.dp).size(100.dp))
                        Text(tr("Get Your Username."), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(tr("A short username people can use to pay you."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        UsernamePrimary(tr("Choose My Username"), action = model::choose)
                        TextButton(onClick = close) { Text(tr("Not Now")) }
                    }
                    UsernameStep.CHOOSE -> {
                        Text(tr("Choose Your Username"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        val valid = remember(state.input) { runCatching { OahCore.normalize(state.input) }.isSuccess }
                        OutlinedTextField(state.input, model::input, modifier = Modifier.fillMaxWidth(), prefix = { Text("@", color = MoneroOrange) },
                            singleLine = true, textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                            placeholder = { Text(tr("username")) })
                        Text(usernameValidation(state.input), style = MaterialTheme.typography.bodySmall, color = if (valid || state.input.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MoneroOrange)
                        UsernameWalletNote(wallet)
                        UsernamePrimary(tr("Continue"), enabled = valid, action = model::confirm)
                    }
                    UsernameStep.CONFIRM -> if (stage == UsernameStage.NO_FREE_CLAIM) {
                        Text(tr("A free username isn't available on this phone."), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        UsernamePrimary(tr("Done"), action = close)
                    } else {
                        val claimed = state.binding.takeIf { stage != UsernameStage.CONFIRM }
                        // The reveal's layout, with room for the title above the card. Without Reduce Motion
                        // the room opens (`settle`) and the card moves down while the ring closes.
                        val revealLayout = revealed || (landing && !reduceMotion)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedVisibility(revealLayout,
                                enter = if (reduceMotion) EnterTransition.None else expandVertically(Motion.settle(), expandFrom = Alignment.Top),
                                exit = ExitTransition.None) {
                                // Hidden until it arrives, so the card does not move again.
                                UsernameArrive(shownArrived >= 1, reduceMotion) {
                                    Text(tr("It's Yours."), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                                }
                            }
                            UsernameCard(claimed?.displayHandle ?: state.input,
                                claimed?.externalAddress ?: "${state.input.lowercase(java.util.Locale.ROOT)}@monero.one",
                                // The tilt starts after the moment, not with the pop.
                                still = claiming || landing || (revealed && shownArrived < 3),
                                edge = if (stage == UsernameStage.CONFIRM) null else edge,
                                celebrates = landed == 2, awaitsClaim = true, capture = cardCapture)
                        }
                        UsernameWalletNote(wallet)
                        if (claiming || landing) {
                            // It fades where it is while the card moves, so the note passes no text.
                            val shown by animateFloatAsState(if (claiming) 1f else 0f, Motion.crossFade(), label = "claiming")
                            Text(trRes(R.string.username_claiming, "Claiming…"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { alpha = shown })
                        } else if (stage == UsernameStage.CONFIRM) {
                            Text(tr("Usernames are permanent."))
                            Text(tr("This is a local development claim."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            UsernamePrimary(tr("Claim @%s", state.input)) {
                                edge = ClaimEdge(System.nanoTime())
                                landed = 0
                                arrived = 0
                                model.claim(wallet, session, wallets)
                            }
                        }
                        // The buttons are laid out, hidden, as soon as the result is in, so the pop does
                        // not wait for them to be built.
                        if (claimed != null) {
                            UsernameArrive(shownArrived >= 2, reduceMotion) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    UsernameActions(claimed, shareCard, enabled = shownArrived >= 2)
                                    UsernamePrimary(tr("Done"), enabled = shownArrived >= 2, action = close)
                                }
                            }
                        }
                    }
                }
                if (step == UsernameStep.CONFIRM && state.error != null && stage != UsernameStage.NO_FREE_CLAIM) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One step of the flow, at the full sheet height as on iOS (a large sheet): the claim and its moment never move the sheet. */
@Composable private fun UsernamePage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 24.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
}

/** The step's bar: the back glyph, the title and Done (iOS navigation bar). */
@Composable private fun UsernameBar(title: String?, onBack: (() -> Unit)?, showsDone: Boolean, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
            }
        }
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center).semantics { heading() })
        }
        AnimatedVisibility(showsDone, Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(Motion.crossFade()), exit = fadeOut(Motion.crossFade())) {
            TextButton(onClick = onDone) { Text(tr("Done")) }
        }
    }
}

/** `arrive` for the reveal's title and buttons: fade in and rise 20 dp. Reduce Motion: a fade only. */
@Composable private fun UsernameArrive(shown: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(400, easing = Motion.EaseOut), label = "arrive")
    Box(Modifier.graphicsLayer {
        alpha = progress
        translationY = if (reduceMotion) 0f else (1 - progress) * Motion.ARRIVE_RISE_DP.dp.toPx()
    }.then(if (shown) Modifier else Modifier.clearAndSetSemantics { })) { content() }
}

@Composable private fun UsernameWalletNote(wallet: WalletInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Payments go to"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${wallet.emoji} ${wallet.name}", fontWeight = FontWeight.Medium)
    }
}
/** The sheet's primary button. The whole button takes taps (the iOS Continue bug: text only). */
@Composable internal fun UsernamePrimary(title: String, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun UsernameActions(binding: UsernameBinding, shareCard: suspend () -> android.graphics.Bitmap, enabled: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    fun copy(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Username", value))
        menu = false; copied = true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Copy"))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("${binding.shortName} · Monero One") }, onClick = { copy(binding.shortName) })
                    DropdownMenuItem(text = { Text(tr("%s · Other apps", binding.externalAddress)) }, onClick = { copy(binding.externalAddress) })
                    DropdownMenuItem(text = { Text(tr("Share link")) }, onClick = { copy(binding.shareUrl) })
                }
            }
            OutlinedButton(onClick = {
                sharing = true; shareError = false
                scope.launch {
                    try {
                        val bitmap = shareCard()
                        val uri = withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
                            val file = File(directory, "username-card.png")
                            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            FileProvider.getUriForFile(context, "${context.packageName}.qrshare", file)
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "${binding.shortName}\n${binding.externalAddress}\n${binding.shareUrl}")
                            clipData = ClipData.newUri(context.contentResolver, "Username card", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, tr("Share username")))
                    } catch (_: Exception) { shareError = true }
                    finally { sharing = false }
                }
            }, enabled = enabled && !sharing, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                Icon(Icons.Default.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Share"))
            }
        }
        if (copied) Text(tr("Copied"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shareError) Text(tr("The card couldn't be shared. Please try again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Follows the same relative attitude as iOS, and stops when the sheet or app leaves. */
@Composable private fun rememberUsernameTilt(enabled: Boolean): Offset {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, lifecycle, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotation = FloatArray(9); val orientation = FloatArray(3)
        var initial: Offset? = null
        val listener = object : android.hardware.SensorEventListener {
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) { }
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                android.hardware.SensorManager.getRotationMatrixFromVector(rotation, event.values)
                android.hardware.SensorManager.getOrientation(rotation, orientation)
                val current = Offset(orientation[2], orientation[1])
                if (initial == null) initial = current
                val delta = current - initial!!
                tilt = Offset((delta.x*2).coerceIn(-1f,1f), (delta.y*2).coerceIn(-1f,1f))
            }
        }
        fun update() {
            manager.unregisterListener(listener); initial = null; tilt = Offset.Zero
            if (enabled && sensor != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                manager.registerListener(listener, sensor, 33_333)
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _,_ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); manager.unregisterListener(listener) }
    }
    return tilt
}
private fun usernameValidation(input: String): String {
    if (input.isEmpty()) return tr("3–15 characters. Letters, numbers and _.")
    return try { OahCore.normalize(input); tr("3–15 characters. Availability is checked when you claim.") }
    catch (e: OahException) { when (e.code) {
        "handle.too_short" -> tr("Use at least 3 characters.")
        "handle.too_long" -> tr("Use no more than 15 characters.")
        "handle.first_not_letter" -> tr("Start with a letter.")
        "handle.double_underscore" -> tr("Use only one _ in a row.")
        "handle.blocked" -> tr("This username is reserved to protect users from scams.")
        else -> tr("Use only a–z, 0–9 and _.")
    } }
}
