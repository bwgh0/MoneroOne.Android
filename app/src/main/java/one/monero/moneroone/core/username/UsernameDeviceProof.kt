package one.monero.moneroone.core.username

import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.monero.moneroone.BuildConfig
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The `device` object of a claim request: the platform and the base64url proof. */
data class DeviceProof(val platform: String, val proof: String)

/** What a failed Play Integrity request means for the claim. */
enum class IntegrityFailure {
    /** Network, quota, a Google server or a client hiccup: the claim can be tried again. */
    TRANSIENT,
    /** The phone cannot make a token: no Play Store or Play services, too old, or not this app. */
    PERMANENT,
    /** The build's cloud project number is wrong. */
    NOT_CONFIGURED,
}

class IntegrityTokenException(val failure: IntegrityFailure, cause: Throwable? = null) : Exception(cause)

/** Makes a standard Play Integrity token for one claim. Unit tests replace it. */
fun interface IntegrityTokenSource {
    /** A token whose `requestHash` is [requestHash]. Throws [IntegrityTokenException]. */
    suspend fun token(requestHash: String): String
}

/** The proof parts that the registry checks (`crates/registry/src`: service.rs, gates.rs, playintegrity.rs). */
object UsernameDeviceProof {
    private val claimLabel = "oah-registry/claim/v1".toByteArray(Charsets.US_ASCII)
    private val claimKeyLabel = "oah-registry/claim-key/v1".toByteArray(Charsets.US_ASCII)
    private val hex = Regex("[0-9a-f]{64}")

    /**
     * The secret that the same wallet sends on every claim: lowercase hex of
     * HMAC-SHA256(key = the 32-byte private view key, message = "oah-registry/claim-key/v1").
     * Never log or store it.
     */
    fun claimKey(privateViewKey: ByteArray): String {
        require(privateViewKey.size == 32) { "A private view key has 32 bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(privateViewKey, "HmacSHA256"))
        return mac.doFinal(claimKeyLabel).toHex()
    }

    /** What the device proof signs: SHA-256("oah-registry/claim/v1" || nonce || unsigned record). */
    fun claimDigest(nonce: ByteArray, unsignedRecord: ByteArray): ByteArray {
        require(nonce.size == 32) { "A nonce has 32 bytes" }
        return MessageDigest.getInstance("SHA-256").run {
            update(claimLabel); update(nonce); update(unsignedRecord); digest()
        }
    }

    /** The digest of the claim as the registry decodes it: the nonce in lowercase hex, the record in base64url. */
    fun claimDigest(pending: PendingUsernameClaim): ByteArray {
        val record = unb64(pending.unsignedRecord)
        if (!hex.matches(pending.nonce) || record == null) throw UsernameException("verification")
        return claimDigest(pending.nonce.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), record)
    }

    /** The token's `requestHash`: base64url, no padding, of the claim digest (43 characters). */
    fun requestHash(claimDigest: ByteArray): String = b64(claimDigest)

    fun android(integrityToken: String, claimKey: String): DeviceProof = DeviceProof("android", b64(
        JsonObject(mapOf("integrity_token" to JsonPrimitive(integrityToken), "claim_key" to JsonPrimitive(claimKey)))
            .toString().toByteArray(Charsets.UTF_8)))

    /** The proof that a development registry accepts. Debug builds only. */
    fun development(deviceId: String): DeviceProof = DeviceProof("dev", b64(deviceId.toByteArray(Charsets.UTF_8)))

    /** Sorts a [StandardIntegrityErrorCode], as Google's error code page advises. */
    fun failure(errorCode: Int): IntegrityFailure = when (errorCode) {
        StandardIntegrityErrorCode.API_NOT_AVAILABLE,
        StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
        StandardIntegrityErrorCode.APP_NOT_INSTALLED,
        StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
        StandardIntegrityErrorCode.APP_UID_MISMATCH,
        StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
        StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
        StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
        StandardIntegrityErrorCode.REQUEST_HASH_TOO_LONG -> IntegrityFailure.PERMANENT
        StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID -> IntegrityFailure.NOT_CONFIGURED
        // NETWORK_ERROR, TOO_MANY_REQUESTS, GOOGLE_SERVER_UNAVAILABLE, CLIENT_TRANSIENT_ERROR,
        // INTERNAL_ERROR, a provider that stayed invalid, and codes this build does not know.
        else -> IntegrityFailure.TRANSIENT
    }

    private fun b64(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    /** Strict, as the registry's `unb64`: base64url, no padding, canonical. */
    private fun unb64(text: String): ByteArray? =
        runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull()?.takeIf { b64(it) == text }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/**
 * Picks the device proof of a claim. A release build sends only a Play Integrity proof.
 * A debug build sends the development proof when the phone cannot make a token.
 */
class UsernameDeviceGate(debug: Boolean, private val integrity: IntegrityTokenSource?) {
    // The build-time guard: a release build never sends the development proof.
    private val development = BuildConfig.DEBUG && debug

    /** Throws [UsernameException]: "not_configured", "device.unsupported" or "device.unavailable". */
    suspend fun proof(pending: PendingUsernameClaim, deviceId: String, claimKey: suspend () -> String): DeviceProof {
        val source = integrity ?: return fallback(deviceId, "not_configured")
        val requestHash = UsernameDeviceProof.requestHash(UsernameDeviceProof.claimDigest(pending))
        val key = claimKey()
        val token = try { source.token(requestHash) } catch (e: IntegrityTokenException) {
            return when (e.failure) {
                IntegrityFailure.TRANSIENT -> throw UsernameException("device.unavailable")
                IntegrityFailure.PERMANENT -> fallback(deviceId, "device.unsupported")
                IntegrityFailure.NOT_CONFIGURED -> fallback(deviceId, "not_configured")
            }
        }
        return UsernameDeviceProof.android(token, key)
    }

    private fun fallback(deviceId: String, code: String): DeviceProof =
        if (development) UsernameDeviceProof.development(deviceId) else throw UsernameException(code)
}
