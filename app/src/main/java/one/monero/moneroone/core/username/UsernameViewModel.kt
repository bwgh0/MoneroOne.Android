package one.monero.moneroone.core.username

import one.monero.moneroone.core.locale.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletViewModel

enum class UsernameStage { WELCOME, CHOOSE, CONFIRM, CLAIMING, REVEALED, NO_FREE_CLAIM }
data class UsernameFlowState(val stage: UsernameStage = UsernameStage.WELCOME, val input: String = "",
                             val error: String? = null, val binding: UsernameBinding? = null)

class UsernameViewModel(application: Application) : AndroidViewModel(application) {
    val store = UsernameStore.get(application)
    private val mutable = MutableStateFlow(UsernameFlowState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    fun open(wallet: WalletInfo) {
        generation++
        val pending = store.state.value.pending?.takeIf { it.walletKey == wallet.cachedPrimaryAddress && it.network == "mainnet" }
        mutable.value = if (pending != null) UsernameFlowState(UsernameStage.CONFIRM, pending.displayHandle) else UsernameFlowState()
    }
    fun choose() { mutable.value = mutable.value.copy(stage = UsernameStage.CHOOSE, error = null) }
    fun input(value: String) { mutable.value = mutable.value.copy(input = value.take(15), error = null) }
    fun confirm() { mutable.value = mutable.value.copy(stage = UsernameStage.CONFIRM, error = null) }
    fun claim(wallet: WalletInfo, session: Long, wallets: WalletViewModel) {
        if (mutable.value.stage != UsernameStage.CONFIRM) return
        val config = UsernameConfiguration.current ?: return
        val input = mutable.value.input
        val key = wallet.cachedPrimaryAddress?.takeIf { it.isNotEmpty() } ?: return
        val attempt = generation
        mutable.value = mutable.value.copy(stage = UsernameStage.CLAIMING, error = null)
        viewModelScope.launch {
            try {
                val binding = UsernameRegistry(config, getApplication()).claim(input, key, "mainnet", store,
                    makeSubaddress = { wallets.createUsernameSubaddress(session) },
                    sign = { message, address -> wallets.signUsername(message, address, session) },
                    claimKey = { wallets.usernameClaimKey(session) },
                    ownsSubaddress = { address -> wallets.usernameOwnsSubaddress(address, session) },
                    isCurrentWallet = { !wallets.isLocked.value && wallets.walletSessionId.value == session &&
                        wallets.activeWallet.value?.cachedPrimaryAddress == key })
                if (generation == attempt) mutable.value = mutable.value.copy(stage = UsernameStage.REVEALED, binding = binding)
            } catch (e: Exception) {
                if (generation == attempt) mutable.value = mutable.value.copy(
                    stage = if ((e as? UsernameException)?.code == "device.slot_used") UsernameStage.NO_FREE_CLAIM else UsernameStage.CONFIRM,
                    error = if (e is UsernameException) e.message else tr("The claim could not be completed. Please try again."))
            }
        }
    }
}
