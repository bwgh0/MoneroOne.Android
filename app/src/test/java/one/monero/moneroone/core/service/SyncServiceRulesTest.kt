package one.monero.moneroone.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The background sync service: the stop before the dataSync limit. */
class SyncServiceRulesTest {

    private val hour = 60 * 60 * 1000L
    private val left = 1_000_000L

    @Test
    fun stopsOneMinuteBeforeTheSixHourLimit() {
        assertEquals(left + 6 * hour - 60_000L, SyncTimeLimit.stopAt(left))
    }

    @Test
    fun keepsRunningBeforeTheStopTime() {
        assertFalse(SyncTimeLimit.shouldStop(left + 5 * hour, left, appInForeground = false))
        assertFalse(SyncTimeLimit.shouldStop(SyncTimeLimit.stopAt(left) - 1, left, appInForeground = false))
    }

    @Test
    fun stopsAtTheStopTimeInTheBackground() {
        assertTrue(SyncTimeLimit.shouldStop(SyncTimeLimit.stopAt(left), left, appInForeground = false))
        assertTrue(SyncTimeLimit.shouldStop(left + 7 * hour, left, appInForeground = false))
    }

    @Test
    fun waitsWhileTheAppIsInTheForeground() {
        assertFalse(SyncTimeLimit.shouldStop(left + 7 * hour, left, appInForeground = true))
    }
}
