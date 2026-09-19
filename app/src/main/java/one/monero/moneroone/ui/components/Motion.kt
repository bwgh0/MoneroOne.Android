package one.monero.moneroone.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Motion tokens shared with iOS (see the MoneroOne design notes):
 * `.snappy(duration: 0.35)` / `.snappy(duration: 0.4)` for slide-swaps such
 * as the wallet switcher, `easeInOut 0.2` for rolling digits.
 */
object Motion {
    /** iOS `.snappy(duration: 0.35)`: settles in roughly a third of a second with a soft overshoot. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)

    /** iOS `.snappy(duration: 0.4)`: same feel, a touch slower, for larger content swaps. */
    fun <T> snappySlow(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 320f)

    /** Rolling digits and other small value changes. */
    const val DIGIT_MS = 200
}
