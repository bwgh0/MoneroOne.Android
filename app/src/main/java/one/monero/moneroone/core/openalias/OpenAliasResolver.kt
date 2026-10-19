package one.monero.moneroone.core.openalias

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.network.TorConfig
import one.monero.moneroone.core.network.TorNetwork
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.horizontalsystems.monerokit.model.OpenAlias as KitOpenAlias

/**
 * The IPv4 loopback, from the hosts file. Never InetAddress.getLoopbackAddress():
 * on Android it returns the IPv6 loopback, and the kit connects to the IPv4 one.
 * Null when no IPv4 loopback comes back.
 */
fun loopbackV4(): Inet4Address? = try {
    InetAddress.getAllByName("localhost").firstOrNull { it is Inet4Address && it.isLoopbackAddress } as Inet4Address?
} catch (e: Exception) {
    null
}

/** One DoH HTTP reply, before the bridge checks it. */
class DohReply(val status: Int, val contentType: String?, val body: ByteArray)

/** Sends one DNS query as DoH. Tests use a fake. */
interface DohTransport {
    /** POSTs [query] to [server]. Throws on a connection error or a timeout. */
    fun post(server: String, query: ByteArray): DohReply

    /** Cancels every request in flight. */
    fun cancelAll() {}
}

/**
 * The loopback DNS-over-TCP to DoH bridge for one lookup (spec section 3.5).
 * The kit's libunbound forwards every query here; each goes out as an HTTPS
 * POST. After [stop], read [answeredCount] and [synthesizedCount].
 */
