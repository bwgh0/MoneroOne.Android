package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.paymentlinks.EnvelopeWallet

/** What Send's review waits for before it builds a transaction ([SendGate]). */
sealed interface SendWait {
    /** The main wallet runs and is synced: the review builds. */
    data object Ready : SendWait

    /** The main wallet starts, connects, loads or scans. [syncState]: what the sync ladder shows. */
    data class Syncing(val syncState: SyncState) : SendWait

    /** No network: a neutral state. The wait goes on by itself when the network returns. */
    data object Offline : SendWait

    /** The network is up and the node was not reached. [nodeLoginFailed]: the node refused the RPC login. */
    data class NotConnected(val nodeLoginFailed: Boolean) : SendWait
}

/**
 * Send opens at any time. Its review builds the transaction, so the review waits until the main wallet runs and
 * is synced, for every send (address, name, QR, paste, deep link, payment link). A payment-link check stops the
 * main wallet's kit and leaves its last state, often Synced, until the kit starts again: "synced" alone is not
 * enough, the kit must run too.
 */
object SendGate {
    /** How often [states] reads the wallet: the kit's run state and a link check have no flow. */
    const val POLL_MS = 250L

    /**
     * [syncState]: the wallet's sync state, as the balance card shows it. [kitRunning]: the main wallet's kit has
     * started. [envelopeBusy]: a payment link's wallet opens, runs or closes, and the main wallet stops for it.
     */
    fun waitOf(
        syncState: SyncState,
        kitRunning: Boolean,
        envelopeBusy: Boolean,
        online: Boolean,
        nodeLoginFailed: Boolean = false
    ): SendWait = when {
        !online -> SendWait.Offline
        // The main wallet starts again once the link's wallet closes.
        envelopeBusy -> SendWait.Syncing(SyncState.Connecting(waiting = true))
        syncState is SyncState.NotSynced && syncState.error !is MoneroKit.SyncError.NotStarted ->
            SendWait.NotConnected(nodeLoginFailed)
        // Stopped or starting: its last state may be a stale Synced.
        !kitRunning -> SendWait.Syncing(syncState as? SyncState.Connecting ?: SyncState.Connecting(waiting = false))
        syncState is SyncState.Synced -> SendWait.Ready
        syncState is SyncState.NotSynced -> SendWait.Syncing(SyncState.Connecting(waiting = false))
        else -> SendWait.Syncing(syncState)
    }

    /** The main wallet's kit runs. */
    private fun kitRunning(): Boolean = WalletManager.kit?.isStarted == true

    /** The live [SendWait], read every [POLL_MS]. */
    fun states(
        syncState: () -> SyncState = { WalletManager.syncStateFlow.value },
        nodeLoginFailed: () -> Boolean = { false }
    ): Flow<SendWait> = flow {
        while (true) {
            emit(now(syncState(), nodeLoginFailed()))
            delay(POLL_MS)
        }
    }.distinctUntilChanged()

    /** The [SendWait] at this moment. */
    fun now(syncState: SyncState = WalletManager.syncStateFlow.value, nodeLoginFailed: Boolean = false): SendWait =
        waitOf(syncState, kitRunning(), EnvelopeWallet.isBusy, NetworkMonitor.isConnected.value, nodeLoginFailed)

    /** Returns once the main wallet runs and is synced. */
    suspend fun awaitReady(syncState: () -> SyncState = { WalletManager.syncStateFlow.value }) {
        states(syncState).first { it == SendWait.Ready }
    }
}
