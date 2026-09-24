package one.monero.moneroone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A PIN pad key that acts when the finger lands, as the iOS passcode pad does,
 * not when it lifts: no wait for the release, no digit lost when the finger
 * slides off the key while pressing, and fast two-thumb entry keeps the order
 * the fingers came down in (they can lift in the other order).
 *
 * [content] draws the key and gets the click handler to give it: the key's own
 * clickable keeps drawing the press and serves TalkBack and keyboard
 * activation, and a touch that already acted on its down is not delivered again
 * when it lifts. The wrapper adds no layout of its own.
 */
@Composable
fun KeypadKey(onPress: () -> Unit, content: @Composable (onClick: () -> Unit) -> Unit) {
    val current = rememberUpdatedState(onPress)
    val key = remember { KeypadKeyState(current) }
    Box(modifier = key.modifier) {
        content(key.onClick)
    }
}

private class KeypadKeyState(private val onPress: State<() -> Unit>) {

    private var actedOnTouch = false

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Initial pass: ahead of the key's clickable, without consuming the
                // down, so its press state and ripple still run.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                    if (change.changedToDownIgnoreConsumed()) {
                        actedOnTouch = true
                        onPress.value()
                    }
                }
                // Final pass of the same event: the clickable has handled it (a
                // lift's click runs in the Main pass), so once every finger is up
                // the click path is live again for TalkBack and keyboards.
                val handled = awaitPointerEvent(PointerEventPass.Final)
                if (handled.changes.none { it.pressed }) actedOnTouch = false
            }
        }
    }

    val onClick: () -> Unit = {
        if (actedOnTouch) actedOnTouch = false else onPress.value()
    }
}
