package one.monero.moneroone.paymentlinks

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.util.SoundFeedback
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.softTickHaptic
import one.monero.moneroone.ui.components.successHaptic
import one.monero.moneroone.ui.theme.SuccessGreen
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.exp

private const val TAG = "LinkEnvelope"

/** tokens.json motion.hero heroEntrance: SwiftUI .spring(response: 0.8, dampingFraction: 0.7). */
private fun <T> heroEntrance() = spring<T>(dampingRatio = 0.7f, stiffness = 61.685f)

/** tokens.json motion.springs celebrate: SwiftUI .spring(response: 0.5, dampingFraction: 0.7). */
private fun <T> celebrate() = spring<T>(dampingRatio = 0.7f, stiffness = 157.9f)

// The opening (link-card brief, "Motion"), in ms. t0 is the crack: 0.42 s after the entrance starts,
// or when the check ends, whichever is later.
private const val CRACK_AFTER_ENTRANCE_MS = 420L
private const val CRACK_MS = 120
private const val GLINT_DELAY_MS = 20L
private const val FLAP_AT_MS = 140L
private const val RISE_AT_MS = 400L
private const val LAND_AT_MS = 680L
private const val SWEEP_AT_MS = 880L
private const val SWEEP_MS = 1200
/** Reduce Motion: one cross-fade, half out and half in, with the success haptic in its middle. */
private const val HALF_FADE_MS = Motion.CROSS_FADE_MS / 2

// The check's light: the sweep's band crosses the sealed envelope again and again while the link is
// checked (the shine on a Wallet pass). A check that ends mid-pass fades the band out as it goes on.
private const val SHINE_PASS_MS = 1600
private const val SHINE_REST_MS = 800L
private const val SHINE_FADE_OUT_MS = 300
/** Reduce Motion: no travel; the band stays here, on the seal, and only fades in and out. */
private const val SHINE_STILL_AT = 0.5f

/**
 * Reduce Motion is Android's Remove animations (animator scale 0), which ends every Compose animation
 * at once. The check's light still fades under it, as on iOS, so it runs at the normal scale.
 */
private object UnscaledMotion : MotionDurationScale {
    override val scaleFactor = 1f
}

// The tilt: the light's rest place on the 0..1 tilt scale, and how far the phone turns for the full scale.
private val REST_TILT = Offset(
    (EnvelopeGeometry.REST_LX - 0.08f) / 0.84f,
    (EnvelopeGeometry.REST_LY - 0.05f) / 0.7f
)
private const val TILT_RANGE_RAD = 0.52f
private const val TILT_SMOOTHING_S = 0.08f

/** The card's face: the amount, and "XMR" after it (XMR mode), or the fiat amount alone (Fiat Mode). */
internal data class EnvelopeCardFace(val number: String, val unit: Boolean)

/**
 * The amounts of an open envelope: the card's face, the line under the envelope (the other unit,
 * null with no price) and the TalkBack words for both ("About 2.49996 XMR, $856.75").
 */
internal data class EnvelopeFigures(val card: EnvelopeCardFace, val line: String?, val spoken: String)

/**
 * In XMR mode the card shows "~2.49996" and "XMR", and the line "~$856.75". In Fiat Mode the card
 * shows "~$856.75" with no unit, and the line "~2.49996 XMR". [estimated]: the amount is what a
 * claim delivers, an estimate ("~").
 */
internal fun envelopeFigures(xmr: String, fiat: String?, estimated: Boolean, fiatMode: Boolean): EnvelopeFigures {
    val xmrText = (if (estimated) "~" else "") + xmr
    val spoken = listOfNotNull(if (estimated) tr("About %s XMR", xmr) else "$xmr XMR", fiat).joinToString(", ")
    return if (fiatMode && fiat != null) {
        EnvelopeFigures(EnvelopeCardFace("~$fiat", unit = false), "$xmrText XMR", spoken)
    } else {
        EnvelopeFigures(EnvelopeCardFace(xmrText, unit = true), fiat?.let { "~$it" }, spoken)
    }
}

/**
 * The envelope's motion on the claim sheet: the entrance once the sheet is at rest, then, for a link
 * that holds money, the opening (crack, flap, card, haptic, sweep). [landed] turns true when the
 * card is out: the rest of the sheet arrives then.
 */
