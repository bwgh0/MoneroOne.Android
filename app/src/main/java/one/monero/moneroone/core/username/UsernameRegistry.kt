package one.monero.moneroone.core.username

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import one.monero.moneroone.BuildConfig
import org.json.JSONArray
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
    /**
     * The held name, or null while the handle is free. A name the registry holds throws [UsernameException]
     * [UsernameException.HELD] with its checked hold; one whose notice fails the check throws the generic error.
     */
    suspend fun lookup(handle: String): HeldName?
    suspend fun nonce(handle: String, subaddress: String): String
    /** Posts the signed claim. Returns the handle and the record the registry admitted. */
    suspend fun claim(pending: PendingUsernameClaim, device: DeviceProof): Pair<String, String>
    /**
     * Posts an owner record (`POST /v1/handles/{handle}/records` with `{record, owner_signatures}`): here a
     * tombstone. Returns the handle and the record the registry admitted. A hold throws as in [lookup].
     */
    suspend fun appendRecord(handle: String, record: String, ownerSignatures: List<String>): Pair<String, String>
}

/** How a registry refusal becomes a [UsernameException]. */
internal object UsernameRegistryAnswer {
    /**
     * The failure for an answer with HTTP [status] and body [text]. [held] names the handle of a lookup or an
     * owner record: a `451 name.held` for it is [UsernameException.HELD] once its notice passes the check with
     * [registrarKey]. A notice that fails a check, or a hold for another request, gives the generic error with
     * no reason. Other codes stay as the registry sends them (for example [UsernameException.TERMS_OUTDATED]).
     */
    fun failure(status: Int, text: String, held: String?, registrarKey: String): UsernameException {
        val body = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        val code = ((body as? JsonObject)?.get("error") as? JsonObject)?.get("code").text() ?: "server"
        if (code != UsernameException.HELD) return UsernameException(code)
        val hold = if (status == 451 && held != null && body != null) UsernameHold.parse(body, held, registrarKey) else null
        return if (hold != null) UsernameException(UsernameException.HELD, hold) else UsernameException("server")
    }
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

