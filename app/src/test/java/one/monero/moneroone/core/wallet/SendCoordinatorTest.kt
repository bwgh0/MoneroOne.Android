package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendCoordinatorTest {
    @Test
    fun `leaving sending screen suppresses its late success without allowing another broadcast`() {
        val coordinator = SendCoordinator()
        val paymentA = SendFlow("wallet-a")
        val operation = coordinator.begin(paymentA, "wallet-a", true)!!
        coordinator.dismiss(paymentA)

        val paymentB = SendFlow("wallet-a")
        assertNull(coordinator.begin(paymentB, "wallet-a", true))
        val blocked = coordinator.state.value
        assertTrue(blocked is SendState.Error)
        assertEquals(paymentB, blocked.flow)

        coordinator.finish(operation)
        assertEquals(blocked, coordinator.state.value)
        assertNotNull(coordinator.begin(paymentB, "wallet-a", true))
    }

    @Test
    fun `completion while no send screen is open cannot prepopulate the next payment`() {
        val coordinator = SendCoordinator()
        val flow = SendFlow("wallet-a")
        val operation = coordinator.begin(flow, "wallet-a", true)!!
        coordinator.dismiss(flow)
        coordinator.finish(operation)
        assertEquals(SendState.Idle, coordinator.state.value)
    }

    @Test
    fun `confirmation collected for another wallet is rejected`() {
        val coordinator = SendCoordinator()
        assertNull(coordinator.begin(SendFlow("wallet-a"), "wallet-b", true))
        assertTrue(coordinator.state.value is SendState.Error)
        assertNotNull(coordinator.begin(SendFlow("wallet-b"), "wallet-b", true))
    }

    @Test
    fun `duplicate confirmations and stale completions cannot release a newer operation`() {
        val coordinator = SendCoordinator()
        val flow = SendFlow("wallet-a")
        val first = coordinator.begin(flow, "wallet-a", true)!!
        assertNull(coordinator.begin(flow, "wallet-a", true))
        assertEquals(SendState.Sending(flow), coordinator.state.value)
        coordinator.finish(first, "failed")
        val second = coordinator.begin(flow, "wallet-a", true)!!
        coordinator.finish(first)
        assertEquals(SendState.Sending(flow), coordinator.state.value)
        assertNull(coordinator.begin(flow, "wallet-a", true))
        coordinator.finish(second)
        assertEquals(SendState.Success(flow, ""), coordinator.state.value)
    }

    @Test
    fun `disposing an older screen cannot dismiss a new screens result`() {
        val coordinator = SendCoordinator()
        val old = SendFlow("wallet-a")
        val current = SendFlow("wallet-b")
        val operation = coordinator.begin(current, "wallet-b", true)!!
        coordinator.finish(operation)
        coordinator.dismiss(old)
        assertEquals(SendState.Success(current, ""), coordinator.state.value)
    }

    @Test
    fun `missing wallet and invalid amount do not acquire broadcast gate`() {
        val coordinator = SendCoordinator()
        assertNull(coordinator.begin(SendFlow(null), null, true))
        assertNull(coordinator.begin(SendFlow("wallet-a"), "wallet-a", false))
        assertNotNull(coordinator.begin(SendFlow("wallet-a"), "wallet-a", true))
    }

    @Test
    fun `success carries the txid for the Sent screen`() {
        val coordinator = SendCoordinator()
        val flow = SendFlow("wallet-a")
        val operation = coordinator.begin(flow, "wallet-a", true)!!
        coordinator.finish(operation, txHash = "abc123")
        assertEquals(SendState.Success(flow, "abc123"), coordinator.state.value)
    }
}
