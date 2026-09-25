package one.monero.moneroone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Holds an action to run when its NavBackStackEntry leaves the back stack for
 * good. The entry's ViewModelStore is cleared then, and never for an Activity
 * recreation. Disposal is not that signal: a recreation during a navigation
 * transition disposes an entry that then leaves the back stack without
 * composing again, so its cleanup never ran.
 */
class NavEntryEnd : ViewModel() {
    var action: (() -> Unit)? = null

    override fun onCleared() {
        action?.invoke()
        action = null
    }
}

/**
 * Runs [onEnd] once, when the NavBackStackEntry of the calling screen leaves
 * the back stack for good. [key] tells apart several of these in one screen.
 * [onEnd] must not capture the composition: it can run after the Activity is gone.
 */
@Composable
fun OnNavEntryEnd(key: String, onEnd: () -> Unit) {
    val holder: NavEntryEnd = viewModel(key = "nav-entry-end:$key")
    SideEffect { holder.action = onEnd }
}
