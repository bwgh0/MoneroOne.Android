package one.monero.moneroone.core.username

import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** A name this wallet already holds comes back with no new claim (iOS parity: UsernameRegistry.restore). */
class UsernameRestoreTest {
    private val disk = MemoryUsernames()
    private val store = UsernameStore(disk)
    private val api = FakeRegistryApi().also { it.store = store }
    private var current = true
    private val asked = mutableListOf<String>()
    /** The open wallet's subaddresses. */
    private var own = setOf(OWNER)

    /** By default, a new subaddress, a signature or a claim key fails the test. [fresh]: a new claim may run. */
    private fun claim(handle: String = "Joe", network: String = "mainnet", owns: suspend (String) -> Boolean = { asked += it; it in own },
                      fresh: Boolean = false): UsernameBinding = runBlocking {
        testRegistry(api).claim(handle, "wallet-a", network, store,
            makeSubaddress = { if (!fresh) fail("A held name must not make a subaddress"); "subaddress-new" },
            sign = { _, _ -> if (!fresh) fail("A held name must not be signed again"); "signature" },
            claimKey = { if (!fresh) fail("A held name must not read the claim key"); testClaimKey },
            ownsSubaddress = owns, isCurrentWallet = { current })
    }

    /** `GET /v1/handles/joe` for a held name whose whole chain is one genesis record of [OWNER]. */
    private fun held(record: String = genesisRecord("joe", OWNER, HASH)) =
        HeldName(listOf(HeldRecord(record, HASH)), handle = "joe", owner = OWNER, genesisHash = HASH, tombstoned = false,
            headSeq = 0, headHash = HASH)

    private fun code(block: () -> Unit): String {
        try { block() } catch (e: UsernameException) { return e.code }
        fail("Expected a failure"); throw AssertionError()
    }

    @Test fun ownNameComesBackWithNoClaim() {
        api.found["joe"] = held()
        val restored = claim()
        assertEquals(listOf("info", "lookup joe"), api.calls)
        assertEquals(listOf(OWNER), asked)
        assertEquals("joe", restored.handle)
        assertEquals("Joe", restored.displayHandle)
        assertEquals(OWNER, restored.subaddress)
        assertEquals(HASH, restored.genesisHash)
        assertEquals(genesisRecord("joe", OWNER, HASH), restored.record)
        assertEquals(restored, store.state.value.binding("wallet-a", "mainnet"))
        assertEquals(restored, UsernameStore(disk).state.value.binding("wallet-a", "mainnet"))
        assertNull(store.state.value.pending)
        // No claim ran on this phone: its free claim is still there.
        assertNull(store.state.value.nextFreeClaim)
        assertTrue(store.state.value.hasLocalClaimSlot())
    }

    @Test fun aRestoreKeepsThePhonesClaimDate() {
        val later = System.currentTimeMillis() + 86_400_000L
        disk.state = UsernameState(nextFreeClaim = later)
        store.reload()
        api.found["joe"] = held()
        claim()
        assertEquals(later, store.state.value.nextFreeClaim)
    }

    @Test fun nameOfAnotherWalletStaysUnavailable() {
        // (the open wallet's subaddresses, the lookup's owner field)
        val rows = listOf(
            // The name points to a subaddress of another wallet.
            setOf("public-test-address") to OWNER,
            // The lookup names this wallet, but the signed record points elsewhere.
            setOf("public-test-address") to "public-test-address",
        )
        for ((wallet, owner) in rows) {
            own = wallet
            api.found["joe"] = held().copy(owner = owner)
            assertEquals(owner, "name.unavailable", code { claim() })
            assertTrue(owner, store.state.value.bindings.isEmpty())
            assertTrue(owner, store.state.value.hasLocalClaimSlot())
        }
        assertEquals(0, api.claims)
    }