@Stable
internal class EnvelopeMotion {
    val entrance = Animatable(0f)
    val fade = Animatable(1f)
    val crack = Animatable(0f)
    val glint = Animatable(0f)
    val flap = Animatable(0f)
    val drop = Animatable(0f)
    val rise = Animatable(0f)
    val sweep = Animatable(-1f)
    /** The band's strength, 0 to 1: the check's light fades out with it (Reduce Motion: it only fades). */
    val sweepAlpha = Animatable(1f)
    /** True while the link is checked: the light crosses the sealed envelope ([shine]). */
    var checking by mutableStateOf(false)
    /** A pass of the check's light is on the envelope: the opening waits for it to leave. */
    private var shining by mutableStateOf(false)
    var split by mutableStateOf(false)
        private set
    var opened by mutableStateOf(false)
        private set
    var landed by mutableStateOf(false)
        private set
    private var enteredAt = 0L
    private val entered = CompletableDeferred<Unit>()

    /**
     * The heroEntrance (scale 0.4 to 1 and fade). Reduce Motion: no scale, it is there. Called
     * again after a cancel, it goes on from where it stopped.
     */
    suspend fun enter(reduceMotion: Boolean) {
        if (enteredAt == 0L) {
            enteredAt = SystemClock.uptimeMillis()
            entered.complete(Unit)
        }
        if (reduceMotion) entrance.snapTo(1f) else entrance.animateTo(1f, heroEntrance())
    }

    /** The opening, once: at t0 the crack and a soft tick, the success haptic when the card lands. */
    suspend fun open(view: View, reduceMotion: Boolean) = coroutineScope {
        if (opened) return@coroutineScope
        entered.await()
        val wait = enteredAt + CRACK_AFTER_ENTRANCE_MS - SystemClock.uptimeMillis()
        if (wait > 0) delay(wait)
        // The check's light leaves first (it fades out within SHINE_FADE_OUT_MS).
        snapshotFlow { shining }.first { !it }
        opened = true
        if (reduceMotion) {
            fade.animateTo(0f, tween(HALF_FADE_MS, easing = Motion.EaseInOut))
            showOpen()
            successHaptic(view)
            landed = true
            fade.animateTo(1f, tween(HALF_FADE_MS, easing = Motion.EaseInOut))
            return@coroutineScope
        }
        softTickHaptic(view)
        launch { crack.animateTo(1f, tween(CRACK_MS, easing = Motion.EaseOut)) }
        launch { delay(GLINT_DELAY_MS); glint.animateTo(1f, tween(CRACK_MS, easing = Motion.EaseOut)) }
        delay(FLAP_AT_MS)
        split = true
        launch { flap.animateTo(180f, Motion.stepForward()) }
        launch { drop.animateTo(1f, Motion.stepForward()) }
        delay(RISE_AT_MS - FLAP_AT_MS)
        launch { rise.animateTo(1f, celebrate()) }
        delay(LAND_AT_MS - RISE_AT_MS)
        landed = true
        successHaptic(view)
        delay(SWEEP_AT_MS - LAND_AT_MS)
        sweep.snapTo(-0.35f)
        sweep.animateTo(1.35f, tween(SWEEP_MS, easing = Motion.EaseInOut))
        sweep.snapTo(-1f)
    }

    /**
     * The check's light, for as long as the sheet shows: once the entrance has landed, while [checking]
     * and sealed, the sweep's band crosses the envelope (SHINE_PASS_MS easeInOut, then SHINE_REST_MS
     * off). When the check ends mid-pass, the band keeps moving and fades out; nothing stops mid-envelope.
     * Reduce Motion: the band stays on the seal and fades in and out at the same cadence.
     */
    suspend fun shine(reduceMotion: Boolean) = withContext(if (reduceMotion) UnscaledMotion else EmptyCoroutineContext) {
        entered.await()
        snapshotFlow { !entrance.isRunning && entrance.value >= 1f }.first { it }
        try {
            shineLoop(reduceMotion)
        } finally {
            // Cancelled mid-pass (the sheet left): no band stays behind, and no opening waits for it.
            if (shining) {
                shining = false
                withContext(NonCancellable) { sweep.snapTo(-1f); sweepAlpha.snapTo(1f) }
            }
        }
    }