class DnsBridge(
    private val transport: DohTransport,
    private val servers: List<String> = OpenAliasConfig.DOH_SERVERS,
    private val queryCap: Int = OpenAliasConfig.QUERY_CAP,
    /**
     * Debug builds only: one line per upstream try and per made-up answer, with
     * the query type, the server, the HTTP status, the rcode and the time. Never
     * the name. Null in release builds.
     */
    private val trace: ((String) -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val queryTotal = AtomicInteger()
    private val answered = AtomicInteger()
    private val synthesized = AtomicInteger()
    private val preferred = AtomicInteger()
    private val stopped = AtomicBoolean(false)

    @Volatile private var down = false
    @Volatile private var listener: ServerSocket? = null

    val answeredCount: Int get() = answered.get()
    val synthesizedCount: Int get() = synthesized.get()

    /** Listens on an ephemeral port of the IPv4 loopback. Returns the port, or null on failure. */
    fun start(): Int? {
        val loopback = loopbackV4() ?: return null
        val server = try {
            ServerSocket(0, BACKLOG, loopback)
        } catch (e: IOException) {
            return null
        }
        listener = server
        scope.launch { acceptLoop(server) }
        return server.localPort
    }

    /** Closes the listener, every connection and every request in flight. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        closeQuietly(listener)
        sockets.forEach(::closeQuietly)
        sockets.clear()
        transport.cancelAll()
        scope.cancel()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!stopped.get()) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                return
            }
            sockets += socket
            if (stopped.get()) {
                closeQuietly(socket)
                return
            }
            scope.launch { serve(socket) }
        }
    }

    private suspend fun serve(socket: Socket) {
        val writeLock = Any()
        val inFlight = mutableListOf<Job>()
        var peerDone = false
        try {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = socket.getOutputStream()
            while (!stopped.get()) {
                val length = readLength(input)
                if (length < 0) {
                    peerDone = true
                    break
                }
                if (length < 12) break
                val query = ByteArray(length)
                input.readFully(query)
                inFlight += scope.launch {
                    val reply = answer(query)
                    synchronized(writeLock) {
                        try {
                            output.write(frame(reply))
                            output.flush()
                        } catch (e: IOException) {
                            // The connection is gone.
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Reset, or closed by stop().
        }
        try {
            // After the peer's end of stream, let the answers in flight go out.
            if (peerDone) inFlight.joinAll()
        } finally {
            closeQuietly(socket)
            sockets -= socket
        }
    }

    /** The 2-byte length of the next frame, or -1 at a clean end of stream. */
    private fun readLength(input: InputStream): Int {
        val high = input.read()
        if (high < 0) return -1
        val low = input.read()
        if (low < 0) throw IOException("truncated frame")
        return (high shl 8) or low
    }

    internal fun answer(query: ByteArray): ByteArray {
        val number = queryTotal.incrementAndGet()
        val type = if (trace != null) DnsDebug.qtype(query) else ""
        if (number > queryCap || down || stopped.get()) {
            trace?.invoke("#$number $type: made up (${if (number > queryCap) "cap" else if (down) "down" else "stopped"})")
            return synthesize(query)
        }
        if (!isWellFormedQuery(query)) {
            // Never sent upstream: a DoH server answers such a frame with NOTIMPL or
            // FORMERR, and libunbound then stops sending EDNS to the bridge for the
            // rest of the lookup. Without EDNS the answers carry no signatures, and
            // the lookup fails as a DNSSEC failure. A SERVFAIL fails only this query.
            trace?.invoke("#$number $type: made up (malformed)")
            return synthesize(query)
        }
        val copy = query.copyOf()
        copy[0] = 0
        copy[1] = 0
        val first = preferred.get()
        var refused = false
        for (step in servers.indices) {
            if (stopped.get()) break
            val index = (first + step) % servers.size
            val postedAt = System.nanoTime()
            val reply = try {
                transport.post(servers[index], copy)
            } catch (e: Exception) {
                null
            }
            if (trace != null) {
                val ms = (System.nanoTime() - postedAt) / 1_000_000
                val result = if (reply == null) "http none" else "http ${reply.status}, rcode ${DnsDebug.rcode(reply.body)}"
                trace.invoke("#$number $type via ${DnsDebug.host(servers[index])}: $result in $ms ms")
            }
            if (reply == null || !isDnsReply(reply)) continue
            if (isRefusal(reply)) {
                refused = true
                continue
            }
            preferred.set(index)
            answered.incrementAndGet()
            val body = reply.body.copyOf()
            body[0] = query[0]
            body[1] = query[1]
            return body
        }
        // Only a transport failure marks the bridge down. A server that refuses
        // one query still answers the others.
        if (!refused) down = true
        trace?.invoke("#$number $type: made up (${if (refused) "refused" else "every server failed"})")
        return synthesize(query)
    }

    private fun synthesize(query: ByteArray): ByteArray {
        synthesized.incrementAndGet()
        return servfail(query)
    }

    companion object {
        const val BACKLOG = 16
        private const val DNS_MESSAGE = "application/dns-message"

        private const val RCODE_FORMERR = 1
        private const val RCODE_NOTIMPL = 4

        /**
         * Status 200, a dns-message content type, 12 to 65535 bytes, the QR bit
         * set, and an rcode other than FORMERR and NOTIMPL. libunbound reads those
         * two as "this server has no EDNS" and then asks without the DO bit, so
         * the answers lose their signatures. Every DoH server has EDNS, so the
         * bridge tries the next server instead.
         */
        fun isGoodReply(reply: DohReply): Boolean = isDnsReply(reply) && !isRefusal(reply)

        /** Status 200, a dns-message content type, 12 to 65535 bytes, and the QR bit set. */
        private fun isDnsReply(reply: DohReply): Boolean =
            reply.status == 200 &&
                reply.contentType?.trim()?.lowercase()?.startsWith(DNS_MESSAGE) == true &&
                reply.body.size in 12..65535 &&
                (reply.body[2].toInt() and 0x80) != 0

        /** FORMERR or NOTIMPL. Call only after [isDnsReply]. */
        private fun isRefusal(reply: DohReply): Boolean =
            (reply.body[3].toInt() and 0x0F).let { it == RCODE_FORMERR || it == RCODE_NOTIMPL }

        /**
         * One standard query: QR off, opcode 0, one question, no answer or
         * authority records, and every byte used by the question and the
         * additional records (EDNS). libunbound 1.16.1 sometimes sends a frame
         * that fails this: a resent query with its ID written over its flags.
         */
        fun isWellFormedQuery(query: ByteArray): Boolean {
            if (query.size < 17) return false
            val flags = u16(query, 2)
            if ((flags and 0x8000) != 0 || ((flags shr 11) and 0x0F) != 0) return false
            if (u16(query, 4) != 1 || u16(query, 6) != 0 || u16(query, 8) != 0) return false
            var at = skipName(query, 12, pointerAllowed = false) ?: return false
            at += 4
            repeat(u16(query, 10)) {
                at = skipName(query, at, pointerAllowed = true) ?: return false
                if (at + 10 > query.size) return false
                at += 10 + u16(query, at + 8)
            }
            return at == query.size
        }

        private fun u16(message: ByteArray, at: Int): Int =
            ((message[at].toInt() and 0xFF) shl 8) or (message[at + 1].toInt() and 0xFF)

        /** The offset after the name at [start], or null when the name is bad or runs past the end. */
        private fun skipName(message: ByteArray, start: Int, pointerAllowed: Boolean): Int? {
            var at = start
            var total = 0
            while (at < message.size) {
                val length = message[at].toInt() and 0xFF
                if (length == 0) return at + 1
                if ((length and 0xC0) == 0xC0) {
                    return if (pointerAllowed && at + 2 <= message.size) at + 2 else null
                }
                if (length >= 64) return null
                total += length + 1
                if (total > 255) return null
                at += 1 + length
            }
            return null
        }

        /** A SERVFAIL for [query] (spec section 3.5). [query] has 12 bytes or more. */
        fun servfail(query: ByteArray): ByteArray {
            val question = questionOf(query)
            val out = ByteArray(12 + (question?.size ?: 0))
            out[0] = query.getOrElse(0) { 0 }
            out[1] = query.getOrElse(1) { 0 }
            out[2] = (0x80 or (query.getOrElse(2) { 0 }.toInt() and 0x79)).toByte()
            out[3] = (0x80 or (query.getOrElse(3) { 0 }.toInt() and 0x10) or 0x02).toByte()
            if (question != null) {
                out[5] = 1
                question.copyInto(out, 12)
            }
            return out
        }

        /** The first question (name, type, class), or null when it fails to parse. */
        private fun questionOf(query: ByteArray): ByteArray? {
            var i = 12
            while (true) {
                if (i >= query.size) return null
                val length = query[i].toInt() and 0xFF
                if (length == 0) {
                    i += 1
                    break
                }
                if (length >= 64) return null
                i += 1 + length
            }
            if (i + 4 > query.size) return null
            return query.copyOfRange(12, i + 4)
        }

        fun frame(body: ByteArray): ByteArray {
            val out = ByteArray(2 + body.size)
            out[0] = (body.size shr 8).toByte()
            out[1] = body.size.toByte()
            body.copyInto(out, 2)
            return out
        }

        private fun closeQuietly(closeable: Closeable?) {
            try {
                closeable?.close()
            } catch (e: IOException) {
                // Nothing to do.
            }
        }
    }
}

/**
 * Debug builds only: the parts of a DNS message that the bridge trace may show.
 * Never the name.
 */
internal object DnsDebug {
    private val typeNames = mapOf(
        1 to "A", 2 to "NS", 5 to "CNAME", 6 to "SOA", 10 to "NULL", 16 to "TXT", 28 to "AAAA",
        43 to "DS", 46 to "RRSIG", 47 to "NSEC", 48 to "DNSKEY", 50 to "NSEC3", 51 to "NSEC3PARAM",
    )

    /** The type of the first question, or "?" when the question fails to parse. */
    fun qtype(query: ByteArray): String {
        var at = 12
        while (at < query.size) {
            val length = query[at].toInt() and 0xFF
            if (length == 0) {
                if (at + 3 > query.size) return "?"
                val type = ((query[at + 1].toInt() and 0xFF) shl 8) or (query[at + 2].toInt() and 0xFF)
                return typeNames[type] ?: "TYPE$type"
            }
            if (length >= 64) return "?"
            at += 1 + length
        }
        return "?"
    }

    /** The rcode of a reply, or "?" when it is shorter than a header. */
    fun rcode(reply: ByteArray): String = if (reply.size < 12) "?" else (reply[3].toInt() and 0x0F).toString()

    /** The host of a DoH server URL. */
    fun host(server: String): String = server.substringAfter("://").substringBefore('/')

    /**
     * The kind of DNSSEC failure, from a fixed list, so the text with the name
     * never reaches the log.
     */
    fun bogusKind(whyBogus: String): String {
        val text = whyBogus.lowercase()
        return when {
            text.isEmpty() -> "none"
            "no signatures" in text -> "no signatures"
            "crypto" in text -> "crypto"
            "expired" in text || "before inception" in text -> "expired"
            "servfail" in text || "no dnskey" in text || "no ds" in text ||
                "could not fetch" in text || "timeout" in text || "no server" in text -> "fetch failure"
            else -> "other"
        }
    }
}

/** The Tor SOCKS proxy of the app's Tor setting. */
data class TorProxy(val host: String, val port: Int)

sealed class TorState {
    data object Off : TorState()

    /** [proxy] is null when the proxy string fails to parse: the lookup fails with the Tor error. */
    data class On(val proxy: TorProxy?) : TorState()
}

object OpenAliasTor {
    /**
     * The app's Tor setting ([TorNetwork]), the one every other connection uses. A setting that is not
     * loaded yet, or a proxy that does not parse, gives On(null): the lookup fails, it never goes direct.
     */
    fun current(route: () -> TorConfig = { TorNetwork.route }): TorState {
        val config = try {
            route()
        } catch (e: Exception) {
            return TorState.On(null)
        }
        if (!config.enabled) return TorState.Off
        val endpoint = try {
            config.endpoint()
        } catch (e: Exception) {
            null
        }
        return TorState.On(endpoint?.let { TorProxy(it.host, it.port) })
    }

    /** Parses `host:port`, split at the last ':'. Null when it fails. */
    fun parseProxy(value: String?): TorProxy? {
        if (value.isNullOrBlank()) return null
        val colon = value.lastIndexOf(':')
        if (colon <= 0) return null
        val host = value.substring(0, colon).trim().removePrefix("[").removeSuffix("]")
        val port = value.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return TorProxy(host, port)
    }
}

/**
 * A new OkHttp client for one lookup: no cache, no cookies, no retries, and
 * the default protocols (HTTP/2 with an HTTP/1.1 fallback). With [proxy], every
 * connection goes through the Tor SOCKS proxy, which resolves the host names:
 * the Dns throws, so no local lookup ever runs.
 */
fun buildDohClient(timeoutMs: Long, proxy: TorProxy?): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .cache(null)
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
    if (proxy != null) {
        builder
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxy.host, proxy.port)))
            .dns(TorOnlyDns)
    }
    return builder.build()
}

