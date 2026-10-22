package one.monero.moneroone.core.username

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.monero.moneroone.BuildConfig
import org.json.JSONObject
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

data class UsernameConfiguration(val url: String, val registrarKey: String, val rulesHash: String) {
    companion object {
        val current: UsernameConfiguration? get() {
            if (!BuildConfig.DEBUG) return null
            val uri = runCatching { URI(BuildConfig.USERNAME_REGISTRY_URL) }.getOrNull() ?: return null
            if (uri.host != "localhost" || uri.scheme !in listOf("http", "https") || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            val hex = Regex("[0-9a-f]{64}")
            if (!hex.matches(BuildConfig.USERNAME_REGISTRAR_KEY) || !hex.matches(BuildConfig.USERNAME_RULES_HASH)) return null
            return UsernameConfiguration(uri.toString().trimEnd('/'), BuildConfig.USERNAME_REGISTRAR_KEY, BuildConfig.USERNAME_RULES_HASH)
        }
    }
}

/** The registry calls a claim makes. Unit tests replace it. */
interface UsernameRegistryApi {
    /** The rules text, after the registry's info matches the pinned configuration for [network]. */
    suspend fun checkedRules(network: String): String
    /** The handle's records, oldest first, or null while the handle is free. */
    suspend fun records(handle: String): List<String>?
    suspend fun nonce(handle: String, subaddress: String): String
    /** Posts the signed claim. Returns the handle and the record the registry admitted. */
    suspend fun claim(pending: PendingUsernameClaim, deviceProof: String): Pair<String, String>
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

    override suspend fun records(handle: String): List<String>? {
        val rows = try { JSONObject(request("handles/$handle")).getJSONArray("records") }
            catch (e: UsernameException) { if (e.code == "handle.not_found") return null else throw e }
        return List(rows.length()) { rows.getJSONObject(it).getString("record") }
    }

    override suspend fun nonce(handle: String, subaddress: String): String =
        JSONObject(request("nonce", json("handle" to handle, "subaddress" to subaddress))).getString("nonce")

    override suspend fun claim(pending: PendingUsernameClaim, deviceProof: String): Pair<String, String> {
        val response = JSONObject(request("claims", json("nonce" to pending.nonce, "record" to pending.unsignedRecord,
            "owner_signature" to pending.ownerSignature, "device" to json("platform" to "dev", "proof" to deviceProof),
            "terms_version" to "local-development-v1")))
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

/** Local development claim client. All record construction and verification is in oah-core. */
class UsernameRegistry internal constructor(
    private val configuration: UsernameConfiguration,
    private val api: UsernameRegistryApi,
    private val core: UsernameClaimCore,
    private val developmentClaims: Boolean,
) {
    constructor(configuration: UsernameConfiguration) :
        this(configuration, HttpUsernameRegistryApi(configuration), OahClaimCore, BuildConfig.DEBUG)

    /** What a re-check of a saved pending claim found. */
    private sealed interface Recheck {
        /** The name points to the pending claim's wallet: the claim is done. */
        data class Finished(val binding: UsernameBinding) : Recheck
        /** The name is free, or someone else holds it. The pending claim is cleared. */
        data class Cleared(val taken: Boolean) : Recheck
    }

    suspend fun claim(displayHandle: String, walletKey: String, network: String, store: UsernameStore,
                      makeSubaddress: suspend () -> String, sign: suspend (String, String) -> String,
                      isCurrentWallet: () -> Boolean): UsernameBinding {
        if (!isCurrentWallet() || walletKey.isEmpty()) throw UsernameException("wallet_changed")
        if (store.storageError) throw UsernameException("storage")
        if (!store.claimMutex.tryLock()) throw UsernameException("pending")
        try {
            val handle = core.normalize(displayHandle)
            // This wallet's earlier claim of the same name, cleared because the name was free.
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
                    is Recheck.Cleared -> if (same) {
                        if (outcome.taken) throw UsernameException("name.unavailable")
                        earlier = pending
                    }
                }
            }
            val rules = api.checkedRules(network)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            api.records(handle)?.let { records ->
                // A slow earlier POST can land after the re-check found the name free.
                val own = earlier?.takeIf { records.size == 1 && core.isOwnClaim(records[0], it) }
                if (own != null) return bind(records[0], own, store)
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
            // Saved before the POST: after a lost reply, the next claim re-checks this exact signed genesis.
            store.savePending(pending)
            return submit(pending, store)
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
            return Recheck.Cleared(taken = false)
        }
        val records = api.records(pending.handle)
        if (records == null) {
            store.savePending(null)
            return Recheck.Cleared(taken = false)
        }
        if (records.isEmpty()) throw UsernameException("verification")
        if (records.size == 1 && core.isOwnClaim(records[0], pending)) return Recheck.Finished(bind(records[0], pending, store))
        store.savePending(null)
        return Recheck.Cleared(taken = true)
    }

    private suspend fun submit(pending: PendingUsernameClaim, store: UsernameStore): UsernameBinding {
        if (!developmentClaims) throw UsernameException("not_configured")
        try {
            val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(store.state.value.deviceId.toByteArray(Charsets.UTF_8))
            val (handle, record) = api.claim(pending, proof)
            if (handle != pending.handle || !core.isOwnClaim(record, pending)) throw UsernameException("verification")
            return bind(record, pending, store)
        } catch (e: UsernameException) {
            if (e.code in listOf("nonce.invalid", "device.slot_used", "name.unavailable")) store.savePending(null)
            throw e
        }
    }

    private fun bind(record: String, pending: PendingUsernameClaim, store: UsernameStore): UsernameBinding =
        UsernameBinding(pending.walletKey, pending.network, pending.handle, pending.displayHandle, pending.subaddress,
            pending.recordHash, record, System.currentTimeMillis()).also(store::accept)
}

internal fun json(vararg fields: Pair<String, Any>) = JSONObject().apply { fields.forEach { (k,v) -> put(k,v) } }