    private suspend fun shineLoop(reduceMotion: Boolean) {
        while (true) {
            snapshotFlow { checking && !opened }.first { it }
            shining = true
            coroutineScope {
                val pass = launch {
                    if (reduceMotion) {
                        sweepAlpha.snapTo(0f)
                        sweep.snapTo(SHINE_STILL_AT)
                        sweepAlpha.animateTo(1f, tween(SHINE_PASS_MS / 2, easing = Motion.EaseInOut))
                        sweepAlpha.animateTo(0f, tween(SHINE_PASS_MS / 2, easing = Motion.EaseInOut))
                    } else {
                        sweep.snapTo(-0.35f)
                        sweep.animateTo(1.35f, tween(SHINE_PASS_MS, easing = Motion.EaseInOut))
                    }
                }
                var fading = false
                val stop = launch {
                    snapshotFlow { checking }.first { !it }
                    fading = true
                    // Under Reduce Motion this takes over the pass's own fade, from where it is.
                    sweepAlpha.animateTo(0f, tween(SHINE_FADE_OUT_MS, easing = Motion.EaseOut))
                    pass.cancel()
                }
                pass.join()
                if (fading) stop.join() else stop.cancel()
            }
            sweep.snapTo(-1f)
            sweepAlpha.snapTo(1f)
            shining = false
            delay(SHINE_REST_MS)
        }
    }

    /** Back to sealed (the link stopped holding money): one cross-fade, no haptic. */
    suspend fun seal() {
        if (!opened) return
        fade.animateTo(0f, tween(HALF_FADE_MS, easing = Motion.EaseInOut))
        split = false
        landed = false
        opened = false
        crack.snapTo(0f); glint.snapTo(0f); flap.snapTo(0f); drop.snapTo(0f); rise.snapTo(0f); sweep.snapTo(-1f)
        fade.animateTo(1f, tween(HALF_FADE_MS, easing = Motion.EaseInOut))
    }

    private suspend fun showOpen() {
        split = true
        crack.snapTo(1f); glint.snapTo(1f); flap.snapTo(180f); drop.snapTo(1f); rise.snapTo(1f)
    }
}

/**
 * The claim sheet's hero, 338 x 280 dp at most: the sealed envelope, or the open one with the
 * ivory card out ([face]). TalkBack reads [spoken] once it is open; sealed, it reads nothing (the
 * text under it says what is going on).
 */
@Composable
internal fun LinkEnvelopeHero(
    motion: EnvelopeMotion,
    face: EnvelopeCardFace?,
    spoken: String?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tilt = rememberTilt(enabled = !reduceMotion)
    var widthPx by remember { mutableIntStateOf(0) }
    val scale = widthPx / EnvelopeGeometry.FRAME_W * EnvelopeGeometry.ENV_SCALE
    val art by produceState<EnvelopeArt?>(null, widthPx) {
        if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.render(scale) }
    }
    val label = tr("Payment Link").uppercase(Locale.getDefault())
    val card by produceState<Bitmap?>(null, widthPx, face, label) {
        if (widthPx > 0 && face != null) {
            value = withContext(Dispatchers.Default) {
                EnvelopeArt.renderCard(context, scale, face.number, if (face.unit) "XMR" else null, label)
            }
        }
    }
    val said = spoken?.takeIf { motion.opened }
    Canvas(
        modifier
            .widthIn(max = 338.dp)
            .fillMaxWidth()
            .aspectRatio(EnvelopeGeometry.FRAME_W / EnvelopeGeometry.FRAME_H)
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                val e = motion.entrance.value
                val s = 0.4f + 0.6f * e
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f) * motion.fade.value
                val t = tilt.value
                rotationY = (t.x - REST_TILT.x) * 16f
                rotationX = -(t.y - REST_TILT.y) * 11f
                cameraDistance = 16f * density
            }
            .clearAndSetSemantics { if (said != null) contentDescription = said }
    ) {
        val drawn = art ?: return@Canvas
        val k = size.width / EnvelopeGeometry.FRAME_W
        val t = tilt.value
        val pose = EnvelopePose(
            flapDegrees = motion.flap.value,
            rise = motion.rise.value,
            split = motion.split,
            crack = motion.crack.value,
            crackGlint = motion.glint.value,
            lightX = 0.08f + 0.84f * t.x,
            lightY = 0.05f + 0.7f * t.y,
            sweep = motion.sweep.value,
            sweepAlpha = motion.sweepAlpha.value.coerceIn(0f, 1f),
        )
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(k, k)
            native.translate(EnvelopeGeometry.ENV_LEFT, EnvelopeGeometry.ENV_TOP + EnvelopeGeometry.DROP * motion.drop.value)
            native.scale(EnvelopeGeometry.ENV_SCALE, EnvelopeGeometry.ENV_SCALE)
            drawn.draw(native, pose, card)
            native.restore()
        }
    }
}

