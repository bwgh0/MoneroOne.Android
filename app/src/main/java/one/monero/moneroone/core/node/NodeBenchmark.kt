package one.monero.moneroone.core.node

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import one.monero.moneroone.core.wallet.DefaultNodes
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/** Latency probe for the node list, credential-aware. */
object NodeBenchmark {
    const val UNREACHABLE = -1L
    const val UNAUTHORIZED = -2L
    private const val TIMEOUT_MS = 5000L
    private const val PATH = "/get_info"

    /**
     * Round-trip time of GET /get_info in ms, [UNREACHABLE] when the node does
     * not answer HTTP 200, [UNAUTHORIZED] when it demands RPC credentials that
     * are missing or rejected. Only 200 counts: a node answering 403 (e.g. a
     * CDN or restricted proxy in front) is not usable by wallet2 RPC, and
     * auto-select must never save a node the wallet layer can't talk to.
     * For an authenticated node the timed request is the authenticated one,
     * which is what every wallet RPC after the first costs.
     */
    suspend fun measure(uri: String, credentials: NodeCredentials?): Long = withContext(Dispatchers.IO) {
        // monerod (epee) keeps the digest nonce per TCP connection, so the
        // answer must travel on the socket that issued the challenge. A client
        // of its own with a one-connection pool guarantees the second request
        // reuses the first one's connection instead of any pooled socket.
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(1, 30, TimeUnit.SECONDS))
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        try {
            val scheme = if (DefaultNodes.isTls(uri)) "https" else "http"
            val url = "$scheme://$uri$PATH"
            val first = request(client, url, authorization = null)
            when (first.code) {
                HttpURLConnection.HTTP_OK -> first.millis
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    if (credentials == null) return@withContext UNAUTHORIZED
                    val authorization = first.challenges
                        .firstNotNullOfOrNull { DigestAuth.authorization(it, "GET", PATH, credentials) }
                        ?: return@withContext UNAUTHORIZED
                    val second = request(client, url, authorization)
                    when (second.code) {
                        HttpURLConnection.HTTP_OK -> second.millis
                        HttpURLConnection.HTTP_UNAUTHORIZED -> UNAUTHORIZED
                        else -> UNREACHABLE
                    }
                }
                else -> UNREACHABLE
            }
        } catch (e: Exception) {
            UNREACHABLE
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private class Reply(val code: Int, val millis: Long, val challenges: List<String>)

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
