package one.monero.moneroone.core.network

import one.monero.moneroone.core.wallet.WalletFilePasswords
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.util.Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import one.monero.moneroone.core.node.NodeBenchmark
import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.repository.PriceRepository
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Opt-in checks against a live, bootstrapped Tor SOCKS port: `-e torProxy <ip:port>`, for example the
 * host's Tor through the emulator's host loopback alias. Skipped without it, so CI never needs the network.
 */
@RunWith(AndroidJUnit4::class)
class LiveTorSmokeTest {
    private val proxy: String? = InstrumentationRegistry.getArguments().getString("torProxy")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Public golden-vector wallet, with no spend key.
    private val address = "43Xuqb8woKbELkxbc4U8ZEMk87rx8VingbtWmxXpFiJK6mKHJuK8bGGTrndC4y6DmGPdwQDyJaWgu6ZXCKNfeoRSVMTUBCX"
    private val viewKey = "f6375bd99c5d6ba250660fe1bda555cf1eee558a5076207afb7b12602b66980b"

    @Before fun needsLiveTor() = assumeTrue("Needs -e torProxy <ip:port> of a bootstrapped Tor", !proxy.isNullOrBlank())

    private fun withKit(node: String, block: suspend (MoneroKit) -> Unit) = runBlocking {
        val id = "live-tor-test-${UUID.randomUUID()}"
        val kit = MoneroKit.getInstance(context, Seed.WatchOnly(address, viewKey),
            MoneroKit.restoreHeightForNewWallet().toString(), id, WalletFilePasswords.throwaway(), node, false, parseProxyEndpoint(proxy!!)!!.address)
        try { block(kit) } finally {
            kit.release()
            kit.stop()
            Helper.getWalletRoot(context).listFiles()?.filter { it.name == id || it.name.startsWith("$id.") }?.forEach { it.delete() }
        }
    }

    /** A fee estimate is a wallet2 RPC: it succeeds only after the node answered through the proxy. */
    private suspend fun assertNodeAnswersThroughTor(kit: MoneroKit) {
        kit.start()
        assertTrue("Wallet start through Tor: ${kit.syncStateFlow.value}", kit.isStarted)
        val fee = withContext(Dispatchers.IO) { kit.estimateFee(1_000_000_000L, address, null) }
        assertTrue("Fee from the node through Tor: $fee", fee > 0)
    }

    @Test fun nativeWalletReachesAClearnetNodeThroughTor() = withKit("xmr-node.cakewallet.com:18081") {
        assertNodeAnswersThroughTor(it)
    }

    @Test fun nativeWalletReachesTheMoneroOneOnionNodes() {
        val down = DefaultNodes.TOR.filter { node ->
            runCatching { withKit(node.uri) { assertNodeAnswersThroughTor(it) } }.isFailure
        }
        assertTrue("Wallet cannot reach through Tor: ${down.map { it.name }}", down.isEmpty())
    }

    @Test fun moneroOneOnionNodesAnswerThroughTor() = withTorRoute {
        val down = DefaultNodes.TOR.filter { NodeBenchmark.measure(it.uri, null) < 0 }
        assertTrue("Node check cannot reach through Tor: ${down.map { it.name }}", down.isEmpty())
    }

    @Test fun httpExitsThroughTorForPricesAndNodeChecks() = withTorRoute {
        assertTrue("The SOCKS service answers", TorProxyProbe.available(proxy!!))
        val result = TorNetwork.withClient { client ->
            client.newCall(Request.Builder().url("https://check.torproject.org/api/ip").build()).execute().use {
                assertTrue("Tor check HTTP ${it.code}", it.isSuccessful)
                Json.parseToJsonElement(it.body!!.string()).jsonObject
            }
        }
        assertTrue("The Android HTTP client must exit through Tor", result.getValue("IsTor").jsonPrimitive.boolean)
        val price = PriceRepository().fetchCurrentPrice(Currency.USD)
        assertTrue("Price request through Tor: ${price.exceptionOrNull()?.message}", price.isSuccess)
        assertTrue("Clearnet node check through Tor", NodeBenchmark.measure("xmr-node.cakewallet.com:18081", null) >= 0)
    }

    /** Control for the onion checks: the Tor Project's own onion service. */
    @Test fun httpReachesAnOnionServiceThroughTor() = withTorRoute {
        val code = TorNetwork.withClient { client ->
            client.newCall(Request.Builder().url("http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion/").build())
                .execute().use { it.code }
        }
        assertEquals(200, code)
    }

    private fun withTorRoute(block: suspend () -> Unit) = runBlocking<Unit> {
        val original = TorNetwork.current
        try {
            TorNetwork.save(TorConfig(true, proxy!!))
            block()
        } finally { TorNetwork.save(original) }
    }
}
