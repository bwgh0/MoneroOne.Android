package one.monero.moneroone.paymentlinks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import one.monero.moneroone.R
import one.monero.moneroone.ui.theme.LinkEnvelopeColors
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The payment link envelope (final v2 mockup, link-card): dark laid paper, the orange wax seal
 * with the official mark, the flap with its orange liner, and the ivory card with the amount.
 * Every part is drawn in envelope units, as in the mockup's CSS: the closed envelope is
 * 1000 x 680, the flap 1000 x 310 on its top edge, the seal 196 x 196 and the card 880 x 600.
 */
internal object EnvelopeGeometry {
    const val W = 1000f
    const val H = 680f
    const val RADIUS = 12f
    const val FLAP_H = 310f
    const val SEAL_X = 402f
    const val SEAL_Y = 207f
    const val SEAL = 196f
    const val CARD_X = 60f
    const val CARD_Y = 40f
    const val CARD_W = 880f
    const val CARD_H = 600f
    /** How far the card slides up out of the pocket (98 dp in the 338 dp frame). */
    const val CARD_RISE = 363f
    /** The card's amount line fits in 760 of its 880 units, down to 40%. */
    const val CARD_FIT = 760f
    const val PERSPECTIVE = 1600f

    /** The hero frame in object units (338 x 280 dp). The envelope sits in it at 0.8, centered. */
    const val FRAME_W = 1000f
    const val FRAME_H = 828.4f
    const val ENV_SCALE = 0.8f
    const val ENV_LEFT = 100f
    const val ENV_TOP = 142.2f
    /** The envelope moves down by this much (object units) as the flap opens, so the open flap stays in the frame. */
    const val DROP = 142.2f

    /** Room around a bitmap for its shadow, in envelope units. */
    const val MARGIN = 110f
    const val SEAL_MARGIN = 24f

    /** The light at rest (the mockup's --lx and --ly). */
    const val REST_LX = 0.36f
    const val REST_LY = 0.3f
}

/** One frame of the envelope. */
internal data class EnvelopePose(
    /** 0 closed, 180 open. */
    val flapDegrees: Float = 0f,
    /** 0 in the pocket, 1 risen. */
    val rise: Float = 0f,
    /** The seal has cracked: its lower half stays on the pocket, its upper half lifts with the flap. */
    val split: Boolean = false,
    /** The crack's two strokes, 0 to 1 (stroke-dashoffset 260 to 0). */
    val crack: Float = 0f,
    val crackGlint: Float = 0f,
    val lightX: Float = EnvelopeGeometry.REST_LX,
    val lightY: Float = EnvelopeGeometry.REST_LY,
    /** The light sweep: -1 before, -0.35 to 1.35 while it runs. */
    val sweep: Float = -1f,
    /** The sweep's strength, 0 to 1 (the check's light fades with it). */
    val sweepAlpha: Float = 1f,
)

/**
 * One frame of the send side's moment (send-link-moment, Option 2), in object units of the hero
 * frame: the envelope's back and front, and the note between them at its own place.
 */
internal data class PackingPose(
    /** The envelope's top below its rest place, in object units (the mockup's envB and envF translate). */
    val envelopeY: Float,
    val envelopeAlpha: Float,
    /** The note's layer below its rest place, in object units (the mockup's cardW translate). */
    val noteLayerY: Float,
    /** The note below its place in the pocket, in envelope units (the mockup's ecard translate). */
    val noteY: Float,
    /** 0 the check's green disc, 1 the note (its scale and its corners). */
    val noteShape: Float,
    val noteAlpha: Float,
    /** The green over the note as it comes from the check, 0 to 1. */
    val noteTint: Float,
    /** 180 open, 0 closed. */
    val flapDegrees: Float,
    val sealAlpha: Float,
    val sealScale: Float,
    /** How much of the ring and the mark shows, 0 (a plain drop of wax) to 1. */
    val sealMark: Float,
    /** How round the wax still is, 0 to 1: the light on a fresh drop. */
    val sealDome: Float,
    /** The stamp ([StampGeometry]): its opacity, its place from its contact with the seal's middle
     * (envelope units), its tilt about that point (degrees), and 1 above the paper to 0 on the wax. */
    val toolAlpha: Float,
    val toolX: Float,
    val toolY: Float,
    val toolTilt: Float,
    val toolHeight: Float,
    /** The light sweep: -1 before, -0.35 to 1.35 while it runs. */
    val sweep: Float,
    /** How far the foil amount shows: -0.35 none to 1.35 all (the mockup's --fr). */
    val foilReveal: Float,
)

/**
 * The stamp that presses the seal on the send side, seen from a little above: a graphite head, a
 * collar with a thin orange band and a turned handle, 150 x 374 envelope units. [CONTACT] is the
 * middle of the head's face, where it meets the wax.
 */
internal object StampGeometry {
    const val W = 150f
    const val H = 374f
    const val CONTACT_X = 75f
    const val CONTACT_Y = 352f
    /** A cap's height over its width: the angle we see the tool from. */
    const val SQUASH = 22f / 75f
    /** Room around the tool for its blurred shadow (css blur up to 24). */
    const val SHADOW_MARGIN = 48f
    /** Room around the tool for its caps' bright edge. */
    const val MARGIN = 2f

    /** The front (lower) half of a cap's ellipse, from one end to the other. */
    fun Path.front(cx: Float, cy: Float, rx: Float, leftToRight: Boolean) {
        val ry = rx * SQUASH
        val k = 0.5523f
        val side = if (leftToRight) 1f else -1f
        cubicTo(cx - side * rx, cy + k * ry, cx - side * k * rx, cy + ry, cx, cy + ry)
        cubicTo(cx + side * k * rx, cy + ry, cx + side * rx, cy + k * ry, cx + side * rx, cy)
    }

    /** A round part's side, from its top cap's middle line down to its front edge (half an ellipse). */
    fun side(left: Float, top: Float, width: Float, height: Float): Path = Path().apply {
        moveTo(left, top)
        lineTo(left, top + height)
        front(left + width / 2, top + height, width / 2, leftToRight = true)
        lineTo(left + width, top)
        close()
    }

    /** The turned handle: a round knob at the top, a long taper, a narrow neck and a foot on the collar. */
    fun handle(): Path = Path().apply {
        moveTo(75f, 0f)
        cubicTo(98f, 0f, 111f, 30f, 111f, 62f)
        cubicTo(111f, 110f, 100f, 160f, 94f, 200f)
        cubicTo(90f, 225f, 90f, 240f, 92f, 248f)
        cubicTo(95f, 255f, 100f, 257f, 100f, 262f)
        lineTo(100f, 272f)
        front(75f, 272f, 25f, leftToRight = false)
        lineTo(50f, 262f)
        cubicTo(50f, 257f, 55f, 255f, 58f, 248f)
        cubicTo(60f, 240f, 60f, 225f, 56f, 200f)
        cubicTo(50f, 160f, 39f, 110f, 39f, 62f)
        cubicTo(39f, 30f, 52f, 0f, 75f, 0f)
        close()
    }

    val HEAD_SIDE = RectF(0f, 294f, 150f, 352f)
    val HEAD_CAP = RectF(0f, 272f, 150f, 316f)
    val COLLAR_SIDE = RectF(29f, 272f, 121f, 294f)
    val COLLAR_CAP = RectF(29f, 258.5f, 121f, 285.5f)
}

