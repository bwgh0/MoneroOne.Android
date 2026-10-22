package one.monero.moneroone.core.username

import org.junit.Assert.*
import org.junit.Test

class UsernameStoreTest {
    private class Memory : UsernamePersistence {
        var state = UsernameState()
        var fail = false
        override fun read() = state
        override fun write(state: UsernameState) {
            if (fail) throw IllegalStateException("test write failure")
            this.state = state
        }
    }
    private fun binding(wallet: String = "A", network: String = "mainnet", hash: String = "genesis") =
        UsernameBinding(wallet, network, "joe", "Joe", "public-test-address", hash, "record", 1_800_000_000_000L)
    private fun pending() = PendingUsernameClaim("A", "mainnet", "joe", "Joe", "address", "nonce", "unsigned", "hash", "sig", "key", "rules", "ruleshash")
    @Test fun bindingSurvivesReloadAndRemainsScopedToWalletAndNetwork() {
        val disk = Memory(); val store = UsernameStore(disk)
        store.accept(binding())
        val reloaded = UsernameStore(disk).state.value
        assertEquals("@Joe", reloaded.binding("A", "mainnet")?.shortName)
        assertEquals("joe@monero.one", reloaded.binding("A", "mainnet")?.externalAddress)
        assertNull(reloaded.binding("B", "mainnet")); assertNull(reloaded.binding("A", "testnet"))
        assertFalse(reloaded.hasLocalClaimSlot(1_800_000_000_001L))
        assertTrue(reloaded.hasLocalClaimSlot(1_870_000_000_000L))
    }
    @Test fun failedWriteKeepsPendingAndDoesNotPublishClaim() {
        val disk = Memory(); val store = UsernameStore(disk)
        store.savePending(pending()); disk.fail = true
        try { store.accept(binding()); fail("Expected persistence failure") } catch (_: UsernameException) { }
        assertNotNull(store.state.value.pending)
        assertTrue(store.state.value.bindings.isEmpty()); assertNull(store.state.value.nextFreeClaim)
        assertEquals(pending(), UsernameStore(disk).state.value.pending)
    }
    @Test fun anotherGenesisCannotReplaceAnExistingBinding() {
        val store = UsernameStore(Memory()); store.accept(binding())
        try { store.accept(binding(hash = "replacement")); fail("Replaced the owner binding") } catch (_: UsernameException) { }
        assertEquals("genesis", store.state.value.binding("A", "mainnet")?.genesisHash)
    }
    @Test fun unreadableStorageFailsClosed() {
        val store = UsernameStore(object : UsernamePersistence {
            override fun read(): UsernameState = throw IllegalStateException("test unreadable store")
            override fun write(state: UsernameState) { fail("Must not overwrite unreadable storage") }
        })
        assertTrue(store.storageError)
        try { store.savePending(pending()); fail("Expected closed storage") } catch (_: UsernameException) { }
    }
}
