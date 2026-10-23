package one.monero.moneroone.core.username

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The claim messages of the shared contract (registry code or platform error, then the text). */
class UsernameMessagesTest {
    @Test fun eachRegistryCodeShowsItsText() {
        val texts = mapOf(
            "geo.blocked" to "Username registration isn't available in your region.",
            "device.invalid" to "This phone can't claim a username.",
            "device.unsupported" to "This phone can't claim a username.",
            "device.unavailable" to "The claim could not be completed. Please try again.",
            "not_configured" to "Username registration isn't available yet.",
            "device.slot_used" to "A free username isn't available on this phone.",
            "name.unavailable" to "This username is unavailable.",
            "handle.reserved" to "This username is reserved to protect users from scams.",
            "handle.blocked" to "This username is reserved to protect users from scams.",
            "rate_limited" to "The username service couldn't complete the request. Please try again.",
        )
        texts.forEach { (code, text) -> assertEquals(code, text, UsernameException(code).message) }
    }

    @Test fun theNewTextsAreTranslated() {
        for (text in listOf("Username registration isn't available in your region.", "This phone can't claim a username."))
            assertTrue(text, text in one.monero.moneroone.core.locale.localizedStrings)
    }
}
