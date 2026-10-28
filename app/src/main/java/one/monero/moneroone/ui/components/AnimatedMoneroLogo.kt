package one.monero.moneroone.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.ui.theme.MoneroTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Windows shorter than this, system bars included, are squat (iOS
 * SquatScreenModifier: 720pt, true on the iPhone SE and in landscape). The
 * hero screens (Welcome, Unlock) show a smaller logo on them.
 */
internal val SquatHeight = 720.dp

/** SwiftUI `.easeInOut`: cubic Bezier (0.42, 0, 0.58, 1). */
private val SwiftEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// Entrance (tokens.json motion.hero heroEntrance): scale 0.4 to 1 and fade in,
// SwiftUI .spring(response: 0.8, dampingFraction: 0.7) = stiffness (2 pi / 0.8)^2.
private const val EntranceScale = 0.4f
private const val EntranceDamping = 0.7f
private const val EntranceStiffness = 61.685f

// Float (heroFloat): the logo moves from +10 (down) to -10 (up) and back,
// easeInOut 2.5 s each way, starting 0.3 s after the logo appears.
private val FloatDistance = 10.dp
private const val FloatMillis = 2500
private const val FloatDelayMillis = 300

// Glow (heroGlow): a brand ellipse 70% x 15% of the logo size under it, lifted
// 20. Opacity 0.6 and width x1.1 when the logo is down, 0.2 and x0.75 when up.
private const val GlowWidth = 0.7f
private const val GlowHeight = 0.15f
private val GlowLift = 20.dp
private const val GlowOpacityDown = 0.6f
private const val GlowOpacityUp = 0.2f
private const val GlowScaleDown = 1.1f
private const val GlowScaleUp = 0.75f

// Shine (heroShine): a white band (0 / 0.3 / 0.6 / 0.3 / 0) 35% of the logo
// wide sweeps once from -1.5 to 1.5 logo widths, easeInOut 1.2 s after 0.6 s.
private const val ShineWidth = 0.35f
private const val ShineFrom = -1.5f
private const val ShineTo = 1.5f
private const val ShineDelayMillis = 600L
private const val ShineMillis = 1200

// iOS blur(radius: 25) on the glow and blur(radius: 8) on the shine, as a
// Gaussian sigma. SwiftUI draws sigma = radius or very close: the glow fits
// sigma 24.5 pt in lossless iPhone 17 simulator screenshots.
// Android takes a radius instead and draws sigma = 0.57735 * radius + 0.5 px,
// so the radius is worked back from these (blurRadius below).
private val GlowSigma = 24.5.dp
private val ShineSigma = 8.dp

/**
 * The hero logo of the Welcome and Unlock screens, ported value for value from
 * iOS AnimatedMoneroLogo (tokens.json motion.hero): the glass [MoneroLogo]
 * springs in, floats up and down over a breathing orange glow, and one shine
 * sweeps across it. Sizes are the iOS points as dp.
 *
 * The layout box is [size] wide and 1.15 x [size] tall (logo plus glow slot),
 * as on iOS. The blurs use Modifier.blur on API 31+; below that, the glow is a
 * software-blurred bitmap and the shine a pre-blurred gradient.
 *
 * With "Remove animations" on, Compose ends every animation at once: the logo
 * shows at full size in its up pose, and the shine does not show. iOS does not
 * read Reduce Motion here and always animates.
 */
