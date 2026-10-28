package one.monero.moneroone.core.network

import one.monero.moneroone.core.wallet.WalletFilePasswords
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.util.Helper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativeTorRoutingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    // Public golden-vector wallet, with no spend key.
    private val seed = Seed.WatchOnly("43Xuqb8woKbELkxbc4U8ZEMk87rx8VingbtWmxXpFiJK6mKHJuK8bGGTrndC4y6DmGPdwQDyJaWgu6ZXCKNfeoRSVMTUBCX",
        "f6375bd99c5d6ba250660fe1bda555cf1eee558a5076207afb7b12602b66980b")

    private fun withKit(node: String, proxy: String, block: (MoneroKit) -> Unit) {
        val id = "tor-test-${UUID.randomUUID()}"
        val kit = MoneroKit.getInstance(context, seed, "0", id, WalletFilePasswords.throwaway(), node, false, proxy)
        try { block(kit) } finally {
            kit.release()
            runBlocking { kit.stop() }
            Helper.getWalletRoot(context).listFiles()?.filter { it.name == id || it.name.startsWith("$id.") }?.forEach { it.delete() }
        }
    }
    @Test fun nativeStartupPassesNodeNameToProxyBeforeItsFirstRpc() {
        LocalSocksProxy(LocalSocksProxy.Mode.REJECT).use { proxy ->
            withKit("native-node.invalid:18081", proxy.address) { kit ->
                runBlocking { kit.start() }
                assertEquals(LocalSocksProxy.Destination("native-node.invalid", 18081), proxy.destinations.poll(3, TimeUnit.SECONDS))
                assertFalse(kit.isStarted)
            }
        }
    }
    @Test fun nativeStartupWithClosedProxyNeverContactsTheDirectNode() {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { direct ->
            val unavailable = ServerSocket(0).use { it.localPort }
            withKit("127.0.0.1:${direct.localPort}", "127.0.0.1:$unavailable") { kit ->
                runBlocking { kit.start() }
                assertFalse(kit.isStarted)
                direct.soTimeout = 300
                assertThrows(SocketTimeoutException::class.java) { direct.accept().close() }
            }
        }
    }
}
