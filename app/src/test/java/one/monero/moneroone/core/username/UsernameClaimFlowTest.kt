package one.monero.moneroone.core.username

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import one.monero.moneroone.BuildConfig
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
    private var claimKeys = 0
    /** The request hashes that the phone asked Play Integrity to sign. */
    private val requestHashes = mutableListOf<String>()

    private fun claim(handle: String = "Joe", wallet: String = "wallet-a", developmentClaims: Boolean = true,
                      integrity: IntegrityTokenSource? = null): UsernameBinding = runBlocking {
        testRegistry(api, developmentClaims = developmentClaims, integrity = integrity).claim(handle, wallet, "mainnet", store,
            makeSubaddress = {
                "subaddress-${made.size + 1}".also { made += it; if (changeWalletAfterSubaddress) current = false }
            },
            sign = { message, address -> signed += message to address; "signature" },
            // Made from the wallet at each claim, as the app does.
            claimKey = { claimKeys++; UsernameDeviceProof.claimKey(testViewKey) },
            isCurrentWallet = { current })
    }

    private fun tokens(token: String = "token") = IntegrityTokenSource { requestHashes += it; token }
    private fun failing(failure: IntegrityFailure) = IntegrityTokenSource { throw IntegrityTokenException(failure) }

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
        assertNull(store.state.value.pending)
        assertEquals(0, claimKeys)
    }

    @Test fun aDebugBuildWithoutACloudProjectSendsTheDevelopmentProof() {
        claim()
        if (BuildConfig.DEBUG) {
            assertEquals(UsernameDeviceProof.development(store.state.value.deviceId), api.device)
            assertEquals(0, claimKeys)
        }
    }

    @Test fun aPlayIntegrityClaimSendsTheTokenAndTheClaimKey() {
        val binding = claim(integrity = tokens("integrity-token"), developmentClaims = false)
        val saved = present(api.savedAtPost)
        val digest = UsernameDeviceProof.claimDigest(saved)
        assertEquals(listOf(UsernameDeviceProof.requestHash(digest)), requestHashes)
        assertEquals(UsernameDeviceProof.android("integrity-token", testClaimKey), api.device)
        assertEquals(1, claimKeys)
        assertEquals("joe", binding.handle)
    }

    @Test fun aRetryOfTheSameClaimSendsTheSameClaimKey() {
        api.loseReply = true
        failure { claim(integrity = tokens(), developmentClaims = false) }
        val first = present(api.device)
        api.handles.clear()
        api.loseReply = false
        claim(integrity = tokens(), developmentClaims = false)
        val second = present(api.device)
        assertEquals(proofClaimKey(first), proofClaimKey(second))
        // A new nonce binds the retry, so its token asks for another hash.
        assertEquals(2, requestHashes.distinct().size)
    }

    @Test fun aPhoneThatCannotMakeATokenSavesNoPendingClaim() {
        for ((failure, release) in listOf(IntegrityFailure.PERMANENT to "device.unsupported", IntegrityFailure.TRANSIENT to "device.unavailable",
                                          IntegrityFailure.NOT_CONFIGURED to "not_configured")) {
            assertEquals(release, code { claim(integrity = failing(failure), developmentClaims = false) })
            assertNull(store.state.value.pending)
        }
        assertEquals(0, api.claims)
    }

    private fun proofClaimKey(device: DeviceProof): String =
        kotlinx.serialization.json.Json.parseToJsonElement(String(java.util.Base64.getUrlDecoder().decode(device.proof)))
            .let { (it as kotlinx.serialization.json.JsonObject)["claim_key"].toString() }
}
