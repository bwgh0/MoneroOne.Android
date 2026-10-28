package one.monero.moneroone.core.username

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.monero.moneroone.BuildConfig
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * The registry this build talks to. Release builds carry the production values and accept only the
 * production hosts. Debug builds carry the staging values by default and accept only this machine,
 * the developer's Tailscale network over HTTPS, or the staging hosts (never production).
 */
data class UsernameConfiguration(val url: String, val registrarKey: String, val rulesHash: String, val termsVersion: String) {
    companion object {
        val current: UsernameConfiguration? get() = from(BuildConfig.USERNAME_REGISTRY_URL, BuildConfig.USERNAME_REGISTRAR_KEY,
            BuildConfig.USERNAME_RULES_HASH, BuildConfig.USERNAME_TERMS_VERSION, BuildConfig.DEBUG)

        internal fun from(rawUrl: String, registrarKey: String, rulesHash: String, termsVersion: String, debug: Boolean): UsernameConfiguration? {
            val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
            if (!UsernameHosts.allows(uri, debug)) return null
            val hex = Regex("[0-9a-f]{64}")
            if (!hex.matches(registrarKey) || !hex.matches(rulesHash)) return null
            if (!Regex("[!-~]{1,64}").matches(termsVersion)) return null
            return UsernameConfiguration(uri.toString().trimEnd('/'), registrarKey, rulesHash, termsVersion)
        }
    }
}

/** The registry and mirror hosts a build may reach. */
internal object UsernameHosts {
    private val production = setOf("registry.monero.one", "mirror.monero.one")
    private val staging = setOf("staging-registry.monero.one", "staging-mirror.monero.one")

    fun allows(uri: URI, debug: Boolean): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return false
        if (!debug) return scheme == "https" && uri.port == -1 && host in production
        if (host == "localhost") return scheme in listOf("http", "https")
        if (scheme != "https") return false
        if (host in staging) return uri.port == -1
        val labels = host.split(".")
        return labels.size >= 3 && labels.takeLast(2) == listOf("ts", "net") &&
            labels.all { label -> label.isNotEmpty() && label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } }
    }
}

/** One record of a held name, as `GET /v1/handles/{h}` lists it. */
data class HeldRecord(val record: String, val recordHash: String? = null)

/**
 * A held name, as `GET /v1/handles/{h}` answers it. [records] is the whole chain, oldest first. A field
 * that is missing or of another type is null, and a restore then refuses the name.
 */
data class HeldName(
    val records: List<HeldRecord>, val handle: String? = null, val owner: String? = null,
    val genesisHash: String? = null, val tombstoned: Boolean? = null, val headSeq: Long? = null,
    val headHash: String? = null,
)

/** The registry calls a claim makes. Unit tests replace it. */
interface UsernameRegistryApi {
    /** The rules text, after the registry's info matches the pinned configuration for [network]. */
    suspend fun checkedRules(network: String): String
    /** The held name, or null while the handle is free. */
    suspend fun lookup(handle: String): HeldName?
    suspend fun nonce(handle: String, subaddress: String): String
    /** Posts the signed claim. Returns the handle and the record the registry admitted. */
    suspend fun claim(pending: PendingUsernameClaim, device: DeviceProof): Pair<String, String>
}

/** The shared Rust core's claim operations. Unit tests replace it. */
interface UsernameClaimCore {
    fun normalize(input: String): String
    /** The unsigned record, its hash and the message to sign. */
    fun prepare(handle: String, subaddress: String, nonce: String, network: String, rules: String, rulesHash: String): Triple<String, String, String>
    /** True when [record] is the exact signed genesis of [pending]. */
    fun isOwnClaim(record: String, pending: PendingUsernameClaim): Boolean
}

private class HttpUsernameRegistryApi(private val configuration: UsernameConfiguration) : UsernameRegistryApi {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(25, TimeUnit.SECONDS).build()

    private suspend fun request(path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${configuration.url}/v1/$path").apply {
            if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
        }.build()
        client.newCall(request).execute().use { response ->
            val source = response.body?.source() ?: throw UsernameException("verification")
            source.request(1_048_577)
            if (source.buffer.size > 1_048_576) throw UsernameException("verification")
            val text = source.readUtf8()
            if (!response.isSuccessful) {
                val code = runCatching { JSONObject(text).getJSONObject("error").getString("code") }.getOrDefault("server")
                throw UsernameException(code)
            }
            text
        }
    }

    override suspend fun checkedRules(network: String): String {
        val info = JSONObject(request("info"))
        if (info.optString("registrar_key") != configuration.registrarKey || info.optString("rules_hash") != configuration.rulesHash ||
            info.optString("network") != network || info.optString("zone") != "monero.one" || !info.optBoolean("open")) throw UsernameException("verification")
        return request("rules")
    }

    override suspend fun lookup(handle: String): HeldName? {
        val found = try { JSONObject(request("handles/$handle")) }
            catch (e: UsernameException) { if (e.code == "handle.not_found") return null else throw e }
        val rows = found.getJSONArray("records")
        val head = found.opt("head") as? JSONObject
        return HeldName(
            records = List(rows.length()) { rows.getJSONObject(it).let { row -> HeldRecord(row.getString("record"), row.opt("record_hash") as? String) } },
            handle = found.opt("handle") as? String, owner = found.opt("owner") as? String,
            genesisHash = found.opt("genesis_hash") as? String, tombstoned = found.opt("tombstoned") as? Boolean,
            headSeq = when (val seq = head?.opt("seq")) { is Int -> seq.toLong(); is Long -> seq; else -> null },
            headHash = head?.opt("record_hash") as? String,
        )
    }

    override suspend fun nonce(handle: String, subaddress: String): String =
        JSONObject(request("nonce", json("handle" to handle, "subaddress" to subaddress))).getString("nonce")

    override suspend fun claim(pending: PendingUsernameClaim, device: DeviceProof): Pair<String, String> {
        val response = JSONObject(request("claims", json("nonce" to pending.nonce, "record" to pending.unsignedRecord,
            "owner_signature" to pending.ownerSignature, "device" to json("platform" to device.platform, "proof" to device.proof),
            "terms_version" to configuration.termsVersion)))
        return response.optString("handle") to response.getString("record")
    }
}

