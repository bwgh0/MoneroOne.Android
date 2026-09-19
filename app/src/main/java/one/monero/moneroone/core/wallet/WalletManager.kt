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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Singleton that owns the one running MoneroKit instance (KitManager allows a
 * single running kit process-wide), shared by WalletViewModel and
 * WalletSyncService.
 *
 * Multi-wallet rules:
 *  - [currentWalletId] tracks which cache id the kit was opened for, so the
 *    caller can tell whether the running kit belongs to the active wallet.
 *  - Collectors are cancelled and flows reset on every (re)initialize so a
 *    replaced kit can never republish stale data into the new wallet's UI
 *    (the old observeKit leak).
 */
object WalletManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJobs: List<Job> = emptyList()

    /** In-flight fire-and-forget stop (see [stop]); joined before any start/reopen. */
    private var pendingStop: Job? = null

    var kit: MoneroKit? = null
        private set

    /** Cache id (walletId) the current kit was opened with, null when no kit. */
    var currentWalletId: String? = null
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
     * Any previously held kit is fully stopped (awaited) first.
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
        // Fully tear down any existing kit — cancels collectors BEFORE the new
        // kit exists so nothing stale leaks across.
        stopAndRelease()

        val newKit = MoneroKit.getInstance(
            context = context,
            seed = seed,
            restoreDateOrHeight = restoreDateOrHeight,
            walletId = walletId,
            node = node,
            trustNode = trustNode
        )

        kit = newKit
        currentWalletId = walletId
        resetFlows()
        observeKit(newKit)
        Timber.d("WalletManager: initialized kit for walletId=$walletId")
        return newKit
    }

    private fun resetFlows() {
        _syncStateFlow.value = SyncState.NotSynced(MoneroKit.SyncError.NotStarted)
        _balanceFlow.value = Balance(0, 0)
        _transactionsFlow.value = emptyList()
    }

    private fun observeKit(kit: MoneroKit) {
        observeJobs.forEach { it.cancel() }
        observeJobs = listOf(
            scope.launch {
                kit.syncStateFlow.collect { state ->
                    _syncStateFlow.value = state
                }
            },
            scope.launch {
                kit.balanceFlow.collect { balance ->
                    _balanceFlow.value = balance
                }
            },
            scope.launch {
                kit.allTransactionsFlow.collect { txs ->
                    _transactionsFlow.value = txs
                }
            }
        )
    }

    suspend fun start() {
        awaitPendingStop()
        val k = kit ?: run {
            Timber.w("WalletManager.start() called but kit is null")
            return
        }
        withContext(Dispatchers.IO) { k.start() }
    }

    /**
     * Fire-and-forget stop (ViewModel teardown). The job is remembered so a
     * new ViewModel's start()/initialize() awaits it instead of racing the
     * kit's 1s-delayed stop: start-then-stop left the kit dead until the
     * next resume while the UI showed it as running.
     */
    fun stop() {
        val k = kit ?: return
        pendingStop = scope.launch {
            try {
                k.stop()
            } catch (e: Exception) {
                Timber.e(e, "WalletManager.stop() failed")
            }
        }
    }

    private suspend fun awaitPendingStop() {
        pendingStop?.join()
        pendingStop = null
    }

    /**
     * Await full teardown of the kit and release the reference. Collectors are
     * cancelled first so no republish can land after release. Flow state
     * (balance, txs) is preserved for UI continuity on node changes.
     */
    suspend fun stopAndRelease() {
        awaitPendingStop()
        observeJobs.forEach { it.cancel() }
        observeJobs = emptyList()
        try {
            kit?.stop()
        } catch (e: Exception) {
            Timber.w(e, "WalletManager.stopAndRelease() stop failed")
        }
        kit = null
        currentWalletId = null
        Timber.d("WalletManager: stopped and released kit")
    }

    /**
     * Stop and release the kit, resetting flows, and suspend until the kit has
     * fully stopped. Callers that delete wallet files afterwards must use this
     * rather than [clear]: stopping the kit stores the wallet, which would
     * re-create the very files being deleted.
     */
    suspend fun clearAndAwait() {
        try {
            // stop() stores and closes the wallet through the native layer, which
            // blocks; callers run on Main.
            withContext(Dispatchers.IO) { kit?.stop() }
        } catch (e: Exception) {
            Timber.e(e, "WalletManager.clearAndAwait() stop failed")
        }
        kit = null
        _syncStateFlow.value = SyncState.NotSynced(MoneroKit.SyncError.NotStarted)
        _balanceFlow.value = Balance(0, 0)
        _transactionsFlow.value = emptyList()
        Timber.d("WalletManager: cleared (awaited)")
    }

    /**
     * Stop and release the kit. Resets flows to defaults.
     */
    suspend fun clear() {
        stopAndRelease()
        resetFlows()
        Timber.d("WalletManager: cleared")
    }
}
