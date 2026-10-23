package one.monero.moneroone.ui.screens.send

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.util.SoundFeedback
import one.monero.moneroone.paymentlinks.CHECK_RISE_DP
import one.monero.moneroone.paymentlinks.LinkPacking
import one.monero.moneroone.paymentlinks.LinkPackingHero
import one.monero.moneroone.paymentlinks.LinkSendResult
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ShrinkToFitText
import one.monero.moneroone.ui.components.rememberReduceMotion
import one.monero.moneroone.ui.components.softTickHaptic
import one.monero.moneroone.ui.components.successHaptic
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SettingsBlue
import one.monero.moneroone.ui.theme.SettingsCyan
import one.monero.moneroone.ui.theme.SettingsPink
import one.monero.moneroone.ui.theme.SettingsPurple
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.WarningYellow
import one.monero.moneroone.ui.theme.truncateMiddle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The amount at the top of Sending and Sent: the big number and the other unit under it. */
internal class SentAmount(val big: String, val unit: String?, val other: String?)

/**
 * Sending and Sent on one screen (moment 3). The amount stays at the top.
 * At success the spinner fades out, the green circle draws (draw, 0.4 s),
 * then the check strokes in (checkStroke) with the success haptic and the
 * chime. When the check lands (660 ms) the ring burst and the confetti start.
 * "Sent!", the hash pill and Done arrive. Reduce Motion: cross-fades, no
 * burst, no confetti; the haptic and the chime play at once.
 *
 * A payment link ([link]) keeps the same sending screen. At success the amount fades with the
 * spinner, the green check turns into the note and the note is sealed in the envelope
 * ([LinkPacking]); "Link ready", the other unit, Share and Done arrive.
 */
