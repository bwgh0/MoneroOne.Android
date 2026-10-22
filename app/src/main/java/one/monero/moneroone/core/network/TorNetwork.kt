package one.monero.moneroone.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** One saved route for wallet setup, node checks, prices, and background price workers. */
object TorNetwork {
    private var preferences: SharedPreferences? = null
    private val clients = mutableSetOf<OkHttpClient>()
    private val configState = MutableStateFlow(TorConfig())
    val config = configState.asStateFlow()
    val current: TorConfig get() = configState.value

    /** Loads the saved route. Safe to call again: background workers call it too, as they can run first. */
    @Synchronized fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        configState.value = TorConfig(prefs.getBoolean("tor_enabled", false), prefs.getString("tor_proxy", DEFAULT_TOR_PROXY) ?: DEFAULT_TOR_PROXY)
        preferences = prefs
    }

    /**
     * The route for a new connection. Before the saved setting is loaded, the default route is direct even
     * when Tor is on, so nothing may connect until then.
     */
    val route: TorConfig
        @Synchronized get() {
            if (preferences == null) throw IOException("Connection settings are not loaded")
            return configState.value
        }

    @Synchronized fun save(config: TorConfig) {
        require(!config.enabled || parseProxyEndpoint(config.address) != null) { "Invalid Tor proxy address" }
        // Written at once: after a crash, a lost write would reopen the wallet on the old, maybe direct, route.
        preferences?.edit()?.putBoolean("tor_enabled", config.enabled)?.putString("tor_proxy", config.address)?.commit()
        configState.value = config
        // Switching routes cannot reuse a direct connection or accept its late response.
        clients.forEach { it.dispatcher.cancelAll(); it.connectionPool.evictAll() }
    }

    /**
     * For a client built outside [withClient] (OpenAlias DoH): [save] cancels its calls. Pair with [untrack].
     */
    @Synchronized fun track(client: OkHttpClient) { clients += client }

    @Synchronized fun untrack(client: OkHttpClient) { clients -= client }

    /** Throws unless [route] is still the saved route: no new connection may use an old one. */
    fun ensureRoute(route: TorConfig) {
        if (route != this.route) throw IOException("Connection settings changed")
    }

    fun <T> withClient(route: TorConfig = current, directTimeoutSeconds: Long = 10, block: (OkHttpClient) -> T): T {
        val client = synchronized(this) {
            if (route != this.route) throw IOException("Connection settings changed")
            newClient(route, directTimeoutSeconds).also { clients += it }
        }
        try {
            val result = block(client)
            if (route != current) throw IOException("Connection settings changed")
            return result
        } finally {
            synchronized(this) { clients -= client }
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    internal fun newClient(route: TorConfig, directTimeoutSeconds: Long = 10): OkHttpClient {
        val timeout = if (route.enabled) 30L else directTimeoutSeconds
        return OkHttpClient.Builder()
            .proxy(route.javaProxy()) // Explicit SOCKS proxy: no ProxySelector/direct fallback.
            .apply {
                if (route.enabled) dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        throw UnknownHostException("Destination DNS must use the Tor proxy")
                })
            }
            .connectionPool(ConnectionPool(1, 30, TimeUnit.SECONDS))
            .connectTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS).callTimeout(timeout * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