/** tokens.json motion.springs settle: SwiftUI .spring(response: 0.4, dampingFraction: 0.8). */
private fun <T> settle() = spring<T>(dampingRatio = 0.8f, stiffness = 246.7f)

/** tokens.json motion.springs selector: SwiftUI .spring(response: 0.3, dampingFraction: 0.7). */
private fun <T> selector() = spring<T>(dampingRatio = 0.7f, stiffness = 438.6f)

// The send side's moment (send-link-moment, Option 2: check, then package), in ms from the result.
/** The check springs in with today's ring, the success haptic and the chime at 0; confetti at 200. */
private const val PACK_CONFETTI_AT_MS = 200L
/** "Link ready" and its lines fade in (easeOut 0.4 s after 0.3 s, as the ready step did). */
private const val PACK_READY_AT_MS = 300L
/** The check turns into the note: it fades and rises, the note grows from its disc. */
private const val PACK_NOTE_AT_MS = 550L
/** The open envelope comes up around the note. */
private const val PACK_ENVELOPE_AT_MS = 850L
/** The note drops into the envelope. */
private const val PACK_DROP_AT_MS = 1050L
/** The flap folds down and the envelope lifts to the middle. */
private const val PACK_FLAP_AT_MS = 1300L
/** The seal presses on; it touches the paper 150 ms later (softTick), and Share and Done arrive. */
private const val PACK_SEAL_AT_MS = 1600L
private const val PACK_CONTACT_MS = 1750L
/** Done arrives 100 ms after Share. */
private const val PACK_DONE_AT_MS = 1850L
/** One light sweep shows the foil amount. */
private const val PACK_SWEEP_AT_MS = 1950L

// Where the note and the envelope start, in the claim mockup's units (send.js G).
/** The note as the check's disc: 406.8 envelope units under its risen place (CARD_RISE above the pocket). */
private const val NOTE_FROM_Y = 406.8f - EnvelopeGeometry.CARD_RISE
/** The open envelope comes up from 148 object units below its open place. */
private const val ENVELOPE_ARRIVE = 148f

/** How far the check rises as it turns into the note, in dp of the 338 dp hero. */
internal const val CHECK_RISE_DP = 110f

/**
 * The send side's moment (send-link-moment, Option 2): after the funding goes out, today's green
 * check springs in, turns into the ivory note, the note drops into the envelope, the flap closes
 * and the wax seal presses on. "Link ready", Share and Done arrive on the way. Reduce Motion: one
 * cross-fade to the sealed envelope. [settled]: show the end at once (a screen made again).
 */
@Stable
internal class LinkPacking(private val settled: Boolean) {
    private fun start(end: Float, from: Float) = Animatable(if (settled) end else from)

    val checkAlpha = Animatable(0f)
    val checkScale = start(1f, 0.3f)
    /** 0 in the slot, 1 risen [CHECK_RISE_DP]. */
    val checkRise = start(1f, 0f)
    var burst by mutableStateOf(false)
        private set
    var confetti by mutableStateOf(false)
        private set

    val heroAlpha = Animatable(1f)
    val envelopeY = start(0f, EnvelopeGeometry.DROP + ENVELOPE_ARRIVE)
    val envelopeAlpha = start(1f, 0f)
    val noteLayerY = start(0f, EnvelopeGeometry.DROP)
    val noteY = start(0f, NOTE_FROM_Y)
    val noteShape = start(1f, 0f)
    val noteAlpha = start(1f, 0f)
    val noteTint = start(0f, 1f)
    val flap = start(0f, 180f)
    val sealAlpha = start(1f, 0f)
    val sealScale = start(1f, 1.5f)
    val sealPress = start(1f, 0f)
    val sweep = Animatable(-1f)
    val foilReveal = start(1.35f, -0.35f)

    val readyIn = start(1f, 0f)
    val shareIn = start(1f, 0f)
    val doneIn = start(1f, 0f)

    /** The hero shows: from the note on (TalkBack reads it then). */
    val shown: Boolean get() = noteAlpha.value > 0f || envelopeAlpha.value > 0f

