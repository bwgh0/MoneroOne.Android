package one.monero.moneroone.core.username

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The claim flow against an in-memory registry and a stand-in for the Rust core. */
class UsernameClaimFlowTest {
    private val disk = MemoryUsernames()
    private val store = UsernameStore(disk)
    private val api = FakeRegistryApi().also { it.store = store }
    private val made = mutableListOf<String>()
    private val signed = mutableListOf<Pair<String, String>>()
    private var current = true
    private var changeWalletAfterSubaddress = false

    private fun claim(handle: String = "Joe", wallet: String = "wallet-a", developmentClaims: Boolean = true): UsernameBinding = runBlocking {
        testRegistry(api, developmentClaims = developmentClaims).claim(handle, wallet, "mainnet", store,
            makeSubaddress = {
                "subaddress-${made.size + 1}".also { made += it; if (changeWalletAfterSubaddress) current = false }
            },
            sign = { message, address -> signed += message to address; "signature" },
            isCurrentWallet = { current })
    }

    private fun failure(block: () -> Unit): Exception {
        try { block() } catch (e: Exception) { return e }
        fail("Expected a failure"); throw AssertionError()
    }

    private fun <T : Any> present(value: T?): T { assertNotNull(value); return value!! }

    private fun code(block: () -> Unit) = (failure(block) as UsernameException).code

    @Test fun aClaimBindsTheNameToTheWallet() {
        val binding = claim()
        assertEquals("joe", binding.handle)
        assertEquals("Joe", binding.displayHandle)
        assertEquals("@Joe", binding.shortName)
        assertEquals(listOf("subaddress-1"), made)
        assertEquals(listOf("message:joe" to "subaddress-1"), signed)
        assertEquals(binding, store.state.value.binding("wallet-a", "mainnet"))
        assertNull(store.state.value.pending)
        assertFalse(store.state.value.hasLocalClaimSlot())
        // Persisted, so a relaunch shows it.
        assertEquals(binding, UsernameStore(disk).state.value.binding("wallet-a", "mainnet"))
    }

    @Test fun theSignedClaimIsSavedBeforeThePost() {
        claim()
        val saved = present(api.savedAtPost)
        assertEquals("joe", saved.handle)
        assertEquals("subaddress-1", saved.subaddress)
        assertEquals("signature", saved.ownerSignature)
        assertEquals(testConfiguration.rulesHash, saved.rulesHash)
    }

    @Test fun aLostReplyKeepsThePendingClaimAndTheNextClaimFinishesIt() {
        api.loseReply = true
        assertTrue(failure { claim() } is IOException)
        val pending = present(store.state.value.pending)
        assertEquals(pending, UsernameStore(disk).state.value.pending)
        api.loseReply = false
        val binding = claim()
        assertEquals(pending.subaddress, binding.subaddress)
        assertEquals(1, api.claims)
        assertEquals(listOf("subaddress-1"), made)
    }

    @Test fun anEarlierPostThatLandsAfterTheRecheckStillFinishes() {
        api.loseReply = true
        failure { claim() }
        api.loseReply = false
        val pending = store.state.value.pending!!
        val landed = api.handles.remove("joe")!!
        // Free at the re-check; the earlier POST lands before the next lookup.
        var lookups = 0
        api.afterRecords = { if (++lookups == 1) api.handles["joe"] = landed }
        val binding = claim()
        assertEquals(pending.subaddress, binding.subaddress)
        assertEquals(listOf("subaddress-1"), made)
    }

    @Test fun aTakenNameStopsBeforeASubaddress() {
        api.handles["joe"] = listOf("admitted:someone-else")
        assertEquals("name.unavailable", code { claim() })
        assertTrue(made.isEmpty())
        assertNull(store.state.value.pending)
    }

    @Test fun aWalletChangeStopsBeforeSigning() {
        changeWalletAfterSubaddress = true
        assertEquals("wallet_changed", code { claim() })
        assertTrue(signed.isEmpty())
        assertNull(store.state.value.pending)
        assertEquals(0, api.claims)
    }

    @Test fun anotherWalletCannotStart() {
        current = false
        assertEquals("wallet_changed", code { claim() })
        current = true
        assertEquals("wallet_changed", code { claim(wallet = "") })
        assertEquals(0, api.claims)
    }

    @Test fun anExpiredNonceOrAUsedSlotClearsThePendingClaim() {
        for (error in listOf("nonce.invalid", "device.slot_used", "name.unavailable")) {
            api.claimError = error
            assertEquals(error, code { claim() })
            assertNull(error, store.state.value.pending)
        }
    }

    @Test fun aServerErrorKeepsThePendingClaim() {
        api.claimError = "server"
        assertEquals("server", code { claim() })
        assertNotNull(store.state.value.pending)
    }

    @Test fun aStorageFailureStopsBeforeThePost() {
        disk.fail = true
        assertEquals("storage", code { claim() })
        assertEquals(0, api.claims)
    }

    @Test fun oneClaimAtATime() {
        assertTrue(store.claimMutex.tryLock())
        try { assertEquals("pending", code { claim() }) } finally { store.claimMutex.unlock() }
        assertTrue(made.isEmpty())
    }

    @Test fun aReleaseBuildPostsNoDevelopmentClaim() {
        assertEquals("not_configured", code { claim(developmentClaims = false) })
        assertEquals(0, api.claims)
    }
}
