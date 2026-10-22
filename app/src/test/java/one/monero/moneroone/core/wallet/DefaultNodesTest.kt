package one.monero.moneroone.core.wallet

import one.monero.moneroone.core.network.isOnionNode
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** As on iOS, only the user's own choice selects an onion node. */
class DefaultNodesTest {
    private val onion = DefaultNodes.TOR.first().uri
    private val cake = "xmr-node.cakewallet.com:18081"
    private val seth = "node.sethforprivacy.com:18089"

    @Test fun autoSelectSkipsAFasterOnionNode() {
        assertEquals(seth, DefaultNodes.fastest(mapOf(onion to 5L, cake to 90L, seth to 40L)))
    }
    @Test fun autoSelectSkipsNodesThatDidNotAnswer() {
        assertEquals(cake, DefaultNodes.fastest(mapOf(seth to -1L, cake to 90L)))
    }
    @Test fun autoSelectPicksNothingWhenOnlyOnionNodesAnswer() {
        assertNull(DefaultNodes.fastest(DefaultNodes.TOR.associate { it.uri to 5L } + (cake to -1L)))
    }
    @Test fun failoverWalksOnlyClearnetDefaults() {
        assertTrue(DefaultNodes.URIS.isNotEmpty())
        assertTrue(DefaultNodes.URIS.none(::isOnionNode))
    }
    @Test fun turningTorOffUnderAnOnionNodeMovesToTheFirstDefault() {
        assertEquals("node.monero.one:443", DefaultNodes.TOR_OFF_FALLBACK)
        assertFalse(isOnionNode(DefaultNodes.TOR_OFF_FALLBACK))
    }
    @Test fun firstRunAlwaysPicksTheTlsNodeMoneroOne() {
        repeat(200) {
            val pick = DefaultNodes.initial(FakeSharedPreferences(), Random(it))
            assertEquals("node.monero.one:443", pick)
            assertTrue(DefaultNodes.isTls(pick))
        }
    }
    @Test fun firstRunPickIsKeptOnLaterLaunches() {
        val prefs = FakeSharedPreferences()
        val first = DefaultNodes.initial(prefs, Random(1))
        repeat(20) { assertEquals(first, DefaultNodes.initial(prefs, Random(it + 2))) }
    }
}
