package one.monero.moneroone.core.wallet

import android.content.Context
import io.horizontalsystems.monerokit.Balance
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.model.NetworkType
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Singleton that owns the MoneroKit instance, allowing both WalletViewModel
 * and WalletSyncService to share the same wallet connection.
 */
object WalletManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var kit: MoneroKit? = null
        private set

    private val _syncStateFlow = MutableStateFlow<SyncState>(
        SyncState.NotSynced(MoneroKit.SyncError.NotStarted)
    )
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow.asStateFlow()

    private val _balanceFlow = MutableStateFlow(Balance(0, 0))
    val balanceFlow: StateFlow<Balance> = _balanceFlow.asStateFlow()

    private val _transactionsFlow = MutableStateFlow<List<TransactionInfo>>(emptyList())
    val transactionsFlow: StateFlow<List<TransactionInfo>> = _transactionsFlow.asStateFlow()

    /**
     * Create a new MoneroKit instance and wire up flow observers.
     * Any previously held kit is stopped first.
     */
    suspend fun initialize(
        context: Context,
        seed: Seed,
        restoreDateOrHeight: String,
        walletId: String,
        node: String,
        trustNode: Boolean,
        networkType: NetworkType = NetworkType.NetworkType_Mainnet
    ): MoneroKit {
        // Stop any existing kit
        kit?.let {
            try { it.stop() } catch (e: Exception) {
                Timber.w(e, "Error stopping previous kit during initialize")
            }
        }

        val newKit = MoneroKit.getInstance(
            context = context,
            seed = seed,
            restoreDateOrHeight = restoreDateOrHeight,
            walletId = walletId,
            node = node,
            trustNode = trustNode
        )

        kit = newKit
        observeKit(newKit)
        Timber.d("WalletManager: initialized kit for walletId=$walletId")
        return newKit
    }

    private fun observeKit(kit: MoneroKit) {
        scope.launch {
            kit.syncStateFlow.collect { state ->
                _syncStateFlow.value = state
            }
        }
        scope.launch {
            kit.balanceFlow.collect { balance ->
                _balanceFlow.value = balance
            }
        }
        scope.launch {
            kit.allTransactionsFlow.collect { txs ->
                _transactionsFlow.value = txs
            }
        }
    }

    suspend fun start() {
        val k = kit ?: run {
            Timber.w("WalletManager.start() called but kit is null")
            return
        }
        withContext(Dispatchers.IO) { k.start() }
    }

    fun stop() {
        scope.launch {
            try {
                kit?.stop()
            } catch (e: Exception) {
                Timber.e(e, "WalletManager.stop() failed")
            }
        }
    }

    /**
     * Stop the kit and release the reference, but preserve flow state (balance, txs).
     * Used for node switching where we want to reinitialize without losing UI state.
     */
    suspend fun stopAndRelease() {
        try {
            kit?.stop()
        } catch (e: Exception) {
            Timber.w(e, "WalletManager.stopAndRelease() stop failed")
        }
        kit = null
        Timber.d("WalletManager: stopped and released kit")
    }

    /**
     * Stop and release the kit. Resets flows to defaults.
     */
    fun clear() {
        scope.launch {
            try {
                kit?.stop()
            } catch (e: Exception) {
                Timber.e(e, "WalletManager.clear() stop failed")
            }
            kit = null
            _syncStateFlow.value = SyncState.NotSynced(MoneroKit.SyncError.NotStarted)
            _balanceFlow.value = Balance(0, 0)
            _transactionsFlow.value = emptyList()
            Timber.d("WalletManager: cleared")
        }
    }
}