private object OahClaimCore : UsernameClaimCore {
    override fun normalize(input: String) = OahCore.normalize(input)
    override fun prepare(handle: String, subaddress: String, nonce: String, network: String, rules: String, rulesHash: String): Triple<String, String, String> {
        val unsigned = OahCore.call(json("op" to "prepare_claim", "handle" to handle, "subaddress" to subaddress,
            "nonce" to nonce, "network" to network, "rules" to rules, "rules_hash" to rulesHash))
        return Triple(unsigned.getString("record"), unsigned.getString("record_hash"), unsigned.getString("signing_message"))
    }
    override fun isOwnClaim(record: String, pending: PendingUsernameClaim): Boolean = try {
        OahCore.call(json("op" to "verify_claim", "record" to record, "expected_record_hash" to pending.recordHash,
            "handle" to pending.handle, "subaddress" to pending.subaddress, "network" to pending.network,
            "registrar_key" to pending.registrarKey, "rules" to pending.rules, "rules_hash" to pending.rulesHash))
        true
    } catch (_: Exception) { false }
}

/** The claim client. All record construction and verification is in oah-core. */
class UsernameRegistry internal constructor(
    private val configuration: UsernameConfiguration,
    private val api: UsernameRegistryApi,
    private val core: UsernameClaimCore,
    private val deviceGate: UsernameDeviceGate,
) {
    constructor(configuration: UsernameConfiguration, context: Context) :
        this(configuration, HttpUsernameRegistryApi(configuration), OahClaimCore,
            UsernameDeviceGate(BuildConfig.DEBUG, PlayIntegrityTokenSource.forBuild(context)))

    /** What a re-check of a saved pending claim found. */
    private sealed interface Recheck {
        /** The name points to the pending claim's wallet: the claim is done. */
        data class Finished(val binding: UsernameBinding) : Recheck
        /** The name is free, or someone else holds it. The pending claim is cleared. */
        data object Cleared : Recheck
    }

    /**
     * [claimKey] gives the claiming wallet's claim key ([UsernameDeviceProof.claimKey]). [ownsSubaddress] is
     * true for a subaddress of the open wallet: a name that already points to one comes back with no new
     * claim ([restore]).
     */
    suspend fun claim(displayHandle: String, walletKey: String, network: String, store: UsernameStore,
                      makeSubaddress: suspend () -> String, sign: suspend (String, String) -> String,
                      claimKey: suspend () -> String, ownsSubaddress: suspend (String) -> Boolean,
                      isCurrentWallet: () -> Boolean): UsernameBinding {
        if (!isCurrentWallet() || walletKey.isEmpty()) throw UsernameException("wallet_changed")
        if (store.storageError) throw UsernameException("storage")
        if (!store.claimMutex.tryLock()) throw UsernameException("pending")
        try {
            val handle = core.normalize(displayHandle)
            // This wallet's earlier claim of the same name, cleared at the re-check (the name was free, or another
            // genesis held it).
            var earlier: PendingUsernameClaim? = null
            store.state.value.pending?.let { pending ->
                val same = pending.walletKey == walletKey && pending.network == network && pending.handle == handle
                // A pending claim blocks another claim only while its result is unknown.
                val outcome = try { recheck(pending, store) } catch (e: Exception) {
                    if (same || e is kotlinx.coroutines.CancellationException) throw e
                    throw UsernameException("pending")
                }
                when (outcome) {
                    // One username per device: another wallet's finished claim used the slot.
                    is Recheck.Finished -> if (same) return outcome.binding else throw UsernameException("device.slot_used")
                    // The name is free, or another genesis holds it (that can be this wallet's, from another
                    // phone): a new claim starts here, and a taken name stops at the lookup below (iOS parity).
                    is Recheck.Cleared -> if (same) earlier = pending
                }
            }
            val rules = api.checkedRules(network)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            api.lookup(handle)?.let { found ->
                val records = found.records.map { it.record }
                // A slow earlier POST can land after the re-check found the name free.
                val own = earlier?.takeIf { records.size == 1 && core.isOwnClaim(records[0], it) }
                if (own != null) return bind(records[0], own, store)
                // This wallet already holds the name: a new phone, a restored wallet or cleared app data.
                if (isCurrentWallet()) {
                    restore(found, handle, displayHandle, walletKey, network, rules, ownsSubaddress, isCurrentWallet, store)
                        ?.let { return it }
                }
                throw UsernameException("name.unavailable")
            }
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val address = makeSubaddress()
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val nonce = api.nonce(handle, address)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val (record, recordHash, message) = core.prepare(handle, address, nonce, network, rules, configuration.rulesHash)
            val signature = sign(message, address)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val pending = PendingUsernameClaim(walletKey, network, handle, displayHandle, address, nonce,
                record, recordHash, signature, configuration.registrarKey, rules, configuration.rulesHash)
            // The device proof comes first: a phone that cannot claim saves no pending claim.
            val device = deviceGate.proof(pending, store.state.value.deviceId, claimKey)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            // Saved before the POST: after a lost reply, the next claim re-checks this exact signed genesis.
            store.savePending(pending)
            return submit(pending, device, store)
        } finally { store.claimMutex.unlock() }
    }

    /**
     * Asks the registry about a saved pending claim. The name points to its wallet: finish it.
     * The name is free or someone else holds it: clear it. The registry cannot be reached:
     * this throws and the pending claim stays for the next re-check.
     */
    private suspend fun recheck(pending: PendingUsernameClaim, store: UsernameStore): Recheck {
        // Signed under other pins, it can never finish here.
        if (pending.registrarKey != configuration.registrarKey || pending.rulesHash != configuration.rulesHash) {
            store.savePending(null)
            return Recheck.Cleared
        }
        val records = api.lookup(pending.handle)?.records?.map { it.record }
        if (records == null) {
            store.savePending(null)
            return Recheck.Cleared
        }
        if (records.isEmpty()) throw UsernameException("verification")
        if (records.size == 1 && core.isOwnClaim(records[0], pending)) return Recheck.Finished(bind(records[0], pending, store))
        store.savePending(null)
        return Recheck.Cleared
    }

    private suspend fun submit(pending: PendingUsernameClaim, device: DeviceProof, store: UsernameStore): UsernameBinding {
        try {
            val (handle, record) = api.claim(pending, device)
            if (handle != pending.handle || !core.isOwnClaim(record, pending)) throw UsernameException("verification")
            return bind(record, pending, store)
        } catch (e: UsernameException) {
            // The registry refused before it wrote anything: the claim is gone. A device check that
            // was unavailable keeps it, as on iOS.
            if (e.code in listOf("nonce.invalid", "device.slot_used", "name.unavailable", "geo.blocked", "device.invalid",
                    "device.unsupported")) store.savePending(null)
            throw e
        }
    }

    /**
     * The found name as this wallet's binding, or null when a check fails. The chain must be one record
     * that is the head, with no tombstone, the claim checks of the core (registrar and owner signatures,
     * handle, owner, network, rules) must pass, and its owner must be a subaddress of the open wallet. No
     * nonce, signature or device proof is made, nothing is posted, and the phone's free claim stays. A chain
     * with later records (a rotation, a subhandle) stays unavailable: the app makes none, and verify_claim
     * checks a genesis head only (iOS parity).
     */
    private suspend fun restore(found: HeldName, handle: String, displayHandle: String, walletKey: String, network: String,
                                rules: String, owns: suspend (String) -> Boolean, isCurrentWallet: () -> Boolean,
                                store: UsernameStore): UsernameBinding? {
        val owner = found.owner ?: return null
        val hash = found.headHash ?: return null
        val row = found.records.singleOrNull() ?: return null
        if (found.handle != handle || found.tombstoned != false || found.headSeq != 0L || found.genesisHash != hash ||
            row.recordHash != hash || owner.isEmpty()) return null
        // The checks of a claim's reply, with the found head and owner in place of a saved claim. It is
        // never saved and holds no request or signature.
        val held = PendingUsernameClaim(walletKey, network, handle, displayHandle, owner, nonce = "", unsignedRecord = "",
            recordHash = hash, ownerSignature = "", registrarKey = configuration.registrarKey, rules = rules,
            rulesHash = configuration.rulesHash)
        // The record verifies before the wallet is asked: one that fails never makes the wallet list a subaddress.
        if (!core.isOwnClaim(row.record, held)) return null
        if (!owns(owner)) return null
        if (!isCurrentWallet()) throw UsernameException("wallet_changed")
        return bind(row.record, held, store, usesFreeClaim = false)
    }

    private fun bind(record: String, pending: PendingUsernameClaim, store: UsernameStore, usesFreeClaim: Boolean = true): UsernameBinding =
        UsernameBinding(pending.walletKey, pending.network, pending.handle, pending.displayHandle, pending.subaddress,
            pending.recordHash, record, System.currentTimeMillis()).also { store.accept(it, usesFreeClaim) }
}

internal fun json(vararg fields: Pair<String, Any>) = JSONObject().apply { fields.forEach { (k,v) -> put(k,v) } }
