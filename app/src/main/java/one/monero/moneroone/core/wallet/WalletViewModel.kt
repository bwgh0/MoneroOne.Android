package one.monero.moneroone.core.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.monerokit.Balance
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.data.DefaultNodes
import io.horizontalsystems.monerokit.data.Subaddress
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.util.UUID

data class WalletState(
    val hasWallet: Boolean = false,
    val isInitializing: Boolean = false,
    val syncState: SyncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
    val balance: Balance = Balance(0, 0),
    val transactions: List<TransactionInfo> = emptyList(),
    val receiveAddress: String = "",
    val subaddresses: List<Subaddress> = emptyList(),
    val error: String? = null
)

data class PendingSeed(
    val words: List<String>,
    val type: SeedType
)

enum class SeedType {
    LEGACY_25,      // 25-word Electrum seed
    BIP39_12,       // 12-word BIP39
    BIP39_24        // 24-word BIP39
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var moneroKit: MoneroKit? = null

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _pendingSeed = MutableStateFlow<PendingSeed?>(null)
    val pendingSeed: StateFlow<PendingSeed?> = _pendingSeed.asStateFlow()

    private val _pin = MutableStateFlow<String?>(null)

    private var walletId: String? = null

    init {
        checkExistingWallet()
    }

    private fun checkExistingWallet() {
        // Check if wallet exists in secure storage
        val prefs = context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE)
        val existingWalletId = prefs.getString("wallet_id", null)
        val hasPin = prefs.getString("pin_hash", null) != null

        if (existingWalletId != null && hasPin) {
            walletId = existingWalletId
            _walletState.update { it.copy(hasWallet = true) }
        }
    }

    fun generateNewSeed(seedType: SeedType): List<String> {
        // Generate a new seed using MoneroKit
        // For now, we'll generate a placeholder that would be replaced with actual generation
        val words = when (seedType) {
            SeedType.LEGACY_25 -> {
                // Would use actual Monero seed generation
                List(25) { "word${it + 1}" }
            }
            SeedType.BIP39_12 -> {
                List(12) { "word${it + 1}" }
            }
            SeedType.BIP39_24 -> {
                List(24) { "word${it + 1}" }
            }
        }

        _pendingSeed.value = PendingSeed(words, seedType)
        return words
    }

    fun createWallet(seed: List<String>, seedType: SeedType) {
        viewModelScope.launch {
            try {
                _walletState.update { it.copy(isInitializing = true, error = null) }

                val newWalletId = UUID.randomUUID().toString()
                val restoreHeight = MoneroKit.restoreHeightForNewWallet().toString()
                val node = DefaultNodes.CAKE.uri.split("/")[0]

                val moneroSeed = when (seedType) {
                    SeedType.LEGACY_25 -> Seed.Electrum(seed, "")
                    SeedType.BIP39_12, SeedType.BIP39_24 -> Seed.Bip39(seed, "")
                }

                val kit = MoneroKit.getInstance(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = restoreHeight,
                    walletId = newWalletId,
                    node = node,
                    trustNode = false
                )

                moneroKit = kit
                walletId = newWalletId

                // Save wallet ID
                context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("wallet_id", newWalletId)
                    .apply()

                setupKitObservers(kit)

                _walletState.update {
                    it.copy(
                        hasWallet = true,
                        isInitializing = false,
                        receiveAddress = kit.receiveAddress
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to create wallet")
                _walletState.update {
                    it.copy(
                        isInitializing = false,
                        error = e.message ?: "Failed to create wallet"
                    )
                }
            }
        }
    }

    fun restoreWallet(seed: List<String>, restoreHeight: String?) {
        viewModelScope.launch {
            try {
                _walletState.update { it.copy(isInitializing = true, error = null) }

                val newWalletId = UUID.randomUUID().toString()
                val height = restoreHeight ?: "0"
                val node = DefaultNodes.CAKE.uri.split("/")[0]

                // Determine seed type from word count
                val moneroSeed = when (seed.size) {
                    25 -> Seed.Electrum(seed, "")
                    12, 24 -> Seed.Bip39(seed, "")
                    else -> throw IllegalArgumentException("Invalid seed word count: ${seed.size}")
                }

                val kit = MoneroKit.getInstance(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = height,
                    walletId = newWalletId,
                    node = node,
                    trustNode = false
                )

                moneroKit = kit
                walletId = newWalletId

                // Save wallet ID
                context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("wallet_id", newWalletId)
                    .apply()

                setupKitObservers(kit)

                _walletState.update {
                    it.copy(
                        hasWallet = true,
                        isInitializing = false,
                        receiveAddress = kit.receiveAddress
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to restore wallet")
                _walletState.update {
                    it.copy(
                        isInitializing = false,
                        error = e.message ?: "Failed to restore wallet"
                    )
                }
            }
        }
    }

    private fun setupKitObservers(kit: MoneroKit) {
        viewModelScope.launch {
            kit.syncStateFlow.collect { syncState ->
                _walletState.update { it.copy(syncState = syncState) }
            }
        }

        viewModelScope.launch {
            kit.balanceFlow.collect { balance ->
                _walletState.update { it.copy(balance = balance) }
            }
        }

        viewModelScope.launch {
            kit.allTransactionsFlow.collect { transactions ->
                _walletState.update { it.copy(transactions = transactions) }
            }
        }
    }

    fun setPin(pin: String) {
        // Hash and store PIN securely
        val pinHash = pin.hashCode().toString() // In production, use proper hashing
        context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("pin_hash", pinHash)
            .apply()
        _pin.value = pin
        _isLocked.value = false
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE)
            .getString("pin_hash", null) ?: return false

        val enteredHash = enteredPin.hashCode().toString()
        val isValid = storedHash == enteredHash

        if (isValid) {
            _pin.value = enteredPin
            _isLocked.value = false
            startWallet()
        }

        return isValid
    }

    fun lock() {
        _isLocked.value = true
        _pin.value = null
    }

    fun startWallet() {
        viewModelScope.launch {
            try {
                moneroKit?.start()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start wallet")
                _walletState.update { it.copy(error = e.message) }
            }
        }
    }

    fun stopWallet() {
        viewModelScope.launch {
            try {
                moneroKit?.stop()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop wallet")
            }
        }
    }

    fun send(address: String, amount: Long, memo: String? = null) {
        viewModelScope.launch {
            try {
                moneroKit?.send(amount, address, memo)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send transaction")
                _walletState.update { it.copy(error = e.message) }
            }
        }
    }

    fun estimateFee(address: String, amount: Long): Long {
        return try {
            moneroKit?.estimateFee(amount, address, null) ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate fee")
            0L
        }
    }

    fun getSubaddresses(): List<Subaddress> {
        return moneroKit?.getSubaddresses() ?: emptyList()
    }

    fun formatXmr(atomicUnits: Long): String {
        val xmr = BigDecimal(atomicUnits).divide(BigDecimal(1_000_000_000_000L))
        return xmr.setScale(4, java.math.RoundingMode.DOWN).toPlainString()
    }

    fun parseXmr(xmrString: String): Long {
        return try {
            val xmr = BigDecimal(xmrString)
            xmr.multiply(BigDecimal(1_000_000_000_000L)).toLong()
        } catch (e: Exception) {
            0L
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            moneroKit?.stop()
        }
    }
}
