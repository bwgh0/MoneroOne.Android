package one.monero.moneroone.core.username

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameTrustTest {
    private val pointOneXmr = 100_000_000_000L

    @Test fun publishedHasNoLimit() {
        assertNull(UsernameTrust.PUBLISHED.amountLimit)
        assertNull(UsernameTrust.PUBLISHED.limitMessage)
    }

    @Test fun pendingStaleAndExpiredShareTheLimit() {
        for (trust in listOf(UsernameTrust.PENDING, UsernameTrust.STALE, UsernameTrust.EXPIRED)) {
            assertEquals(trust.name, pointOneXmr, trust.amountLimit)
        }
    }

    @Test fun theMessageFitsTheTrust() {
        assertEquals("This username is not in the public log yet. The payment limit is 0.1 XMR.", UsernameTrust.PENDING.limitMessage)
        for (trust in listOf(UsernameTrust.STALE, UsernameTrust.EXPIRED)) {
            assertEquals("Username records are out of date. The payment limit is 0.1 XMR.", trust.limitMessage)
        }
    }
}