    /** The moment, once the funding went out. Times in the PACK_ constants. */
    suspend fun play(view: View, context: Context, reduceMotion: Boolean) = coroutineScope {
        if (reduceMotion) {
            // One cross-fade (0.35 s) to the sealed envelope; the haptic and the chime in its middle.
            heroAlpha.snapTo(0f)
            envelopeY.snapTo(0f); envelopeAlpha.snapTo(1f); noteLayerY.snapTo(0f); noteY.snapTo(0f)
            noteShape.snapTo(1f); noteAlpha.snapTo(1f); noteTint.snapTo(0f); flap.snapTo(0f)
            sealAlpha.snapTo(1f); sealScale.snapTo(1f); sealPress.snapTo(1f); foilReveal.snapTo(1.35f)
            launch { heroAlpha.animateTo(1f, Motion.crossFade()) }
            launch { readyIn.animateTo(1f, Motion.crossFade()) }
            launch { shareIn.animateTo(1f, Motion.crossFade()) }
            launch { doneIn.animateTo(1f, Motion.crossFade()) }
            delay(HALF_FADE_MS.toLong())
            successHaptic(view)
            SoundFeedback.sendComplete(context)
            return@coroutineScope
        }
        fun at(ms: Long, block: suspend () -> Unit) = launch {
            if (ms > 0) delay(ms)
            block()
        }
        // The result: the check, as today (SuccessCheckmarkView), with the success haptic and the chime.
        at(0) { checkAlpha.animateTo(1f, tween(200, easing = Motion.EaseInOut)) }
        at(0) { checkScale.animateTo(1f, celebrate()) }
        burst = true
        successHaptic(view)
        SoundFeedback.sendComplete(context)
        at(PACK_CONFETTI_AT_MS) { confetti = true }
        at(PACK_READY_AT_MS) { readyIn.animateTo(1f, tween(400, easing = Motion.EaseOut)) }
        // The check turns into the note and rises.
        at(PACK_NOTE_AT_MS) { checkAlpha.animateTo(0f, tween(180, easing = Motion.EaseIn)) }
        at(PACK_NOTE_AT_MS) { checkRise.animateTo(1f, Motion.stepForward()) }
        at(PACK_NOTE_AT_MS) { noteAlpha.animateTo(1f, tween(140, easing = LinearEasing)) }
        at(PACK_NOTE_AT_MS) { noteY.animateTo(-EnvelopeGeometry.CARD_RISE, Motion.stepForward()) }
        at(PACK_NOTE_AT_MS) { noteShape.animateTo(1f, Motion.stepForward()) }
        at(PACK_NOTE_AT_MS) { noteTint.animateTo(0f, tween(300, easing = Motion.EaseInOut)) }
        // The open envelope comes up around it.
        at(PACK_ENVELOPE_AT_MS) { envelopeAlpha.animateTo(1f, tween(300, easing = Motion.EaseOut)) }
        at(PACK_ENVELOPE_AT_MS) { envelopeY.animateTo(EnvelopeGeometry.DROP, Motion.stepForward()) }
        // The note drops in.
        at(PACK_DROP_AT_MS) { noteY.animateTo(0f, settle()) }
        // The flap folds down, the envelope lifts to the middle.
        at(PACK_FLAP_AT_MS) { flap.animateTo(0f, Motion.stepForward()) }
        at(PACK_FLAP_AT_MS) { envelopeY.animateTo(0f, Motion.stepForward()) }
        at(PACK_FLAP_AT_MS) { noteLayerY.animateTo(0f, Motion.stepForward()) }
        // The seal presses on.
        at(PACK_SEAL_AT_MS) { sealAlpha.animateTo(1f, tween(90, easing = LinearEasing)) }
        at(PACK_SEAL_AT_MS) { sealScale.animateTo(1f, selector()) }
        at(PACK_SEAL_AT_MS) { sealPress.animateTo(1f, tween(160, easing = Motion.EaseIn)) }
        at(PACK_CONTACT_MS) { softTickHaptic(view) }
        at(PACK_CONTACT_MS) { shareIn.animateTo(1f, Motion.arrive()) }
        at(PACK_DONE_AT_MS) { doneIn.animateTo(1f, Motion.arrive()) }
        // One light sweep; the foil amount shows where it has passed.
        at(PACK_SWEEP_AT_MS) { foilReveal.animateTo(1.35f, tween(SWEEP_MS, easing = Motion.EaseInOut)) }
        at(PACK_SWEEP_AT_MS) {
            sweep.snapTo(-0.35f)
            sweep.animateTo(1.35f, tween(SWEEP_MS, easing = Motion.EaseInOut))
            sweep.snapTo(-1f)
        }
    }

