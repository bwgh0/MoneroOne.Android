package one.monero.moneroone.core.username

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Re-checks of a saved pending claim: finish it, clear it, or keep it while the result is unknown. */
class UsernamePendingRecheckTest {
    private val api = FakeRegistryApi()
    private val store = UsernameStore(MemoryUsernames())
    private var subaddresses = 0

    private fun claim(handle: String, wallet: String = "wallet-a"): UsernameBinding = runBlocking {
        testRegistry(api).claim(handle, wallet, "mainnet", store,
            makeSubaddress = { "subaddress-${++subaddresses}" }, sign = { _, _ -> "signature" }, isCurrentWallet = { true })
    }

    private fun <T : Any> assertNotNull(value: T?): T { org.junit.Assert.assertNotNull(value); return value!! }

    private fun failure(block: () -> Unit): Exception {
        try { block() } catch (e: Exception) { return e }
        fail("Expected a failure"); throw AssertionError()
    }

    /** A claim whose reply is lost, so its pending claim stays saved. */
    private fun lostClaim(handle: String = "joe", wallet: String = "wallet-a"): PendingUsernameClaim {
        api.loseReply = true
        assertTrue(failure { claim(handle, wallet) } is IOException)
        api.loseReply = false
        return assertNotNull(store.state.value.pending)
    }

    @Test fun theNamePointsToThisWalletFinishesTheClaim() {
        val pending = lostClaim()
        val binding = claim("joe")
        assertEquals("joe", binding.handle)
        assertEquals(pending.subaddress, binding.subaddress)
        assertNull(store.state.value.pending)
        assertNotNull(store.state.value.binding("wallet-a", "mainnet"))
        assertEquals(1, api.claims)
    }

    @Test fun someoneElseHoldsTheNameClearsTheClaimWithTheTakenMessage() {
        lostClaim()
        api.handles["joe"] = listOf("admitted:someone-else")
        val error = failure { claim("joe") } as UsernameException
        assertEquals("name.unavailable", error.code)
        assertEquals("This username is unavailable.", error.message)
        assertNull(store.state.value.pending)
        // The user can claim another name at once.
        assertEquals("joseph", claim("joseph").handle)
    }

    @Test fun theNameIsFreeClearsTheClaimAndClaimsAgain() {
        val first = lostClaim()
        api.handles.remove("joe")
        val binding = claim("joe")
        assertEquals("joe", binding.handle)
        assertNotEquals(first.subaddress, binding.subaddress)
        assertNull(store.state.value.pending)
    }

    @Test fun anUnreachableRegistryKeepsTheClaim() {
        val pending = lostClaim()
        api.unreachable = true
        assertTrue(failure { claim("joe") } is IOException)
        assertEquals(pending, store.state.value.pending)
        // The next re-check finishes it.
        api.unreachable = false
        assertEquals(pending.subaddress, claim("joe").subaddress)
    }

    @Test fun anotherWalletsClaimBlocksOnlyWhileItsResultIsUnknown() {
        lostClaim(wallet = "wallet-a")
        api.unreachable = true
        assertEquals("pending", (failure { claim("bob", wallet = "wallet-b") } as UsernameException).code)
        assertNotNull(store.state.value.pending)
        // Someone else took wallet A's name: the slot is free again for wallet B.
        api.unreachable = false
        api.handles["joe"] = listOf("admitted:someone-else")
        assertEquals("bob", claim("bob", wallet = "wallet-b").handle)
        assertNull(store.state.value.binding("wallet-a", "mainnet"))
    }

    @Test fun anotherWalletsFinishedClaimUsesThePhonesSlot() {
        lostClaim(wallet = "wallet-a")
        assertEquals("device.slot_used", (failure { claim("bob", wallet = "wallet-b") } as UsernameException).code)
        assertEquals("joe", store.state.value.binding("wallet-a", "mainnet")?.handle)
        assertNull(store.state.value.pending)
    }

    @Test fun aClaimSignedUnderOtherPinsIsCleared() {
        lostClaim()
        val repinned = testConfiguration.copy(rulesHash = "c".repeat(64))
        api.handles.clear()
        val binding = runBlocking {
            testRegistry(api, repinned).claim("joe", "wallet-a", "mainnet", store,
                makeSubaddress = { "subaddress-new" }, sign = { _, _ -> "signature" }, isCurrentWallet = { true })
        }
        assertEquals("subaddress-new", binding.subaddress)
    }

    @Test fun deletingTheWalletClearsItsClaim() {
        lostClaim(wallet = "wallet-a")
        store.clearPending("wallet-b")
        assertNotNull(store.state.value.pending)
        store.clearPending("wallet-a")
        assertNull(store.state.value.pending)
        lostClaim(handle = "kim", wallet = "wallet-c")
        store.clearPending(null)
        assertNull(store.state.value.pending)
    }
}
