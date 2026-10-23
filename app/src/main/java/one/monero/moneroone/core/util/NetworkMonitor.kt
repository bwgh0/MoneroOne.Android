package one.monero.moneroone.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

object NetworkMonitor {

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits when the default network is replaced by another one (Wi-Fi <-> cellular, a new Wi-Fi). */
    val networkChanges: SharedFlow<Unit> = _networkChanges.asSharedFlow()

    private val _networkReturns = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Emits when the app can use the network again: the phone is back online, or the system stopped
     * blocking the app (Doze, the background restrictions).
     */
    val networkReturns: SharedFlow<Unit> = _networkReturns.asSharedFlow()

    /** When the app last got the network back or moved to another one ([SystemClock.elapsedRealtime]); 0 before. */
    @Volatile
    var changedAt: Long = 0L
        private set

    private lateinit var state: DefaultNetworkState<Network>

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Set initial state
        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        state = DefaultNetworkState(
            activeNetwork,
            connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            blockedStatusFollows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
        _isConnected.value = state.connected

        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("Network available")
                publish(state.onAvailable(network))
            }

            override fun onLost(network: Network) {
                Timber.d("Network lost")
                publish(state.onLost())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                publish(state.onCapabilitiesChanged(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)))
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                Timber.d("Network blocked: $blocked")
                publish(state.onBlockedStatusChanged(blocked))
            }
        })
    }

    private fun publish(change: DefaultNetworkState.Change) {
        _isConnected.value = state.connected
        if (change.switched || change.returned) changedAt = SystemClock.elapsedRealtime()
        if (change.switched) _networkChanges.tryEmit(Unit)
        if (change.returned) _networkReturns.tryEmit(Unit)
    }
}
