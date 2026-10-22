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

/** Local development claim client. All record construction and verification is in oah-core. */
class UsernameRegistry(private val configuration: UsernameConfiguration) {
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

    suspend fun claim(displayHandle: String, walletKey: String, network: String, store: UsernameStore,
                      makeSubaddress: suspend () -> String, sign: suspend (String, String) -> String,
                      isCurrentWallet: () -> Boolean): UsernameBinding {
        if (!isCurrentWallet() || walletKey.isEmpty()) throw UsernameException("wallet_changed")
        if (store.storageError) throw UsernameException("storage")
        if (!store.claimMutex.tryLock()) throw UsernameException("pending")
        try {
            val handle = OahCore.normalize(displayHandle)
            store.state.value.pending?.let {
                if (it.walletKey != walletKey || it.network != network || it.handle != handle) throw UsernameException("pending")
                return submit(it, store)
            }
            val info = JSONObject(request("info"))
            if (info.optString("registrar_key") != configuration.registrarKey || info.optString("rules_hash") != configuration.rulesHash ||
                info.optString("network") != network || info.optString("zone") != "monero.one" || !info.optBoolean("open")) throw UsernameException("verification")
            val rules = request("rules")
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            try { request("handles/$handle"); throw UsernameException("name.unavailable") }
            catch (e: UsernameException) { if (e.code != "handle.not_found") throw e }
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val address = makeSubaddress()
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val nonce = JSONObject(request("nonce", json("handle" to handle, "subaddress" to address))).getString("nonce")
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val unsigned = OahCore.call(json("op" to "prepare_claim", "handle" to handle, "subaddress" to address,
                "nonce" to nonce, "network" to network, "rules" to rules, "rules_hash" to configuration.rulesHash))
            val signature = sign(unsigned.getString("signing_message"), address)
            if (!isCurrentWallet()) throw UsernameException("wallet_changed")
            val pending = PendingUsernameClaim(walletKey, network, handle, displayHandle, address, nonce,
                unsigned.getString("record"), unsigned.getString("record_hash"), signature,
                configuration.registrarKey, rules, configuration.rulesHash)
            store.savePending(pending)
            return submit(pending, store)
        } finally { store.claimMutex.unlock() }
    }

    private suspend fun submit(pending: PendingUsernameClaim, store: UsernameStore): UsernameBinding {
        if (pending.registrarKey != configuration.registrarKey || pending.rulesHash != configuration.rulesHash) throw UsernameException("verification")
        try {
            val records = JSONObject(request("handles/${pending.handle}")).getJSONArray("records")
            if (records.length() != 1) throw UsernameException("verification")
            return accept(records.getJSONObject(0).getString("record"), pending, store)
        } catch (e: UsernameException) { if (e.code != "handle.not_found") throw e }
        if (!BuildConfig.DEBUG) throw UsernameException("not_configured")
        try {
            val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(store.state.value.deviceId.toByteArray(Charsets.UTF_8))
            val response = JSONObject(request("claims", json("nonce" to pending.nonce, "record" to pending.unsignedRecord,
                "owner_signature" to pending.ownerSignature, "device" to json("platform" to "dev", "proof" to proof),
                "terms_version" to "local-development-v1")))
            if (response.optString("handle") != pending.handle) throw UsernameException("verification")
            return accept(response.getString("record"), pending, store)
        } catch (e: UsernameException) {
            if (e.code in listOf("nonce.invalid", "device.slot_used", "name.unavailable")) store.savePending(null)
            throw e
        }
    }
    private fun accept(record: String, pending: PendingUsernameClaim, store: UsernameStore): UsernameBinding {
        try {
            OahCore.call(json("op" to "verify_claim", "record" to record, "expected_record_hash" to pending.recordHash,
                "handle" to pending.handle, "subaddress" to pending.subaddress, "network" to pending.network,
                "registrar_key" to pending.registrarKey, "rules" to pending.rules, "rules_hash" to pending.rulesHash))
        } catch (_: Exception) { throw UsernameException("verification") }
        return UsernameBinding(pending.walletKey, pending.network, pending.handle, pending.displayHandle, pending.subaddress,
            pending.recordHash, record, System.currentTimeMillis()).also(store::accept)
    }
}

internal fun json(vararg fields: Pair<String, Any>) = JSONObject().apply { fields.forEach { (k,v) -> put(k,v) } }