    fun pose() = PackingPose(
        envelopeY = envelopeY.value,
        envelopeAlpha = envelopeAlpha.value.coerceIn(0f, 1f),
        noteLayerY = noteLayerY.value,
        noteY = noteY.value,
        noteShape = noteShape.value,
        noteAlpha = noteAlpha.value,
        noteTint = noteTint.value,
        flapDegrees = flap.value,
        sealAlpha = sealAlpha.value.coerceIn(0f, 1f),
        sealScale = sealScale.value,
        sealPress = sealPress.value,
        sweep = sweep.value,
        foilReveal = foilReveal.value,
    )
}

/**
 * The send side's hero, 338 x 280 dp at most (the claim sheet's hero box): the note and the
 * envelope of [packing], drawn from the claim's parts. The light stays at rest (no tilt). TalkBack
 * reads [spoken] once it shows.
 */
@Composable
internal fun LinkPackingHero(
    packing: LinkPacking,
    face: EnvelopeCardFace,
    spoken: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var widthPx by remember { mutableIntStateOf(0) }
    val scale = widthPx / EnvelopeGeometry.FRAME_W * EnvelopeGeometry.ENV_SCALE
    val art by produceState<EnvelopeArt?>(null, widthPx) {
        if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderForPacking(scale) }
    }
    val label = tr("Payment Link").uppercase(Locale.getDefault())
    val unit = if (face.unit) "XMR" else null
    val note by produceState<Bitmap?>(null, widthPx, face, label) {
        if (widthPx > 0) {
            value = withContext(Dispatchers.Default) { EnvelopeArt.renderCard(context, scale, face.number, unit, label) }
        }
    }
    val foil by produceState<Bitmap?>(null, widthPx, face) {
        if (widthPx > 0) value = withContext(Dispatchers.Default) { EnvelopeArt.renderFoil(context, scale, face.number, unit) }
    }
    val tint = SuccessGreen.toArgb()
    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer { alpha = packing.heroAlpha.value }
            .clearAndSetSemantics { if (packing.shown) contentDescription = spoken }
    ) {
        val drawn = art ?: return@Canvas
        val k = size.width / EnvelopeGeometry.FRAME_W
        val pose = packing.pose()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(k, k)
            drawn.drawPacking(native, pose, note, foil, tint)
            native.restore()
        }
    }
}

/**
 * The phone's tilt from the game rotation vector, on a 0..1 scale per axis, from the light's rest
 * place when the screen opens. Off under Reduce Motion.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(REST_TILT) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            tilt.value = REST_TILT
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val angles = FloatArray(3)
            private var base: FloatArray? = null
            private var last = 0L

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                val pitch = angles[1]
                val roll = angles[2]
                val start = base ?: floatArrayOf(pitch, roll).also { base = it }
                val x = (REST_TILT.x + (roll - start[1]) / TILT_RANGE_RAD * 0.5f).coerceIn(0f, 1f)
                val y = (REST_TILT.y - (pitch - start[0]) / TILT_RANGE_RAD * 0.5f).coerceIn(0f, 1f)
                val dt = if (last == 0L) 0.016f else ((event.timestamp - last) / 1e9f).coerceIn(0f, 0.1f)
                last = event.timestamp
                val k = 1f - exp(-dt / TILT_SMOOTHING_S)
                val now = tilt.value
                tilt.value = Offset(now.x + (x - now.x) * k, now.y + (y - now.y) * k)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}

/**
 * The share sheet for a link: the share text, with the sealed envelope image and "Claim your
 * Monero" as its preview (the iOS link preview). The image is drawn off the main thread; with no
 * image the text goes alone. The link never goes to the clipboard.
 */
internal fun shareLinkWithPreview(context: Context, amountXmr: String, text: String) {
    val app = context.applicationContext
    Thread {
        val image = shareImage(app, amountXmr)
        Handler(Looper.getMainLooper()).post {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, tr("Claim your Monero"))
                if (image != null) {
                    clipData = ClipData.newRawUri(null, image)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }.start()
}

/** The 1200 x 1200 share image as a PNG the share sheet can read, or null. Call off the main thread. */
internal fun shareImage(context: Context, amountXmr: String): Uri? = try {
    val image = EnvelopeArt.renderShare(context, EnvelopeArt.shareAmount(context, amountXmr))
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(folder, "payment-link.png")
    FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    image.recycle()
    FileProvider.getUriForFile(context, "${context.packageName}.qrshare", file)
} catch (e: Exception) {
    Log.w(TAG, "Share image failed: ${e.javaClass.simpleName}")
    null
}
