package one.monero.moneroone.core.username

import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import one.monero.moneroone.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/** The device proof as the registry decodes it (crates/registry/src: service.rs, gates.rs, playintegrity.rs). */
class UsernameDeviceProofTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** The registry's own code gave these: registry::service::claim_digest and registry::playintegrity::request_hash. */
    private val registryNonce = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val registryRecord = "AwoRGB8mLTQ7QklQV15lbHN6gYiPlp2kq7K5wMfO1dzj6vH4_wYNFBsiKTA3PkVM"
    private val registryDigest = "4b295b126d431f17a055d51ae263d05070d0fc0cf6d11cb554a490ff6783aba5"
    private val registryRequestHash = "SylbEm1DHxegVdUa4mPQUHDQ_Az20Ry1VKSQ_2eDq6U"

    private fun pending(nonce: String = registryNonce, record: String = registryRecord) =
        PendingUsernameClaim("wallet-a", "mainnet", "joe", "Joe", "subaddress-1", nonce, record, "hash", "signature",
            "a".repeat(64), "rules", "b".repeat(64))

    /** Strict base64url without padding, as the registry's `unb64`. */
    private fun decodeProof(proof: String): JsonObject {
        assertFalse("no padding", proof.contains('='))
        assertTrue("base64url alphabet", proof.none { it == '+' || it == '/' })
        return Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(proof), Charsets.UTF_8)) as JsonObject
    }

    @Test fun theClaimKeyMatchesTheSharedVector() {
        assertEquals("6f19b34dced9cf703d2e0222e085d6a475695543cce6b912af942a541524ba6a",
            UsernameDeviceProof.claimKey(ByteArray(32) { it.toByte() }))
        assertEquals(testClaimKey, UsernameDeviceProof.claimKey(testViewKey))
    }

    @Test fun theSameWalletAlwaysGivesTheSameClaimKey() {
        assertEquals(UsernameDeviceProof.claimKey(testViewKey), UsernameDeviceProof.claimKey(testViewKey))
        assertTrue(UsernameDeviceProof.claimKey(testViewKey) != UsernameDeviceProof.claimKey(ByteArray(32)))
    }

    @Test fun theClaimDigestAndRequestHashMatchTheRegistry() {
        val nonce = ByteArray(32) { it.toByte() }
        val record = ByteArray(48) { (it * 7 + 3).toByte() }
        assertEquals(registryDigest, hex(UsernameDeviceProof.claimDigest(nonce, record)))
        val digest = UsernameDeviceProof.claimDigest(pending())
        assertEquals(registryDigest, hex(digest))
        assertEquals(registryRequestHash, UsernameDeviceProof.requestHash(digest))
        assertEquals(43, UsernameDeviceProof.requestHash(digest).length)
    }

    @Test fun aClaimTheRegistryCannotDecodeHasNoDigest() {
        for (bad in listOf(pending(nonce = registryNonce.uppercase()), pending(nonce = "00"), pending(record = "$registryRecord="),
                           pending(record = registryRecord.replace('_', '/')))) {
            try { UsernameDeviceProof.claimDigest(bad); fail("Expected a failure") }
            catch (e: UsernameException) { assertEquals("verification", e.code) }
        }
    }

    @Test fun theAndroidProofDecodesToTheRegistryJson() {
        val token = "eyJhbGciOiJBMjU2S1ciLCJlbmMiOiJBMjU2R0NNIn0.part-two_x.part3"
        val device = UsernameDeviceProof.android(token, testClaimKey)
        assertEquals("android", device.platform)
        val json = decodeProof(device.proof)
        assertEquals(setOf("integrity_token", "claim_key"), json.keys)
        assertEquals(token, json.getValue("integrity_token").jsonPrimitive.content)
        assertEquals(testClaimKey, json.getValue("claim_key").jsonPrimitive.content)
    }

    @Test fun theDevelopmentProofIsTheDeviceId() {
        val device = UsernameDeviceProof.development("device-id")
        assertEquals("dev", device.platform)
        assertEquals("device-id", String(Base64.getUrlDecoder().decode(device.proof)))
    }

    @Test fun playIntegrityErrorsAreSortedAsGoogleAdvises() {
        val permanent = listOf(StandardIntegrityErrorCode.API_NOT_AVAILABLE, StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
            StandardIntegrityErrorCode.APP_NOT_INSTALLED, StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
            StandardIntegrityErrorCode.APP_UID_MISMATCH, StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED, StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
            StandardIntegrityErrorCode.REQUEST_HASH_TOO_LONG)
        val transient = listOf(StandardIntegrityErrorCode.NETWORK_ERROR, StandardIntegrityErrorCode.TOO_MANY_REQUESTS,
            StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE, StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
            StandardIntegrityErrorCode.INTERNAL_ERROR, StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID, -999)
        permanent.forEach { assertEquals("$it", IntegrityFailure.PERMANENT, UsernameDeviceProof.failure(it)) }
        transient.forEach { assertEquals("$it", IntegrityFailure.TRANSIENT, UsernameDeviceProof.failure(it)) }
        assertEquals(IntegrityFailure.NOT_CONFIGURED, UsernameDeviceProof.failure(StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID))
    }

    // The gate, row by row. "debug" is BuildConfig.DEBUG && the build flag, so a release build never sends the dev proof.

    private fun proof(debug: Boolean, integrity: IntegrityTokenSource?): Result<DeviceProof> = runCatching {
        runBlocking { UsernameDeviceGate(debug, integrity).proof(pending(), "device-id") { testClaimKey } }
    }

    private fun Result<DeviceProof>.code() = (exceptionOrNull() as UsernameException).code

    private val dev = UsernameDeviceProof.development("device-id")

    @Test fun noCloudProjectNumber() {
        assertEquals("not_configured", proof(debug = false, integrity = null).code())
        if (BuildConfig.DEBUG) assertEquals(dev, proof(debug = true, integrity = null).getOrThrow())
    }

    @Test fun aPermanentPlatformError() {
        val failing = IntegrityTokenSource { throw IntegrityTokenException(IntegrityFailure.PERMANENT) }
        assertEquals("device.unsupported", proof(debug = false, integrity = failing).code())
        if (BuildConfig.DEBUG) assertEquals(dev, proof(debug = true, integrity = failing).getOrThrow())
    }

    @Test fun aTransientPlatformErrorNeverFallsBack() {
        val failing = IntegrityTokenSource { throw IntegrityTokenException(IntegrityFailure.TRANSIENT) }
        assertEquals("device.unavailable", proof(debug = false, integrity = failing).code())
        assertEquals("device.unavailable", proof(debug = true, integrity = failing).code())
    }

    @Test fun anInvalidCloudProjectNumber() {
        val failing = IntegrityTokenSource { throw IntegrityTokenException(IntegrityFailure.NOT_CONFIGURED) }
        assertEquals("not_configured", proof(debug = false, integrity = failing).code())
        if (BuildConfig.DEBUG) assertEquals(dev, proof(debug = true, integrity = failing).getOrThrow())
    }

    @Test fun aTokenGivesTheAndroidProofBoundToTheClaim() {
        for (debug in listOf(false, true)) {
            val asked = mutableListOf<String>()
            val device = proof(debug, IntegrityTokenSource { asked += it; "token" }).getOrThrow()
            assertEquals(listOf(registryRequestHash), asked)
            assertEquals(UsernameDeviceProof.android("token", testClaimKey), device)
        }
    }

    @Test fun theProofJsonHasNoOtherFields() {
        // The registry's serde structs deny unknown fields.
        val json = decodeProof(UsernameDeviceProof.android("t", testClaimKey).proof)
        assertEquals(JsonObject(mapOf("integrity_token" to JsonPrimitive("t"), "claim_key" to JsonPrimitive(testClaimKey))), json)
    }
}
