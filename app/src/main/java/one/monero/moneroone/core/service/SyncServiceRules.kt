package one.monero.moneroone.core.service

/**
 * Android 15+ gives a dataSync foreground service 6 hours after the app was last in the
 * foreground. At the limit the system calls onTimeout, and the service must stop within
 * a few seconds or the app crashes (ForegroundServiceDidNotStopInTimeException). Some
 * devices never call onTimeout, so the service also stops itself a little before the limit.
 */
internal object SyncTimeLimit {
    /** The system's dataSync limit, counted from the last time the app was in the foreground. */
    const val LIMIT_MS = 6 * 60 * 60 * 1000L

    /** The service stops this long before the limit. */
    const val MARGIN_MS = 60 * 1000L

    /** Uptime at which the service stops itself. */
    fun stopAt(lastForegroundUptime: Long): Long = lastForegroundUptime + LIMIT_MS - MARGIN_MS

    /**
     * True when the service must stop now. While the app is in the foreground the system
     * limit does not run out, so the service waits.
     */
    fun shouldStop(nowUptime: Long, lastForegroundUptime: Long, appInForeground: Boolean): Boolean =
        !appInForeground && nowUptime >= stopAt(lastForegroundUptime)
}

/**
 * Foreground starts sent whose onStartCommand has not run yet. A service stopped before it
 * calls startForeground crashes the app (ForegroundServiceDidNotStartInTimeException), so a
 * stop asked for while a start is pending runs after that start instead.
 */
internal class PendingStarts {
    private var count = 0
    private var stopWhenStarted = false

    /** A foreground start was sent. It cancels an earlier stop that waits for a start. */
    @Synchronized
    fun sent() {
        count++
        stopWhenStarted = false
    }

    @Synchronized
    fun any(): Boolean = count > 0

    /** Stop the service as soon as the pending starts have run. */
    @Synchronized
    fun stopWhenStarted() {
        if (count > 0) stopWhenStarted = true
    }

    /**
     * A foreground start reached onStartCommand. True when it is the last pending start and a
     * stop waits for it: the service then stops right after its startForeground.
     */
    @Synchronized
    fun ran(): Boolean {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0 && stopWhenStarted) {
            stopWhenStarted = false
            return true
        }
        return false
    }
}
