package one.monero.moneroone.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import one.monero.moneroone.core.node.NodeBenchmark
import one.monero.moneroone.core.openalias.AddressCheck
import one.monero.moneroone.core.openalias.OpenAliasConfig
import one.monero.moneroone.core.openalias.OpenAliasFailure
import one.monero.moneroone.core.openalias.OpenAliasName
import one.monero.moneroone.core.openalias.OpenAliasOutcome
import one.monero.moneroone.core.openalias.OpenAliasResolver
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.repository.PriceRepository
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TorRoutingTest {
    private lateinit var original: TorConfig
    @Before fun rememberRoute() { original = TorNetwork.current }
    @After fun restoreRoute() { TorNetwork.save(original) }

    @Test fun nodeProbeSendsHostnameToProxyWithoutLocalDns() = runBlocking<Unit> {
        LocalSocksProxy().use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            assertTrue(NodeBenchmark.measure("node-that-only-the-proxy-knows.invalid:18081", null) >= 0)
            assertEquals(LocalSocksProxy.Destination("node-that-only-the-proxy-knows.invalid", 18081), proxy.destinations.poll(2, TimeUnit.SECONDS))
        }
    }
    @Test fun onionProbeUsesTheSameRoute() = runBlocking<Unit> {
        LocalSocksProxy().use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            assertTrue(NodeBenchmark.measure("test-only.onion:18081", null) >= 0)
            assertEquals("test-only.onion", proxy.destinations.poll(2, TimeUnit.SECONDS)?.host)
        }
    }
    @Test fun onionServiceWithTlsOnlyRpcCountsAsReachable() = runBlocking<Unit> {
        LocalSocksProxy(LocalSocksProxy.Mode.CLOSE).use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            assertTrue(NodeBenchmark.measure("test-only.onion:18089", null) >= 0)
            // Clearnet nodes still need an HTTP answer.
            assertEquals(NodeBenchmark.UNREACHABLE, NodeBenchmark.measure("clearnet-node.invalid:18089", null))
        }
    }
    @Test fun onionServiceTorCannotReachIsUnreachable() = runBlocking<Unit> {
        LocalSocksProxy(LocalSocksProxy.Mode.REJECT).use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            assertEquals(NodeBenchmark.UNREACHABLE, NodeBenchmark.measure("test-only.onion:18089", null))
        }
    }
    @Test fun closedProxyDoesNotFallBackToDirectNode() = runBlocking<Unit> {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { node ->
            val unavailable = ServerSocket(0).use { it.localPort }
            TorNetwork.save(TorConfig(true, "127.0.0.1:$unavailable"))
            assertEquals(NodeBenchmark.UNREACHABLE, NodeBenchmark.measure("127.0.0.1:${node.localPort}", null))
            node.soTimeout = 300
            assertThrows(SocketTimeoutException::class.java) { node.accept().close() }
        }
    }
    @Test fun priceRequestsAlsoUseTheProxy() = runBlocking<Unit> {
        LocalSocksProxy(LocalSocksProxy.Mode.REJECT).use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            assertTrue(PriceRepository().fetchCurrentPrice(Currency.USD).isFailure)
            assertEquals("monero.one", proxy.destinations.poll(2, TimeUnit.SECONDS)?.host)
        }
    }
    @Test fun openAliasLookupUsesTheProxyWithoutLocalDns() = runBlocking<Unit> {
        LocalSocksProxy(LocalSocksProxy.Mode.REJECT).use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            val outcome = OpenAliasResolver().resolve(OpenAliasName("donate@monero.one", "donate.monero.one")) { AddressCheck.VALID }
            assertEquals(OpenAliasOutcome.Failed(OpenAliasFailure.TOR), outcome)
            val dohHosts = OpenAliasConfig.DOH_SERVERS.map { it.removePrefix("https://").substringBefore('/') }.toSet()
            val first = proxy.destinations.poll(2, TimeUnit.SECONDS)
            assertNotNull("the DoH request reached the proxy", first)
            assertTrue("${first?.host} is a DoH server name", first!!.host in dohHosts)
            assertEquals(443, first.port)
        }
    }
    @Test fun changingSettingsCancelsAnInFlightRequest() {
        LocalSocksProxy(LocalSocksProxy.Mode.HOLD).use { proxy ->
            TorNetwork.save(TorConfig(true, proxy.address))
            val executor = Executors.newSingleThreadExecutor()
            try {
                val request = executor.submit<Boolean> {
                    runCatching { TorNetwork.withClient { it.newCall(Request.Builder().url("http://test.invalid:18081").build()).execute().close() } }.isFailure
                }
                assertNotNull(proxy.destinations.poll(2, TimeUnit.SECONDS))
                TorNetwork.save(TorConfig(false, proxy.address))
                assertTrue(request.get(3, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun socksAvailabilityCheckRejectsClosedPort() = runBlocking<Unit> {
        LocalSocksProxy().use { assertTrue(TorProxyProbe.available(it.address)) }
        val unavailable = ServerSocket(0).use { it.localPort }
        assertFalse(TorProxyProbe.available("127.0.0.1:$unavailable"))
    }
}
