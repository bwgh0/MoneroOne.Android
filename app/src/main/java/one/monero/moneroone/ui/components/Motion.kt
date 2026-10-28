package one.monero.moneroone.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens shared with iOS (tokens.json motion.springs):
 * `.snappy(duration: 0.3)` for the wallet-row reorder and ring,
 * `.snappy(duration: 0.35)` for slide-swaps such as the wallet switcher,
 * `easeInOut 0.2` for rolling digits.
 */
object Motion {
    /** iOS `.snappy(duration: 0.3)`: settles in roughly a third of a second with a soft overshoot. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 438.6f)

    /** iOS `.snappy(duration: 0.35)` (tokens.json slideSwap): same feel, a touch slower, for larger content swaps. */
    fun <T> snappySlow(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 322.3f)

    /** tokens.json stepForward, iOS `.spring(response: 0.3, dampingFraction: 0.85)`: a step into a flow. */
    fun <T> stepForward(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 438.6f)

    /** tokens.json stepBack, iOS `.spring(response: 0.5, dampingFraction: 0.8)`: the way back, a little slower. */
    fun <T> stepBack(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 157.9f)

    /** tokens.json settle, iOS `.spring(response: 0.4, dampingFraction: 0.8)`: a layout that moves into its new place. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 246.7f)

    /** tokens.json celebrate, iOS `.spring(response: 0.5, dampingFraction: 0.7)`: a success. */
    fun <T> celebrate(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 157.9f)

    /** Rolling digits and other small value changes. */
    const val DIGIT_MS = 200

    /** iOS `.easeInOut`, the curve of rolling digits. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** iOS `.easeIn`. */
    val EaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    /** iOS `.easeOut`, the curve of arrive. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** tokens.json disclose, iOS `.spring(response: 0.3, dampingFraction: 0.8)`: a row that opens. */
    fun <T> disclose(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 438.6f)

    /** tokens.json crossFade, iOS `.easeInOut` 0.35 s. */
    fun <T> crossFade(): TweenSpec<T> = tween(CROSS_FADE_MS, easing = EaseInOut)

    const val CROSS_FADE_MS = 350

    /** tokens.json arrive: fade and a 20 dp rise, easeOut 0.4 s after 0.1 s. */
    fun <T> arrive(): TweenSpec<T> = tween(400, delayMillis = 100, easing = EaseOut)

    /** The rise of arrive, in dp. */
    const val ARRIVE_RISE_DP = 20f

    /** tokens.json draw: a stroke that draws itself, easeInOut 0.4 s. */
    const val DRAW_MS = 400

    /** tokens.json checkStroke: the short leg runs linear to the corner, the long leg eases out. */
    const val CHECK_SHORT_MS = 90
    const val CHECK_LONG_MS = 170
}

/** Settings > Accessibility > Remove animations: the iOS Reduce Motion. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** tokens.json haptics.success: CONFIRM (API 30+), else two VIRTUAL_KEY taps 120 ms apart. */
fun successHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }, 120)
    }
}

/** tokens.json haptics.softTick: KEYBOARD_TAP. */
fun softTickHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
