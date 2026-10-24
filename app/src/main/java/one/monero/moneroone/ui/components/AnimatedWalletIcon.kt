package one.monero.moneroone.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.ui.theme.BrandHighlight
import one.monero.moneroone.ui.theme.BrandShade
import one.monero.moneroone.ui.theme.MoneroTheme

// iOS AnimatedWalletIcon, value for value (tokens.json motion.hero).
// Entrance (heroEntrance): scale 0.4 to 1 and fade in, spring(0.8, 0.7).
private const val EntranceScale = 0.4f
private const val EntranceDamping = 0.7f
private const val EntranceStiffness = 61.685f

// Float (heroFloat): the glyph moves from +8 (down) to -8 (up) and back,
// easeInOut 2.5 s each way, starting 0.3 s after it appears.
private val FloatDistance = 8.dp
private const val FloatMillis = 2500
private const val FloatDelayMillis = 300

// Glow (heroGlow, the glyph's values): an ellipse 60% x 6% of the size under
// the tile, 6 lower, blur 16; black in light mode and brand in dark mode.
// Opacity 0.45 and width x1.1 when the tile is down, 0.15 and x0.7 when up.
private const val GlowWidth = 0.6f
private const val GlowHeight = 0.06f
private val GlowDrop = 6.dp
private val GlowSigma = 16.dp
private const val GlowOpacityDown = 0.45f
private const val GlowOpacityUp = 0.15f
private const val GlowScaleDown = 1.1f
private const val GlowScaleUp = 0.7f

// Shine: a white band (0 / 0.15 / 0.4 / 0.15 / 0) 40% of the tile wide, blur 6,
// sweeps once from -1.5 to 1.5 tile widths, easeInOut 1.2 s after 0.6 s.
private const val ShineWidth = 0.4f
private val ShineSigma = 6.dp
private const val ShineMid = 0.15f
private const val ShinePeak = 0.4f
private const val ShineFrom = -1.5f
private const val ShineTo = 1.5f
private const val ShineDelayMillis = 600L
private const val ShineMillis = 1200

// The tile: corner 24% of the size, shadow black 0.3 blur 20 down 10. The
// glyph: SF wallet.bifold at 45% of the size as a font; the Material
// outlined wallet (square, a margin in its 24 box) draws at 58%, so it covers
// about the same area; its shadow is brand 0.4 blur 12 down 4.
private const val CornerFraction = 0.24f
private val TileShadowSigma = 20.dp
private val TileShadowDrop = 10.dp
private const val TileShadowAlpha = 0.3f
private const val GlyphFraction = 0.58f
private val GlyphShadowSigma = 12.dp
private val GlyphShadowDrop = 4.dp
private const val GlyphShadowAlpha = 0.4f

/**
 * The hero of the Add Wallet screen, ported from iOS AnimatedWalletIcon: a
 * tile with the brand wallet glyph springs in, floats up and down over a soft
 * glow, and one shine sweeps across it. Sizes are the iOS points as dp. The
 * layout box is [size] wide and 1.06 x [size] tall (tile plus glow slot).
 *
 * With "Remove animations" on (the iOS Reduce Motion), the glyph shows at
 * once, at rest, without the float or the shine.
 */
@Composable
fun AnimatedWalletIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val shine = remember { Animatable(ShineFrom) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            launch {
                appear.animateTo(1f, spring(dampingRatio = EntranceDamping, stiffness = EntranceStiffness))
            }
            delay(ShineDelayMillis)
            shine.animateTo(ShineTo, tween(ShineMillis, easing = Motion.EaseInOut))
        }
    }
    // 0 = down (where it starts), 1 = up; 0.5 (level) with reduced motion.
    val float: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "walletFloat").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(FloatMillis, easing = Motion.EaseInOut),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(FloatDelayMillis)
            ),
            label = "walletFloat"
        )
    }

    val dark = MoneroTheme.isDark
    val brand = MoneroTheme.colors.brand
    val tileColor = MoneroTheme.colors.fill
    val shape = RoundedCornerShape(size * CornerFraction)
    val density = LocalDensity.current
    // Dark mode tints the tile from the top-left corner; light mode leaves it
    // plain (iOS: the tint turns muddy over the light material).
    val tint: Brush? = if (dark) {
        Brush.linearGradient(
            listOf(brand.copy(alpha = 0.25f), brand.copy(alpha = 0.08f), brand.copy(alpha = 0f))
        )
    } else {
        null
    }
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = 0.08f)
        )
    )
    val glyphBrush = Brush.verticalGradient(listOf(BrandHighlight, brand, BrandShade))

    // Animated values are read inside layer and draw blocks, so a frame only
    // redraws and never recomposes.
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
                    // Fades the tile and its shadow together, without an
                    // offscreen buffer that would cut the shadow off.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawBehind {
                    // The tile's shadow (API 28+ draws a shadow layer on shapes).
                    val r = this.size.width * CornerFraction
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = tileColor.toArgb()
                            setShadowLayer(
                                radiusForSigma(TileShadowSigma.toPx()),
                                0f,
                                TileShadowDrop.toPx(),
                                Color.Black.copy(alpha = TileShadowAlpha).toArgb()
                            )
                        }
                        canvas.nativeCanvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, r, r, paint)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(tileColor)
                    .then(if (tint != null) Modifier.background(tint) else Modifier)
                    .border(0.75.dp, edge, shape),
                contentAlignment = Alignment.Center
            ) {
                if (Build.VERSION.SDK_INT >= 31) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = brand.copy(alpha = GlyphShadowAlpha),
                        modifier = Modifier
                            .size(size * GlyphFraction)
                            .offset(y = GlyphShadowDrop)
                            .blur(with(density) { blurRadius(GlyphShadowSigma) }, BlurredEdgeTreatment.Unbounded)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(size * GlyphFraction)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(glyphBrush, blendMode = BlendMode.SrcIn)
                        }
                )
                if (!reduceMotion) {
                    ShineBand(
                        logoSize = size,
                        offset = { shine.value },
                        width = ShineWidth,
                        sigma = ShineSigma,
                        mid = ShineMid,
                        peak = ShinePeak
                    )
                }
            }
        }
        Box(
            modifier = Modifier.size(width = size * GlowWidth, height = size * GlowHeight),
            contentAlignment = Alignment.Center
        ) {
            GlowEllipse(
                width = size * GlowWidth,
                height = size * GlowHeight,
                color = if (dark) brand else Color.Black,
                sigma = GlowSigma
            ) {
                val up = float.value
                translationY = GlowDrop.toPx()
                scaleX = GlowScaleDown + (GlowScaleUp - GlowScaleDown) * up
                alpha = (GlowOpacityDown + (GlowOpacityUp - GlowOpacityDown) * up) *
                    appear.value.coerceIn(0f, 1f)
            }
        }
    }
}
