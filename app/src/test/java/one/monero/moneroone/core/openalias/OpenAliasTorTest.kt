package one.monero.moneroone.core.openalias

import kotlinx.coroutines.runBlocking
import one.monero.moneroone.core.network.DEFAULT_TOR_PROXY
import one.monero.moneroone.core.network.TorConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** OpenAlias follows the app's Tor setting (TorNetwork), as every other connection does, and fails closed. */
class OpenAliasTorTest {

    @Test
    fun torOffIsADirectLookup() {
        assertEquals(TorState.Off, OpenAliasTor.current { TorConfig(false, "127.0.0.1:9150") })
    }

    @Test
    fun torOnUsesTheSavedProxy() {
        assertEquals(TorState.On(TorProxy("127.0.0.1", 9150)), OpenAliasTor.current { TorConfig(true, "127.0.0.1:9150") })
        assertEquals(TorState.On(TorProxy("127.0.0.1", 9050)), OpenAliasTor.current { TorConfig(true, DEFAULT_TOR_PROXY) })
        assertEquals(TorState.On(TorProxy("::1", 9050)), OpenAliasTor.current { TorConfig(true, "[::1]:9050") })
    }

    @Test
    fun localhostProxyBecomesTheLoopbackAddress() {
        // The proxy's own name never needs a lookup: the setting keeps it numeric.
        assertEquals(TorState.On(TorProxy("127.0.0.1", 9050)), OpenAliasTor.current { TorConfig(true, "localhost:9050") })
    }

    @Test
    fun invalidProxyFailsClosed() {
        assertEquals(TorState.On(null), OpenAliasTor.current { TorConfig(true, "not a proxy") })
    }

    @Test
    fun settingNotLoadedFailsClosed() {
        assertEquals(TorState.On(null), OpenAliasTor.current { throw IOException("Connection settings are not loaded") })
    }

    @Test
    fun noSavedRouteMeansNoRequest() = runBlocking {
        // In a unit test the saved route is never loaded: the real transport refuses to start.
        val kitCalls = AtomicInteger()
        val tor = OpenAliasResolver(
            kitLookup = { _, _, _ -> kitCalls.incrementAndGet(); "" },
            torState = { TorState.On(TorProxy("127.0.0.1", 9050)) },
        )
        val name = OpenAliasName("donate@monero.one", "donate.monero.one")
        assertEquals(OpenAliasOutcome.Failed(OpenAliasFailure.TOR), tor.resolve(name) { AddressCheck.VALID })
        val direct = OpenAliasResolver(
            kitLookup = { _, _, _ -> kitCalls.incrementAndGet(); "" },
            torState = { TorState.Off },
        )
        assertEquals(OpenAliasOutcome.Failed(OpenAliasFailure.NETWORK), direct.resolve(name) { AddressCheck.VALID })
        assertEquals(0, kitCalls.get())
    }
}
