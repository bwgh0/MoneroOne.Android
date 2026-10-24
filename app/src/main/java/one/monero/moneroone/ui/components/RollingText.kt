package one.monero.moneroone.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import one.monero.moneroone.ui.theme.TabularFigures
import java.math.BigDecimal
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Text whose changed characters roll when the value changes: the Compose
 * counterpart of iOS `.contentTransition(.numericText())` with
 * `.monospacedDigit()`, on the balance card and the Price tab.
 *
 * The look copies iOS as its frames show. Every character from the first
 * change to the last rolls down, whether the value rises or falls and
 * whatever each digit does: the new character comes in from above and the
 * old one leaves downward, and an unchanged '.' between them rolls in place.
 * The old character blurs, fades and shrinks as it leaves; the new one comes
 * in blurred, faint and small, lands on a spring with a small overshoot and
 * sharpens. Each character travels and blurs by its own height, so the '.'
 * only shifts a little. The rolls ripple from left to right, each place a
 * little after the one before it. Nothing clips them, so no hard edge shows.
 * [RollLook.PerDigit] instead rolls each digit up or down by its own change.
 *
 * While a finger scrubs a chart the value changes on almost every frame. A
 * place that is rolling rolls on to each new character on the next frame, so
 * the characters rolling out pile up into the soft, dark blur iOS shows; a
 * place whose roll has not begun yet shows its latest character at once, so
 * the trailing digits stay sharp.
 *
 * When the text grows or shrinks ("0.2500 XMR" to "250.2500 XMR"), the
 * characters the old and new text share at the start and at the end keep
 * their places, as on iOS: only the places between them roll, a new place
 * rolls in from nothing, and the shared end slides to its new x on the roll
 * spring.
 *
 * Each place draws on two layers whatever it shows: one for the characters
 * rolling out, under one blur, and one for the character in place or rolling
 * in. So a fast scrub costs no more to draw than a single roll.
 *
 * With [minScale] under 1 the text shrinks to fit its width, never below
 * that share of its size: the iOS `minimumScaleFactor`.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    /**
     * The iOS call site's animation duration: 0.1 s while a finger scrubs a
     * chart, 0.2 s for live changes. It sets the ripple from place to place;
     * each character rolls on the measured iOS spring whatever the duration.
     */
    durationMillis: Int = Motion.DIGIT_MS,
    minScale: Float = 1f,
    /**
     * iOS `.numericText(countsDown:)`, or `.numericText(value:)` with the
     * direction of the value's change. True: a falling count, the changed
     * characters roll down (the new one comes in from above, the old one
     * leaves downward). False: a rising count, they roll up (the new one
     * comes in from below). Null keeps the app's look ([LOOK]).
     */
    countsDown: Boolean? = null
) {
    val glyphStyle = style.copy(
        fontSize = if (fontSize.isSpecified) fontSize else style.fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        fontFeatureSettings = TabularFigures
    )
    if (minScale >= 1f) {
        RollingLine(text, glyphStyle, color, durationMillis, countsDown, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val scale = remember(text, glyphStyle, maxWidth, minScale) {
            val width = measurer.measure(text, glyphStyle, maxLines = 1, softWrap = false).size.width
            // Each character is laid out on its own, and each rounds up a pixel at most.
            val drawn = width + text.length
            if (maxWidth == Constraints.Infinity || drawn <= maxWidth) 1f else (maxWidth.toFloat() / drawn).coerceAtLeast(minScale)
        }
        val fitted = if (scale < 1f) glyphStyle.copy(fontSize = glyphStyle.fontSize * scale) else glyphStyle
        RollingLine(text, fitted, color, durationMillis, countsDown, Modifier)
    }
}

@Composable
private fun RollingLine(text: String, style: TextStyle, color: Color, durationMillis: Int, countsDown: Boolean?, modifier: Modifier) {
    val line = remember { RollLine() }
    line.show(text, durationMillis, countsDown)
    LaunchedEffect(line) { line.run() }

    val em = with(LocalDensity.current) { (if (style.fontSize.isSpecified) style.fontSize else 16.sp).toPx() }
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(resolver, style.fontFamily, style.fontWeight, style.fontStyle, style.fontSynthesis) {
        resolver.resolve(
            style.fontFamily,
            style.fontWeight ?: FontWeight.Normal,
            style.fontStyle ?: FontStyle.Normal,
            style.fontSynthesis ?: FontSynthesis.All
        )
    }.value as? Typeface
    val measurer = rememberTextMeasurer()
    // The color material3 Text would draw with.
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    line.use(measurer, style, typeface, em, textColor)

    Layout(
        content = {
            for (slot in line.slots) {
                key(slot) {
                    // Made once per place, so a new text leaves the places already shown alone.
                    Spacer(remember { line.trail(slot) })
                    Spacer(remember { line.front(slot) })
                }
            }
        },
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        measurePolicy = remember(line) { MeasurePolicy { measurables, _ -> line.measure(this, measurables) } }
    )
}

/**
 * The places of the text, one per character, and the frame clock that moves
 * them. It changes only in composition and on frames, both on the main thread.
 */
private class RollLine {
    val slots = ArrayList<RollSlot>()

    /** Where each character's ink sits, which sets how far it travels and blurs. */
    private val ink = GlyphInk()

    /** Each character's text layout and the line they share; null until the first composition. */
    private var shapes: GlyphShapes? = null
    private var textColor = Color.Unspecified

    /** Room around a place's layers, in px, so its rolls and blurs stay inside them. */
    private var padX = 0
    private var padY = 0

    private var shown: String? = null

    /** The time of the latest frame the clock ran on. */
    private var now = 0L

    /**
     * The animator duration scale, read when used, so it follows the system
     * setting: 0 while animations are off. Unknown (1) until the clock runs.
     */
    private var motion: MotionDurationScale? = null
    private val pace: Float get() = motion?.scaleFactor ?: 1f

    /** Every layer reads it, so each frame and each new text redraws them. */
    private val redraw = mutableIntStateOf(0)

    /** The layout reads it, so each new text or font lays the places out again. */
    private val relayout = mutableIntStateOf(0)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Takes the font, size and color of this composition. */
    fun use(measurer: TextMeasurer, style: TextStyle, typeface: Typeface?, em: Float, color: Color) {
        ink.use(typeface, em)
        if (shapes?.isFor(measurer, style) != true) {
            shapes = GlyphShapes(measurer, style)
            relayout.intValue++
            redraw.intValue++
        }
        val x = ceil(PAD_X_SHARE * em).toInt()
        val y = ceil(PAD_Y_SHARE * em).toInt()
        if (x != padX || y != padY) {
            padX = x
            padY = y
            relayout.intValue++
        }
        if (color != textColor) {
            textColor = color
            redraw.intValue++
        }
    }

    /** True from a rolled change until the layout that follows it: the places whose x changes slide there. */
    private var slides = false

    /**
     * Rolls each place whose character differs from the one shown. The
     * characters the old and new text share at the start and at the end keep
     * their places ([rollDiff]); the places between them roll, come in or go away.
     */
    fun show(text: String, durationMillis: Int, countsDown: Boolean? = null) {
        val old = shown
        if (old == text) return
        shown = text
        val rolls = old != null && pace > 0f && durationMillis > 0
        // The places that have rolled out go; the others hold the old text, one character each.
        slots.removeAll { it.isEmpty }
        val places = slots.filter { it.latest != null }
        val diff = rollDiff(old ?: "", text)
        val rose = if (old != null) rose(old, text) else null
        val changed = max(diff.oldEnd, diff.newEnd) - diff.start
        // As on iOS, the ripple spreads over the changed span, with a cap per place.
        val span = max(1, changed - 1)
        val step = minOf(STEP_SHARE * durationMillis, SPREAD_SHARE * durationMillis / span)
        // New places go in before the shared end.
        var insertAt = places.getOrNull(diff.oldEnd)?.let { slots.indexOf(it) } ?: slots.size
        for (k in 0 until changed) {
            val i = diff.start + k
            val slot = (if (i < diff.oldEnd) places.getOrNull(i) else null) ?: RollSlot().also { slots.add(insertAt++, it) }
            val want = if (i < diff.newEnd) text[i] else null
            val have = slot.latest
            // An unchanged character inside the changed span, such as the '.',
            // rolls in place once; while it rolls, a new value leaves it alone.
            if (have == want && !(rolls && LOOK.rollsWholeSpan && slot.isResting)) continue
            val glyph = want?.let { RollGlyph(it) }
            if (rolls) slot.change(glyph, rollsUp(countsDown, have, want, rose), k * step) else slot.rest(glyph)
        }
        slots.removeAll { it.isEmpty }
        slides = rolls
        relayout.intValue++
        redraw.intValue++
        if (rolls) wake.trySend(Unit)
    }

    /** Runs the clock while anything moves, and sleeps between changes. */
    suspend fun run() {
        motion = coroutineContext[MotionDurationScale]
        while (true) {
            wake.receive()
            do {
                val moving = withFrameMillis { time ->
                    now = time
                    val pace = pace
                    var moving = false
                    for (slot in slots) {
                        if (slot.tick(time, pace)) moving = true
                    }
                    redraw.intValue++
                    moving
                }
            } while (moving)
        }
    }

    /**
     * Lays the places out on one baseline, each at the x of the characters
     * before it. A place that goes away keeps its last x while its character
     * rolls out. Both layers of a place cover it with room to spare.
     */
    fun measure(scope: MeasureScope, measurables: List<Measurable>): MeasureResult = with(scope) {
        relayout.intValue
        val shapes = shapes ?: return layout(0, 0) {}
        // Every character that may draw is laid out, so the line and the layers hold it.
        for (slot in slots) slot.forEachGlyph { shapes.layout(it.char) }
        var width = 0
        for (slot in slots) {
            val latest = slot.latest ?: continue
            slot.moveTo(width, slides, now)
            width += shapes.layout(latest).size.width
        }
        slides = false
        val room = Constraints.fixed(shapes.maxWidth + 2 * padX, shapes.height + 2 * padY)
        val placeables = measurables.map { it.measure(room) }
        val baseline = shapes.ascent
        layout(width, shapes.height, mapOf(FirstBaseline to baseline, LastBaseline to baseline)) {
            placeables.forEachIndexed { i, placeable ->
                val slot = measurables[i].layoutId as RollSlot
                placeable.place(slot.x - padX, -padY)
            }
        }
    }

    /** The layer of [slot] that shows its character in place or rolling in, blurred by its opacity. */
    fun front(slot: RollSlot): Modifier = Modifier
        .layoutId(slot)
        .graphicsLayer {
            redraw.intValue
            translationX = slot.slide(now)
            renderEffect = slot.shownGlyph?.let { blur(sigma(it, it.opacity(now))) }
        }
        .drawBehind {
            redraw.intValue
            slot.shownGlyph?.let { draw(it) }
        }

    /**
     * The layer of [slot] that shows the characters rolling out of it. They
     * share one blur, weighted by their opacities: while a finger scrubs, they
     * are all faint and about equally blurred.
     */
    fun trail(slot: RollSlot): Modifier = Modifier
        .layoutId(slot)
        .graphicsLayer {
            redraw.intValue
            translationX = slot.slide(now)
            var weight = 0f
            var sum = 0f
            for (glyph in slot.leavingGlyphs) {
                val alpha = glyph.opacity(now)
                weight += alpha
                sum += alpha * sigma(glyph, alpha)
            }
            renderEffect = if (weight > 0f) blur(sum / weight) else null
        }
        .drawBehind {
            redraw.intValue
            for (glyph in slot.leavingGlyphs) draw(glyph)
        }

    /** The Gaussian sigma of a character at [alpha], in px. */
    private fun sigma(glyph: RollGlyph, alpha: Float): Float =
        BLUR_SHARE * (ink.bottom(glyph.char) - ink.top(glyph.char)) * (1f - alpha).pow(BLUR_POWER)

    /**
     * Draws one character as it is on this frame. As on iOS, each character
     * travels, shrinks and blurs by its own ink height, about its own ink
     * centre, so a '.' only shifts a little while a digit rolls a long way.
     */
    private fun DrawScope.draw(glyph: RollGlyph) {
        val alpha = glyph.opacity(now)
        if (alpha <= 0f) return
        val shapes = shapes ?: return
        val layout = shapes.layout(glyph.char)
        val top = ink.top(glyph.char)
        val bottom = ink.bottom(glyph.char)
        val baseline = (padY + shapes.ascent).toFloat()
        val size = 1f - (1f - LOOK.endScale) * (1f - alpha)
        val pivot = Offset(padX + layout.size.width / 2f, baseline + (top + bottom) / 2f)
        withTransform({
            translate(0f, glyph.offset(now) * (bottom - top))
            scale(size, size, pivot)
        }) {
            drawText(layout, textColor, Offset(padX.toFloat(), baseline - layout.firstBaseline.roundToInt()), alpha)
        }
    }
}

/** A blur of Gaussian [sigma] px; null when too small to show or before Android 12. */
private fun blur(sigma: Float): RenderEffect? {
    // Android turns a blur radius r into a Gaussian sigma of 0.57735 r + 0.5 px.
    val radius = (sigma - 0.5f) / 0.57735f
    return if (radius >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BlurEffect(radius, radius, TileMode.Decal)
    } else {
        null
    }
}

/**
 * One place in the text: the character that shows or rolls in, the ones
 * rolling out, and the next roll while it waits for its turn in the ripple.
 */
private class RollSlot {
    /** Its x in the line, kept while its place goes away. */
    var x = 0
        private set
    private var placed = false

    /** While it slides to [x]: its offset from [x] (px) and velocity when the slide began, on the roll spring. */
    private var sliding = false
    private var slideFrom = 0f
    private var slideVelocity = 0f
    private var slideSince = UNSCHEDULED
    private var slidePace = 1f

    /**
     * Puts the place at [newX]. With [slides] (a rolled change), a place
     * already shown moves there from where it shows now, as the shared end of
     * a text that grows or shrinks does on iOS; else it is there at once.
     */
    fun moveTo(newX: Int, slides: Boolean, now: Long) {
        if (placed && newX == x) return
        if (placed && slides) {
            val shownAt = x + slide(now)
            slideVelocity = if (sliding && slideSince != UNSCHEDULED) springVelocity(slideFrom, slideVelocity, 0f, slideAge(now)) else 0f
            slideFrom = shownAt - newX
            slideSince = UNSCHEDULED
            sliding = true
        } else {
            sliding = false
        }
        x = newX
        placed = true
    }

    /** Its offset from [x] on this frame, in px: 0 unless it slides. */
    fun slide(now: Long): Float = when {
        !sliding -> 0f
        slideSince == UNSCHEDULED -> slideFrom
        else -> spring(slideFrom, slideVelocity, 0f, slideAge(now))
    }

    private fun slideAge(now: Long): Float = ((now - slideSince).coerceAtLeast(0L) / 1000f) / slidePace

    /** Starts a slide on its first frame, and ends it once it has settled. */
    private fun tickSlide(time: Long, pace: Float) {
        if (!sliding) return
        if (slideSince == UNSCHEDULED) {
            slideSince = time
            slidePace = pace
            return
        }
        val t = slideAge(time)
        if (t * 1000f >= FADE_MS &&
            abs(spring(slideFrom, slideVelocity, 0f, t)) < SLIDE_SETTLED_PX &&
            abs(springVelocity(slideFrom, slideVelocity, 0f, t)) < SLIDE_SETTLED_PX * 10f
        ) sliding = false
    }

    /** The character in its place or rolling into it; null while the place goes away. */
    private var incoming: RollGlyph? = null

    /** Characters rolling out, oldest first. */
    private val leaving = ArrayList<RollGlyph>(MAX_LEAVING + 1)

    /** The next roll: its character (null when the place goes away), direction and delay. */
    private var waiting = false
    private var next: RollGlyph? = null
    private var nextUp = false
    private var nextDelay = 0f
    private var nextAt = UNSCHEDULED

    /** When the place last began a roll. */
    private var lastBegin = UNSCHEDULED

    /** The character the text has in this place, rolled in or not. */
    private val latestGlyph: RollGlyph? get() = if (waiting) next else incoming
    val latest: Char? get() = latestGlyph?.char
    val isEmpty: Boolean get() = !waiting && incoming == null && leaving.isEmpty()

    /** The character in place or rolling in. */
    val shownGlyph: RollGlyph? get() = incoming

    /** The characters rolling out, oldest first. */
    val leavingGlyphs: List<RollGlyph> get() = leaving

    /** True while nothing rolls or waits to roll here. */
    val isResting: Boolean get() = !waiting && !rolling

    fun forEachGlyph(action: (RollGlyph) -> Unit) {
        for (glyph in leaving) action(glyph)
        incoming?.let(action)
        if (waiting) next?.let(action)
    }

    /** Shows [glyph] in its place at once, with nothing rolling. */
    fun rest(glyph: RollGlyph?) {
        for (g in leaving) g.gone = true
        leaving.clear()
        incoming?.takeIf { it !== glyph }?.gone = true
        next?.takeIf { it !== glyph }?.gone = true
        waiting = false
        next = null
        incoming = glyph?.also { it.rest() }
    }

    /** True while a character rolls in or out of this place. */
    private val rolling: Boolean get() = leaving.isNotEmpty() || incoming?.moving == true

    /**
     * Rolls to [glyph] (null: the place goes away), upward when [up]. A place
     * at rest waits [delay] ms for its turn in the ripple; while it waits, a
     * newer character shows the one it waited with at once, so while a finger
     * scrubs the trailing digits show each new value sharp. A place that is
     * rolling rolls on to the new character on the next frame, so it stays a
     * soft blur, as on iOS.
     */
    fun change(glyph: RollGlyph?, up: Boolean, delay: Float) {
        if (waiting && rolling) {
            next?.gone = true
            next = glyph
            nextUp = up
            return
        }
        if (waiting) rest(next)
        nextDelay = if (rolling) 0f else delay
        waiting = true
        next = glyph
        nextUp = up
        nextAt = UNSCHEDULED
    }

    /** Moves the place on to [time]; returns true while anything in it moves or waits. */
    fun tick(time: Long, pace: Float): Boolean {
        if (pace <= 0f) {
            if (waiting) rest(next) else rest(incoming)
            sliding = false
            return false
        }
        tickSlide(time, pace)
        if (waiting) {
            if (nextAt == UNSCHEDULED) nextAt = time + (nextDelay * pace).toLong()
            if (time >= nextAt && (lastBegin == UNSCHEDULED || time - lastBegin >= MIN_ROLL_MS * pace)) begin(time, pace)
        }
        leaving.removeAll { g -> g.faded(time).also { if (it) g.gone = true } }
        incoming?.let { if (it.landed(time)) it.rest() }
        return waiting || sliding || leaving.isNotEmpty() || incoming?.moving == true
    }

    /** The shown character rolls out from where it is now, and the next one rolls in. */
    private fun begin(time: Long, pace: Float) {
        // Screen y grows downward: an upward roll moves toward negative y.
        val down = if (nextUp) -1f else 1f
        incoming?.let {
            it.leave(time, pace, down * OUT_SHARE)
            leaving += it
        }
        while (leaving.size > MAX_LEAVING) leaving.removeAt(0).gone = true
        incoming = next?.also { it.enter(time, pace, -down * IN_SHARE) }
        next = null
        waiting = false
        lastBegin = time
    }
}

/**
 * One drawn character. Its offset (in shares of its ink height, positive downward) follows the
 * iOS spring from where its current motion began; its opacity follows a
 * cubic ease in or out, and its blur and size follow its opacity.
 */
private class RollGlyph(val char: Char) {
    /** True once its place has dropped it; it draws nothing. */
    var gone = false

    private var state = State.Waiting
    private var leaving = false
    private var since = 0L
    private var pace = 1f
    private var from = 0f
    private var velocity0 = 0f
    private var target = 0f
    private var alpha0 = 0f

    val moving: Boolean get() = state == State.Moving

    fun rest() {
        state = State.Resting
        leaving = false
    }

    fun enter(time: Long, pace: Float, offset: Float) {
        state = State.Moving
        leaving = false
        since = time
        this.pace = pace
        from = offset
        velocity0 = 0f
        target = 0f
        alpha0 = 0f
    }

    fun leave(time: Long, pace: Float, offset: Float) {
        val x = offset(time)
        val v = velocity(time)
        val a = opacity(time)
        state = State.Moving
        leaving = true
        since = time
        this.pace = pace
        from = x
        velocity0 = v
        target = offset
        alpha0 = a
    }

    /** Seconds since its current motion began, on the animator's clock. */
    private fun age(now: Long): Float = ((now - since).coerceAtLeast(0L) / 1000f) / pace

    fun offset(now: Long): Float = when (state) {
        State.Waiting -> from
        State.Resting -> 0f
        State.Moving -> spring(from, velocity0, target, age(now))
    }

    private fun velocity(now: Long): Float =
        if (state == State.Moving) springVelocity(from, velocity0, target, age(now)) else 0f

    fun opacity(now: Long): Float {
        if (gone) return 0f
        return when (state) {
            State.Waiting -> 0f
            State.Resting -> 1f
            State.Moving -> {
                val rest = (1f - age(now) * 1000f / FADE_MS).coerceIn(0f, 1f)
                val out = rest * rest * rest
                if (leaving) alpha0 * out else 1f - (1f - alpha0) * out
            }
        }
    }

    /** True once a leaving character has faded out, or so far that it no longer shows. */
    fun faded(now: Long): Boolean = leaving && (age(now) * 1000f >= FADE_MS || opacity(now) < UNSEEN_ALPHA)

    /** True once an entering character has faded in and its spring has settled. */
    fun landed(now: Long): Boolean {
        if (state != State.Moving || leaving) return false
        val t = age(now)
        return t * 1000f >= FADE_MS && abs(offset(now)) < SETTLED_SHARE && abs(velocity(now)) < SETTLED_SHARE * 10f
    }

    private enum class State { Waiting, Moving, Resting }
}

/**
 * How the roll looks. [IosExact] is the one the app uses: iOS 1.0.9 as its
 * frames show, where every character from the first change to the last rolls
 * down, whatever the digits do, and shrinks to 0.38 of its size as it fades.
 * [PerDigit] rolls each changed digit on its own, up when it gets bigger and
 * down when it gets smaller, with a mild scale.
 */
private enum class RollLook(
    /** True when unchanged characters inside the changed span roll too, as the '.' does on iOS. */
    val rollsWholeSpan: Boolean,
    /** A character's size when it has faded out, as a share of its size in place. */
    val endScale: Float
) {
    PerDigit(rollsWholeSpan = false, endScale = 0.85f),
    IosExact(rollsWholeSpan = true, endScale = 0.38f);

    /** True when the place rolls up: the old character leaves upward and the new one comes in from below. */
    fun up(old: Char?, new: Char?, rose: Boolean?): Boolean = when (this) {
        IosExact -> false
        PerDigit -> if (old != null && new != null && old.isDigit() && new.isDigit()) new > old else rose == true
    }
}

/** The look in use; [RollLook.PerDigit] is the alternative. */
private val LOOK = RollLook.IosExact

/**
 * True when a place rolls up: the old character leaves upward and the new
 * one comes in from below. [countsDown] true (a falling count) rolls down,
 * false (a rising count) rolls up, as iOS `.numericText(countsDown:)`; null
 * keeps the look in use.
 */
internal fun rollsUp(countsDown: Boolean?, old: Char?, new: Char?, rose: Boolean?): Boolean =
    if (countsDown != null) !countsDown else LOOK.up(old, new, rose)

/**
 * The places that change from one text to the next: [start] until [oldEnd]
 * in the old text become [start] until [newEnd] in the new one. The
 * characters before [start] and after the ends are the same in both, so
 * they keep their places.
 */
internal data class RollDiff(val start: Int, val oldEnd: Int, val newEnd: Int)

/** The shared start of [old] and [new], then the longest shared end that does not overlap it. */
internal fun rollDiff(old: String, new: String): RollDiff {
    var start = 0
    while (start < old.length && start < new.length && old[start] == new[start]) start++
    var end = 0
    while (end < old.length - start && end < new.length - start &&
        old[old.length - 1 - end] == new[new.length - 1 - end]
    ) end++
    return RollDiff(start, old.length - end, new.length - end)
}

/** True when the number in [new] is bigger than the one in [old]; null when equal or not a number. */
private fun rose(old: String, new: String): Boolean? {
    val a = numberIn(old) ?: return null
    val b = numberIn(new) ?: return null
    val order = a.compareTo(b)
    return if (order == 0) null else order < 0
}

/**
 * The number in a formatted amount such as "$1,234.56", "1.234,56 €" or
 * "0.137996314949 XMR". The last '.' or ',' is the decimal mark when both
 * kinds show, or when it shows once and is not followed by exactly three digits.
 */
private fun numberIn(text: String): BigDecimal? {
    val start = text.indexOfFirst { it.isDigit() }
    if (start < 0) return null
    val end = text.indexOfLast { it.isDigit() }
    val body = text.substring(start, end + 1)
    val mark = body.indexOfLast { it == '.' || it == ',' }
    val decimal = mark >= 0 && (
        (body.contains('.') && body.contains(',')) ||
            (body.count { it == body[mark] } == 1 && body.length - mark - 1 != 3)
        )
    val whole = StringBuilder()
    val fraction = StringBuilder()
    body.forEachIndexed { i, c ->
        val d = Character.digit(c, 10)
        if (d >= 0) (if (decimal && i > mark) fraction else whole).append(('0' + d))
    }
    val negative = text.substring(0, start).any { it == '-' || it == '−' }
    val number = BigDecimal(if (fraction.isEmpty()) whole.toString() else "$whole.$fraction")
    return if (negative) number.negate() else number
}

/** Angular frequency and damping of the iOS roll spring: response 0.425 s, damping 0.56. */
private val SPRING_W0 = (2.0 * PI / 0.425).toFloat()
private const val DAMPING = 0.56f
private val SPRING_WD = SPRING_W0 * sqrt(1f - DAMPING * DAMPING)

/** Offset at [t] seconds of a damped spring that starts at [x0] with velocity [v0] and heads for [target]. */
private fun spring(x0: Float, v0: Float, target: Float, t: Float): Float {
    val a = x0 - target
    val b = (v0 + DAMPING * SPRING_W0 * a) / SPRING_WD
    return target + exp(-DAMPING * SPRING_W0 * t) * (a * cos(SPRING_WD * t) + b * sin(SPRING_WD * t))
}

private fun springVelocity(x0: Float, v0: Float, target: Float, t: Float): Float {
    val a = x0 - target
    val b = (v0 + DAMPING * SPRING_W0 * a) / SPRING_WD
    val c = cos(SPRING_WD * t)
    val s = sin(SPRING_WD * t)
    val decay = exp(-DAMPING * SPRING_W0 * t)
    return decay * ((SPRING_WD * b - DAMPING * SPRING_W0 * a) * c - (SPRING_WD * a + DAMPING * SPRING_W0 * b) * s)
}

private const val UNSCHEDULED = Long.MIN_VALUE

/**
 * How far from its place a new character starts, as a share of its ink
 * height (iOS: 0.59, which is 0.42 em for a digit).
 */
private const val IN_SHARE = 0.59f

/** How far an old character travels as it leaves, as a share of its ink height (iOS: 0.67, 0.48 em for a digit). */
private const val OUT_SHARE = 0.67f

/** How long a character takes to fade in or out, in ms, on a cubic ease (iOS: 380). */
private const val FADE_MS = 380f

/**
 * A faded-out character's blur (Gaussian sigma) as a share of its ink height;
 * it follows (1 - opacity)^0.6 (iOS: 0.118, which is 0.084 em for a digit).
 */
private const val BLUR_SHARE = 0.118f
private const val BLUR_POWER = 0.6f

/**
 * The ripple from place to place, as shares of the call site's duration:
 * each place waits at most [STEP_SHARE] of it after the one before, and the
 * whole changed span at most [SPREAD_SHARE] of it. With iOS's 0.1 s that is
 * the 60 ms and 155 ms its frames show.
 */
private const val STEP_SHARE = 0.6f
private const val SPREAD_SHARE = 1.55f

/**
 * A place begins a new roll at most this often, in ms. While a finger scrubs
 * faster, the waiting roll takes each new character, so each roll gets far
 * enough to show before the next one: the dense, soft stack of half-rolled
 * characters iOS shows, not a faint smear.
 */
private const val MIN_ROLL_MS = 100f

/** At most this many old characters roll out of one place at a time: all of them, at one roll per [MIN_ROLL_MS]. */
private const val MAX_LEAVING = 8

/** A leaving character this faint no longer shows, so its place drops it. */
private const val UNSEEN_ALPHA = 0.02f

/**
 * The room around a place's layers, as shares of the font size: enough for
 * the widest blur at the sides, and for the longest roll and its blur above
 * and below.
 */
private const val PAD_X_SHARE = 0.35f
private const val PAD_Y_SHARE = 1.2f

/** An entering character has landed once it is this close to its place, as a share of its ink height. */
private const val SETTLED_SHARE = 0.003f

/** A slide has settled when it is this close to its x (px) and slower than ten times that per second. */
private const val SLIDE_SETTLED_PX = 0.5f

/**
 * The ink of each character for one typeface and size: its top and bottom in
 * px from the baseline, downward positive. Read on the main thread only.
 */
private class GlyphInk {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()
    private val tops = HashMap<Char, Float>()
    private val bottoms = HashMap<Char, Float>()
    private var typeface: Typeface? = null
    private var size = 0f

    fun use(typeface: Typeface?, size: Float) {
        if (typeface == this.typeface && size == this.size) return
        this.typeface = typeface
        this.size = size
        paint.typeface = typeface ?: Typeface.DEFAULT
        paint.textSize = size
        tops.clear()
        bottoms.clear()
    }

    fun top(char: Char): Float = tops[char] ?: measure(char).let { tops.getValue(char) }

    fun bottom(char: Char): Float = bottoms[char] ?: measure(char).let { bottoms.getValue(char) }

    private fun measure(char: Char) {
        paint.getTextBounds(char.toString(), 0, 1, bounds)
        // A space has no ink: it neither travels nor blurs.
        val empty = bounds.isEmpty
        tops[char] = if (empty) 0f else bounds.top.toFloat()
        bottoms[char] = if (empty) 0f else bounds.bottom.toFloat()
    }
}

/**
 * The text layout of each character for one style, and the line they share:
 * the ascent and height that hold every character laid out so far, and the
 * widest one. Read on the main thread only.
 */
private class GlyphShapes(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val layouts = HashMap<Char, TextLayoutResult>()
    var maxWidth = 0
        private set
    var ascent = 0
        private set
    private var descent = 0
    val height: Int get() = ascent + descent

    fun isFor(measurer: TextMeasurer, style: TextStyle): Boolean = measurer === this.measurer && style == this.style

    fun layout(char: Char): TextLayoutResult = layouts.getOrPut(char) {
        measurer.measure(char.toString(), style, maxLines = 1, softWrap = false).also {
            val baseline = it.firstBaseline.roundToInt()
            ascent = max(ascent, baseline)
            descent = max(descent, it.size.height - baseline)
            maxWidth = max(maxWidth, it.size.width)
        }
    }
}
