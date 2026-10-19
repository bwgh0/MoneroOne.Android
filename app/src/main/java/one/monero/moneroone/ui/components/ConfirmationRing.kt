package one.monero.moneroone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.SystemFill

/**
 * The confirmation ring on an Activity row's disc (iOS parity): a 2 dp ring on
 * the rim of the 40 dp disc that fills one tenth per confirmation. At 10 it
 * closes green with a check, then the row goes back to its plain disc.
 */
object ConfirmationRing {
    const val CONFIRMATIONS = 10L

    /** The arc's share of the ring for [confirmations]: n / 10, from 0 to 1. */
    fun progress(confirmations: Long): Float =
        confirmations.coerceIn(0L, CONFIRMATIONS).toFloat() / CONFIRMATIONS

    /** A row shows the ring from 0 to 9 confirmations, unless its transaction failed. */
    fun shows(confirmations: Long?, failed: Boolean): Boolean =
        confirmations != null && !failed && confirmations in 0 until CONFIRMATIONS

    /** What TalkBack adds to a row with a ring: "3 of 10 confirmations". */
    fun spoken(confirmations: Long): String =
        tr("%s of 10 confirmations", confirmations.coerceIn(0L, CONFIRMATIONS))
}

/** One disc's ring and glyph values on the moment-4 timeline. */
@Stable
internal class DiscRingState(confirmations: Long?, failed: Boolean) {
    /** The ring is on the disc: 0 to 9 confirmations, or the close at 10 has not ended. */
    var ringOn by mutableStateOf(ConfirmationRing.shows(confirmations, failed))
    val arc = Animatable(ConfirmationRing.progress(confirmations ?: 0L))
    /** 0 = the brand arc, 1 = the green arc. */
    val ringGreen = Animatable(0f)
    /** 0 = the disc's own hue, 1 = green. */
    val discGreen = Animatable(0f)
    val ringAlpha = Animatable(1f)
    val arrowAlpha = Animatable(1f)
    val arrowScale = Animatable(1f)
    val checkAlpha = Animatable(0f)
    val checkScale = Animatable(0.5f)
    var closeJob: Job? = null

    /** Back to a plain ring at [progress] (a row that went back to pending). */
    suspend fun reset(progress: Float) {
        arc.snapTo(progress)
        ringGreen.snapTo(0f)
        discGreen.snapTo(0f)
        ringAlpha.snapTo(1f)
        arrowAlpha.snapTo(1f)
        arrowScale.snapTo(1f)
        checkAlpha.snapTo(0f)
        checkScale.snapTo(0.5f)
    }
}

/**
 * A row that comes on screen shows its ring as it is. Each new confirmation
 * moves the arc on stepForward. At 10 the ring closes (see [close]); a row
 * that is already at 10 when it comes on screen has the plain disc.
 */
@Composable
internal fun rememberDiscRing(confirmations: Long?, failed: Boolean): DiscRingState {
    val state = remember { DiscRingState(confirmations, failed) }
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    LaunchedEffect(confirmations, failed) {
        val n = confirmations
        when {
            n == null || failed -> {
                state.closeJob?.cancel()
                state.closeJob = null
                state.ringOn = false
            }
            n >= ConfirmationRing.CONFIRMATIONS -> if (state.ringOn && state.closeJob == null) {
                // Its own scope: a later confirmation must not restart the close.
                state.closeJob = scope.launch {
                    state.close(reduceMotion)
                    state.ringOn = false
                    state.closeJob = null
                }
            }
            else -> {
                val target = ConfirmationRing.progress(n)
                if (!state.ringOn || state.closeJob != null) {
                    state.closeJob?.cancel()
                    state.closeJob = null
                    state.reset(target)
                    state.ringOn = true
                } else {
                    state.arc.animateTo(target, if (reduceMotion) Motion.crossFade() else Motion.stepForward())
                }
            }
        }
    }
    return state
}

/**
 * The close at 10 (iOS timeline): the arc fills (0 ms), the ring and the disc
 * turn green and the arrow becomes a check (250 ms), then the disc and the
 * arrow come back and the ring fades out (750 ms). Reduce Motion: the arc
 * cross-fades full and the glyphs cross-fade.
 */
private suspend fun DiscRingState.close(reduceMotion: Boolean) = coroutineScope {
    launch { arc.animateTo(1f, if (reduceMotion) Motion.crossFade() else Motion.stepForward()) }
    delay(250)
    launch { ringGreen.animateTo(1f, Motion.crossFade()) }
    launch { discGreen.animateTo(1f, Motion.crossFade()) }
    swapGlyph(this@close, toCheck = true, reduceMotion = reduceMotion)
    delay(500)
    launch { discGreen.animateTo(0f, Motion.crossFade()) }
    launch { ringAlpha.animateTo(0f, Motion.crossFade()) }
    swapGlyph(this@close, toCheck = false, reduceMotion = reduceMotion)
}

/** The SF Symbol replace: the old glyph shrinks and fades (150 ms easeIn); the new one grows in on stepForward from 120 ms. */
private fun CoroutineScope.swapGlyph(state: DiscRingState, toCheck: Boolean, reduceMotion: Boolean) {
    val outAlpha = if (toCheck) state.arrowAlpha else state.checkAlpha
    val outScale = if (toCheck) state.arrowScale else state.checkScale
    val inAlpha = if (toCheck) state.checkAlpha else state.arrowAlpha
    val inScale = if (toCheck) state.checkScale else state.arrowScale
    if (reduceMotion) {
        launch { inScale.snapTo(1f) }
        launch { outAlpha.animateTo(0f, Motion.crossFade()) }
        launch { inAlpha.animateTo(1f, Motion.crossFade()) }
        return
    }
    launch { outAlpha.animateTo(0f, tween(150, easing = Motion.EaseIn)) }
    launch { outScale.animateTo(0.5f, tween(150, easing = Motion.EaseIn)) }
    launch {
        delay(120)
        inScale.snapTo(0.5f)
        launch { inAlpha.animateTo(1f, tween(150, easing = Motion.EaseOut)) }
        inScale.animateTo(1f, Motion.stepForward())
    }
}

/** The ring: the track and the arc from 12 o'clock, a 2 dp stroke inside the edge of the 40 dp disc. */
@Composable
internal fun DiscRing(state: DiscRingState, modifier: Modifier = Modifier) {
    val track = SystemFill
    val brand = MoneroOrange
    val green = SuccessGreen
    Canvas(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { alpha = state.ringAlpha.value }
    ) {
        val stroke = 2.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val sweep = 360f * state.arc.value.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                color = lerp(brand, green, state.ringGreen.value),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
