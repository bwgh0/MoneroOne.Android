package one.monero.moneroone.core.username

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.openalias.*
import one.monero.moneroone.core.wallet.SecurePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class UsernameTrust { PUBLISHED, PENDING, STALE, EXPIRED;
    /** 0.1 XMR, in atomic units, for a record not yet in the public log or out of date. */
    val amountLimit: Long? get() = if (this == PUBLISHED) null else 100_000_000_000L

    /**
     * The note above the amount while [amountLimit] applies, and why a send over it stops.
     * English source text for `tr`.
     */
    val limitMessage: String? get() = when (this) {
        PUBLISHED -> null
        PENDING -> "This username is not in the public log yet. The payment limit is 0.1 XMR."
        STALE, EXPIRED -> "Username records are out of date. The payment limit is 0.1 XMR."
    }
}

/** The username check just before the broadcast. */
object UsernameSendCheck {
    /**
     * Null to send, else why the send stops (English source text for `tr`).
     * Only a new address stops it. A new trust alone does not: its own limit
     * applies, so published to stale gains the limit, pending to published loses it.
     */
    fun stopReason(reviewedAddress: String, checked: OpenAliasResult, sendAll: Boolean, amount: Long): String? {
        if (checked.address != reviewedAddress) return "The username changed since your review. Look it up again before sending."
        val trust = checked.usernameTrust ?: return null
        val limit = trust.amountLimit ?: return null
        return if (sendAll || amount > limit) trust.limitMessage else null
    }
}

object UsernameRecipient {
    fun localPart(raw: String): String? {
        // Classify syntax locally. Rust verifies the frozen rules at resolve.
        // A username is one label: `@getmonero.org` is not a username, and
        // a dotted name under monero.one stays reserved.
        if (raw.any { it.code !in 0x21..0x7e }) return null
        val lower = raw.lowercase(Locale.ROOT)
        val local = when {
            lower.startsWith('@') -> lower.drop(1)
            lower.endsWith("@monero.one") || lower.endsWith(".monero.one") -> lower.dropLast(11)
            else -> return null
        }
        return local.takeIf { it.matches(Regex("[a-z][a-z0-9_]{2,14}")) && !it.contains("__") }
    }
}

data class UsernameResolutionConfiguration(val registry: UsernameConfiguration, val mirror: String, val genesisRoot: String,
    val origin: String = "monero.one/oah") {
    companion object {
        val current: UsernameResolutionConfiguration? get() {
            val registry = UsernameConfiguration.current ?: return null
            val uri = runCatching { URI(BuildConfig.USERNAME_MIRROR_URL) }.getOrNull() ?: return null
            if (!BuildConfig.DEBUG || uri.host != "localhost" || uri.scheme !in listOf("http", "https") ||
                uri.userInfo != null || uri.query != null || uri.fragment != null || uri.toString().trimEnd('/') == registry.url ||
                !BuildConfig.USERNAME_GENESIS_ROOT.matches(Regex("[0-9a-f]{64}"))) return null
            return UsernameResolutionConfiguration(registry, uri.toString().trimEnd('/'), BuildConfig.USERNAME_GENESIS_ROOT)
        }
    }
}

/** Saves signed evidence before exposing a payment destination. Receipts are
 * never evicted: forgetting a genesis would silently restore first-contact trust. */
