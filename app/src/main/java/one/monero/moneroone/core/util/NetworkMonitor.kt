package one.monero.moneroone.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

    private var defaultNetwork: Network? = null

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Set initial state
        val activeNetwork = cm.activeNetwork
        defaultNetwork = activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        _isConnected.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("Network available")
                _isConnected.value = true
                val previous = defaultNetwork
                defaultNetwork = network
                if (previous != null && previous != network) _networkChanges.tryEmit(Unit)
            }

            override fun onLost(network: Network) {
                Timber.d("Network lost")
                _isConnected.value = false
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                _isConnected.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }
}
