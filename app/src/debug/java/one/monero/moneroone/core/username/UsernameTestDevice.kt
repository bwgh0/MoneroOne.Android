package one.monero.moneroone.core.username

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The staging test device proof (registry ops/DEPLOY.md, "Test device proofs"). Debug builds only:
 * src/release holds a stub with no signer, no domain tag and no launch value name.
 *
 * An emulator cannot pass Play Integrity, so staging accepts the debug `dev` proof when it carries
 * test_mac = HMAC-SHA256(test key, "oah-registry/device-test/v1" || 32 claim key bytes || UTF-8 device id).
 * The key comes from the launch value only:
 * `adb shell am start -n one.monero.moneroone.debug/one.monero.moneroone.MainActivity -e USERNAME_TEST_DEVICE_KEY <64 hex>`.
 * It stays in this process's memory. Never log it.
 */
object UsernameTestDevice {
    const val EXTRA = "USERNAME_TEST_DEVICE_KEY"
    private val tag = "oah-registry/device-test/v1".toByteArray(Charsets.US_ASCII)
    private val hex = Regex("[0-9a-fA-F]{64}")
    private val claimKeyHex = Regex("[0-9a-f]{64}")

    private class Signer(val key: ByteArray, val deviceId: String)
    @Volatile private var signer: Signer? = null

    /** True when a launch gave a well-formed key: claims then send the test proof. */
    val active: Boolean get() = signer != null

    /** Takes the launch value from [intent], if it has one. A malformed value logs one line and changes nothing. */
    fun capture(context: Context, intent: Intent?) {
        val raw = intent?.getStringExtra(EXTRA) ?: return
        if (!configure(raw) { deviceId(context) }) {
            Log.w("UsernameTestDevice", "The test device launch value is malformed; claims send the plain dev proof.")
        }
    }

    /** False, and nothing changes, when [rawKey] is missing or not 64 hex digits. */
    internal fun configure(rawKey: String?, deviceId: () -> String): Boolean {
        val key = parseKey(rawKey) ?: return false
        signer = Signer(key, deviceId())
        return true
    }

    internal fun reset() { signer = null }

    /** The test proof for [claimKey], or null without a key (the caller then sends today's proof). */
    fun proof(claimKey: String): DeviceProof? = signer?.let { proof(it.key, claimKey, it.deviceId) }

    internal fun parseKey(raw: String?): ByteArray? {
        val text = raw?.trim() ?: return null
        if (!hex.matches(text)) return null
        return text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /** Null when [claimKey] is not 64 lowercase hex digits. */
    internal fun proof(key: ByteArray, claimKey: String, deviceId: String): DeviceProof? {
        if (key.size != 32 || !claimKeyHex.matches(claimKey)) return null
        val json = JsonObject(mapOf(
            "claim_key" to JsonPrimitive(claimKey),
            "device_id" to JsonPrimitive(deviceId),
            "test_mac" to JsonPrimitive(mac(key, claimKey, deviceId)),
        )).toString()
        return DeviceProof("dev", Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8)))
    }

    internal fun mac(key: ByteArray, claimKey: String, deviceId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(tag)
        mac.update(claimKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        mac.update(deviceId.toByteArray(Charsets.UTF_8))
        return mac.doFinal().joinToString("") { "%02x".format(it) }
    }

    /** One UUID per install. Clearing the app data gives a new one, and with it a new free claim on staging. */
    private fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("username_test_device", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).commit()
        return id
    }
}
