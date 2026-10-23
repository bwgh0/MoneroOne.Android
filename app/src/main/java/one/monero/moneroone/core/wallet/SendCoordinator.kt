package one.monero.moneroone.core.wallet

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A confirmation belongs to the wallet and screen that collected it. */
data class SendFlow(val walletId: String?, val id: String = UUID.randomUUID().toString())

sealed class SendState(open val flow: SendFlow? = null) {
    object Idle : SendState()
    data class Sending(override val flow: SendFlow) : SendState(flow)
    data class Success(override val flow: SendFlow, val txHash: String) : SendState(flow)
    data class Error(override val flow: SendFlow, val message: String) : SendState(flow)
}

/** UI dismissal never releases the broadcast gate or transfers a result to another screen. */
class SendCoordinator {
    class Operation internal constructor(val flow: SendFlow, val sequence: Long)

    private val mutableState = MutableStateFlow<SendState>(SendState.Idle)
    val state = mutableState.asStateFlow()
    private var inFlight: Operation? = null
    private var sequence = 0L

    @Synchronized
    fun begin(flow: SendFlow, activeWalletId: String?, validAmount: Boolean): Operation? {
        if (inFlight?.flow == flow) return null
        val error = when {
            flow.walletId == null || flow.walletId != activeWalletId ->
                "The wallet changed. Close this payment and start again."
            inFlight != null -> "A transaction is still in progress. Wait for it to finish before sending again."
            !validAmount -> "Invalid amount"
            else -> null
        }
        if (error != null) {
            mutableState.value = SendState.Error(flow, error)
            return null
        }
        return Operation(flow, ++sequence).also {
            inFlight = it
            mutableState.value = SendState.Sending(flow)
        }
    }

    /** [txHash]: the first txid of the broadcast transaction; the Sent screen shows it. */
    @Synchronized
    fun finish(operation: Operation, error: String? = null, txHash: String = "") {
        if (inFlight != operation) return
        inFlight = null
        if (mutableState.value is SendState.Sending && mutableState.value.flow == operation.flow) {
            mutableState.value = if (error == null) SendState.Success(operation.flow, txHash)
            else SendState.Error(operation.flow, error)
        }
    }

    @Synchronized
    fun dismiss(flow: SendFlow) {
        if (mutableState.value.flow == flow) mutableState.value = SendState.Idle
    }
}
