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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.softTickHaptic
import one.monero.moneroone.ui.components.successHaptic
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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
    var split by mutableStateOf(false)
        private set
    var opened by mutableStateOf(false)
        private set
    var landed by mutableStateOf(false)
        private set
    private var enteredAt = 0L

    /** The heroEntrance (scale 0.4 to 1 and fade), once. Reduce Motion: no scale, it is there. */
    fun enter(scope: CoroutineScope, reduceMotion: Boolean) {
        if (enteredAt != 0L) return
        enteredAt = SystemClock.uptimeMillis()
        scope.launch { if (reduceMotion) entrance.snapTo(1f) else entrance.animateTo(1f, heroEntrance()) }
    }

    /** The opening, once: at t0 the crack and a soft tick, the success haptic when the card lands. */
    suspend fun open(view: View, reduceMotion: Boolean) = coroutineScope {
        if (opened) return@coroutineScope
        val start = if (enteredAt != 0L) enteredAt else SystemClock.uptimeMillis()
        val wait = start + CRACK_AFTER_ENTRANCE_MS - SystemClock.uptimeMillis()
        if (wait > 0) delay(wait)
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