/** Stops every local name lookup while Tor is on. */
object TorOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        throw UnknownHostException("Tor resolves host names")
}

class OkHttpDohTransport(private val client: OkHttpClient) : DohTransport {

    override fun post(server: String, query: ByteArray): DohReply {
        val request = Request.Builder()
            .url(server)
            .header("Accept", DNS_MESSAGE)
            .header("User-Agent", "doh-client")
            .post(query.toRequestBody(DNS_MESSAGE.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.byteStream()?.use(::readCapped) ?: ByteArray(0)
            return DohReply(response.code, response.header("Content-Type"), body)
        }
    }

    override fun cancelAll() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    /** Reads at most one byte past the DNS limit, so a huge body fails the size check. */
    private fun readCapped(input: InputStream): ByteArray {
        val limit = 65536
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = input.read(buffer, total, limit - total)
            if (read < 0) break
            total += read
        }
        return buffer.copyOf(total)
    }

    private companion object {
        const val DNS_MESSAGE = "application/dns-message"
    }
}

/**
 * The DoH transport on the app's Tor route: [TorNetwork.save] cancels its calls, and no request starts
 * once the saved route is not the one the lookup began on.
 */
class RoutedDohTransport private constructor(
    private val route: TorConfig,
    private val client: OkHttpClient,
) : DohTransport {
    private val inner = OkHttpDohTransport(client)

    override fun post(server: String, query: ByteArray): DohReply {
        TorNetwork.ensureRoute(route)
        return inner.post(server, query)
    }

    override fun cancelAll() {
        TorNetwork.untrack(client)
        inner.cancelAll()
    }

    companion object {
        /** Throws when the saved route is no longer the one [proxy] came from. */
        fun open(proxy: TorProxy?): RoutedDohTransport {
            val route = TorNetwork.route
            if (route.endpoint()?.let { TorProxy(it.host, it.port) } != proxy) {
                throw IOException("Connection settings changed")
            }
            val timeout = if (proxy == null) OpenAliasConfig.DOH_TIMEOUT_DIRECT_MS else OpenAliasConfig.DOH_TIMEOUT_TOR_MS
            val client = buildDohClient(timeout, proxy)
            TorNetwork.track(client)
            return RoutedDohTransport(route, client)
        }
    }
}

