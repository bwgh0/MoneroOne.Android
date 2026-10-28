package one.monero.moneroone.paymentlinks

import android.content.Context
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.SignedTransaction
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.model.PendingTransaction
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.monero.moneroone.core.network.TorNetwork
import one.monero.moneroone.core.network.isOnionNode
import one.monero.moneroone.core.wallet.WalletFilePasswords
import one.monero.moneroone.core.wallet.WalletManager
import timber.log.Timber

/** The node refused the sweep because the envelope's outputs are already spent: someone claimed it first. */
class AlreadyClaimedException : IllegalStateException("Already claimed")

/** A claim or reclaim that went out: its [txid] (known before the broadcast) and the [amount] that arrives, fee paid. */
data class SweepSent(val txid: String, val amount: Long)

/**
 * One envelope wallet, open in its own kit instance with its own files
 * ([EnvelopeFiles]), on the main wallet's node and proxy, syncing from the
 * link's restore height only.
 *
 * The kit runs one wallet at a time process-wide (KitManager), so the main
 * wallet's kit gives way while an envelope is open and starts again when it
 * closes. One envelope is open at a time: [open] closes the previous one.
 */
class EnvelopeWallet private constructor(
    private val app: Context,
    val id: String,
    /** Where this open's scan must start: the link's restore height, or lower for a rescan. */
    val restoreHeight: Long,
    private val kit: MoneroKit,
    private val pausedMain: MoneroKit?
) {
    val syncState: StateFlow<SyncState> get() = kit.syncStateFlow

    @Volatile
    private var closed = false

    /** Records the scan's start once it begins ([startKit]); cancelled on [close]. */
    private var scanMark: Job? = null


    /**
     * Waits for the first full sync (or a failure). Returns what the envelope
     * holds, or null when the node did not answer within [timeoutMs].
     */
    suspend fun awaitSnapshot(timeoutMs: Long = SYNC_TIMEOUT_MS): EnvelopeSnapshot? {
        val state = withTimeoutOrNull(timeoutMs) {
            kit.syncStateFlow.first { it is SyncState.Synced || it is SyncState.NotSynced && it.error !is MoneroKit.SyncError.NotStarted }
        }
        if (state !is SyncState.Synced) {
            // The kit keeps only the message of what failed: log it, with the state, for the bug report.
            val error = (state as? SyncState.NotSynced)?.error
            if (error != null) Timber.w(error, "PaymentLinks: envelope check failed: %s", state) else Timber.w("PaymentLinks: envelope check timed out (%s)", kit.syncStateFlow.value)
            return null
        }
        // The kit publishes Synced, then the balance, in the same pass: let the balance land.
        delay(BALANCE_SETTLE_MS)
        return snapshot()
    }

    /** What the envelope holds now. Confirmations come from the chain height: the kit's history is refreshed only on money events. */
    fun snapshot(): EnvelopeSnapshot {
        val incoming = kit.allTransactionsFlow.value.filter {
            it.direction == TransactionInfo.Direction.Direction_In && !it.isFailed
        }
        val mined = incoming.filter { !it.isPending && it.blockheight > 0 }
        val chainHeight = kit.lastBlockHeight ?: 0L
        val confirmations = mined.maxOfOrNull { maxOf(it.confirmations, chainHeight - it.blockheight) } ?: 0L
        val balance = kit.balance
        val spent = kit.allTransactionsFlow.value.any {
            it.direction == TransactionInfo.Direction.Direction_Out && !it.isFailed
        }
        return EnvelopeSnapshot(
            checkedAt = System.currentTimeMillis(),
            // A balance is money that arrived, even before the history lists its transaction.
            fundingSeen = incoming.isNotEmpty() || balance.all > 0,
            fundingMined = mined.isNotEmpty(),
            confirmations = confirmations.coerceAtLeast(0),
            balance = balance.all,
            unlocked = balance.unlocked,
            spendSeen = spent,
            spendTxids = outgoingHashes().sorted(),
            fundingHeight = mined.minOfOrNull { it.blockheight },
            // Oldest first; one still in the pool last.
            fundingTxids = incoming.sortedBy { if (!it.isPending && it.blockheight > 0) it.blockheight else Long.MAX_VALUE }.mapNotNull { it.hash },
            minedSpendTxids = outgoing().filter { !it.isPending && it.blockheight > 0 }.mapNotNull { it.hash }.sorted(),
            failedSpendTxids = kit.allTransactionsFlow.value
                .filter { it.direction == TransactionInfo.Direction.Direction_Out && it.isFailed }.mapNotNull { it.hash }.sorted(),
            scannedHeight = chainHeight
        )
    }

    /**
     * wallet2's fee estimate for the claim sweep of [balance]: one input, two outputs (a
     * sweep to one address adds a dummy change output), at [SWEEP_PRIORITY], the priority
     * [sweepTo] uses. Nothing is built or sent. Null when empty or the estimate failed.
     */
    suspend fun sweepFee(balance: Long): Long? {
        if (balance <= 0) return null
        return withContext(Dispatchers.IO) {
            try {
                kit.estimateFee(0, kit.receiveAddress, null, sweepAll = true, priority = SWEEP_PRIORITY).takeIf { it > 0 }
            } catch (e: Exception) {
                Timber.w(e, "PaymentLinks: sweep fee estimate failed")
                null
            }
        }
    }

    /**
     * Sweeps every unlocked output to [address]. [beforeCommit] gets the signed sweep and the chain height
     * before its broadcast, so the record names the claim or reclaim before it can go out; when it throws,
     * nothing is broadcast. Returns the sweep's txid and the amount that arrives (fee paid). Throws
     * [AlreadyClaimedException] when the node rejects a double spend, and
     * [io.horizontalsystems.monerokit.TransactionRejectedException] when it refuses the sweep otherwise
     * (nothing went out either way). Any other error may come after the node took the sweep.
     */
    suspend fun sweepTo(address: String, beforeCommit: (SignedTransaction, Long?) -> Unit): SweepSent {
        // The envelope's chain height now: a settle's grace counts blocks from here (LinkStatusRules.SETTLE_GRACE_BLOCKS).
        val height = kit.lastBlockHeight?.takeIf { it > 0 }
        val signed = try {
            withContext(Dispatchers.IO) {
                kit.send(0, address, null, sweepAll = true, priority = SWEEP_PRIORITY) { beforeCommit(it, height) }
            }
        } catch (e: Exception) {
            val message = e.message.orEmpty().lowercase()
            if ("double spend" in message || "double_spend" in message || "already spent" in message) {
                throw AlreadyClaimedException()
            }
            throw e
        }
        return SweepSent(signed.txids.first(), signed.amount)
    }

    private fun outgoing(): List<TransactionInfo> = kit.allTransactionsFlow.value
        .filter { it.direction == TransactionInfo.Direction.Direction_Out && !it.isFailed && it.hash != null }

    private fun outgoingHashes(): Set<String> = outgoing().mapNotNull { it.hash }.toSet()

    /** Stops and closes the envelope kit, then starts the main wallet's kit again. */
    suspend fun close() {
        if (closed) return
        closed = true
        scanMark?.cancel()
        withContext(NonCancellable) {
            runtimeLock.withLock {
                withContext(Dispatchers.IO) {
                    // The stop frees the kit's running slot for the main wallet: a failed release must not skip it.
                    try {
                        kit.release()
                    } catch (e: Exception) {
                        Timber.w(e, "PaymentLinks: envelope release failed")
                    }
                    try {
                        kit.stop()
                    } catch (e: Exception) {
                        Timber.w(e, "PaymentLinks: envelope stop failed")
                    }
                }
                if (current === this@EnvelopeWallet) current = null
                resumeMain(pausedMain)
            }
        }
    }

    companion object {
        /**
         * Claims and reclaims, as on iOS: wallet2's default, which it lowers to Low while the
         * pool is quiet. The sender's claim fee reserve is estimated at Medium, so the spare
         * part of the reserve reaches the recipient.
         */
        val SWEEP_PRIORITY: PendingTransaction.Priority = PendingTransaction.Priority.Priority_Default

        private const val SYNC_TIMEOUT_MS = 120_000L
        private const val SLOT_WAIT_MS = 1_500L
        private const val BALANCE_SETTLE_MS = 750L

        private val runtimeLock = Mutex()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var current: EnvelopeWallet? = null

        /**
         * Opens the envelope of [link] and starts its sync. The main wallet's kit
         * stops first and starts again on [close]. [restoreHeight]: where a new
         * cache starts its scan (a rescan passes a lower one after deleting the cache).
         */
        suspend fun open(context: Context, link: PaymentLink, restoreHeight: Long = link.height): EnvelopeWallet {
            current?.close()
            // One unit: a cancel between the main kit's stop and the return would leave the
            // envelope kit running and the main wallet stopped, with no one to close them.
            var opened: EnvelopeWallet? = null
            try {
                withContext(NonCancellable) { opened = openLocked(context, link, restoreHeight) }
            } catch (e: CancellationException) {
                opened?.close()
                throw e
            }
            val envelope = checkNotNull(opened)
            if (!currentCoroutineContext().isActive) {
                envelope.close()
                throw CancellationException("Envelope open cancelled")
            }
            return envelope
        }

        /** An envelope opens, runs or closes: the main wallet's kit is stopped or about to be. */
        val isBusy: Boolean get() = current != null || runtimeLock.isLocked

        /** Closes the open envelope, if any (Remove All). */
        suspend fun closeCurrent() {
            current?.close()
        }

        private suspend fun openLocked(context: Context, link: PaymentLink, restoreHeight: Long): EnvelopeWallet =
            runtimeLock.withLock {
                val app = context.applicationContext
                val node = EnvelopeNode.kitNodeString(app)
                val proxy = TorNetwork.route.walletProxy
                check(proxy.isNotEmpty() || !isOnionNode(node)) { "Tor is required for this node" }
                val kit = withContext(Dispatchers.IO) {
                    // A cache whose scan from this height never reached the tip (cut short by a timeout, an app
                    // kill or a node drop) can skip the funding block when it opens: restore it from the words.
                    val rebuild = EnvelopeScanRules.rebuildBeforeOpen(
                        EnvelopeFiles.hasWallet(app, link.id), EnvelopeFiles.scannedFrom(app, link.id), restoreHeight
                    )
                    if (rebuild) {
                        Timber.w("PaymentLinks: envelope scan from %d never finished: restoring the envelope", restoreHeight)
                        EnvelopeFiles.deleteWallet(app, link.id)
                    }
                    val walletId = EnvelopeFiles.kitWalletId(app, link.id)
                    MoneroKit.getInstance(
                        context = app,
                        seed = Seed.Electrum(link.words, ""),
                        restoreDateOrHeight = restoreHeight.toString(),
                        walletId = walletId,
                        walletPassword = WalletFilePasswords.get(app)
                            .forKitWallet(app, WalletFilePasswords.envelopeName(link.id), walletId),
                        node = node,
                        trustNode = false,
                        proxyAddress = proxy
                    )
                }
                val paused = pauseMain()
                val envelope = EnvelopeWallet(app, link.id, restoreHeight, kit, paused)
                current = envelope
                envelope.startKit()
                envelope
            }

        private suspend fun pauseMain(): MoneroKit? {
            val main = WalletManager.kit ?: return null
            withContext(Dispatchers.IO) {
                try {
                    main.stop()
                } catch (e: Exception) {
                    Timber.w(e, "PaymentLinks: main kit stop failed")
                }
            }
            return main
        }

        private suspend fun resumeMain(paused: MoneroKit?) {
            // A switch or node change replaced the kit meanwhile: the new one starts on its own.
            if (paused == null || WalletManager.kit !== paused) return
            try {
                WalletManager.start()
            } catch (e: Exception) {
                Timber.w(e, "PaymentLinks: main kit restart failed")
            }
        }
    }

    private fun startKit() {
        // Off the main thread: the start resolves the node's name and opens the wallet file, and the
        // system refuses network on the main thread (NetworkOnMainThreadException with a host name).
        val start: Job = scope.launch(Dispatchers.IO) { kit.start() }
        // The scan from [restoreHeight] has begun once the kit reports a refresh (after its init put the
        // start there, WalletService.initWallet): from then on the cache resumes a scan that started right,
        // so a long first scan cut short by a timeout carries on at the next check instead of starting over.
        scanMark = scope.launch {
            kit.syncStateFlow.first { it is SyncState.Syncing || it is SyncState.Synced }
            if (!closed) withContext(Dispatchers.IO) { EnvelopeFiles.markScanned(app, id, restoreHeight) }
        }
        // A resume can start the main kit again between its stop and this start; then this kit waits for
        // the running slot. Stopping the main kit hands the slot over (KitManager promotes the waiting kit).
        scope.launch {
            delay(SLOT_WAIT_MS)
            while (start.isActive && !closed) {
                val state = kit.syncStateFlow.value
                if (state is SyncState.Connecting && state.waiting) {
                    Timber.d("PaymentLinks: envelope waits for the kit slot; pausing the main kit again")
                    pauseMain()
                }
                delay(SLOT_WAIT_MS)
            }
        }
    }
}