@Composable
fun AnimatedMoneroLogo(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val appear = remember { Animatable(0f) }
    val shine = remember { Animatable(ShineFrom) }
    LaunchedEffect(Unit) {
        launch {
            appear.animateTo(1f, spring(dampingRatio = EntranceDamping, stiffness = EntranceStiffness))
        }
        delay(ShineDelayMillis)
        shine.animateTo(ShineTo, tween(ShineMillis, easing = SwiftEaseInOut))
    }
    // 0 = down (where it starts), 1 = up.
    val float = rememberInfiniteTransition(label = "heroFloat").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(FloatMillis, easing = SwiftEaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(FloatDelayMillis)
        ),
        label = "heroFloat"
    )
    val glowColor = MoneroTheme.colors.brand

    // Animated values are read inside the layer blocks, so a frame only
    // redraws layers and never recomposes.
    Column(
        modifier = modifier.graphicsLayer {
            val scale = EntranceScale + (1f - EntranceScale) * appear.value
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = FloatDistance.toPx() * (1f - 2f * float.value)
                    alpha = appear.value.coerceIn(0f, 1f)
                    shape = CircleShape
                    clip = true
                },
            contentAlignment = Alignment.Center
        ) {
            MoneroLogo(size = size)
            ShineBand(logoSize = size, offset = { shine.value })
        }
        Box(
            modifier = Modifier.size(width = size * GlowWidth, height = size * GlowHeight),
            contentAlignment = Alignment.Center
        ) {
            GlowEllipse(
                width = size * GlowWidth,
                height = size * GlowHeight,
                color = glowColor
            ) {
                val up = float.value
                translationY = -GlowLift.toPx()
                scaleX = GlowScaleDown + (GlowScaleUp - GlowScaleDown) * up
                alpha = (GlowOpacityDown + (GlowOpacityUp - GlowOpacityDown) * up) *
                    appear.value.coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * The shine band, drawn centered and moved so its left edge sits at [offset] x [logoSize].
 * [height] is the art's height (the username card is wider than it is tall).
 */
@Composable
internal fun ShineBand(logoSize: Dp, offset: () -> Float, height: Dp = logoSize) {
    val density = LocalDensity.current
    val band = logoSize * ShineWidth
    val pad = ShineSigma * 3
    val preBlurred: Brush? = if (Build.VERSION.SDK_INT >= 31) null else remember(logoSize, density) {
        with(density) { blurredBandBrush(band.toPx(), ShineSigma.toPx(), pad.toPx()) }
    }
    Box(
        modifier = Modifier
            .requiredSize(width = band + pad * 2, height = height + pad * 2)
            .graphicsLayer {
                val logo = logoSize.toPx()
                translationX = offset() * logo - (logo - band.toPx()) / 2f
            }
            .then(
                if (preBlurred == null) {
                    Modifier.blur(with(density) { blurRadius(ShineSigma) }, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val p = pad.toPx()
                val logo = height.toPx()
                if (preBlurred != null) {
                    drawRect(preBlurred, topLeft = Offset(0f, p), size = Size(this.size.width, logo))
                } else {
                    val w = band.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Clear,
                            0.25f to Color.White.copy(alpha = 0.3f),
                            0.5f to Color.White.copy(alpha = 0.6f),
                            0.75f to Color.White.copy(alpha = 0.3f),
                            1f to Clear,
                            startX = p,
                            endX = p + w
                        ),
                        topLeft = Offset(p, p),
                        size = Size(w, logo)
                    )
                }
            }
    )
}

/** The glow ellipse, blurred, with room around it for the blur to spread. */
@Composable
internal fun GlowEllipse(
    width: Dp,
    height: Dp,
    color: Color,
    layer: GraphicsLayerScope.() -> Unit
) {
    val density = LocalDensity.current
    val pad = GlowSigma * 3
    val preBlurred: ImageBitmap? = if (Build.VERSION.SDK_INT >= 31) null else remember(width, height, color, density) {
        with(density) { blurredOval(width.toPx(), height.toPx(), pad.toPx(), GlowSigma.toPx(), color) }
    }
    Box(
        modifier = Modifier
            .requiredSize(width + pad * 2, height + pad * 2)
            .graphicsLayer(layer)
            .then(
                if (preBlurred == null) {
                    Modifier.blur(with(density) { blurRadius(GlowSigma) }, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (preBlurred != null) {
                    drawImage(preBlurred)
                } else {
                    val p = pad.toPx()
                    drawOval(color, topLeft = Offset(p, p), size = Size(width.toPx(), height.toPx()))
                }
            }
    )
}

/** Transparent white, so gradients fade to clear without a gray fringe. */
private val Clear = Color.White.copy(alpha = 0f)

/** The Android blur radius (Modifier.blur, BlurMaskFilter) that draws a Gaussian of [sigma]. */
private fun Density.blurRadius(sigma: Dp): Dp = radiusForSigma(sigma.toPx()).toDp()

private fun radiusForSigma(sigmaPx: Float): Float = ((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0f)

/** API 27-30: the glow ellipse blurred once in software (BlurMaskFilter is software only there). */
private fun blurredOval(width: Float, height: Float, pad: Float, sigma: Float, color: Color): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        ceil(width + pad * 2).toInt(),
        ceil(height + pad * 2).toInt(),
        Bitmap.Config.ARGB_8888
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(radiusForSigma(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    android.graphics.Canvas(bitmap).drawOval(pad, pad, pad + width, pad + height, paint)
    return bitmap.asImageBitmap()
}

/**
 * API 27-30: the shine band's gradient already blurred sideways (the band
 * profile convolved with the Gaussian), across a node [band] + 2 x [pad] wide.
 */
private fun blurredBandBrush(band: Float, sigma: Float, pad: Float): Brush {
    val total = band + pad * 2
    val half = band / 2
    val steps = 32
    val fine = 200
    val du = band / fine
    val norm = sqrt(2f * PI.toFloat()) * sigma
    val stops = Array(steps + 1) { i ->
        val x = total * i / steps - pad - half   // from the band center
        var sum = 0f
        for (j in 0..fine) {
            val u = -half + du * j
            val tri = 0.6f * (1f - abs(u) / half)
            sum += tri * exp(-((x - u) * (x - u)) / (2f * sigma * sigma)) * du
        }
        (i.toFloat() / steps) to Color.White.copy(alpha = (sum / norm).coerceIn(0f, 1f))
    }
    return Brush.horizontalGradient(*stops, startX = 0f, endX = total)
}