    /** [held]: the handle of a lookup or an owner record, whose `451 name.held` gives its checked hold. */
    private suspend fun request(path: String, body: JSONObject? = null, held: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${configuration.url}/v1/$path").apply {
            if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
        }.build()
        client.newCall(request).execute().use { response ->
            val source = response.body?.source() ?: throw UsernameException("verification")
            source.request(1_048_577)
            if (source.buffer.size > 1_048_576) throw UsernameException("verification")
            val text = source.readUtf8()
            if (!response.isSuccessful) throw UsernameRegistryAnswer.failure(response.code, text, held, configuration.registrarKey)
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
        val found = try { JSONObject(request("handles/$handle", held = handle)) }
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

    override suspend fun appendRecord(handle: String, record: String, ownerSignatures: List<String>): Pair<String, String> {
        val body = JSONObject().put("record", record).put("owner_signatures", JSONArray(ownerSignatures))
        val response = JSONObject(request("handles/$handle/records", body, held = handle))
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
            // A held name stops the claim here, and a saved claim stays.
            lookUp(handle, network, store)?.let { found ->
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
        // A held name throws here and keeps the saved claim: its result is not known.
        val records = lookUp(pending.handle, pending.network, store)?.records?.map { it.record }
        if (records == null) {
            store.savePending(null)
            return Recheck.Cleared
        }
        if (records.isEmpty()) throw UsernameException("verification")
        if (records.size == 1 && core.isOwnClaim(records[0], pending)) return Recheck.Finished(bind(records[0], pending, store))
        store.savePending(null)
        return Recheck.Cleared
    }

    /**
     * `GET /v1/handles/{handle}`. The store learns the name's hold: a checked hold is saved, then thrown as
     * [UsernameException.HELD]; a live or unknown name clears it. A hold is an answer: nothing is read elsewhere.
     */
    private suspend fun lookUp(handle: String, network: String, store: UsernameStore): HeldName? {
        val found = try { api.lookup(handle) } catch (e: UsernameException) {
            e.hold?.let { store.noteHold(it, handle, network) }
            throw e
        }
        store.noteHold(null, handle, network)
        return found
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

    // The wallet's own name: its state and its retire.

    /**
     * The registry's state of the wallet's own name. The store learns a hold (or its end), so
     * [UsernameStore.nameState] shows it; the @name screen runs this when it opens. It signs and saves nothing
     * else. Throws while the registry cannot answer, or [UsernameException] "verification" when its answer is
     * not about this name (another genesis or owner).
     */
    suspend fun status(binding: UsernameBinding, store: UsernameStore): UsernameNameStatus {
        val found = try { lookUp(binding.handle, binding.network, store) } catch (e: UsernameException) {
            e.hold?.let { return UsernameNameStatus.Held(it) }
            throw e
        } ?: return UsernameNameStatus.NotFound
        if (found.handle != binding.handle || found.genesisHash != binding.genesisHash) throw UsernameException("verification")
        val tombstoned = found.tombstoned ?: throw UsernameException("verification")
        if (tombstoned) return UsernameNameStatus.Retired
        // The owner signs the retire: the name must still point to it.
        val head = UsernameHead.of(found.headSeq, found.headHash)
        if (found.owner != binding.subaddress || head == null || (head.seq == 0L && head.recordHash != binding.genesisHash))
            throw UsernameException("verification")
        return UsernameNameStatus.Live(head)
    }

    /**
     * Retires the wallet's own name: signs a tombstone with the owner key and posts it
     * (`POST /v1/handles/{handle}/records`). A live name gets it at head seq + 1 over the head hash; a `notice`
     * or `terms` hold at `hold.head.seq + 1` over `hold.head.record_hash`. On success, and when the registry
     * shows the name retired or gone already, the app forgets the name, its saved claim and its hold; the free
     * claim date stays.
     *
     * Throws [UsernameException]: [UsernameException.HELD] for an `order` or `sanctions` hold (no retire; the
     * store shows the hold), "wallet_changed", "storage", "pending" (a claim runs), "verification" (a reply that
     * fails a check: the name stays), and the registry's other refusals as in a claim. [sign] is the claim's
     * signer: (message, subaddress) to a `SigV2` string.
     */
    suspend fun retire(binding: UsernameBinding, store: UsernameStore, sign: suspend (String, String) -> String,
                       isCurrentWallet: () -> Boolean) {
        if (!isCurrentWallet() || binding.walletKey.isEmpty()) throw UsernameException("wallet_changed")
        if (store.storageError) throw UsernameException("storage")
        // One username request at a time: a claim and a retire never overlap.
        if (!store.claimMutex.tryLock()) throw UsernameException("pending")
        try {
            if (store.state.value.binding(binding.walletKey, binding.network) != binding) throw UsernameException("wallet_changed")
            val head = when (val status = status(binding, store)) {
                // An earlier retire landed (its reply was lost), or the epoch erased the name.
                UsernameNameStatus.Retired, UsernameNameStatus.NotFound -> { store.forget(binding); return }
                is UsernameNameStatus.Live -> status.head
                is UsernameNameStatus.Held -> {
                    val held = status.hold.head?.takeIf { status.hold.allowsRetire }
                        ?: throw UsernameException(UsernameException.HELD, status.hold)
                    // A genesis head must be this wallet's genesis.
                    if (held.seq == 0L && held.recordHash != binding.genesisHash) throw UsernameException("verification")
                    held
                }
            }
            val unsigned = UsernameTombstone.unsigned(binding.handle, head.seq + 1, head.recordHash)
                ?: throw UsernameException("verification")
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val signature = sign(UsernameTombstone.signingMessage(unsigned), binding.subaddress)
            if (!signature.startsWith("SigV2")) throw UsernameException("verification")
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val reply = try { api.appendRecord(binding.handle, UsernameBase64.encode(unsigned), listOf(signature)) }
                catch (e: UsernameException) {
                    when {
                        // The hold changed to `order` or `sanctions` since the lookup.
                        e.hold != null -> { store.noteHold(e.hold, binding.handle, binding.network); throw e }
                        // The same retire landed from an earlier try.
                        e.code == UsernameException.RETIRED -> null
                        else -> throw e
                    }
                }
            if (reply != null) {
                val record = UsernameBase64.decode(reply.second)
                if (reply.first != binding.handle || record == null ||
                    !UsernameTombstone.isCountersigned(record, unsigned, configuration.registrarKey)) throw UsernameException("verification")
            }
            store.forget(binding)
        } finally { store.claimMutex.unlock() }
    }
}

internal fun json(vararg fields: Pair<String, Any>) = JSONObject().apply { fields.forEach { (k,v) -> put(k,v) } }