/**
 * Looks up OpenAlias names (spec section 3.3): a new [DnsBridge] per lookup,
 * the kit's DNSSEC-validating TXT lookup through it, then the answer mapping.
 * Never calls the wallet2 resolver.
 */
class OpenAliasResolver(
    private val kitLookup: (name: String, port: Int, timeoutMs: Int) -> String =
        { name, port, timeoutMs -> KitOpenAlias.lookupTxt(name, port, timeoutMs) },
    private val torState: () -> TorState = OpenAliasTor::current,
    private val transportFactory: (TorProxy?) -> DohTransport = RoutedDohTransport::open,
    private val servers: List<String> = OpenAliasConfig.DOH_SERVERS,
) {
    /** Cancelling the caller stops the bridge at once; the kit call then ends within about a second. */
    suspend fun resolve(name: OpenAliasName, check: (String) -> AddressCheck): OpenAliasOutcome =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            val tor = torState()
            val torOn = tor is TorState.On
            val netError = OpenAliasOutcome.Failed(if (torOn) OpenAliasFailure.TOR else OpenAliasFailure.NETWORK)
            if (tor is TorState.On && tor.proxy == null) {
                return@withContext netError.also { debugLog(it, startedAt, null) }
            }
            val transport = try {
                transportFactory((tor as? TorState.On)?.proxy)
            } catch (e: Exception) {
                return@withContext netError.also { debugLog(it, startedAt, null) }
            }
            val trace: ((String) -> Unit)? = if (BuildConfig.DEBUG) {
                { line -> Timber.tag(TRACE_TAG).d("%s", line) }
            } else {
                null
            }
            val bridge = DnsBridge(transport, servers, trace = trace)
            val port = bridge.start()
                ?: return@withContext OpenAliasOutcome.Failed(OpenAliasFailure.NETWORK)
                    .also { transport.cancelAll(); debugLog(it, startedAt, null) }
            val timeoutMs = if (torOn) OpenAliasConfig.KIT_TIMEOUT_TOR_MS else OpenAliasConfig.KIT_TIMEOUT_DIRECT_MS
            val json = try {
                coroutineScope {
                    val call = async {
                        try {
                            kitLookup(name.query, port, timeoutMs)
                        } catch (e: Exception) {
                            ""
                        } catch (e: LinkageError) {
                            ""
                        }
                    }
                    try {
                        call.await()
                    } finally {
                        bridge.stop()
                    }
                }
            } finally {
                bridge.stop()
            }
            trace?.invoke("kit ${kitSummary(json)}")
            OpenAlias.mapAnswer(json, bridge.synthesizedCount, torOn, check)
                .also { debugLog(it, startedAt, bridge) }
        }

    /** Debug builds only: the kit result flags and the kind of DNSSEC failure. Never the name or the records. */
    private fun kitSummary(json: String): String = try {
        val obj = org.json.JSONObject(json)
        if (obj.optString("status") != "ok") {
            "status ${obj.optString("status").takeIf { it in setOf("timeout", "error") } ?: "other"}"
        } else {
            "status ok, rcode ${obj.optInt("rcode")}, havedata ${obj.optBoolean("havedata")}, " +
                "nxdomain ${obj.optBoolean("nxdomain")}, secure ${obj.optBoolean("secure")}, " +
                "bogus ${obj.optBoolean("bogus")}, why_bogus ${DnsDebug.bogusKind(obj.optString("why_bogus"))}"
        }
    } catch (e: Exception) {
        "unparsed"
    }

    /** Debug builds only: the outcome, the time and the counts. Never the name, the records or the address. */
    private fun debugLog(outcome: OpenAliasOutcome, startedAt: Long, bridge: DnsBridge?) {
        if (!BuildConfig.DEBUG) return
        val label = when (outcome) {
            is OpenAliasOutcome.Resolved -> "resolved, DNSSEC ${outcome.result.dnssec}"
            is OpenAliasOutcome.Failed -> "failed, ${outcome.failure}"
        }
        val ms = (System.nanoTime() - startedAt) / 1_000_000
        Timber.d(
            "OpenAlias lookup %s in %d ms (answered %d, synthesized %d)",
            label, ms, bridge?.answeredCount ?: 0, bridge?.synthesizedCount ?: 0
        )
    }

    private companion object {
        const val TRACE_TAG = "OpenAliasTrace"
    }
}
