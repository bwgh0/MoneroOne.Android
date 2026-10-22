package one.monero.moneroone.core.username

import one.monero.moneroone.core.openalias.DnssecStatus
import one.monero.moneroone.core.openalias.OpenAliasResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameSendCheckTest {
    private val reviewed = "reviewed-test-address"
    private val limit = 100_000_000_000L
    private val changed = "The username changed since your review. Look it up again before sending."
    private val outOfDate = "Username records are out of date. The payment limit is 0.1 XMR."
    private val notInLog = "This username is not in the public log yet. The payment limit is 0.1 XMR."

    private fun checked(trust: UsernameTrust, address: String = reviewed) =
        OpenAliasResult(address, null, DnssecStatus.OFF, usernameTrust = trust)

    private fun stop(trust: UsernameTrust, sentAmount: Long, address: String = reviewed) =
        UsernameSendCheck.stopReason(reviewed, checked(trust, address), sentAmount)

    @Test fun aNewAddressStops() {
        for (trust in UsernameTrust.entries) assertEquals(trust.name, changed, stop(trust, 1L, address = "other-test-address"))
    }

    @Test fun aNewTrustAloneDoesNotStop() {
        // Reviewed as pending, now published: the limit goes away.
        assertNull(stop(UsernameTrust.PUBLISHED, limit * 5))
        // Reviewed as published, now stale or expired: under the limit it sends.
        assertNull(stop(UsernameTrust.STALE, limit))
        assertNull(stop(UsernameTrust.EXPIRED, 1L))
    }

    @Test fun theNewTrustLimitApplies() {
        assertEquals(outOfDate, stop(UsernameTrust.STALE, limit + 1))
        assertEquals(outOfDate, stop(UsernameTrust.EXPIRED, limit + 1))
        assertEquals(notInLog, stop(UsernameTrust.PENDING, limit + 1))
    }

    @Test fun sendAllIsJudgedByTheAmountItSends() {
        // The review switched to send all because amount and fee were over the balance.
        for (trust in listOf(UsernameTrust.PENDING, UsernameTrust.STALE, UsernameTrust.EXPIRED)) {
            assertNull(trust.name, stop(trust, limit - 5_000_000L))
            assertNull(trust.name, stop(trust, limit))
        }
        assertEquals(outOfDate, stop(UsernameTrust.STALE, limit + 1))
        assertEquals(notInLog, stop(UsernameTrust.PENDING, limit * 3))
    }
}
