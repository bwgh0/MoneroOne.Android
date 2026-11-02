package one.monero.moneroone.core.username

import android.content.Context
import android.content.Intent

/** Release builds have no test device proof. The debug source set holds the real one. */
object UsernameTestDevice {
    val active: Boolean get() = false

    @Suppress("UNUSED_PARAMETER")
    fun capture(context: Context, intent: Intent?) {}

    @Suppress("UNUSED_PARAMETER")
    fun proof(claimKey: String): DeviceProof? = null
}