@Composable
internal fun SendStatusPhase(
    success: Boolean,
    amount: SentAmount?,
    txHash: String,
    onDone: () -> Unit,
    link: LinkSendResult? = null,
    /** Under "Sent!": where a Transfer shows next ("It shows in Savings when you next open that wallet."). */
    note: String? = null
) {
    val reduceMotion = rememberReduceMotion()
    val context = LocalContext.current
    val view = LocalView.current
    // A screen made again (rotation) shows the end state: the moment plays once.
    var played by rememberSaveable { mutableStateOf(false) }
    val sendingAlpha = remember { Animatable(if (success) 0f else 1f) }
    val circle = remember { Animatable(if (played) 1f else 0f) }
    val check = remember { Animatable(if (played) 1f else 0f) }
    val heroAlpha = remember { Animatable(if (played || !reduceMotion) 1f else 0f) }
    val sentIn = remember { Animatable(if (played) 1f else 0f) }
    val pillIn = remember { Animatable(if (played) 1f else 0f) }
    val doneIn = remember { Animatable(if (played) 1f else 0f) }
    var burst by remember { mutableStateOf(false) }
    val packing = remember { LinkPacking(settled = played) }

    LaunchedEffect(success) {
        if (!success || played) return@LaunchedEffect
        played = true
        coroutineScope {
            launch { sendingAlpha.animateTo(0f, Motion.crossFade()) }
            if (link != null) {
                packing.play(view, context, reduceMotion)
                return@coroutineScope
            }
            if (reduceMotion) {
                circle.snapTo(1f)
                check.snapTo(1f)
                successHaptic(view)
                SoundFeedback.sendComplete(context)
                launch { heroAlpha.animateTo(1f, Motion.crossFade()) }
                launch { sentIn.animateTo(1f, Motion.crossFade()) }
                launch { pillIn.animateTo(1f, Motion.crossFade()) }
                launch { doneIn.animateTo(1f, Motion.crossFade()) }
                return@coroutineScope
            }
            launch { circle.animateTo(1f, tween(Motion.DRAW_MS, easing = Motion.EaseInOut)) }
            launch { delay(400); sentIn.animateTo(1f, Motion.arrive()) }
            launch { delay(500); pillIn.animateTo(1f, Motion.arrive()) }
            launch { delay(600); doneIn.animateTo(1f, Motion.arrive()) }
            delay(Motion.DRAW_MS.toLong())
            // The circle closes: the success haptic and the chime.
            successHaptic(view)
            SoundFeedback.sendComplete(context)
            // The check: the short leg to the corner, then the long leg.
            check.animateTo(CHECK_CORNER, tween(Motion.CHECK_SHORT_MS, easing = LinearEasing))
            check.animateTo(1f, tween(Motion.CHECK_LONG_MS, easing = Motion.EaseOut))
            burst = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            if (amount != null) {
                // A link's amount goes with the spinner: the note shows it.
                AmountBlock(amount, Modifier.graphicsLayer { alpha = if (link != null) sendingAlpha.value else 1f })
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // The hero slot: the spinner, then the drawn circle and check with the burst and confetti.
            Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                if (sendingAlpha.value > 0f) {
                    Box(modifier = Modifier.graphicsLayer { alpha = sendingAlpha.value }) { GradientSpinner() }
                }
                if (link != null) {
                    LinkPackingSlot(packing, link, success, reduceMotion)
                } else if (success) {
                    if (burst && !reduceMotion) RingBurst()
                    DoneCheck(
                        circle = circle.value,
                        check = check.value,
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer { alpha = heroAlpha.value }
                            .semantics { contentDescription = tr("Success") }
                    )
                    if (burst && !reduceMotion) Confetti()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // The text slot holds both states, so nothing above it moves.
            Box(contentAlignment = Alignment.TopCenter) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { alpha = sendingAlpha.value }
                ) {
                    Text(tr("Sending Transaction..."), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        tr("Please wait while your transaction is being broadcast"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                if (success && link != null) {
                    LinkReadyText(amount?.other, Modifier.graphicsLayer { alpha = packing.readyIn.value })
                } else if (success) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tr("Sent!"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.arrive(sentIn.value, reduceMotion)
                        )
                        if (note != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.arrive(sentIn.value, reduceMotion)
                            )
                        }
                        if (txHash.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HashPill(txHash, Modifier.arrive(pillIn.value, reduceMotion))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (success && link != null) {
                PrimaryButton(
                    onClick = link.onShare,
                    modifier = Modifier.fillMaxWidth().arrive(packing.shareIn.value, reduceMotion)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Share"))
                }
                Spacer(modifier = Modifier.height(12.dp))
                DismissTextButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().arrive(packing.doneIn.value, reduceMotion)
                ) {
                    Text(tr("Done"), fontWeight = FontWeight.Medium)
                }
            } else if (success) {
                PrimaryButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().arrive(doneIn.value, reduceMotion)
                ) {
                    Text(tr("Done"))
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Where the check's corner is on its path: the short leg is 17√2 of the 17√2 + 33√2 path. */
private const val CHECK_CORNER = 0.354f

/** arrive: the fade and the 20 dp rise; Reduce Motion fades only. */
private fun Modifier.arrive(progress: Float, reduceMotion: Boolean): Modifier = graphicsLayer {
    alpha = progress
    if (!reduceMotion) translationY = (1f - progress) * Motion.ARRIVE_RISE_DP * density
}

/**
 * A link's hero slot: today's check, ring and confetti, and over them the note and the envelope
 * ([LinkPackingHero]). The hero is the claim sheet's 338 x 280 box, placed so the note starts on
 * the check. It is there while the funding goes out, so its art is ready at the result.
 */
@Composable
private fun LinkPackingSlot(packing: LinkPacking, link: LinkSendResult, success: Boolean, reduceMotion: Boolean) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val heroWidth = minOf(LinkHeroWidth, screenWidth - 32.dp)
    val heroHeight = heroWidth * LinkHeroAspect
    val heroScale = heroWidth / LinkHeroWidth
    if (success) {
        if (packing.burst && !reduceMotion) RingBurst()
        LinkCheck(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    alpha = packing.checkAlpha.value
                    scaleX = packing.checkScale.value
                    scaleY = packing.checkScale.value
                    translationY = -packing.checkRise.value * CHECK_RISE_DP * heroScale * density
                }
        )
        if (packing.confetti && !reduceMotion) Confetti()
    }
    LinkPackingHero(
        packing = packing,
        face = link.face,
        spoken = link.spoken,
        // Centered on the slot, then up: the note starts at the check (71.4% down the hero box).
        modifier = Modifier
            .offset(y = -heroHeight * (LinkNoteStart - 0.5f))
            .requiredSize(heroWidth, heroHeight)
    )
}

/** The claim sheet's hero box: 338 x 280 dp. The note starts as the check, 200 dp down it. */
private val LinkHeroWidth = 338.dp
private const val LinkHeroAspect = 828.4f / 1000f
private const val LinkNoteStart = 200f / 280f

/**
 * Today's link check (iOS SuccessCheckmarkView, checkmark.circle.fill at 80 pt): an 80 dp green
 * disc with the check cut out of it.
 */
@Composable
private fun LinkCheck(modifier: Modifier = Modifier) {
    val green = SuccessGreen
    val cut = MaterialTheme.colorScheme.background
    val check = remember { Path() }
    Canvas(modifier = modifier) {
        val d = 80.dp.toPx()
        val left = center.x - d / 2
        val top = center.y - d / 2
        drawCircle(green, radius = d / 2, center = center)
        // The symbol's check, measured on its 80 pt circle.
        check.reset()
        check.moveTo(left + 0.315f * d, top + 0.546f * d)
        check.lineTo(left + 0.441f * d, top + 0.685f * d)
        check.lineTo(left + 0.685f * d, top + 0.328f * d)
        drawPath(check, cut, style = Stroke(width = 0.092f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** "Link ready", the other unit (as the claim sheet shows it) and how to send the link. */
@Composable
private fun LinkReadyText(other: String?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Text(
            tr("Link ready"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SuccessGreen
        )
        if (other != null) {
            Text(
                other,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            tr("Send it to one person in a private chat. Anyone who has this link can claim the money."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AmountBlock(amount: SentAmount, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            // Shrinks like the amount step (minimumScaleFactor 0.4) instead of clipping.
            ShrinkToFitText(
                text = amount.big,
                style = MaterialTheme.typography.displayLarge,
                minScale = 0.4f,
                modifier = Modifier.weight(1f, fill = false).alignByBaseline()
            )
            if (amount.unit != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = amount.unit,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        if (amount.other != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount.other,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The 120 dp green circle (4 dp) drawn from 12 o'clock, and the check stroked inside it. */
@Composable
private fun DoneCheck(circle: Float, check: Float, modifier: Modifier = Modifier) {
    val green = SuccessGreen
    val checkPath = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier = modifier) {
        // The mockup's 160 box: r 60, check M55 79 L72 96 L103 63.
        val unit = size.minDimension / 160f
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (circle > 0f) {
            drawArc(
                color = green,
                startAngle = -90f,
                sweepAngle = 360f * circle,
                useCenter = false,
                topLeft = Offset(20f * unit, 20f * unit),
                size = Size(120f * unit, 120f * unit),
                style = stroke
            )
        }
        if (check > 0f) {
            checkPath.reset()
            checkPath.moveTo(55f * unit, 79f * unit)
            checkPath.lineTo(72f * unit, 96f * unit)
            checkPath.lineTo(103f * unit, 63f * unit)
            measure.setPath(checkPath, false)
            segment.reset()
            measure.getSegment(0f, measure.length * check, segment, true)
            drawPath(segment, green, style = stroke)
        }
    }
}

/** The expanding ring burst (kept from the old Sent screen): 100 dp, 3 dp, scale 0.8 to 2 and out, easeOut 0.8 s. */
@Composable
private fun RingBurst() {
    val green = SuccessGreen
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(2f, tween(800, easing = Motion.EaseOut)) }
        alpha.animateTo(0f, tween(800, easing = Motion.EaseOut))
    }
    Box(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
            .drawBehind { drawCircle(color = green, style = Stroke(width = 3.dp.toPx())) }
    )
}

private class ConfettiPiece(
    val color: Color,
    val size: Float,
    val shape: Int,
    val endX: Float,
    val endY: Float,
    val spin: Float,
    val durationMs: Float
)

/** The iOS confetti: 50 pieces fly 80 to 200 dp out from the check, spin and fade (easeOut 0.8 to 1.4 s). */
@Composable
private fun Confetti() {
    val colors = listOf(MoneroOrange, SuccessGreen, SettingsBlue, SettingsPink, SettingsPurple, WarningYellow, SettingsCyan)
    val pieces = remember {
        List(50) {
            val angle = Random.nextDouble(0.0, 2 * PI)
            val distance = Random.nextDouble(80.0, 200.0)
            ConfettiPiece(
                color = colors.random(),
                size = Random.nextDouble(4.0, 10.0).toFloat(),
                shape = Random.nextInt(0, 3),
                endX = (cos(angle) * distance).toFloat(),
                endY = (sin(angle) * distance).toFloat(),
                spin = Random.nextDouble(0.0, 720.0).toFloat(),
                durationMs = Random.nextDouble(800.0, 1400.0).toFloat()
            )
        }
    }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < 1400L) withFrameMillis { elapsed = it - start }
    }
    val triangle = remember { Path() }
    Canvas(modifier = Modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        pieces.forEach { p ->
            val t = Motion.EaseOut.transform((elapsed / p.durationMs).coerceIn(0f, 1f))
            if (t >= 1f) return@forEach
            val at = center + Offset(p.endX.dp.toPx() * t, p.endY.dp.toPx() * t)
            val side = p.size.dp.toPx()
            val alpha = 1f - t
            rotate(p.spin * t, pivot = at) {
                when (p.shape) {
                    0 -> drawCircle(p.color, radius = side / 2, center = at, alpha = alpha)
                    1 -> drawRect(p.color, topLeft = at - Offset(side / 2, side / 2), size = Size(side, side), alpha = alpha)
                    else -> {
                        triangle.reset()
                        triangle.moveTo(at.x, at.y - side / 2)
                        triangle.lineTo(at.x + side / 2, at.y + side / 2)
                        triangle.lineTo(at.x - side / 2, at.y + side / 2)
                        triangle.close()
                        drawPath(triangle, p.color, alpha = alpha)
                    }
                }
            }
        }
    }
}

@Composable
private fun HashPill(txHash: String, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current
    var copied by remember { mutableStateOf(false) }
    // Back to the copy label after 2 s, as on iOS
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val tint = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (copied) tr("Transaction ID copied") else tr("Copy transaction ID")
    // As on iOS: the whole pill copies, and the icon comes before the ID.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) {
                clipboardManager.setText(AnnotatedString(txHash))
                copied = true
                softTickHaptic(view)
            }
            .clearAndSetSemantics { contentDescription = label; role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (copied) tr("Copied!") else truncateMiddle(txHash),
            style = MonoCaption,
            color = tint
        )
    }
}
