package one.monero.moneroone.ui.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * The screen key lives in saved state: the screen that the Activity recreates
 * takes the same place again, so the flow never looks closed in between.
 */
@Composable
fun AddWalletFlowEffect(walletViewModel: WalletViewModel) {
    val screenKey = rememberSaveable { UUID.randomUUID().toString() }
    val activity = LocalActivity.current
    DisposableEffect(screenKey) {
        walletViewModel.setAddWalletFlowActive(true, screenKey)
        onDispose {
            if (!activity.isRecreatingForConfigChange()) {
                walletViewModel.setAddWalletFlowActive(false, screenKey)
            }
        }
    }
}
