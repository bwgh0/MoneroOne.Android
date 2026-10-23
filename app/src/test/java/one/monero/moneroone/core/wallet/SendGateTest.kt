package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.MoneroKit.SyncError
import io.horizontalsystems.monerokit.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class SendGateTest {

    private fun wait(
        syncState: SyncState,
        kitRunning: Boolean = true,
        envelopeBusy: Boolean = false,
        online: Boolean = true,
        nodeLoginFailed: Boolean = false
    ) = SendGate.waitOf(syncState, kitRunning, envelopeBusy, online, nodeLoginFailed)

    @Test fun `a running, synced wallet is ready`() =
        assertEquals(SendWait.Ready, wait(SyncState.Synced))

    @Test fun `a link check stops the wallet and leaves a stale Synced, so the review waits`() {
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = true)), wait(SyncState.Synced, kitRunning = false, envelopeBusy = true))
        // The link's wallet closed, the main wallet has not started yet: still a stale Synced.
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = false)), wait(SyncState.Synced, kitRunning = false))
    }

    @Test fun `a link wallet that opens while the main one still runs blocks the build`() =
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = true)), wait(SyncState.Synced, envelopeBusy = true))

    @Test fun `connecting, loading and scanning wait with the ladder state`() {
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = false)), wait(SyncState.Connecting(waiting = false), kitRunning = false))
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = true)), wait(SyncState.Connecting(waiting = true), kitRunning = false))
        val scanning = SyncState.Syncing(0.64, 1200)
        assertEquals(SendWait.Syncing(scanning), wait(scanning))
    }

    @Test fun `a wallet that has not started yet waits as connecting, not as an error`() {
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = false)), wait(SyncState.NotSynced(SyncError.NotStarted), kitRunning = false))
        assertEquals(SendWait.Syncing(SyncState.Connecting(waiting = false)), wait(SyncState.NotSynced(SyncError.NotStarted)))
    }

    @Test fun `offline wins over every wallet state`() {
        assertEquals(SendWait.Offline, wait(SyncState.Synced, online = false))
        assertEquals(SendWait.Offline, wait(SyncState.NotSynced(SyncError.StartError("no connection")), online = false))
        assertEquals(SendWait.Offline, wait(SyncState.Synced, kitRunning = false, envelopeBusy = true, online = false))
    }

    @Test fun `a node that was not reached is a real failure, running or not`() {
        val failed = SyncState.NotSynced(SyncError.StartError("Status_Error: no connection to daemon"))
        assertEquals(SendWait.NotConnected(nodeLoginFailed = false), wait(failed, kitRunning = false))
        assertEquals(SendWait.NotConnected(nodeLoginFailed = false), wait(SyncState.NotSynced(IllegalStateException("refresh failed"))))
        assertEquals(SendWait.NotConnected(nodeLoginFailed = true), wait(failed, kitRunning = false, nodeLoginFailed = true))
    }

    @Test fun `a build fails for real only once the network has settled`() {
        assertEquals(false, SendGate.networkSettling(now = 50_000, changedAt = 0))
        assertEquals(true, SendGate.networkSettling(now = 50_000, changedAt = 49_926))
        assertEquals(true, SendGate.networkSettling(now = 50_000, changedAt = 40_001))
        assertEquals(false, SendGate.networkSettling(now = 50_000, changedAt = 40_000))
    }

    @Test fun `a running link check hides the old error of the main wallet`() =
        assertEquals(
            SendWait.Syncing(SyncState.Connecting(waiting = true)),
            wait(SyncState.NotSynced(SyncError.StartError("old")), kitRunning = false, envelopeBusy = true)
        )
}
