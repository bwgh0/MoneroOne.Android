package one.monero.moneroone.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import one.monero.moneroone.core.wallet.WalletViewModel
import java.util.UUID

/**
 * True while Android destroys the Activity only to recreate it for a
 * configuration change (dark mode, font or display size, language, split
 * screen, fold, rotation). The screen then composes again with its saved
 * state, so that disposal is not the user leaving the screen.
 */
fun Activity?.isRecreatingForConfigChange(): Boolean = this?.isChangingConfigurations == true

/**
 * Keeps auto-lock off while this add-wallet-flow screen is open (iOS parity).
 * The screen registers its key each time it composes (a recreated screen keeps
 * its key in saved state) and drops it only when its back stack entry leaves
 * for good: a recreation during a navigation transition disposes a screen that
 * never composes again, and a disposal-based drop then never ran.
 */
@Composable
fun AddWalletFlowEffect(walletViewModel: WalletViewModel) {
    val screenKey = rememberSaveable { UUID.randomUUID().toString() }
    LaunchedEffect(screenKey) { walletViewModel.setAddWalletFlowActive(true, screenKey) }
    OnNavEntryEnd("add-wallet-flow") { walletViewModel.setAddWalletFlowActive(false, screenKey) }
}