private object EnvelopePaths {
    const val SEAL_BLOB =
        "M191.20 98.00 L191.66 100.68 L191.01 103.32 L189.65 105.87 L188.29 108.36 L187.43 110.86 L186.92 113.39 " +
        "L186.24 115.88 L185.01 118.23 L183.39 120.45 L181.85 122.62 L180.71 124.88 L179.90 127.22 L179.09 129.57 " +
        "L178.17 131.88 L177.37 134.25 L177.02 136.85 L177.07 139.72 L176.98 142.60 L176.11 145.10 L174.22 146.98 " +
        "L171.66 148.36 L169.06 149.63 L166.83 151.08 L164.91 152.71 L162.98 154.31 L160.88 155.71 L158.75 157.03 " +
        "L156.82 158.53 L155.14 160.26 L153.46 162.01 L151.51 163.44 L149.31 164.54 L147.15 165.65 L145.35 167.25 " +
        "L143.92 169.46 L142.55 171.87 L140.86 173.90 L138.76 175.24 L136.42 176.15 L134.15 177.15 L132.05 178.57 " +
        "L130.04 180.30 L127.98 182.03 L125.83 183.64 L123.64 185.33 L121.48 187.32 L119.26 189.42 L116.85 191.01 " +
        "L114.19 191.53 L111.36 190.94 L108.53 189.81 L105.80 188.86 L103.17 188.38 L100.57 188.04 L98.00 187.36 L95.48 " +
        "186.19 L93.03 184.84 L90.63 183.79 L88.23 183.15 L85.82 182.69 L83.44 182.11 L81.08 181.49 L78.65 181.22 " +
        "L76.03 181.56 L73.26 182.26 L70.51 182.61 L68.03 182.00 L65.92 180.41 L64.03 178.37 L62.13 176.54 L60.07 " +
        "175.15 L57.92 173.97 L55.83 172.67 L53.85 171.22 L51.82 169.85 L49.61 168.76 L47.29 167.79 L45.14 166.55 " +
        "L43.37 164.81 L41.89 162.76 L40.30 160.87 L38.26 159.48 L35.80 158.45 L33.36 157.33 L31.37 155.74 L29.88 " +
        "153.70 L28.56 151.55 L26.99 149.59 L25.05 147.88 L22.97 146.22 L20.96 144.46 L18.97 142.63 L16.76 140.87 " +
        "L14.24 139.18 L11.81 137.36 L10.12 135.14 L9.59 132.42 L10.01 129.40 L10.71 126.36 L11.12 123.51 L11.17 120.82 " +
        "L11.27 118.17 L11.78 115.47 L12.63 112.77 L13.41 110.16 L13.80 107.66 L13.87 105.23 L13.97 102.80 L14.26 " +
        "100.39 L14.44 98.00 L14.05 95.60 L12.98 93.14 L11.71 90.59 L11.00 88.02 L11.30 85.53 L12.40 83.19 L13.63 80.91 " +
        "L14.47 78.58 L14.90 76.16 L15.26 73.71 L15.80 71.29 L16.40 68.89 L16.79 66.38 L16.95 63.75 L17.24 61.12 L18.14 " +
        "58.74 L19.73 56.70 L21.61 54.86 L23.17 52.87 L24.18 50.56 L24.94 48.05 L25.99 45.68 L27.60 43.72 L29.57 42.05 " +
        "L31.47 40.35 L33.04 38.38 L34.39 36.18 L35.75 33.95 L37.23 31.79 L38.66 29.52 L39.89 26.94 L41.03 24.12 L42.48 " +
        "21.58 L44.58 19.88 L47.38 19.24 L50.52 19.27 L53.58 19.34 L56.36 19.09 L58.98 18.64 L61.63 18.36 L64.38 18.45 " +
        "L67.12 18.68 L69.69 18.66 L72.10 18.27 L74.45 17.78 L76.84 17.50 L79.25 17.38 L81.59 16.99 L83.79 15.90 L85.95 " +
        "14.18 L88.19 12.47 L90.57 11.48 L93.05 11.42 L95.54 11.85 L98.00 12.13 L100.46 11.93 L102.95 11.44 L105.47 " +
        "11.02 L108.01 10.75 L110.59 10.40 L113.27 9.79 L116.01 9.11 L118.72 8.91 L121.23 9.62 L123.51 11.11 L125.70 " +
        "12.75 L127.98 13.97 L130.44 14.69 L132.93 15.36 L135.23 16.48 L137.26 18.15 L139.17 19.98 L141.18 21.54 " +
        "L143.39 22.72 L145.71 23.77 L147.98 24.91 L150.25 26.08 L152.72 27.03 L155.53 27.65 L158.45 28.24 L160.98 " +
        "29.37 L162.72 31.40 L163.65 34.19 L164.22 37.23 L164.90 40.03 L165.87 42.50 L166.89 44.87 L167.69 47.37 " +
        "L168.25 49.96 L168.87 52.46 L169.79 54.71 L170.92 56.82 L171.93 58.99 L172.62 61.31 L173.22 63.65 L174.24 " +
        "65.78 L175.98 67.64 L178.16 69.40 L180.12 71.32 L181.31 73.54 L181.78 75.98 L182.09 78.45 L182.74 80.83 " +
        "L183.84 83.15 L185.07 85.48 L186.16 87.88 L187.20 90.34 L188.44 92.83 L189.92 95.37 Z"
    const val MARK_TOP =
        "M2249.97 32.61 C1025.55 32.61 32.58 1025.52 32.58 2249.97 32.58 2494.72 72.25 2730.14 145.52 2950.36 L808.67 " +
        "2950.36 L808.67 1084.72 L2249.97 2526.05 3691.28 1084.72 L3691.28 2950.36 L4354.47 2950.36 C4427.75 2730.14 " +
        "4467.42 2494.72 4467.42 2249.97 4467.42 1025.52 3474.45 32.61 2249.97 32.61"
    const val MARK_BOTTOM =
        "M1918.55 2857.41 1289.55 2228.38 L1289.55 3402.30 L1049.08 3402.30 L808.64 3402.30 L355.20 3402.30 C744.45 " +
        "4040.91 1447.48 4467.39 2249.94 4467.39 3052.37 4467.39 3755.47 4040.91 4144.69 3402.30 L3691.22 3402.30 " +
        "L3261.16 3402.30 L3210.34 3402.30 L3210.34 2228.38 L2581.30 2857.41 2249.94 3188.77 1918.58 2857.41 Z"

    /** The zigzag the seal cracks along, in seal units. */
    val CRACK_POINTS = floatArrayOf(
        -6f, 95f, 14f, 100f, 26f, 94f, 40f, 103f, 54f, 96f, 67f, 105f, 81f, 98f, 94f, 107f,
        107f, 99f, 121f, 106f, 135f, 97f, 149f, 104f, 162f, 95f, 177f, 101f, 202f, 94f
    )

    fun parse(d: String): Path = PathParser.createPathFromPathData(d)

    fun flap(): Path = Path().apply {
        moveTo(0f, 0f); lineTo(1000f, 0f); lineTo(524f, 290f); quadTo(500f, 305f, 476f, 290f); close()
    }

    fun flapEdge(): Path = Path().apply {
        moveTo(0f, 0f); lineTo(476f, 290f); quadTo(500f, 305f, 524f, 290f); lineTo(1000f, 0f)
    }

    fun liner(): Path = Path().apply {
        moveTo(24f, 0f); lineTo(976f, 0f); lineTo(516f, 276f); quadTo(500f, 287f, 484f, 276f); close()
    }

    fun crack(): Path = Path().apply {
        moveTo(CRACK_POINTS[0], CRACK_POINTS[1])
        var i = 2
        while (i < CRACK_POINTS.size) { lineTo(CRACK_POINTS[i], CRACK_POINTS[i + 1]); i += 2 }
    }

    /** Below the crack: the half that stays on the pocket. */
    fun sealLower(): Path = crack().apply { lineTo(202f, 202f); lineTo(-6f, 202f); close() }

    /** Above the crack: the half that lifts with the flap. */
    fun sealUpper(): Path = Path().apply {
        moveTo(-6f, -6f); lineTo(202f, -6f)
        var i = CRACK_POINTS.size - 2
        while (i >= 0) { lineTo(CRACK_POINTS[i], CRACK_POINTS[i + 1]); i -= 2 }
        close()
    }

    /** The official Monero mark, 108 x 108 at (44, 44) in the seal. */
    fun mark(): Path {
        val path = parse(MARK_TOP).apply { addPath(parse(MARK_BOTTOM)) }
        val s = 108f / 4435f
        path.transform(Matrix().apply { setTranslate(-32.5f, -32.5f); postScale(s, s); postTranslate(44f, 44f) })
        return path
    }
}

private fun Color.withAlpha(alpha: Float): Int = copy(alpha = alpha).toArgb()

private val Black = Color.Black
private val White = Color.White

