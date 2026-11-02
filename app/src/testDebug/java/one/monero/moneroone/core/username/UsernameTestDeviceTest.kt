package one.monero.moneroone.core.username

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** The staging test device proof against the worked example in monero-one-registry ops/DEPLOY.md. */
class UsernameTestDeviceTest {
    private val zeroKey = "0".repeat(64)
    private val claimKey = "11".repeat(32)
    private val deviceId = "00000000-0000-4000-8000-000000000001"
    private val expectedMac = "3861641707b6370a6b8953065dd944e24909b403e9c7b7c36e5f8e4d6316e0ca"
    private val expectedProof = "eyJjbGFpbV9rZXkiOiIxMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExMTExIiwiZGV2aWNlX2lkIjoiMDAwMDAwMDAtMDAwMC00MDAwLTgwMDAtMDAwMDAwMDAwMDAxIiwidGVzdF9tYWMiOiIzODYxNjQxNzA3YjYzNzBhNmI4OTUzMDY1ZGQ5NDRlMjQ5MDliNDAzZTljN2I3YzM2ZTVmOGU0ZDYzMTZlMGNhIn0"

    @After fun clear() = UsernameTestDevice.reset()

    private fun fields(proof: DeviceProof): Map<String, String> =
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(proof.proof), Charsets.UTF_8))
            .jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test fun theWorkedExampleMac() {
        assertEquals(expectedMac, UsernameTestDevice.mac(UsernameTestDevice.parseKey(zeroKey)!!, claimKey, deviceId))
    }

    @Test fun theWorkedExampleProofDecodesToTheRegistryFields() {
        val proof = UsernameTestDevice.proof(ByteArray(32), claimKey, deviceId)!!
        assertEquals("dev", proof.platform)
        assertFalse(proof.proof.contains('=') || proof.proof.contains('+') || proof.proof.contains('/'))
        assertEquals(mapOf("device_id" to deviceId, "claim_key" to claimKey, "test_mac" to expectedMac), fields(proof))
        assertEquals(fields(DeviceProof("dev", expectedProof)), fields(proof))
    }

    @Test fun badKeysAreRefused() {
        for (bad in listOf(null, "", "zz".repeat(32), "0".repeat(63), "0".repeat(65), "0x" + "0".repeat(62))) {
            assertNull(bad, UsernameTestDevice.parseKey(bad))
            assertFalse(UsernameTestDevice.configure(bad) { deviceId })
            assertFalse(UsernameTestDevice.active)
        }
        assertNull(UsernameTestDevice.proof(ByteArray(32), "11".repeat(31), deviceId))
        assertNull(UsernameTestDevice.proof(ByteArray(32), claimKey.uppercase().replace('1', 'A'), deviceId))
    }

    // The gate. Today's proof is the plain dev proof of the store's device id.

    private fun gate(integrity: IntegrityTokenSource? = null) =
        runBlocking { UsernameDeviceGate(debug = true, integrity).proof(pending(), "store-device") { claimKey } }

    private val today = UsernameDeviceProof.development("store-device")

    private fun pending() = PendingUsernameClaim("wallet-a", "mainnet", "joe", "Joe", "subaddress-1",
        "00".repeat(32), "AAAA", "hash", "signature", "a".repeat(64), "rules", "b".repeat(64))

    @Test fun noKeySendsTodaysProof() {
        assertEquals(today, gate())
    }

    @Test fun aBadKeySendsTodaysProof() {
        assertFalse(UsernameTestDevice.configure("not-a-key") { deviceId })
        assertEquals(today, gate())
    }

    @Test fun aGoodKeySendsTheTestProofAndSkipsPlayIntegrity() {
        assertTrue(UsernameTestDevice.configure(zeroKey) { deviceId })
        val unused = IntegrityTokenSource { error("Play Integrity must not run with the test key") }
        assertEquals(DeviceProof("dev", expectedProof), gate(unused))
    }

    @Test fun aReleaseGateNeverSendsTheTestProof() {
        assertTrue(UsernameTestDevice.configure(zeroKey) { deviceId })
        val code = runCatching { runBlocking { UsernameDeviceGate(debug = false, null).proof(pending(), "store-device") { claimKey } } }
            .exceptionOrNull() as UsernameException
        assertEquals("not_configured", code.code)
    }
}
