package one.monero.moneroone.core.node

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.network.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import one.monero.moneroone.core.wallet.DefaultNodes
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Latency probe for the node list, credential-aware. */
object NodeBenchmark {
    const val UNREACHABLE = -1L
    const val UNAUTHORIZED = -2L
    private const val PATH = "/get_info"

    /**
     * Round-trip time of GET /get_info in ms, [UNREACHABLE] when the node does
     * not answer HTTP 200, [UNAUTHORIZED] when it demands RPC credentials that
     * are missing or rejected. Only 200 counts: a node answering 403 (e.g. a
     * CDN or restricted proxy in front) is not usable by wallet2 RPC, and
     * auto-select must never save a node the wallet layer can't talk to.
     * For an authenticated node the timed request is the authenticated one,
     * which is what every wallet RPC after the first costs. A [tls] node is
     * timed over https, as on iOS.
     */
    suspend fun measure(
        uri: String,
        credentials: NodeCredentials?,
        route: TorConfig = TorNetwork.current,
        tls: Boolean = DefaultNodes.isTls(uri),
    ): Long = withContext(Dispatchers.IO) {
        val onion = isOnionNode(uri)
        if (onion && !route.enabled) return@withContext UNREACHABLE
        try {
            // monerod (epee) keeps the digest nonce per TCP connection, so the answer must travel on the
            // socket that got the challenge: TorNetwork clients pool one connection.
            TorNetwork.withClient(route, directTimeoutSeconds = 5) { routed ->
                val start = System.currentTimeMillis()
                var streamOpened = UNREACHABLE
                // Tor opens a stream to an onion service only after the service reached the node's port.
                val client = if (!onion) routed else routed.newBuilder().eventListener(object : EventListener() {
                    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                        if (streamOpened < 0) streamOpened = System.currentTimeMillis() - start
                    }
                }).build()
                val scheme = if (tls) "https" else "http"
                val url = "$scheme://$uri$PATH"
                val first = try {
                    request(client, url, authorization = null)
                } catch (e: IOException) {
                    // Many TLS nodes use the self-signed certificate monerod makes, which Android does
                    // not trust. wallet2 never checks the certificate (SSL autodetect). The check here
                    // keeps it, so such a node is timed over plain HTTP, as before it was marked TLS.
                    if (tls && e.isCertificateFailure()) return@withClient overPlainHttp(client, uri, credentials)
                    // The Monero One onion services forward to TLS-only RPC. wallet2 reaches it with TLS
                    // autodetect; a plain HTTP check gets a closed stream. A timeout still counts as down.
                    if (streamOpened >= 0 && e !is InterruptedIOException) return@withClient streamOpened
                    throw e
                }
                answer(client, url, first, credentials)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            UNREACHABLE
        }
    }

    private class Reply(val code: Int, val millis: Long, val challenges: List<String>)

    /** The latency for the node's first reply, after the login when it asks for one. */
    private fun answer(client: OkHttpClient, url: String, first: Reply, credentials: NodeCredentials?): Long =
        when (first.code) {
            HttpURLConnection.HTTP_OK -> first.millis
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                val authorization = credentials?.let { login ->
                    first.challenges.firstNotNullOfOrNull { DigestAuth.authorization(it, "GET", PATH, login) }
                }
                if (authorization == null) {
                    UNAUTHORIZED
                } else {
                    val second = request(client, url, authorization)
                    when (second.code) {
                        HttpURLConnection.HTTP_OK -> second.millis
                        HttpURLConnection.HTTP_UNAUTHORIZED -> UNAUTHORIZED
                        else -> UNREACHABLE
                    }
                }
            }
            else -> UNREACHABLE
        }

    /**
     * Times a TLS node whose certificate Android does not trust over plain HTTP. This
     * retry never sends the login, because the user entered the node as TLS: a node
     * that asks for one counts as up, and the wallet checks the login when it connects.
     */
    private fun overPlainHttp(client: OkHttpClient, uri: String, credentials: NodeCredentials?): Long {
        val url = "http://$uri$PATH"
        val first = request(client, url, authorization = null)
        if (first.code == HttpURLConnection.HTTP_UNAUTHORIZED && credentials != null) return first.millis
        return answer(client, url, first, credentials = null)
    }

    private fun IOException.isCertificateFailure(): Boolean =
        this is SSLPeerUnverifiedException ||
            (this is SSLHandshakeException && generateSequence(cause) { it.cause }
                .any { it is CertificateException || it is CertPathValidatorException })

    private fun request(client: OkHttpClient, url: String, authorization: String?): Reply {
        val builder = Request.Builder().url(url).get()
        authorization?.let { builder.header("Authorization", it) }
        val start = System.currentTimeMillis()
        // Closing the body hands the connection back to the (one-slot) pool.
        client.newCall(builder.build()).execute().use { response: Response ->
            val millis = System.currentTimeMillis() - start
            return Reply(response.code, millis, response.headers("WWW-Authenticate"))
        }
    }
}
