package one.monero.moneroone.core.username

import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Each claim token gets a new Play provider (audit F3). Google refreshes device recall only in the warmup, so a
 * kept provider would show the phone's claim slot as unused to a second claim.
 */
class PlayIntegrityTokenSourceTest {
    private val play = FakePlayIntegrity()
    private val source = PlayIntegrityTokenSource(play)

    private fun token(hash: String) = runBlocking { source.token(hash) }
    private fun failure(hash: String = "hash"): IntegrityTokenException {
        try { token(hash) } catch (e: IntegrityTokenException) { return e }
        fail("Expected a failure"); throw AssertionError()
    }

    @Test fun eachTokenPreparesANewProvider() {
        assertEquals("token-1", token("first"))
        assertEquals("token-2", token("second"))
        // The same claim asked again (a retry) also gets a new provider.
        assertEquals("token-3", token("first"))
        assertEquals(3, play.prepares)
        // Each provider makes one token only.
        assertEquals(listOf(1 to "first", 2 to "second", 3 to "first"), play.requests)
    }

    @Test fun anInvalidProviderPreparesAgainOnce() {
        play.requestErrors += StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID
        assertEquals("token-2", token("hash"))
        assertEquals(listOf(1 to "hash", 2 to "hash"), play.requests)
        // A provider that stays invalid is a transient failure, after one more prepare.
        play.requestErrors += listOf(StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID,
            StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID)
        assertEquals(IntegrityFailure.TRANSIENT, failure().failure)
        assertEquals(4, play.prepares)
    }

    @Test fun anotherRequestErrorIsNotRetried() {
        play.requestErrors += StandardIntegrityErrorCode.NETWORK_ERROR
        assertEquals(IntegrityFailure.TRANSIENT, failure().failure)
        assertEquals(1, play.prepares)
        // The next claim does not reuse the provider of a failed one.
        assertEquals("token-2", token("hash"))
    }

    @Test fun playErrorsAreSortedAsTheGateExpects() {
        val rows = listOf(StandardIntegrityErrorCode.APP_NOT_INSTALLED to IntegrityFailure.PERMANENT,
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED to IntegrityFailure.PERMANENT,
            StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID to IntegrityFailure.NOT_CONFIGURED,
            StandardIntegrityErrorCode.TOO_MANY_REQUESTS to IntegrityFailure.TRANSIENT)
        for ((code, expected) in rows) {
            play.prepareError = code
            assertEquals("$code", expected, failure().failure)
        }
        // A failure that is not a Play error code is transient.
        val broken = PlayIntegrityTokenSource(object : StandardIntegrityPlay {
            override suspend fun prepare(): StandardIntegrityProvider = StandardIntegrityProvider { throw IllegalStateException("test") }
        })
        try { runBlocking { broken.token("hash") }; fail("Expected a failure") }
        catch (e: IntegrityTokenException) { assertEquals(IntegrityFailure.TRANSIENT, e.failure) }
    }

    @Test fun aCancelStaysACancel() {
        val cancelled = PlayIntegrityTokenSource(object : StandardIntegrityPlay {
            override suspend fun prepare(): StandardIntegrityProvider = throw CancellationException("test")
        })
        var cancel: Throwable? = null
        try { runBlocking { cancelled.token("hash") } } catch (e: Throwable) { cancel = e }
        assertTrue("$cancel", cancel is CancellationException)
    }
}
