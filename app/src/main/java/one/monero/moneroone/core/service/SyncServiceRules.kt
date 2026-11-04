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