    @Test fun tombstonedOldOrWrongNetworkRecordStaysUnavailable() {
        val later = "2".repeat(64)
        val genesis = HeldRecord(genesisRecord("joe", OWNER, HASH), HASH)
        // (case, network, the lookup reply)
        val rows = listOf<Triple<String, String, HeldName>>(
            Triple("tombstoned", "mainnet", held().copy(tombstoned = true, owner = OWNER)),
            Triple("no tombstone flag", "mainnet", held().copy(tombstoned = null)),
            Triple("no owner", "mainnet", held().copy(owner = null)),
            Triple("a later head", "mainnet", held().copy(headSeq = 1, headHash = later)),
            Triple("no head", "mainnet", held().copy(headSeq = null, headHash = null)),
            Triple("two records", "mainnet", held().copy(headSeq = 1, headHash = later,
                records = listOf(genesis, HeldRecord(genesisRecord("joe", OWNER, later), later)))),
            Triple("the record is not the named head", "mainnet", held().copy(genesisHash = later, headHash = later,
                records = listOf(HeldRecord(genesisRecord("joe", OWNER, HASH), later)))),
            Triple("a record without its hash", "mainnet", held().copy(records = listOf(HeldRecord(genesisRecord("joe", OWNER, HASH))))),
            Triple("a forged signature", "mainnet", held(record = genesisRecord("joe", OWNER, HASH).dropLast(2) + "AA")),
            Triple("another registrar", "mainnet", held(record = genesisRecord("joe", OWNER, HASH, registrarKey = "c".repeat(64)))),
            Triple("other rules", "mainnet", held(record = genesisRecord("joe", OWNER, HASH, rulesHash = "c".repeat(64)))),
            Triple("another name", "mainnet", held().copy(handle = "jof")),
            Triple("the record of another name", "mainnet", held(record = genesisRecord("jof", OWNER, HASH))),
            Triple("wrong network", "testnet", held()),
        )
        for ((name, network, reply) in rows) {
            api.found["joe"] = reply
            assertEquals(name, "name.unavailable", code { claim(network = network) })
            assertTrue(name, store.state.value.bindings.isEmpty())
            assertTrue(name, store.state.value.hasLocalClaimSlot())
        }
        assertEquals(0, api.claims)
    }

    @Test fun theWalletIsAskedOnlyForAVerifiedRecord() {
        // The wallet may add the subaddress to its list, so a reply or a record that fails a check never asks.
        for (reply in listOf(held().copy(headSeq = 1), held(record = genesisRecord("joe", OWNER, HASH).dropLast(2) + "AA"),
                held(record = genesisRecord("joe", OWNER, HASH, network = "testnet")))) {
            api.found["joe"] = reply
            assertEquals("name.unavailable", code { claim() })
        }
        assertTrue(asked.isEmpty())
    }

    @Test fun aWalletThatIsNotOpenYetShowsWhy() {
        api.found["joe"] = held()
        val connecting = SyncState.Connecting(waiting = false)
        assertEquals("wallet.opening", code { claim(owns = { throw UsernameException.walletNotReady(connecting) }) })
        val failed = SyncState.NotSynced(MoneroKit.SyncError.StartError("Connection refused"))
        assertEquals("wallet.offline", code { claim(owns = { throw UsernameException.walletNotReady(failed) }) })
        assertTrue(store.state.value.bindings.isEmpty())
        assertNull(store.state.value.pending)
        assertTrue(store.state.value.hasLocalClaimSlot())
    }

    @Test fun aWalletChangeDuringTheOwnershipCheckStops() {
        api.found["joe"] = held()
        assertEquals("wallet_changed", code { claim(owns = { current = false; true }) })
        assertTrue(store.state.value.bindings.isEmpty())
    }

    @Test fun anotherWalletCannotRestore() {
        api.found["joe"] = held()
        current = false
        assertEquals("wallet_changed", code { claim() })
        assertTrue(asked.isEmpty())
    }

    /** This wallet's claim from another phone won: the lost claim of this phone is cleared and the name comes back. */
    @Test fun thisWalletsNameFromAnotherPhoneComesBackAfterALostClaim() {
        api.loseReply = true
        try { claim(fresh = true); fail("Expected the lost reply") } catch (_: IOException) { }
        api.loseReply = false
        assertTrue(store.state.value.pending != null)
        api.handles.remove("joe")
        api.found["joe"] = held()
        val restored = claim()
        assertEquals(OWNER, restored.subaddress)
        assertNull(store.state.value.pending)
        assertTrue(store.state.value.hasLocalClaimSlot())
        assertEquals(1, api.claims)
    }

    private companion object {
        const val OWNER = "subaddress-owner"
        val HASH = "1".repeat(64)
    }
}