/** CSS blur radius (2 sigma) as a [BlurMaskFilter] radius, in the canvas units it draws in. */
private fun blur(cssRadius: Float): BlurMaskFilter =
    BlurMaskFilter(max(0.5f, (cssRadius / 2f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

/**
 * Gray noise around 50%, one value per pixel: the paper's grain (the mockup's feTurbulence). As
 * [shade], black with the gray's darkness as its alpha: drawn over the paper at alpha a, it gives
 * the paper times (1 - a + a * gray), the CSS multiply (PorterDuff MULTIPLY also multiplies alpha).
 */
private fun noise(width: Int, height: Int, seed: Long, spread: Double, shade: Boolean = false): Bitmap {
    val random = Random(seed)
    val pixels = IntArray(width * height) {
        val v = (128 + random.nextGaussian() * spread * 255).roundToInt().coerceIn(0, 255)
        if (shade) (255 - v) shl 24 else (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * A CSS `linear-gradient(98deg, ...)` band over a box: transparent at [from], [peak] at [at], transparent
 * at [to], as distances along the gradient line from its start. The light sweep uses it.
 */
private fun sweepShader(left: Float, top: Float, width: Float, height: Float, from: Float, at: Float, to: Float, peak: Int): Shader {
    val rad = Math.toRadians(98.0)
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val length = abs(width * dx) + abs(height * dy)
    val sx = left + width / 2 - dx * length / 2
    val sy = top + height / 2 - dy * length / 2
    val clear = peak and 0x00FFFFFF
    return LinearGradient(
        sx + dx * from, sy + dy * from, sx + dx * to, sy + dy * to,
        intArrayOf(clear, peak, clear), floatArrayOf(0f, (at - from) / (to - from), 1f), Shader.TileMode.CLAMP
    )
}

/**
 * The foil's mask (CSS `linear-gradient(98deg, #000 [opaque], transparent [clear])` over a box): what is
 * before [opaque] along the gradient line shows, what is after [clear] does not.
 */
private fun revealShader(left: Float, top: Float, width: Float, height: Float, opaque: Float, clear: Float): Shader {
    val rad = Math.toRadians(98.0)
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val length = abs(width * dx) + abs(height * dy)
    val sx = left + width / 2 - dx * length / 2
    val sy = top + height / 2 - dy * length / 2
    return LinearGradient(
        sx + dx * opaque, sy + dy * opaque, sx + dx * clear, sy + dy * clear,
        Black.toArgb(), Black.withAlpha(0f), Shader.TileMode.CLAMP
    )
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** The note's shape as the check's disc (send-link-moment): 0.324 x 0.475 of itself, an 80 dp circle in the hero. */
private const val NOTE_FROM_SCALE_X = 0.324f
private const val NOTE_FROM_SCALE_Y = 0.475f

/** The foil amount's line box on the pocket, in envelope units, and room around it for its shadows. */
private const val FOIL_TOP = 452f
private const val FOIL_SIZE = 104f
private const val FOIL_ROOM = 24f

/** Room around the seal for its lifted shadow (0 22 16), in seal units. */
private const val SEAL_SHADOW_MARGIN = 64f

/** The length of a CSS 98deg gradient line over a box. */
private fun sweepLength(width: Float, height: Float): Float {
    val rad = Math.toRadians(98.0)
    return abs(width * sin(rad).toFloat()) + abs(height * cos(rad).toFloat())
}

/** An elliptical radial gradient (CSS `radial-gradient(rx ry at cx cy, ...)`). */
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, colors: IntArray, stops: FloatArray): Shader =
    RadialGradient(cx, cy, rx, colors, stops, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
    }

/** Inter at weight [weight] and optical size [opsz], tabular figures. */
private fun interPaint(context: Context, size: Float, weight: Int, opsz: Int, color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT
        setFontVariationSettings("'wght' $weight, 'opsz' $opsz")
        fontFeatureSettings = "tnum"
        textSize = size
        this.color = color
    }

/** The baseline of a CSS line box of [lineHeight] whose top is [top], for [paint]'s font. */
private fun baseline(paint: Paint, top: Float, lineHeight: Float): Float {
    val metrics = paint.fontMetrics
    val ascent = -metrics.ascent
    val descent = metrics.descent
    return top + (lineHeight - (ascent + descent)) / 2 + ascent
}

/**
 * The envelope's still layers, drawn once at [scale] pixels per envelope unit, and [draw], which
 * puts one frame of the opening together from them.
 */
internal class EnvelopeArt private constructor(
    private val shadow: Bitmap,
    private val pocket: Bitmap,
    private val flapOutside: Bitmap,
    private val flapInside: Bitmap,
    private val seal: Bitmap,
    /** The send side's seal shadow, on the paper (the seal is drawn without its own). */
    private val sealShadow: Bitmap? = null,
    /** The send side's ring and mark alone ([seal] is then the plain wax). */
    private val sealMarks: Bitmap? = null,
    /** The send side's stamp: its body, and its silhouette blurred on the wax and high above it. */
    private val stamp: Triple<Bitmap, Bitmap, Bitmap>? = null,
) {
    private val blob = EnvelopePaths.parse(EnvelopePaths.SEAL_BLOB)
    private val sealLower = EnvelopePaths.sealLower()
    private val flap = EnvelopePaths.flap()
    private val pocketClip = Path().apply {
        addRoundRect(RectF(0f, 0f, EnvelopeGeometry.W, EnvelopeGeometry.H), EnvelopeGeometry.RADIUS, EnvelopeGeometry.RADIUS, Path.Direction.CW)
    }
    private val crackMeasure = PathMeasure(EnvelopePaths.crack(), false)
    private val crackSegment = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private val pocketCutPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val rect = RectF()

    /**
     * One frame, in envelope units with the envelope's top left corner at the origin (the caller
     * scales the canvas). Back to front: the shadow, the flap's inside once past 90 degrees, the
     * card, the pocket, the seal's lower half, the flap's outside up to 90 degrees, the whole seal
     * and the crack. [foil] draws on the pocket, under the flap (the share image's amount).
     */
    fun draw(canvas: Canvas, pose: EnvelopePose, card: Bitmap?, foil: ((Canvas) -> Unit)? = null) {
        val g = EnvelopeGeometry
        val m = g.MARGIN
        rect.set(-m, -m, g.W + m, g.H + m)
        canvas.drawBitmap(shadow, null, rect, bitmapPaint)
        if (pose.flapDegrees > 90f) drawFlap(canvas, flapInside, pose.flapDegrees)
        if (card != null && pose.rise > 0f) drawCard(canvas, card, pose)
        rect.set(0f, 0f, g.W, g.H)
        canvas.drawBitmap(pocket, null, rect, bitmapPaint)
        drawPocketLight(canvas, pose)
        foil?.invoke(canvas)
        if (pose.split) drawSeal(canvas, pose, lowerHalf = true)
        if (pose.flapDegrees <= 90f) {
            drawFlap(canvas, flapOutside, pose.flapDegrees)
            if (pose.flapDegrees < 0.5f) drawFlapLight(canvas, pose)
        }
        if (!pose.split) {
            drawSeal(canvas, pose, lowerHalf = false)
            if (pose.crack > 0f) drawCrack(canvas, pose)
        }
    }

    /**
     * One frame of the send side's moment, in object units (the 1000 x 828.4 hero frame; the caller
     * scales the canvas). The claim's parts in the claim's order: the back (the shadow, the open
     * flap's inside), the [note] at its own place, then the front (the pocket, the [foil] amount,
     * the closed flap and the seal). [tint] is the check's green.
     */
    fun drawPacking(canvas: Canvas, pose: PackingPose, note: Bitmap?, foil: Bitmap?, tint: Int) {
        val g = EnvelopeGeometry
        val light = EnvelopePose(flapDegrees = pose.flapDegrees, sweep = pose.sweep)
        if (pose.envelopeAlpha > 0f) inEnvelope(canvas, pose.envelopeY, pose.envelopeAlpha) {
            val m = g.MARGIN
            rect.set(-m, -m, g.W + m, g.H + m)
            canvas.drawBitmap(shadow, null, rect, bitmapPaint)
            if (pose.flapDegrees > 90f) drawFlap(canvas, flapInside, pose.flapDegrees)
        }
        if (note != null && pose.noteAlpha > 0f) inEnvelope(canvas, pose.noteLayerY, 1f) {
            // While the pocket fades in, the note under it fades out at the same rate, so it never shows through.
            val hide = pose.envelopeAlpha
            if (hide <= 0f || hide >= 1f) {
                drawMorphingNote(canvas, note, pose, tint)
            } else {
                val count = canvas.saveLayer(null, null)
                drawMorphingNote(canvas, note, pose, tint)
                val dy = pose.envelopeY - pose.noteLayerY
                rect.set(0f, dy, g.W, g.H + dy)
                pocketCutPaint.alpha = (hide * 255).roundToInt()
                canvas.drawBitmap(pocket, null, rect, pocketCutPaint)
                canvas.restoreToCount(count)
            }
        }
        if (pose.envelopeAlpha > 0f) inEnvelope(canvas, pose.envelopeY, pose.envelopeAlpha) {
            rect.set(0f, 0f, g.W, g.H)
            canvas.drawBitmap(pocket, null, rect, bitmapPaint)
            drawPocketLight(canvas, light)
            if (foil != null) drawFoil(canvas, foil, pose.foilReveal)
            if (pose.flapDegrees <= 90f) {
                drawFlap(canvas, flapOutside, pose.flapDegrees)
                if (pose.flapDegrees < 0.5f) drawFlapLight(canvas, light)
            }
            if (pose.sealAlpha > 0f) drawPressedSeal(canvas, pose, light)
        }
        // Outside the envelope's layer: the stamp rises above the frame.
        if (pose.toolAlpha > 0f) inEnvelope(canvas, pose.envelopeY, 1f) { drawStamp(canvas, pose) }
    }

    /**
     * The stamp, its face's middle on the seal's middle, moved by the pose's tool place and tilted
     * about that point. Its shadow comes in and darkens as it meets the wax: 0 30 24 at .25 high
     * above it, 0 6 8 at .45 on it (iOS EnvelopeStampTool).
     */
    private fun drawStamp(canvas: Canvas, pose: PackingPose) {
        val (body, near, far) = stamp ?: return
        val g = EnvelopeGeometry
        val t = StampGeometry
        canvas.save()
        canvas.translate(g.SEAL_X + g.SEAL / 2 + pose.toolX, g.SEAL_Y + g.SEAL / 2 + pose.toolY)
        canvas.rotate(pose.toolTilt)
        canvas.translate(-t.CONTACT_X, -t.CONTACT_Y)
        val alpha = pose.toolAlpha.coerceIn(0f, 1f)
        val count = if (alpha < 1f) canvas.saveLayerAlpha(null, (alpha * 255).roundToInt()) else canvas.save()
        val h = pose.toolHeight.coerceIn(0f, 1f)
        val shade = 0.45f - 0.2f * h
        val sm = t.SHADOW_MARGIN
        rect.set(-sm, -sm + 6f + 24f * h, t.W + sm, t.H + sm + 6f + 24f * h)
        if (h < 1f) {
            bitmapPaint.alpha = ((1f - h) * shade * 255).roundToInt()
            canvas.drawBitmap(near, null, rect, bitmapPaint)
        }
        if (h > 0f) {
            bitmapPaint.alpha = (h * shade * 255).roundToInt()
            canvas.drawBitmap(far, null, rect, bitmapPaint)
        }
        bitmapPaint.alpha = 255
        val m = t.MARGIN
        rect.set(-m, -m, t.W + m, t.H + m)
        canvas.drawBitmap(body, null, rect, bitmapPaint)
        canvas.restoreToCount(count)
        canvas.restore()
    }

    /** Runs [block] in envelope units, with the envelope [dy] object units below its rest place, at [alpha]. */
    private inline fun inEnvelope(canvas: Canvas, dy: Float, alpha: Float, block: () -> Unit) {
        val g = EnvelopeGeometry
        val count = if (alpha < 1f) {
            canvas.saveLayerAlpha(null, (alpha * 255).roundToInt())
        } else {
            canvas.save()
        }
        canvas.translate(g.ENV_LEFT, g.ENV_TOP + dy)
        canvas.scale(g.ENV_SCALE, g.ENV_SCALE)
        block()
        canvas.restoreToCount(count)
    }

    /**
     * The note as it comes from the check: scaled from the check's disc (0.324 x 0.475, corners 50%)
     * to itself (corners 12), green fading off it.
     */
    private fun drawMorphingNote(canvas: Canvas, note: Bitmap, pose: PackingPose, tint: Int) {
        val g = EnvelopeGeometry
        val p = pose.noteShape
        val top = g.CARD_Y + pose.noteY
        rect.set(g.CARD_X, top, g.CARD_X + g.CARD_W, top + g.CARD_H)
        canvas.save()
        canvas.scale(lerp(NOTE_FROM_SCALE_X, 1f, p), lerp(NOTE_FROM_SCALE_Y, 1f, p), rect.centerX(), rect.centerY())
        val count = canvas.saveLayerAlpha(rect, (pose.noteAlpha.coerceIn(0f, 1f) * 255).roundToInt())
        canvas.drawBitmap(note, null, rect, bitmapPaint)
        if (pose.noteTint > 0f) {
            paint.color = tint
            paint.alpha = (pose.noteTint.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawRect(rect, paint)
        }
        // The corners: what is outside the rounded box goes.
        paint.color = Black.toArgb()
        paint.xfermode = dstIn
        val rx = max(0f, lerp(g.CARD_W / 2, g.RADIUS, p))
        val ry = max(0f, lerp(g.CARD_H / 2, g.RADIUS, p))
        canvas.drawRoundRect(rect, rx, ry, paint)
        paint.xfermode = null
        canvas.restoreToCount(count)
        canvas.restore()
    }

    /** The foil amount, shown left to right as the light passes (the mockup's mask at --fr). */
    private fun drawFoil(canvas: Canvas, foil: Bitmap, reveal: Float) {
        val g = EnvelopeGeometry
        if (reveal <= -0.25f) return
        rect.set(0f, 0f, g.W, g.H)
        if (reveal >= 1.27f) {
            canvas.drawBitmap(foil, null, rect, bitmapPaint)
            return
        }
        val band = RectF(0f, FOIL_TOP - FOIL_ROOM, g.W, FOIL_TOP + FOIL_SIZE + FOIL_ROOM)
        val count = canvas.saveLayer(band, null)
        canvas.drawBitmap(foil, null, rect, bitmapPaint)
        val length = sweepLength(g.W, FOIL_SIZE)
        paint.shader = revealShader(0f, FOIL_TOP, g.W, FOIL_SIZE, (reveal - 0.08f) * length, (reveal + 0.10f) * length)
        paint.xfermode = dstIn
        canvas.drawRect(band, paint)
        paint.xfermode = null
        paint.shader = null
        canvas.restoreToCount(count)
    }

    /** The seal pressing on: it drops from [PackingPose.sealScale] to the paper, its shadow tightening. */
    private fun drawPressedSeal(canvas: Canvas, pose: PackingPose, light: EnvelopePose) {
        val g = EnvelopeGeometry
        val shadow = sealShadow ?: return
        val s = pose.sealScale
        canvas.save()
        canvas.scale(s, s, g.SEAL_X + g.SEAL / 2, g.SEAL_Y + g.SEAL / 2)
        val count = if (pose.sealAlpha < 1f) canvas.saveLayerAlpha(null, (pose.sealAlpha * 255).roundToInt()) else canvas.save()
        val sm = SEAL_SHADOW_MARGIN
        rect.set(g.SEAL_X - sm, g.SEAL_Y - sm, g.SEAL_X + g.SEAL + sm, g.SEAL_Y + g.SEAL + sm)
        canvas.drawBitmap(shadow, null, rect, bitmapPaint)
        drawSeal(canvas, light, lowerHalf = false, mark = pose.sealMark, dome = pose.sealDome)
        canvas.restoreToCount(count)
        canvas.restore()
    }

    /** The flap turned [degrees] on its top edge, in perspective (CSS perspective(1600px) rotateX). */
    private fun drawFlap(canvas: Canvas, bitmap: Bitmap, degrees: Float) {
        val g = EnvelopeGeometry
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        if (abs(c) < 0.015f) return
        val m = g.MARGIN
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val corners = floatArrayOf(-m, -m, g.W + m, -m, g.W + m, g.FLAP_H + m, -m, g.FLAP_H + m)
        val pixels = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        for (i in 0 until 4) {
            val x = corners[2 * i] - g.W / 2
            val y = corners[2 * i + 1]
            val k = g.PERSPECTIVE / (g.PERSPECTIVE - y * s)
            src[2 * i] = pixels[2 * i]
            src[2 * i + 1] = pixels[2 * i + 1]
            dst[2 * i] = g.W / 2 + x * k
            dst[2 * i + 1] = y * c * k
        }
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
    }

    private fun drawCard(canvas: Canvas, card: Bitmap, pose: EnvelopePose) {
        val g = EnvelopeGeometry
        val top = g.CARD_Y - g.CARD_RISE * pose.rise
        rect.set(g.CARD_X, top, g.CARD_X + g.CARD_W, top + g.CARD_H)
        canvas.drawBitmap(card, null, rect, bitmapPaint)
        if (pose.sweep > -0.5f && pose.sweep < 1.5f) {
            // The sweep on the card (soft-light white .7 in the mockup; screen at a low alpha here).
            val length = sweepLength(g.CARD_W, g.CARD_H)
            canvas.save()
            canvas.clipRect(rect)
            paint.shader = sweepShader(
                rect.left, rect.top, g.CARD_W, g.CARD_H,
                (pose.sweep - 0.16f) * length, pose.sweep * length, (pose.sweep + 0.16f) * length, White.withAlpha(0.16f * pose.sweepAlpha)
            )
            paint.xfermode = screen
            canvas.drawRect(rect, paint)
            paint.xfermode = null
            paint.shader = null
            canvas.restore()
        }
    }

    /** The light that follows the tilt, and the sweep, on the pocket. */
    private fun drawPocketLight(canvas: Canvas, pose: EnvelopePose) {
        val g = EnvelopeGeometry
        canvas.save()
        canvas.clipPath(pocketClip)
        paint.shader = ellipse(
            pose.lightX * g.W, pose.lightY * g.H, 0.7f * g.W, 0.9f * g.H,
            intArrayOf(White.withAlpha(0.08f), White.withAlpha(0f)), floatArrayOf(0f, 0.7f)
        )
        canvas.drawRect(0f, 0f, g.W, g.H, paint)
        if (pose.sweep > -0.5f && pose.sweep < 1.5f) {
            val length = sweepLength(g.W, g.H)
            paint.shader = sweepShader(
                0f, 0f, g.W, g.H,
                (pose.sweep - 0.16f) * length, pose.sweep * length, (pose.sweep + 0.16f) * length, White.withAlpha(0.09f * pose.sweepAlpha)
            )
            canvas.drawRect(0f, 0f, g.W, g.H, paint)
        }
        paint.shader = null
        canvas.restore()
    }

    /** The light on the closed flap (its own box, 1000 x 310), and the sweep, in line with the pocket's. */
    private fun drawFlapLight(canvas: Canvas, pose: EnvelopePose) {
        val g = EnvelopeGeometry
        canvas.save()
        canvas.clipPath(flap)
        paint.shader = ellipse(
            pose.lightX * g.W, pose.lightY * g.FLAP_H, 0.7f * g.W, 0.9f * g.FLAP_H,
            intArrayOf(White.withAlpha(0.08f), White.withAlpha(0f)), floatArrayOf(0f, 0.7f)
        )
        canvas.drawRect(0f, 0f, g.W, g.FLAP_H, paint)
        if (pose.sweep > -0.5f && pose.sweep < 1.5f) {
            val length = sweepLength(g.W, g.H)
            paint.shader = sweepShader(
                0f, 0f, g.W, g.H,
                (pose.sweep - 0.16f) * length, pose.sweep * length, (pose.sweep + 0.16f) * length, White.withAlpha(0.09f * pose.sweepAlpha)
            )
            canvas.drawRect(0f, 0f, g.W, g.FLAP_H, paint)
        }
        paint.shader = null
        canvas.restore()
    }

    /** The seal, whole or its lower half, with the light on the wax. */
    private fun drawSeal(canvas: Canvas, pose: EnvelopePose, lowerHalf: Boolean, mark: Float = 1f, dome: Float = 0f) {
        val g = EnvelopeGeometry
        canvas.save()
        canvas.translate(g.SEAL_X, g.SEAL_Y)
        if (lowerHalf) canvas.clipPath(sealLower)
        val sm = g.SEAL_MARGIN
        rect.set(-sm, -sm, g.SEAL + sm, g.SEAL + sm)
        canvas.drawBitmap(seal, null, rect, bitmapPaint)
        canvas.clipPath(blob)
        if (dome > 0f) drawDome(canvas, dome.coerceIn(0f, 1f))
        if (sealMarks != null && mark > 0f) {
            bitmapPaint.alpha = (mark.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawBitmap(sealMarks, null, rect, bitmapPaint)
            bitmapPaint.alpha = 255
        }
        paint.xfermode = screen
        val spot = LinkEnvelopeColors.WaxSpot
        paint.shader = ellipse(
            (0.18f + pose.lightX * 0.64f) * g.SEAL, (0.14f + pose.lightY * 0.6f) * g.SEAL, 54f, 42f,
            intArrayOf(spot.withAlpha(0.42f), spot.withAlpha(0f)), floatArrayOf(0f, 0.72f)
        )
        canvas.drawRect(0f, 0f, g.SEAL, g.SEAL, paint)
        if (pose.sweep > -0.5f && pose.sweep < 1.5f) {
            // The sweep crosses the seal at the envelope's own place (background-position -402px).
            val at = pose.sweep * g.W
            paint.shader = sweepShader(-g.SEAL_X, 0f, g.W, g.SEAL, at - 90f, at, at + 90f, LinkEnvelopeColors.WaxSweep.withAlpha(0.42f * pose.sweepAlpha))
            canvas.drawRect(0f, 0f, g.SEAL, g.SEAL, paint)
        }
        paint.shader = null
        paint.xfermode = null
        canvas.restore()
    }

    /** A fresh drop, at [alpha]: its round top catches the light and its edge falls away (seal units, clipped to the wax). */
    private fun drawDome(canvas: Canvas, alpha: Float) {
        val g = EnvelopeGeometry
        val c = LinkEnvelopeColors
        val r = g.SEAL / 2
        paint.alpha = 255
        paint.shader = RadialGradient(
            r, r, r,
            intArrayOf(c.WaxShade.withAlpha(0f), c.WaxShade.withAlpha(0f), c.WaxShade.withAlpha(0.4f)),
            floatArrayOf(0f, 56f / 98f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = (alpha * 255).roundToInt()
        canvas.drawRect(0f, 0f, g.SEAL, g.SEAL, paint)
        paint.xfermode = screen
        paint.shader = RadialGradient(
            0.4f * g.SEAL, 0.34f * g.SEAL, 62f,
            intArrayOf(c.WaxSpot.withAlpha(0.55f), c.WaxSpot.withAlpha(0f)), null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, g.SEAL, g.SEAL, paint)
        paint.xfermode = null
        paint.shader = null
        paint.alpha = 255
    }

    /** The crack through the wax: a dark stroke and a light one 2.2 below, drawn from the left. */
    private fun drawCrack(canvas: Canvas, pose: EnvelopePose) {
        val g = EnvelopeGeometry
        val length = crackMeasure.length
        canvas.save()
        canvas.translate(g.SEAL_X, g.SEAL_Y)
        canvas.clipPath(blob)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        crackSegment.reset()
        crackMeasure.getSegment(0f, min(length, 260f * pose.crack), crackSegment, true)
        paint.color = LinkEnvelopeColors.Crack.toArgb()
        paint.strokeWidth = 2.8f
        canvas.drawPath(crackSegment, paint)
        if (pose.crackGlint > 0f) {
            crackSegment.reset()
            crackMeasure.getSegment(0f, min(length, 260f * pose.crackGlint), crackSegment, true)
            canvas.translate(0f, 2.2f)
            paint.color = LinkEnvelopeColors.WaxGlint.withAlpha(0.6f)
            paint.strokeWidth = 1.3f
            canvas.drawPath(crackSegment, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    companion object {
        /** Draws the still layers at [scale] pixels per envelope unit. Call off the main thread. */
        fun render(scale: Float): EnvelopeArt {
            val seal = renderSeal(scale)
            return EnvelopeArt(
                shadow = renderShadow(scale),
                pocket = renderPocket(scale),
                flapOutside = renderFlapOutside(scale, seal),
                flapInside = renderFlapInside(scale),
                seal = seal,
            )
        }

        /**
         * The same layers for the send side ([drawPacking]): the flap closes before the seal presses
         * on, so the flap has no seal half, and the seal's shadow is drawn apart. Call off the main thread.
         */
        fun renderForPacking(scale: Float): EnvelopeArt = EnvelopeArt(
            shadow = renderShadow(scale),
            pocket = renderPocket(scale),
            flapOutside = renderFlapOutside(scale, seal = null),
            flapInside = renderFlapInside(scale),
            seal = renderSeal(scale, shadow = false, marks = false),
            // drop-shadow(0 5 6 rgba(0,0,0,.5)): the wax lands on the paper, the stamp presses it.
            sealShadow = renderSealShadow(scale, 5f, 6f, 0.5f),
            sealMarks = renderSeal(scale, shadow = false, wax = false),
            // The stamp's shadow, blur 8 on the wax and 24 high above it (it moves with the tool).
            stamp = Triple(renderStamp(scale), renderStampShadow(scale, 8f), renderStampShadow(scale, 24f)),
        )

        /** The stamp's body ([StampGeometry]), in tool units: graphite parts lit from the upper left. */
        private fun renderStamp(scale: Float): Bitmap {
            val t = StampGeometry
            val (bitmap, canvas) = layer(t.W, t.H, t.MARGIN, scale)
            val metal = LinkEnvelopeColors.StampMetal.toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun turn(minX: Float, maxX: Float): Shader = LinearGradient(
                minX, 0f, maxX, 0f,
                intArrayOf(
                    Black.withAlpha(0.42f), Black.withAlpha(0.1f), White.withAlpha(0.22f),
                    White.withAlpha(0.04f), Black.withAlpha(0.18f), Black.withAlpha(0.5f)
                ),
                floatArrayOf(0f, 0.14f, 0.32f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
            fun metal(path: Path, minX: Float, maxX: Float) {
                paint.shader = null; paint.color = metal; canvas.drawPath(path, paint)
                paint.shader = turn(minX, maxX); canvas.drawPath(path, paint)
                paint.shader = null
            }
            fun cap(r: RectF) {
                paint.style = Paint.Style.FILL
                paint.color = metal; canvas.drawOval(r, paint)
                paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, White.withAlpha(0.2f), Black.withAlpha(0.18f), Shader.TileMode.CLAMP)
                canvas.drawOval(r, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = White.withAlpha(0.2f)
                canvas.drawOval(r, paint)
                paint.style = Paint.Style.FILL
            }
            val head = t.HEAD_SIDE
            metal(t.side(head.left, head.top, head.width(), head.height()), head.left, head.right)
            cap(t.HEAD_CAP)
            // The collar: its side, the orange band round its front, the turn's light.
            val c = t.COLLAR_SIDE
            val collar = t.side(c.left, c.top, c.width(), c.height())
            paint.color = metal; canvas.drawPath(collar, paint)
            canvas.save()
            canvas.clipPath(collar)
            val band = Path().apply { moveTo(29f, 283f); with(t) { front(75f, 283f, 46f, leftToRight = true) } }
            // brand (#FF6600), as the liner.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = LinkEnvelopeColors.LinerTop.toArgb()
            canvas.drawPath(band, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
            paint.shader = turn(c.left, c.right); canvas.drawPath(collar, paint); paint.shader = null
            cap(t.COLLAR_CAP)
            val handle = t.handle()
            metal(handle, 39f, 111f)
            paint.shader = RadialGradient(62f, 40f, 30f, White.withAlpha(0.22f), White.withAlpha(0f), Shader.TileMode.CLAMP)
            canvas.drawPath(handle, paint)
            paint.shader = null
            return bitmap
        }

        /** The stamp's silhouette in black, blurred [blurRadius] (CSS units); drawn at the shadow's alpha. */
        private fun renderStampShadow(scale: Float, blurRadius: Float): Bitmap {
            val t = StampGeometry
            val (bitmap, canvas) = layer(t.W, t.H, t.SHADOW_MARGIN, scale)
            val outline = t.side(0f, 294f, 150f, 58f).apply {
                addOval(t.HEAD_CAP, Path.Direction.CW)
                op(t.side(29f, 272f, 92f, 22f), Path.Op.UNION)
                op(Path().apply { addOval(t.COLLAR_CAP, Path.Direction.CW) }, Path.Op.UNION)
                op(t.handle(), Path.Op.UNION)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Black.toArgb(); maskFilter = blur(blurRadius) }
            canvas.drawPath(outline, paint)
            return bitmap
        }

        /**
         * The foil amount alone, on a clear envelope-sized layer: [number] and [unit] (none in Fiat
         * Mode), fitted to 860 units down to 40%. Call off the main thread.
         */
        fun renderFoil(context: Context, scale: Float, number: String, unit: String?): Bitmap {
            val g = EnvelopeGeometry
            val (bitmap, canvas) = layer(g.W, g.H, 0f, scale)
            foil(context, canvas, number, unit, fitWidth = 860f, minFit = 0.4f)
            return bitmap
        }

        /** The seal's drop shadow alone, [dy] down and [blurRadius] soft (CSS units), around the seal. */
        private fun renderSealShadow(scale: Float, dy: Float, blurRadius: Float, alpha: Float): Bitmap {
            val g = EnvelopeGeometry
            val (bitmap, canvas) = layer(g.SEAL, g.SEAL, SEAL_SHADOW_MARGIN, scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Black.withAlpha(alpha); maskFilter = blur(blurRadius) }
            canvas.translate(0f, dy)
            canvas.drawPath(EnvelopePaths.parse(EnvelopePaths.SEAL_BLOB), paint)
            return bitmap
        }

        private fun layer(width: Float, height: Float, margin: Float, scale: Float): Pair<Bitmap, Canvas> {
            val bitmap = Bitmap.createBitmap(
                max(1, ceil((width + 2 * margin) * scale).toInt()),
                max(1, ceil((height + 2 * margin) * scale).toInt()),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width / (width + 2 * margin), bitmap.height / (height + 2 * margin))
            canvas.translate(margin, margin)
            return bitmap to canvas
        }

        /**
         * Grain at [alpha] over [width] x [height] units, one noise value per pixel: OVERLAY on the
         * dark paper, MULTIPLY (as a shade, see [noise]) on the ivory card.
         */
        private fun grain(canvas: Canvas, width: Float, height: Float, scale: Float, seed: Long, alpha: Float, mode: PorterDuff.Mode, spread: Double) {
            val multiply = mode == PorterDuff.Mode.MULTIPLY
            val bitmap = noise(max(1, ceil(width * scale).toInt()), max(1, ceil(height * scale).toInt()), seed, spread, shade = multiply)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (!multiply) xfermode = PorterDuffXfermode(mode)
                this.alpha = (alpha * 255).roundToInt()
            }
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), paint)
            bitmap.recycle()
        }

        /** box-shadow: 0 26 46 -12 rgba(0,0,0,.5), 0 4 10 rgba(0,0,0,.2). */
        private fun renderShadow(scale: Float): Bitmap {
            val g = EnvelopeGeometry
            val (bitmap, canvas) = layer(g.W, g.H, g.MARGIN, scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Black.withAlpha(0.5f)
            paint.maskFilter = blur(46f)
            canvas.drawRect(12f, 12f + 26f, g.W - 12f, g.H - 12f + 26f, paint)
            paint.color = Black.withAlpha(0.2f)
            paint.maskFilter = blur(10f)
            canvas.drawRoundRect(0f, 4f, g.W, g.H + 4f, g.RADIUS, g.RADIUS, paint)
            return bitmap
        }

        private fun renderPocket(scale: Float): Bitmap {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val (bitmap, canvas) = layer(g.W, g.H, 0f, scale)
            val body = RectF(0f, 0f, g.W, g.H)
            val clip = Path().apply { addRoundRect(body, g.RADIUS, g.RADIUS, Path.Direction.CW) }
            canvas.clipPath(clip)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, 0f, g.H,
                intArrayOf(c.PaperTop.toArgb(), c.PaperMid.toArgb(), c.PaperBottom.toArgb()), floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(body, paint)
            paint.shader = null
            grain(canvas, g.W, g.H, scale, 33L, 0.55f, PorterDuff.Mode.OVERLAY, 0.16)
            // Laid lines: 1 unit of white 2% every 5, from the bottom.
            paint.color = White.withAlpha(0.02f)
            var y = g.H - 1f
            while (y > -1f) { canvas.drawRect(0f, y, g.W, y + 1f, paint); y -= 5f }
            // The seams of the side and bottom flaps.
            val sides = Path().apply { moveTo(0f, 0f); lineTo(468f, 346f); moveTo(1000f, 0f); lineTo(532f, 346f) }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            stroke.color = Black.withAlpha(0.42f); stroke.strokeWidth = 3f
            canvas.drawPath(sides, stroke)
            canvas.save(); canvas.translate(0f, 2.4f)
            stroke.color = White.withAlpha(0.05f); stroke.strokeWidth = 1.4f
            canvas.drawPath(sides, stroke)
            canvas.restore()
            val bottom = Path().apply { moveTo(-4f, 676f); lineTo(500f, 326f); lineTo(1004f, 676f) }
            canvas.save(); canvas.translate(0f, -4f)
            stroke.color = Black.withAlpha(0.55f); stroke.strokeWidth = 7f; stroke.maskFilter = blur(6f)
            canvas.drawPath(bottom, stroke)
            stroke.maskFilter = null
            canvas.restore()
            paint.color = White.withAlpha(0.028f)
            canvas.drawPath(Path().apply { moveTo(0f, 680f); lineTo(500f, 330f); lineTo(1000f, 680f); close() }, paint)
            stroke.color = White.withAlpha(0.10f); stroke.strokeWidth = 1.6f
            canvas.drawPath(Path().apply { moveTo(0f, 680f); lineTo(500f, 330f); lineTo(1000f, 680f) }, stroke)
            // inset 0 0 0 1.5px white 8%, inset 0 2px 0 white 7%.
            stroke.color = White.withAlpha(0.08f); stroke.strokeWidth = 1.5f
            canvas.drawRoundRect(0.75f, 0.75f, g.W - 0.75f, g.H - 0.75f, g.RADIUS - 0.75f, g.RADIUS - 0.75f, stroke)
            val lip = Path(clip).apply { op(Path(clip).apply { offset(0f, 2f) }, Path.Op.DIFFERENCE) }
            paint.color = White.withAlpha(0.07f)
            canvas.drawPath(lip, paint)
            return bitmap
        }

        /** The flap's drop shadow: 0 7 9 rgba(0,0,0,.5). */
        private fun flapShadow(canvas: Canvas, flap: Path) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Black.withAlpha(0.5f); maskFilter = blur(9f) }
            canvas.save(); canvas.translate(0f, 7f); canvas.drawPath(flap, paint); canvas.restore()
        }

        /** The flap's outside; with [seal], the seal's upper half on it (it lifts with the flap when the seal cracks). */
        private fun renderFlapOutside(scale: Float, seal: Bitmap?): Bitmap {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val (bitmap, canvas) = layer(g.W, g.FLAP_H, g.MARGIN, scale)
            val flap = EnvelopePaths.flap()
            flapShadow(canvas, flap)
            canvas.save()
            canvas.clipPath(flap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, 0f, g.FLAP_H,
                intArrayOf(c.FlapTop.toArgb(), c.FlapMid.toArgb(), c.FlapTip.toArgb()), floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, g.W, g.FLAP_H, paint)
            grain(canvas, g.W, g.FLAP_H, scale, 34L, 0.55f, PorterDuff.Mode.OVERLAY, 0.16)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = White.withAlpha(0.13f); strokeWidth = 3f
            }
            canvas.drawPath(EnvelopePaths.flapEdge(), stroke)
            canvas.restore()
            if (seal == null) return bitmap
            // The seal's upper half lifts with the flap.
            canvas.save()
            canvas.translate(g.SEAL_X, g.SEAL_Y)
            canvas.clipPath(EnvelopePaths.sealUpper())
            val sm = g.SEAL_MARGIN
            canvas.drawBitmap(seal, null, RectF(-sm, -sm, g.SEAL + sm, g.SEAL + sm), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            return bitmap
        }

        private fun renderFlapInside(scale: Float): Bitmap {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val (bitmap, canvas) = layer(g.W, g.FLAP_H, g.MARGIN, scale)
            val flap = EnvelopePaths.flap()
            flapShadow(canvas, flap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = c.FlapInside.toArgb()
            canvas.drawPath(flap, paint)
            canvas.save()
            canvas.clipPath(EnvelopePaths.liner())
            paint.shader = LinearGradient(
                0f, 0f, 0f, g.FLAP_H,
                intArrayOf(c.LinerTop.toArgb(), c.LinerMid.toArgb(), c.LinerBottom.toArgb()), floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, g.W, g.FLAP_H, paint)
            paint.shader = null
            // repeating-linear-gradient(118deg, rgba(0,0,0,.05) 0 2px, transparent 2px 9px)
            paint.color = Black.withAlpha(0.05f)
            canvas.rotate(28f)
            var x = -600f
            while (x < 1400f) { canvas.drawRect(x, -1200f, x + 2f, 1200f, paint); x += 9f }
            canvas.restore()
            return bitmap
        }

        /** The wax seal; [shadow]: with its drop shadow (the send side draws the shadow apart). */
        private fun renderSeal(scale: Float, shadow: Boolean = true, wax: Boolean = true, marks: Boolean = true): Bitmap {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val (bitmap, canvas) = layer(g.SEAL, g.SEAL, g.SEAL_MARGIN, scale)
            val blob = EnvelopePaths.parse(EnvelopePaths.SEAL_BLOB)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // drop-shadow(0 5 6 rgba(0,0,0,.5))
            if (shadow) {
                paint.color = Black.withAlpha(0.5f)
                paint.maskFilter = blur(6f)
                canvas.save(); canvas.translate(0f, 5f); canvas.drawPath(blob, paint); canvas.restore()
                paint.maskFilter = null
            }
            // The wax: radial-gradient(circle at 42% 34%, ...), to the farthest corner.
            if (wax) drawWax(canvas, paint, blob)
            if (!marks) return bitmap
            // The ring, lit from above.
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6.5f }
            ring.color = c.WaxGlint.withAlpha(0.6f)
            canvas.drawCircle(98f, 98f - 1.6f, 67f, ring)
            ring.color = c.WaxShade.withAlpha(0.55f)
            canvas.drawCircle(98f, 98f + 1.6f, 67f, ring)
            ring.color = c.WaxRing.toArgb()
            canvas.drawCircle(98f, 98f, 67f, ring)
            // The stamp: the official mark pressed into the wax.
            val mark = EnvelopePaths.mark()
            canvas.save(); canvas.translate(0f, -1.8f)
            paint.color = c.WaxShadeStamp.withAlpha(0.62f); canvas.drawPath(mark, paint)
            canvas.restore()
            canvas.save(); canvas.translate(0f, 1.6f)
            paint.color = c.WaxGlintStamp.withAlpha(0.6f); canvas.drawPath(mark, paint)
            canvas.restore()
            paint.color = c.WaxStamp.toArgb()
            canvas.drawPath(mark, paint)
            return bitmap
        }

        private fun drawWax(canvas: Canvas, paint: Paint, blob: Path) {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val cx = 0.42f * g.SEAL
            val cy = 0.34f * g.SEAL
            val radius = kotlin.math.sqrt((g.SEAL - cx) * (g.SEAL - cx) + (g.SEAL - cy) * (g.SEAL - cy))
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(c.WaxLight.toArgb(), c.WaxBright.toArgb(), c.WaxBody.toArgb(), c.WaxEdge.toArgb()),
                floatArrayOf(0f, 0.34f, 0.64f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawPath(blob, paint)
            paint.shader = null
        }

        /**
         * The ivory card, 880 x 600 units: the amount ([number], then [unit] in brandPressed when
         * given) fitted to 760 units, and [label] in capitals. Call off the main thread.
         */
        fun renderCard(context: Context, scale: Float, number: String, unit: String?, label: String): Bitmap {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val (bitmap, canvas) = layer(g.CARD_W, g.CARD_H, 0f, scale)
            val box = RectF(0f, 0f, g.CARD_W, g.CARD_H)
            val clip = Path().apply { addRoundRect(box, g.RADIUS, g.RADIUS, Path.Direction.CW) }
            canvas.clipPath(clip)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = ellipse(
                0.3f * g.CARD_W, 0.1f * g.CARD_H, 1.2f * g.CARD_W, 1.2f * g.CARD_H,
                intArrayOf(c.CardLight.toArgb(), c.CardMid.toArgb(), c.CardEdge.toArgb()), floatArrayOf(0f, 0.6f, 1f)
            )
            canvas.drawRect(box, paint)
            paint.shader = null
            grain(canvas, g.CARD_W, g.CARD_H, scale, 21L, 0.10f, PorterDuff.Mode.MULTIPLY, 0.2)

            // The amount: Inter Display bold, letterpressed (light below, shade above).
            val full = 140f
            val numberPaint = interPaint(context, full, 700, 32, c.CardInk.toArgb())
            val unitPaint = interPaint(context, full * 0.42f, 700, 32, c.CardUnit.toArgb())
            val gap = 0.12f * full * 0.42f
            val width = numberPaint.measureText(number) + (unit?.let { gap + unitPaint.measureText(it) } ?: 0f)
            val fit = if (width > 0f) (g.CARD_FIT / width).coerceIn(0.4f, 1f) else 1f
            numberPaint.textSize = full * fit
            unitPaint.textSize = full * 0.42f * fit
            val left = (g.CARD_W - width * fit) / 2
            val base = baseline(numberPaint, 58f, full * fit)
            fun amount(dy: Float, ink: Int?, unitInk: Int?) {
                val numberColor = numberPaint.color
                val unitColor = unitPaint.color
                if (ink != null) numberPaint.color = ink
                if (unitInk != null) unitPaint.color = unitInk
                canvas.drawText(number, left, base + dy, numberPaint)
                if (unit != null) canvas.drawText(unit, left + numberPaint.measureText(number) + gap * fit, base + dy, unitPaint)
                numberPaint.color = numberColor
                unitPaint.color = unitColor
            }
            amount(1.6f, White.withAlpha(0.9f), White.withAlpha(0.9f))
            amount(-1f, Black.withAlpha(0.28f), Black.withAlpha(0.28f))
            amount(0f, null, null)

            // "PAYMENT LINK": 26 units (7 dp in the sheet), letter spacing .34 em.
            val labelPaint = interPaint(context, 26f, 700, 14, c.CardLabel.toArgb()).apply {
                letterSpacing = 0.34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, g.CARD_W / 2, baseline(labelPaint, 234f, 30f), labelPaint)

            // The pocket's shade on the card's lower part.
            paint.shader = LinearGradient(
                0f, g.CARD_H + 1f, 0f, g.CARD_H + 1f - 120f,
                Black.withAlpha(0.18f), Black.withAlpha(0f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, g.CARD_H - 120f, g.CARD_W, g.CARD_H, paint)
            paint.shader = null
            // inset 0 0 0 1px rgba(80,60,30,.14), inset 0 1.5px 0 white 80%.
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = c.CardBorder.withAlpha(0.14f)
            }
            canvas.drawRoundRect(0.5f, 0.5f, g.CARD_W - 0.5f, g.CARD_H - 0.5f, g.RADIUS - 0.5f, g.RADIUS - 0.5f, stroke)
            paint.color = White.withAlpha(0.8f)
            canvas.drawPath(Path(clip).apply { op(Path(clip).apply { offset(0f, 1.5f) }, Path.Op.DIFFERENCE) }, paint)
            return bitmap
        }

        /**
         * The share image, 1200 x 1200 px: the sealed envelope on a warm gray backdrop with the
         * amount in orange foil under the seal ([amount], then "XMR"), fitted to 820 px.
         */
        fun renderShare(context: Context, amount: String): Bitmap {
            val c = LinkEnvelopeColors
            val side = 1200
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = ellipse(
                600f, 480f, 0.85f * side, 0.7f * side,
                intArrayOf(c.BackdropCenter.toArgb(), c.BackdropMid.toArgb(), c.BackdropEdge.toArgb()), floatArrayOf(0f, 0.62f, 1f)
            )
            canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
            val art = render(1f)
            canvas.save()
            canvas.translate(100f, 260f)
            art.draw(canvas, EnvelopePose(), card = null) { foil(context, it, amount) }
            canvas.restore()
            return bitmap
        }

        /**
         * The share image's amount (iOS PaymentLinkCard.amount): the full amount, shrunk to fit 820
         * px. When that would take it below 40%, the amount rounded with "~": two decimals, or two
         * significant digits for an amount under 0.1, then whole coins.
         */
        fun shareAmount(context: Context, xmr: String): String {
            val value = xmr.toBigDecimalOrNull() ?: return xmr
            val full = value.stripTrailingZeros().toPlainString()
            val candidates = mutableListOf(full)
            var leadingZeros = 0
            var scaled = value
            while (scaled.signum() > 0 && scaled < java.math.BigDecimal("0.1") && leadingZeros < 10) {
                scaled = scaled.movePointRight(1)
                leadingZeros += 1
            }
            for (digits in listOf(max(2, leadingZeros + 2), 0)) {
                val rounded = value.setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                if (rounded == full || rounded == "0") continue
                val text = "~$rounded"
                if (text !in candidates) candidates.add(text)
            }
            val number = interPaint(context, 104f, 600, 32, 0)
            val unit = interPaint(context, 104f * 0.46f, 600, 32, 0)
            fun width(text: String) = number.measureText(text) + 104f * 0.46f * 0.14f + unit.measureText("XMR")
            return candidates.firstOrNull { 820f / width(it) >= 0.4f } ?: candidates.last()
        }

        /**
         * The share image's amount, debossed in orange foil at 452 units, 104 units tall at most: [amount],
         * then [unit] when given, fitted to [fitWidth] units, down to [minFit].
         */
        private fun foil(
            context: Context,
            canvas: Canvas,
            amount: String,
            unit: String? = "XMR",
            fitWidth: Float = 820f,
            minFit: Float = 0f
        ) {
            val g = EnvelopeGeometry
            val c = LinkEnvelopeColors
            val full = 104f
            val numberPaint = interPaint(context, full, 600, 32, c.FoilBrand.toArgb())
            val unitPaint = interPaint(context, full * 0.46f, 600, 32, c.FoilBrand.toArgb())
            val gap = 0.14f * full * 0.46f
            val width = numberPaint.measureText(amount) + (unit?.let { gap + unitPaint.measureText(it) } ?: 0f)
            val fit = if (width > fitWidth) max(minFit, fitWidth / width) else 1f
            numberPaint.textSize = full * fit
            unitPaint.textSize = full * 0.46f * fit
            val left = (g.W - width * fit) / 2
            val base = baseline(numberPaint, 452f, full * fit)
            val unitLeft = left + numberPaint.measureText(amount) + gap * fit
            fun text(dy: Float, color: Int?, shader: Shader?) {
                for (p in listOf(numberPaint, unitPaint)) {
                    if (color != null) p.color = color
                    p.shader = shader
                }
                canvas.drawText(amount, left, base + dy, numberPaint)
                if (unit != null) canvas.drawText(unit, unitLeft, base + dy, unitPaint)
            }
            // drop-shadow(0 -1.4 0 rgba(0,0,0,.75)) drop-shadow(0 1.1 0 rgba(255,255,255,.09))
            text(1.1f, White.withAlpha(0.09f), null)
            text(-1.4f, Black.withAlpha(0.75f), null)
            // The foil: linear-gradient(98deg, ...) across the envelope box, the light at rest.
            val lx = g.REST_LX * g.W
            val stops = floatArrayOf(lx - 560f, lx, lx + 300f, lx + 620f)
            val length = sweepLength(g.W, g.H)
            val rad = Math.toRadians(98.0)
            val dx = sin(rad).toFloat()
            val dy = -cos(rad).toFloat()
            val sx = g.W / 2 - dx * length / 2
            val sy = g.H / 2 - dy * length / 2
            val shader = LinearGradient(
                sx + dx * stops[0], sy + dy * stops[0], sx + dx * stops[3], sy + dy * stops[3],
                intArrayOf(c.FoilDeep.toArgb(), c.FoilLight.toArgb(), c.FoilBrand.toArgb(), c.FoilEdge.toArgb()),
                floatArrayOf(0f, (stops[1] - stops[0]) / (stops[3] - stops[0]), (stops[2] - stops[0]) / (stops[3] - stops[0]), 1f),
                Shader.TileMode.CLAMP
            )
            text(0f, Black.toArgb(), shader)
            numberPaint.shader = null
            unitPaint.shader = null
        }
    }
}