class UsernameResolver(private val context: Context,
    private val configuration: UsernameResolutionConfiguration,
    private val network: String = "mainnet") {
    companion object { private val receiptMutex = Mutex() }
    private fun b64(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    suspend fun resolve(name: OpenAliasName): OpenAliasOutcome = withContext(Dispatchers.IO) {
        val tor = OpenAliasTor.current()
        if (tor is TorState.On && tor.proxy == null) return@withContext OpenAliasOutcome.Failed(OpenAliasFailure.TOR)
        val netError = if (tor is TorState.On) OpenAliasFailure.TOR else OpenAliasFailure.NETWORK
        val client = buildDohClient(if (tor is TorState.On) 30_000 else 15_000, (tor as? TorState.On)?.proxy)
            .newBuilder().followRedirects(false).followSslRedirects(false).callTimeout(45, TimeUnit.SECONDS).build()
        try {
            fun fetch(base: String, path: String): ByteArray {
                client.newCall(Request.Builder().url("$base/$path").header("Cache-Control", "no-cache").build()).execute().use { response ->
                    if (response.code == 404 || response.code == 451) throw LookupFailure(OpenAliasFailure.USERNAME_UNAVAILABLE)
                    if (response.code != 200) throw LookupFailure(netError)
                    val source = response.body?.source() ?: throw LookupFailure(netError)
                    source.request(4_194_305)
                    if (source.buffer.size > 4_194_304) throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
                    return source.readByteArray()
                }
            }
            val local = UsernameRecipient.localPart(name.query) ?: throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
            val parsed = OahCore.call(json("op" to "parse_recipient", "input" to "@$local"))
            val handle = parsed.getString("handle")
            val canonical = parsed.getString("name")
            val api = configuration.registry.url
            val mirror = configuration.mirror
            // The operator is checked on every resolve, including holds.
            val rows = JSONObject(String(fetch(api, "v1/handles/$handle"), Charsets.UTF_8)).getJSONArray("records")
            if (rows.length() !in 1..4096) throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
            val records = JSONArray().apply { for (i in 0 until rows.length()) put(rows.getJSONObject(i).getString("record")) }
            val rules = String(fetch(api, "v1/rules"), Charsets.UTF_8)
            val latest = String(fetch(mirror, "snapshots/latest"), Charsets.UTF_8)
            val epoch = latest.toLongOrNull()?.takeIf { it >= 0 && it.toString() == latest }
                ?: throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
            val manifest = JSONObject(String(fetch(mirror, "epochs/$epoch/manifest.json"), Charsets.UTF_8))
            val genesis = fetch(mirror, "epochs/0/epoch")
            val signedBucket = fetch(mirror, "snapshots/$epoch/buckets/${parsed.getInt("bucket")}")
            if (signedBucket.size < 64) throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
            val request = json("name" to canonical, "records" to records, "network" to network,
                "registrar_key" to configuration.registry.registrarKey, "rules" to rules,
                "rules_hash" to configuration.registry.rulesHash, "genesis_epoch" to b64(genesis),
                "genesis_epoch_root" to configuration.genesisRoot, "origin" to configuration.origin,
                "now" to System.currentTimeMillis() / 1000,
                "snapshot" to json("epoch" to manifest.getString("encoding"), "checkpoint" to manifest.getString("checkpoint"),
                    "bucket" to b64(signedBucket.copyOfRange(0, signedBucket.size - 64)),
                    "signature" to b64(signedBucket.copyOfRange(signedBucket.size - 64, signedBucket.size)),
                    "published_at" to manifest.getLong("published_at"), "publication_signature" to manifest.getString("publication_signature")))
            val scope = api + configuration.registry.registrarKey + configuration.genesisRoot + network
            val scopeHash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
            val key = "username-receipt.v1.$scopeHash.$handle"
            coroutineContext.ensureActive()
            val result = receiptMutex.withLock {
                val prefs = SecurePrefs.open(context.applicationContext)
                var recipients = JSONArray()
                prefs.getString(key, null)?.let { saved ->
                    val receipt = JSONObject(saved)
                    val old = receipt.getJSONObject("result")
                    request.put("pin", old.getJSONObject("pin")).put("previous_epoch", old.getString("epoch"))
                    recipients = receipt.optJSONArray("recipients") ?: JSONArray()
                }
                val known = (0 until recipients.length()).any { recipients.getString(it) == canonical }
                request.put("known_recipient", known)
                val own = UsernameStore.get(context)
                if (own.storageError) throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
                own.state.value.bindings.firstOrNull { it.handle == handle && it.network == network }?.let {
                    request.put("owned_genesis_hash", it.genesisHash)
                }
                val checked = OahCore.call(json("op" to "verify_resolution", "request" to request))
                if (!known) recipients.put(canonical)
                if (!prefs.edit().putString(key, json("request" to request, "result" to checked, "recipients" to recipients).toString()).commit())
                    throw LookupFailure(OpenAliasFailure.USERNAME_VERIFICATION)
                checked
            }
            val age = result.getLong("snapshot_age")
            val trust = when {
                age > 48 * 3600 -> UsernameTrust.EXPIRED
                age >= 6 * 3600 -> UsernameTrust.STALE
                result.getBoolean("published") -> UsernameTrust.PUBLISHED
                else -> UsernameTrust.PENDING
            }
            OpenAliasOutcome.Resolved(OpenAliasResult(result.getString("address"), null, DnssecStatus.OFF, usernameTrust = trust))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
          catch (e: LookupFailure) { OpenAliasOutcome.Failed(e.failure) }
          catch (e: OahException) { OpenAliasOutcome.Failed(if (e.code == "mobile.snapshot_expired") OpenAliasFailure.USERNAME_STALE else OpenAliasFailure.USERNAME_VERIFICATION) }
          catch (_: java.io.IOException) { OpenAliasOutcome.Failed(netError) }
          catch (_: Exception) { OpenAliasOutcome.Failed(OpenAliasFailure.USERNAME_VERIFICATION) }
        finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
    private class LookupFailure(val failure: OpenAliasFailure): Exception()
}
