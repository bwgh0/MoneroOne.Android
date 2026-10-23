package one.monero.moneroone.paymentlinks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.monerokit.SignedTransaction
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.TransactionRejectedException
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.SendGate
import one.monero.moneroone.core.wallet.WalletManager
import timber.log.Timber

/** The envelope open on screen (claim sheet or link details). */
data class EnvelopeSession(
    val id: String,
    val phase: Phase,
    val snapshot: EnvelopeSnapshot? = null,
    val error: String? = null
) {
    enum class Phase { CHECKING, LIVE, SWEEPING, ERROR }
}

enum class SweepKind { CLAIM, RECLAIM }

/** What the last sweep of envelope [id] did. [walletName]: the wallet it went to. */
data class SweepResult(val id: String, val kind: SweepKind, val outcome: SweepOutcome, val walletName: String = "")

sealed class SweepOutcome {
    data class Sent(val txid: String?, val amount: Long) : SweepOutcome()
    object AlreadyClaimed : SweepOutcome()
    data class Failed(val message: String) : SweepOutcome()
}

/**
 * Payment links: drafts for Send, the records, the envelope on screen, and
 * the claim and reclaim sweeps. Activity-scoped, so a result outlives the
 * screen that asked for it.
 */
class PaymentLinksViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    val store = PaymentLinkStore.get(application)
    val sent: StateFlow<List<SentLink>> = store.sent
    val received: StateFlow<List<ReceivedLink>> = store.received

    private val _session = MutableStateFlow<EnvelopeSession?>(null)
    val session: StateFlow<EnvelopeSession?> = _session.asStateFlow()

    private val _sweep = MutableStateFlow<SweepResult?>(null)
    val sweep: StateFlow<SweepResult?> = _sweep.asStateFlow()

    private val sessionLock = Mutex()
    private var envelope: EnvelopeWallet? = null
    private var sessionJob: Job? = null
    private var refreshJob: Job? = null
    private var openLookJob: Job? = null
    private val _holdSyncStatus = MutableStateFlow<String?>(null)

    /**
     * The wallet whose Wallet-tab link check has the main wallet stopped, until the main wallet is
     * synced again after it ([HOLD_AFTER_MS] at most); null otherwise. The wallet was synced when the
     * check started, so the tab keeps saying so instead of "Reaching node" for each check.
     */
    val holdSyncStatus: StateFlow<String?> = _holdSyncStatus.asStateFlow()

    init {
        // Files of settled or removed links go; live envelopes keep theirs.
        viewModelScope.launch(Dispatchers.IO) {
            if (envelope == null) EnvelopeFiles.sweep(context, store.liveEnvelopeIds())
        }
        // Retain received secrets until explicit Remove All. A mined claim can
        // reorg or leave newer locked funding behind.
        // A record saved without its funding txid (the app quit mid-broadcast, the broadcast timed out, or the
        // error came after the node took it): its signed attempt's txid in the open wallet's history (whichever
        // wallet is open, in any state, with no time limit) is its funding. The link shows again, as Waiting, and
        // the status never contradicts the wallet's own history.
        // A funding the open wallet marks failed (dropped or rejected) sent no money: the link reads as not funded.
        viewModelScope.launch {
            combine(WalletManager.transactionsFlow, store.sent) { txs, sent ->
                val (failed, live) = txs.filter { it.hash != null }.partition { it.isFailed }
                val failedHashes = failed.mapNotNull { it.hash }.toSet()
                val liveHashes = live.mapNotNull { it.hash }.toSet()
                Triple(
                    LinkStatusRules.fundingFailures(sent, failedHashes, liveHashes),
                    LinkStatusRules.fundingsToAdopt(sent, failedHashes + liveHashes, liveHashes) { address -> fundingTxid(address, txs) },
                    failedHashes
                )
            }.collect { (failures, found, failedHashes) ->
                failures.forEach { (id, failed) -> store.updateSent(id) { it.copy(fundingFailed = failed) } }
                found.forEach { (id, txid) ->
                    val failed = txid in failedHashes
                    if (!failed) Timber.d("PaymentLinks: link %s: funding found in the wallet's history", store.sent(id)?.number)
                    store.updateSent(id) {
                        it.copy(fundingTxid = txid, fundingFailed = failed, state = if (failed) it.state else SentState.FUNDED)
                    }
                }
            }
        }
        // A sweep the app lost mid-flight (killed, swiped away): its envelope's spend shows up in the open wallet.
        viewModelScope.launch {
            combine(WalletManager.transactionsFlow, store.sent, store.received) { txs, sent, received ->
                val incoming = txs.filter { it.direction == TransactionInfo.Direction.Direction_In && !it.isFailed && it.hash != null }
                val hashes = incoming.mapNotNull { it.hash }.toSet()
                val open = WalletManager.currentWalletId
                Triple(LinkStatusRules.lostReclaims(sent, open, hashes), LinkStatusRules.lostClaims(received, open, hashes), incoming)
            }.collect { (reclaims, claims, incoming) ->
                reclaims.forEach { (id, txid) -> store.updateSent(id) { it.copy(reclaimTxid = txid, reclaimLostRace = false) } }
                claims.forEach { (id, txid) ->
                    val amount = incoming.firstOrNull { it.hash == txid }?.amount
                    store.updateReceived(id) {
                        it.copy(state = ReceivedState.CLAIMED, claimTxid = txid, claimedAmount = amount ?: it.claimedAmount, claimedTo = it.claimStartedTo)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sender

    /**
     * A new envelope for [amount] (atomic). Needs the main wallet's daemon
     * height. Throws when the wallet is not connected or the restore check fails.
     */
    suspend fun prepare(amount: Long): EnvelopeDraft = withContext(Dispatchers.IO) {
        val height = WalletManager.kit?.lastBlockHeight?.takeIf { it > 0 }
            ?: throw IllegalStateException("Wallet is not connected yet. Wait for the wallet to reconnect and try again.")
        EnvelopeFactory.create(context, amount, height, System.currentTimeMillis() / 1000)
    }

    /** The name the next link gets ("Link 9"). */
    fun nextName(): String = store.nextName()

    /** Saves this phone's note for a link (blank: back to "Link 7"). */
    fun rename(id: String, note: String?) = store.rename(id, note)

    /** The draft was left before funding: nothing was saved, its files go. */
    fun discard(draft: EnvelopeDraft) {
        if (store.sent(draft.link.id) != null) return
        viewModelScope.launch(Dispatchers.IO) { EnvelopeFiles.delete(context, draft.link.id) }
    }

    /** Saves the record before any broadcast (spec "Sender flow" step 2). False: do not fund. */
    fun recordBeforeFunding(draft: EnvelopeDraft, walletId: String, feeReserve: Long, note: String? = null): Boolean {
        val link = draft.link
        return store.createSent(
            SentLink(
                id = link.id, words = if (link.version == 1) link.words else emptyList(), secret = link.secretHex,
                height = link.height, amount = draft.amount, expiry = draft.expiry,
                createdAtMillis = System.currentTimeMillis(), walletId = walletId, envelopeAddress = draft.address,
                feeReserve = feeReserve, state = SentState.CREATED, note = LinkNames.cleanNote(note)
            )
        )
    }

    /**
     * The funding is signed: its txids go on the record before the broadcast (the kit's beforeCommit), so a
     * broadcast that times out, an app kill or a seed restore still finds it by txid. Throws when the save
     * fails: then nothing is broadcast.
     */
    fun fundingSigned(id: String, txids: List<String>) {
        store.updateSent(id) { it.copy(attemptTxids = txids) }
            ?: throw IllegalStateException(tr("Could not save the payment link. Nothing was sent."))
    }

    /**
     * The funding broadcast ended. On success the funding txid is the signed attempt's ([fundingSigned]), else
     * (no attempt saved) it is read from the wallet's history. [beforeBroadcast]: the failure came before
     * anything reached the network, so the hidden attempt needs no envelope scan
     * ([LinkStatusRules.hiddenScanDue]). An attempt that got as far as its txid may have gone out: it is scanned.
     */
    fun fundingFinished(id: String, success: Boolean, beforeBroadcast: Boolean = false) {
        val record = store.updateSent(id) {
            if (success) it.copy(state = SentState.FUNDED, fundingTxid = it.fundingTxid ?: it.attemptTxids.firstOrNull())
            else it.copy(state = SentState.NOT_FUNDED, failedBeforeBroadcast = beforeBroadcast && it.attemptTxids.isEmpty())
        }
        if (!success || record?.fundingTxid != null) return
        viewModelScope.launch {
            val address = store.sent(id)?.envelopeAddress ?: return@launch
            val txid = withTimeoutOrNull(FUNDING_TXID_WAIT_MS) {
                var found: String? = null
                while (found == null) {
                    found = fundingTxid(address)
                    if (found == null) delay(1_000)
                }
                found
            }
            if (txid != null) store.updateSent(id) { it.copy(fundingTxid = txid, fundingFailed = false) }
        }
    }

    /** The main wallet's outgoing transaction to [envelopeAddress], if its history ([transactions]) holds one. */
    fun fundingTxid(envelopeAddress: String, transactions: List<TransactionInfo> = WalletManager.transactionsFlow.value): String? =
        transactions.firstOrNull { tx ->
            tx.direction == TransactionInfo.Direction.Direction_Out && !tx.isFailed &&
                tx.transfers?.any { it.address == envelopeAddress } == true
        }?.hash

    /** The block of [link]'s funding in the open wallet's history (its first, when split); null when none is in a block there. */
    private fun fundingBlock(link: SentLink): Long? =
        fundingTransactions(link, WalletManager.transactionsFlow.value).filter { !it.isPending && it.blockheight > 0 }.minOfOrNull { it.blockheight }

    /**
     * Rescans started in this run, per link. Two at most: a rescan cut short (a detail opened, the node
     * went, the sync timed out) gets one more, and one that still misses the funding is not repeated.
     */
    private val rescans = mutableMapOf<String, Int>()

    /**
     * What the open wallet's history shows of a claim or reclaim into it ([LinkStatusRules.droppedSettles]).
     * Read before an envelope opens: the main wallet stops while it runs.
     */
    private fun walletHistory(): WalletHistory {
        val txs = WalletManager.transactionsFlow.value.filter { it.hash != null }
        return WalletHistory(
            pending = txs.filter { it.isPending && !it.isFailed }.mapNotNull { it.hash }.toSet(),
            confirmed = txs.filter { !it.isPending && !it.isFailed }.mapNotNull { it.hash }.toSet(),
            failed = txs.filter { it.isFailed }.mapNotNull { it.hash }.toSet()
        )
    }

    /**
     * Opens envelope [id] ([link]) and waits for its first full sync. [hold] gets each envelope as it
     * opens, so the caller closes it whatever happens. An envelope whose first scan never finished is
     * restored before it opens ([EnvelopeScanRules]). When the open wallet shows a sent link's funding in
     * a block that the sync did not see, the envelope is restored and scanned again from no later than
     * that block ([LinkStatusRules.rescanFrom]), [MAX_RESCANS] per run, with no UI change.
     * Call under [sessionLock]. Returns the first snapshot (null: the sync failed or timed out). Throws
     * when an open fails.
     */
    private suspend fun openChecked(id: String, link: PaymentLink, hold: (EnvelopeWallet) -> Unit): EnvelopeSnapshot? {
        // Read before the open: the main wallet stops while the envelope runs.
        val record = store.sent(id)
        val fundingBlock = record?.let(::fundingBlock)
        val opened = EnvelopeWallet.open(context, link).also(hold)
        val first = opened.awaitSnapshot() ?: return null
        val from = record?.let { LinkStatusRules.rescanFrom(it, first, fundingBlock) } ?: return first
        val tries = rescans[id] ?: 0
        if (tries >= MAX_RESCANS) return first
        rescans[id] = tries + 1
        Timber.w("PaymentLinks: link %s: funding in block %d not seen; rescanning from %d", record.number, fundingBlock, from)
        withContext(NonCancellable) {
            opened.close()
            withContext(Dispatchers.IO) { EnvelopeFiles.deleteWallet(context, id) }
        }
        val again = EnvelopeWallet.open(context, link, restoreHeight = from).also(hold)
        return again.awaitSnapshot()
    }

    // --------------------------------------------------------------- session

    /** Opens the envelope of [id] (sent or received) and keeps its state current until [closeSession]. */
    fun openSession(id: String) {
        // A second deep link must not close the wallet during a broadcast.
        if (sweeping) return
        if (_session.value?.id == id && _session.value?.phase != EnvelopeSession.Phase.ERROR) return
        _sweep.value = null
        refreshJob?.cancel()
        openLookJob?.cancel()
        leftoverJob?.cancel()
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            var estimatesClaim = false
            val wallet = walletHistory()
            sessionLock.withLock {
                closeEnvelopeLocked()
                val link = store.sent(id)?.link ?: store.received(id)?.link
                if (link == null) {
                    _session.value = null
                    return@withLock
                }
                // A link sent to us: its checks carry the claim's fee estimate (what a claim delivers).
                estimatesClaim = store.sent(id) == null
                _session.value = EnvelopeSession(id, EnvelopeSession.Phase.CHECKING)
                val first = try {
                    openChecked(id, link) { envelope = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "PaymentLinks: envelope open failed")
                    closeEnvelopeLocked()
                    _session.value = EnvelopeSession(id, EnvelopeSession.Phase.ERROR, error = e.message)
                    return@withLock
                }
                val opened = checkNotNull(envelope)
                if (first == null) {
                    val state = opened.syncState.value
                    val message = (state as? SyncState.NotSynced)?.error?.message
                    // A check that failed or timed out holds nothing: close it so the main wallet runs again.
                    // Retry opens it anew.
                    closeEnvelopeLocked()
                    _session.value = EnvelopeSession(id, EnvelopeSession.Phase.ERROR, error = message)
                    return@withLock
                }
                publish(id, if (estimatesClaim) first.copy(sweepFee = opened.sweepFee(first.balance)) else first, wallet)
            }
            // Live: confirmations and balance as blocks arrive. A received link's claim fee is
            // estimated again when the balance moves, and every FEE_EVERY_MS.
            var feeAt = System.currentTimeMillis()
            while (true) {
                delay(LIVE_INTERVAL_MS)
                val open = envelope ?: break
                val current = _session.value ?: break
                if (current.id != id || current.phase == EnvelopeSession.Phase.SWEEPING) continue
                if (open.syncState.value is SyncState.Synced) {
                    val seen = open.snapshot()
                    val last = current.snapshot
                    val moved = seen.copy(checkedAt = 0, sweepFee = null) != last?.copy(checkedAt = 0, sweepFee = null)
                    val now = System.currentTimeMillis()
                    val next = if (estimatesClaim && (moved || now - feeAt >= FEE_EVERY_MS)) {
                        feeAt = now
                        seen.copy(sweepFee = open.sweepFee(seen.balance))
                    } else {
                        seen.copy(sweepFee = last?.sweepFee)
                    }
                    // The estimate suspends: a claim may have started meanwhile.
                    val still = _session.value
                    if (sweeping || still?.id != id || still.phase == EnvelopeSession.Phase.SWEEPING) continue
                    if (next.copy(checkedAt = 0) != last?.copy(checkedAt = 0)) publish(id, next, wallet)
                }
            }
        }
    }

    /** Opens the envelope of [id] again for a fresh check (Retry, Check Again). */
    fun recheck(id: String) {
        if (sweeping) return
        sessionJob?.cancel()
        _session.value = null
        openSession(id)
    }

    /** Debug builds only (Timber plants no tree in release). The line carries the link's number, never its note, address or secret. */
    private fun logCheck(number: Int?, snapshot: EnvelopeSnapshot) {
        Timber.d("PaymentLinks: %s", LinkStatusRules.checkLogLine(number, snapshot))
    }

    private fun publish(id: String, snapshot: EnvelopeSnapshot, wallet: WalletHistory) {
        logCheck(store.sent(id)?.number ?: store.received(id)?.number, snapshot)
        store.updateSent(id) { LinkStatusRules.withCheck(it, snapshot, wallet) }
        store.updateReceived(id) { it.noting(snapshot, wallet) }
        _session.value = EnvelopeSession(id, EnvelopeSession.Phase.LIVE, snapshot)
    }

    fun closeSession() {
        // A sweep in flight keeps its kit open; it closes the session when it ends.
        if (sweeping) {
            closeAfterSweep = true
            return
        }
        sessionJob?.cancel()
        sessionJob = null
        _session.value = null
        viewModelScope.launch(NonCancellable) { sessionLock.withLock { closeEnvelopeLocked() } }
        if (refreshWanted) refreshSent()
    }

    private suspend fun closeEnvelopeLocked() {
        val open = envelope ?: return
        envelope = null
        open.close()
        val id = open.id
        // A settled link's files are no longer needed.
        if (id !in store.liveEnvelopeIds()) withContext(Dispatchers.IO) { EnvelopeFiles.delete(context, id) }
    }

    @Volatile
    private var sweeping = false
    private var closeAfterSweep = false

    /**
     * Sweeps the open envelope into [destination]: a [SweepKind.CLAIM] of a received link or a
     * [SweepKind.RECLAIM] of a sent one. The open wallet takes it at its live receive address, a
     * closed wallet at its saved main address (no wallet switch).
     */
    fun sweepOpenEnvelope(kind: SweepKind, destination: ClaimDestination) {
        val address = destination.address ?: return
        val walletName = destination.name
        val current = _session.value ?: return
        val open = envelope ?: return
        if (current.phase != EnvelopeSession.Phase.LIVE || sweeping) return
        val id = current.id
        if (open.id != id) return
        val amount = current.snapshot?.unlocked ?: 0L
        // Saved before the broadcast, so a sweep the app loses mid-flight is found later by its hash.
        val now = System.currentTimeMillis()
        val earlierAttempt = when (kind) {
            SweepKind.RECLAIM -> store.sent(id)?.reclaimStartedAt != null
            SweepKind.CLAIM -> store.received(id)?.claimStartedAt != null
        }
        // The kit wallet the money lands in: the open one, or the closed wallet's own when it next opens.
        val into = if (destination.isOpen) WalletManager.currentWalletId else destination.kitWalletId
        Timber.d("PaymentLinks: sweep %s into %s wallet", kind, if (destination.isOpen) "the open" else "a closed")
        val saved = when (kind) {
            SweepKind.RECLAIM -> store.updateSent(id) { it.copy(reclaimStartedAt = it.reclaimStartedAt ?: now, reclaimWallet = into) }
            SweepKind.CLAIM -> store.updateReceived(id) {
                it.copy(claimStartedAt = it.claimStartedAt ?: now, claimStartedTo = walletName, claimWallet = into)
            }
        }
        if (saved == null) {
            // The attempt could not be saved before the broadcast, so nothing went out.
            val message = if (kind == SweepKind.RECLAIM) tr("Could not save the reclaim. Nothing was sent.")
            else tr("Could not save the claim. Nothing was sent.")
            _sweep.value = SweepResult(id, kind, SweepOutcome.Failed(message), walletName)
            return
        }
        sweeping = true
        closeAfterSweep = false
        _session.value = current.copy(phase = EnvelopeSession.Phase.SWEEPING)
        _sweep.value = null
        viewModelScope.launch(NonCancellable) {
            // Puts the record back as it was before this sweep's txid was saved: for a sweep the node refused.
            var unsent: (() -> Unit)? = null
            val outcome = try {
                val sweepTx = open.sweepTo(address) { signed, height -> unsent = saveSettle(id, kind, signed, height, amount, walletName) }
                // What arrives: the sweep's amount after its fee; the unlocked total when the kit did not say.
                SweepOutcome.Sent(sweepTx.txid, sweepTx.amount.takeIf { it > 0 } ?: amount)
            } catch (e: AlreadyClaimedException) {
                unsent?.invoke()
                SweepOutcome.AlreadyClaimed
            } catch (e: TransactionRejectedException) {
                // The node refused it: nothing went out.
                unsent?.invoke()
                Timber.w(e, "PaymentLinks: sweep refused")
                SweepOutcome.Failed(e.message ?: "")
            } catch (e: Exception) {
                // The broadcast may have gone out (a timeout, a lost connection): the saved txid stays ours, and a
                // check reads it dropped once its grace is over (LinkStatusRules.droppedSettles).
                Timber.w(e, "PaymentLinks: sweep failed")
                SweepOutcome.Failed(e.message ?: "")
            }
            record(id, kind, outcome, earlierAttempt)
            _sweep.value = SweepResult(id, kind, outcome, walletName)
            val snapshot = runCatching { open.snapshot() }.getOrNull()
            _session.update { s -> s?.takeIf { it.id == id }?.copy(phase = EnvelopeSession.Phase.LIVE, snapshot = snapshot ?: s.snapshot) }
            sweeping = false
            // Done with this envelope: close it so the main wallet syncs again and sees the money.
            if (outcome !is SweepOutcome.Failed || closeAfterSweep) closeSession()
        }
    }

    /**
     * Saves the signed sweep [signed] on [id]'s record as its settle, before the broadcast: the claim or reclaim
     * txids (every transaction of the sweep), when it went out and the chain [height] then (its grace counts
     * blocks from there). An app killed
     * mid-flight, or a broadcast that timed out, still knows the spend is ours. Throws when the save fails: then
     * nothing is broadcast. Returns what puts the record back, for a sweep the node refuses (nothing went out).
     */
    private fun saveSettle(id: String, kind: SweepKind, signed: SignedTransaction, height: Long?, unlocked: Long, walletName: String): () -> Unit {
        val txids = signed.txids
        val now = System.currentTimeMillis()
        val failure = { IllegalStateException(if (kind == SweepKind.RECLAIM) tr("Could not save the reclaim. Nothing was sent.") else tr("Could not save the claim. Nothing was sent.")) }
        when (kind) {
            SweepKind.RECLAIM -> {
                val before = store.sent(id) ?: throw failure()
                store.updateSent(id) { LinkStatusRules.reclaimSigned(it, txids, now, height) } ?: throw failure()
                return { store.updateSent(id) { LinkStatusRules.reclaimRefused(it, before) } }
            }
            SweepKind.CLAIM -> {
                // Only a received record takes a claim: a link this phone sent is reclaimed (iOS parity).
                val before = store.received(id) ?: throw failure()
                // The secret stays after the claim: a reorg or newer locked funding can leave money in this envelope.
                val amount = signed.amount.takeIf { it > 0 } ?: unlocked
                store.updateReceived(id) { it.claimSigned(txids, amount, walletName, now, height) } ?: throw failure()
                return { store.updateReceived(id) { it.claimRefused(before) } }
            }
        }
    }

    /** The sweep ended. A sweep that went out is on its record already ([saveSettle]). */
    private fun record(id: String, kind: SweepKind, outcome: SweepOutcome, earlierAttempt: Boolean) {
        if (outcome !is SweepOutcome.AlreadyClaimed) return
        when (kind) {
            // After a reclaim the app lost, the spend that beat this one may be our own: the open
            // wallet's history decides (see init). Otherwise a claim won the race.
            SweepKind.RECLAIM -> if (!earlierAttempt) store.updateSent(id) { it.copy(reclaimLostRace = true) }
            // Our earlier claim, when the record names one, is followed by its checks; else someone claimed it first.
            SweepKind.CLAIM -> if (store.updateReceived(id) { it.copy(state = ReceivedState.CLAIMED) } == null) {
                Timber.w("PaymentLinks: a claim with no received record")
            }
        }
    }

    fun clearSweep() {
        _sweep.value = null
    }

    // -------------------------------------------------------------- leftover

    /** Settled links whose leftover check this run was conclusive (settle in a block, nothing locked left). */
    private val leftoverDone = mutableSetOf<String>()
    private val leftoverAt = mutableMapOf<String, Long>()
    private var leftoverJob: Job? = null

    /**
     * A settled link's detail opened (founder decision 2026-10-17): one quiet check of its envelope for
     * money its claim or reclaim left behind (a later funding, outputs still locked at the sweep), or never
     * moved (a dropped settle). The link reopens only when [LinkStatusRules.reopens] says so: the settle is
     * in a block or dropped, and spendable money is still there. Each check stops the main wallet for a
     * moment, so a link is checked at most every [LOOK_AGAIN_AFTER_MS], and not again in this run once a
     * check was conclusive.
     */
    fun checkLeftover(id: String) {
        if (sweeping || _session.value != null || sendOpen || leftoverJob?.isActive == true || id in leftoverDone) return
        val sent = store.sent(id)
        val received = if (sent == null) store.received(id) else null
        val settled = sent?.let(LinkStatusRules::isSettled) ?: received?.isSettled ?: return
        val txids = sent?.let(LinkStatusRules::settleTxids) ?: received?.settleTxids.orEmpty()
        val link = sent?.link ?: received?.link ?: return
        // A settle we cannot name (its txid never showed) can prove nothing: no check.
        if (!settled || txids.isEmpty()) return
        // While it waits in the open wallet's pool, a check can change nothing (iOS isLeftoverCheckDue).
        val wallet = walletHistory()
        if (LinkStatusRules.waitsInWallet(txids, wallet)) return
        val now = System.currentTimeMillis()
        if (now - (leftoverAt[id] ?: 0L) < LOOK_AGAIN_AFTER_MS) return
        leftoverAt[id] = now
        val number = sent?.number ?: received?.number
        leftoverJob = viewModelScope.launch {
            sessionLock.withLock {
                // A claim sheet or detail took the envelope runtime meanwhile, or Send opened.
                if (_session.value != null || sendOpen) return@withLock
                var opened: EnvelopeWallet? = null
                try {
                    openChecked(id, link) { opened = it }?.let { snapshot ->
                        logCheck(number, snapshot)
                        val mined = snapshot.minedSpendTxids.containsAll(txids)
                        if (mined && (snapshot.balance == 0L || snapshot.unlocked > 0)) leftoverDone.add(id)
                        store.updateSent(id) { LinkStatusRules.withCheck(it, snapshot, wallet) }
                        store.updateReceived(id) { it.noting(snapshot, wallet) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "PaymentLinks: link %s: envelope open failed (leftover check)", number)
                } finally {
                    // Settled and final: its files are no longer needed.
                    withContext(NonCancellable) { closeChecked(id, opened) }
                }
            }
        }
    }

    /** The settled link's detail went away: its check stops, so the main wallet runs again. */
    fun stopLeftoverCheck() {
        leftoverJob?.cancel()
        leftoverJob = null
    }

    /**
     * Checks every sent link that can still change, one envelope at a time
     * (the list screen), every [REFRESH_INTERVAL_MS] until [stopRefresh] runs.
     * Skips a round while a claim sheet or detail screen holds a session.
     */
    fun refreshSent() {
        refreshWanted = true
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            // While the list shows: pending links turn ready, ready ones turn claimed.
            while (true) {
                checkDueSent()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkDueSent() {
        val now = System.currentTimeMillis()
        val wallet = walletHistory()
        checkSent(store.sent.value.filter { now - (it.lastCheck?.checkedAt ?: 0) > REFRESH_AFTER_MS && LinkStatusRules.checkDue(it, now, wallet) })
        checkReceived(store.received.value.filter { now - (it.lastCheck?.checkedAt ?: 0) > REFRESH_AFTER_MS && it.settleDue(wallet) })
    }

    /**
     * The Wallet tab's look at the open sent links of [walletId] (spec Round 3), orphaned ones too
     * ([OpenLinkRules.belongsTo], [walletIds]: the wallets on this device), so the balance
     * card's line drops a claimed link. A look stops the main wallet for a moment, so the tab asks
     * for one only when it appears, comes back to the foreground or shows another wallet, then every
     * [WALLET_TAB_LOOK_EVERY_MS], and only while the wallet is synced, no send runs and History shows
     * now. Links looked at in the last [LOOK_AGAIN_AFTER_MS] wait. Runs in the caller's scope: the
     * look stops when the tab goes; a claim sheet that opens stops it too. The Links filter's
     * [refreshSent] covers that filter.
     */
    suspend fun lookAtOpenLinks(walletId: String, walletIds: Set<String> = emptySet()) = coroutineScope {
        if (refreshJob?.isActive == true || _session.value != null || sendOpen) {
            Timber.d("PaymentLinks: open link check skipped: the envelope runtime is busy or Send is open")
            return@coroutineScope
        }
        fillNetAmounts()
        val now = System.currentTimeMillis()
        val wallet = walletHistory()
        // Open links, and links that can still come back: a hidden funding attempt, a settle not in a block yet.
        // This wallet's links, and links whose funding wallet is gone (deleted, or restored under a new id).
        val due = store.sent.value.filter {
            OpenLinkRules.belongsTo(it, walletId, walletIds) && now - (it.lastCheck?.checkedAt ?: 0) > LOOK_AGAIN_AFTER_MS &&
                (OpenLinkRules.isOpen(it, now / 1000) || LinkStatusRules.hiddenScanDue(it, now) || LinkStatusRules.settleDue(it, wallet))
        }
        val dueReceived = store.received.value.filter { it.settleDue(wallet) && now - (it.lastCheck?.checkedAt ?: 0) > LOOK_AGAIN_AFTER_MS }
        if (due.isEmpty() && dueReceived.isEmpty()) return@coroutineScope
        val look = launch {
            _holdSyncStatus.value = walletId
            try {
                checkSent(due)
                checkReceived(dueReceived)
                // The main wallet reconnects after the look: wait for it to leave Synced, then come back.
                withTimeoutOrNull(HOLD_AFTER_MS) {
                    WalletManager.syncStateFlow.dropWhile { it is SyncState.Synced }.first { it is SyncState.Synced }
                }
            } finally {
                _holdSyncStatus.value = null
            }
        }
        openLookJob = look
        look.join()
    }

    private var lastNetFillAt = 0L

    /**
     * Received links checked before records had a net amount: their last balance less the
     * active wallet's fee estimate for the claim sweep (one input, two outputs, at the
     * sweep's priority). The claim sheet's next check replaces it with the envelope's own
     * estimate. At most every 10 minutes.
     */
    private suspend fun fillNetAmounts() {
        val balance = store.received.value.firstOrNull { it.needsNetAmount }?.lastCheck?.balance ?: return
        if (WalletManager.syncStateFlow.value !is SyncState.Synced) return
        val now = System.currentTimeMillis()
        if (now - lastNetFillAt < NET_FILL_EVERY_MS) return
        lastNetFillAt = now
        val kit = WalletManager.kit ?: return
        val fee = withContext(Dispatchers.IO) {
            try {
                kit.estimateFee(balance, kit.receiveAddress, null, sweepAll = true, priority = EnvelopeWallet.SWEEP_PRIORITY)
            } catch (e: Exception) {
                Timber.w(e, "PaymentLinks: claim fee estimate failed (old links)")
                0L
            }
        }
        if (fee <= 0) return
        for (link in store.received.value.filter { it.needsNetAmount }) {
            store.updateReceived(link.id) { it.copy(netAmount = ReceivedLink.net(it.lastCheck?.balance ?: 0, fee)) }
        }
    }

    private suspend fun checkSent(due: List<SentLink>) {
        if (due.isEmpty()) return
        val wallet = walletHistory()
        for (link in due) {
            // A claim sheet or detail screen has the envelope runtime: leave it alone.
            if (_session.value != null || sendOpen) return
            sessionLock.withLock {
                var opened: EnvelopeWallet? = null
                try {
                    openChecked(link.id, link.link) { opened = it }?.let { snapshot ->
                        logCheck(link.number, snapshot)
                        store.updateSent(link.id) { LinkStatusRules.withCheck(it, snapshot, wallet) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "PaymentLinks: link %s: envelope open failed (sent link check)", link.number)
                } finally {
                    withContext(NonCancellable) { closeChecked(link.id, opened) }
                }
            }
        }
    }

    /** Received links whose claim no check has seen in a block yet ([ReceivedLink.settleUnconfirmed]). */
    private suspend fun checkReceived(due: List<ReceivedLink>) {
        if (due.isEmpty()) return
        val wallet = walletHistory()
        for (record in due) {
            if (_session.value != null || sendOpen) return
            val link = record.link ?: continue
            sessionLock.withLock {
                var opened: EnvelopeWallet? = null
                try {
                    openChecked(record.id, link) { opened = it }?.let { snapshot ->
                        logCheck(record.number, snapshot)
                        store.updateReceived(record.id) { it.noting(snapshot, wallet) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "PaymentLinks: link %s: envelope open failed (received link check)", record.number)
                } finally {
                    withContext(NonCancellable) { closeChecked(record.id, opened) }
                }
            }
        }
    }

    /**
     * Closes a check's envelope. A settled link's files go once its settle is in a block; until then the
     * cache stays, so wallet2 can mark a settle that left the pool failed.
     */
    private suspend fun closeChecked(id: String, opened: EnvelopeWallet?) {
        opened?.close() ?: return
        withContext(Dispatchers.IO) {
            if (id !in store.liveEnvelopeIds()) EnvelopeFiles.delete(context, id)
        }
    }

    fun stopRefresh() {
        refreshWanted = false
        refreshJob?.cancel()
        refreshJob = null
    }

    /** The list screen shows: a session that cancelled the refresh starts it again when it closes. */
    private var refreshWanted = false

    /** Waits until the main wallet's kit runs and is synced again (after an envelope closed). */
    suspend fun awaitMainWalletSynced(timeoutMs: Long = 90_000): Boolean =
        withTimeoutOrNull(timeoutMs) { SendGate.awaitReady() } != null

    /** Open Send screens. A link check stops the main wallet, so none starts while Send is open. */
    private var sendsOpen = 0

    private val sendOpen: Boolean get() = sendsOpen > 0

    /** Send opened: the link check that runs stops (its envelope closes, the main wallet starts again). */
    fun sendOpened() {
        sendsOpen++
        openLookJob?.cancel()
    }

    fun sendClosed() {
        sendsOpen = (sendsOpen - 1).coerceAtLeast(0)
    }

    override fun onCleared() {
        super.onCleared()
        sessionJob?.cancel()
        refreshJob?.cancel()
        openLookJob?.cancel()
        leftoverJob?.cancel()
        val open = envelope
        envelope = null
        if (open != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main + NonCancellable).launch { open.close() }
        }
    }

    companion object {
        private const val LIVE_INTERVAL_MS = 3_000L

        /** A received link's claim fee estimate, again while its sheet stays open (iOS polls every 15 s). */
        private const val FEE_EVERY_MS = 15_000L

        /** Old received records with no net amount: the active wallet's estimate, at most this often. */
        private const val NET_FILL_EVERY_MS = 10 * 60_000L
        private const val REFRESH_AFTER_MS = 20_000L
        private const val MAX_RESCANS = 2
        private const val REFRESH_INTERVAL_MS = 30_000L
        /** A little under a block (about 2 minutes). */
        /** A link the Wallet tab looked at this recently is not looked at again (a tab switch back and forth). */
        private const val LOOK_AGAIN_AFTER_MS = 120_000L
        /** How long after a check the Wallet tab may keep showing Synced while the main wallet reconnects. */
        private const val HOLD_AFTER_MS = 45_000L

        /** How often the Wallet tab looks at open links while it shows. */
        const val WALLET_TAB_LOOK_EVERY_MS = 10 * 60_000L
        private const val FUNDING_TXID_WAIT_MS = 60_000L
    }
}
