package one.monero.moneroone.core.wallet

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.monerokit.Balance
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.data.Subaddress
import io.horizontalsystems.monerokit.model.NetworkType
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.CurrentPrice
import one.monero.moneroone.data.repository.PriceRepository
import one.monero.moneroone.widget.PriceWidget
import one.monero.moneroone.widget.WalletWidget
import one.monero.moneroone.widget.WidgetDataStore
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
    ELECTRUM_25,    // 25-word Monero legacy
    BIP39_24        // 24-word BIP39 (Standard)
}

sealed class SendState {
    object Idle : SendState()
    object Sending : SendState()
    data class Success(val txHash: String) : SendState()
    data class Error(val message: String) : SendState()
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _pendingSeed = MutableStateFlow<PendingSeed?>(null)
    val pendingSeed: StateFlow<PendingSeed?> = _pendingSeed.asStateFlow()

    private val _pin = MutableStateFlow<String?>(null)

    private var walletId: String? = null

    // Price data
    private val priceRepository = PriceRepository()
    private val _currentPrice = MutableStateFlow<CurrentPrice?>(null)
    val currentPrice: StateFlow<CurrentPrice?> = _currentPrice.asStateFlow()
    private var priceFetchJob: Job? = null
    private var fetchingCurrency: Currency? = null

    // Selected currency (single source of truth for WalletScreen)
    private val _selectedCurrency = MutableStateFlow(Currency.USD)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()

    // Send state tracking
    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    // Encrypted storage for seed
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_wallet_data",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        loadSelectedCurrency()
        checkExistingWallet()
        fetchPrice()
        checkAutoLock()
    }

    private fun loadSelectedCurrency() {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val currencyCode = prefs.getString("selected_currency", Currency.USD.code)
        _selectedCurrency.value = Currency.entries.find { it.code == currencyCode } ?: Currency.USD
    }

    private fun checkAutoLock() {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val shouldLock = prefs.getBoolean("should_lock", false)
        if (shouldLock) {
            Timber.d("Auto-lock flag detected, locking wallet")
            prefs.edit().remove("should_lock").apply()
            _isLocked.value = true
        }
    }

    fun checkAndApplyAutoLock() {
        checkAutoLock()
    }

    private fun fetchPrice() {
        val currency = _selectedCurrency.value

        // If already fetching for this currency, don't restart
        if (fetchingCurrency == currency && priceFetchJob?.isActive == true) {
            return
        }

        // Cancel only if fetching for a DIFFERENT currency (prevents stale data)
        priceFetchJob?.cancel()
        fetchingCurrency = currency

        priceFetchJob = viewModelScope.launch {
            priceRepository.fetchCurrentPrice(currency)
                .onSuccess { result ->
                    // Only update if this is still the selected currency
                    if (_selectedCurrency.value == currency) {
                        _currentPrice.value = result
                        Timber.d("Price updated for $currency: ${result.price}")
                        // Update widget data
                        WidgetDataStore.savePrice(
                            context,
                            result.price,
                            result.change24h,
                            currency.code,
                            currency.symbol
                        )
                        PriceWidget.updateAll(context)
                    }
                    fetchingCurrency = null
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to fetch price for $currency")
                    // Retry once after a short delay (handles rate limiting from CurrencyScreen)
                    kotlinx.coroutines.delay(1000)
                    priceRepository.fetchCurrentPrice(currency)
                        .onSuccess { result ->
                            if (_selectedCurrency.value == currency) {
                                _currentPrice.value = result
                            }
                        }
                    fetchingCurrency = null
                }
        }
    }

    fun refreshPrice(currency: Currency? = null) {
        if (currency != null && currency != _selectedCurrency.value) {
            // CRITICAL: Switching to different currency - clear stale price FIRST
            // This prevents showing old price with new symbol
            Timber.d("refreshPrice: switching from ${_selectedCurrency.value} to $currency, clearing stale price")
            _currentPrice.value = null
            _selectedCurrency.value = currency
            // Persist to SharedPreferences so selection survives app restart
            context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                .edit()
                .putString("selected_currency", currency.code)
                .apply()
        } else if (currency != null) {
            _selectedCurrency.value = currency
            // Also persist even if same currency (defensive)
            context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                .edit()
                .putString("selected_currency", currency.code)
                .apply()
        }
        fetchPrice()
    }

    private fun checkExistingWallet() {
        // Check if wallet exists in secure storage
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val existingWalletId = prefs.getString("wallet_id", null)
        val hasPin = prefs.getString("pin_hash", null) != null
        val hasEncryptedSeed = encryptedPrefs.getString("seed_words", null) != null

        if (existingWalletId != null && hasPin && hasEncryptedSeed) {
            walletId = existingWalletId
            _walletState.update { it.copy(hasWallet = true) }
        } else if (existingWalletId != null && hasPin && !hasEncryptedSeed) {
            // Stale state: wallet_id and PIN exist but no encrypted seed
            // This can happen if app was updated or seed was never stored
            // Clear the stale state to allow user to restore wallet
            Timber.w("Clearing stale wallet state: wallet_id and PIN exist but no encrypted seed found")
            prefs.edit()
                .remove("wallet_id")
                .remove("pin_hash")
                .apply()
        } else if (existingWalletId != null && !hasPin) {
            // Stale state: wallet_id exists but PIN was never set (incomplete onboarding)
            // Clear the wallet_id to allow fresh onboarding
            Timber.w("Clearing stale wallet state: wallet_id exists but no PIN was set")
            prefs.edit().remove("wallet_id").apply()
        }
    }

    private fun storeSeedEncrypted(seed: List<String>, seedType: SeedType) {
        encryptedPrefs.edit()
            .putString("seed_words", seed.joinToString(" "))
            .putString("seed_type", seedType.name)
            .apply()
    }

    private fun loadSeedEncrypted(): Pair<List<String>, SeedType>? {
        val seedWords = encryptedPrefs.getString("seed_words", null) ?: return null
        val seedTypeName = encryptedPrefs.getString("seed_type", null) ?: return null

        return try {
            val words = seedWords.split(" ")
            val type = SeedType.valueOf(seedTypeName)
            Pair(words, type)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load encrypted seed")
            null
        }
    }

    fun unlockWithBiometrics() {
        _isLocked.value = false
        initializeWalletIfNeeded()
    }

    private fun initializeWalletIfNeeded() {
        Timber.d("initializeWalletIfNeeded: kit=${WalletManager.kit != null}, walletId=$walletId")

        // If kit is already initialized, just start it
        if (WalletManager.kit != null) {
            Timber.d("MoneroKit already initialized, starting wallet")
            startWallet()
            return
        }

        // Need to reinitialize the wallet from encrypted seed
        val savedWalletId = walletId ?: run {
            Timber.w("No walletId found, cannot reinitialize wallet")
            return
        }
        val seedData = loadSeedEncrypted() ?: run {
            Timber.w("No encrypted seed found, cannot reinitialize wallet")
            return
        }

        viewModelScope.launch {
            try {
                _walletState.update { it.copy(syncState = SyncState.Connecting(waiting = false)) }

                val (seedWords, seedType) = seedData
                val node = getSelectedNode()
                val networkType = NetworkType.NetworkType_Mainnet
                Timber.d("initializeWalletIfNeeded: Connecting to node=$node, seedType=$seedType, networkType=$networkType")

                val moneroSeed = when (seedType) {
                    SeedType.ELECTRUM_25 -> Seed.Electrum(seedWords, "")
                    SeedType.BIP39_24 -> Seed.Bip39(seedWords, "")
                }

                val kit = WalletManager.initialize(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = "0", // Wallet files exist, height from files
                    walletId = savedWalletId,
                    node = node,
                    trustNode = false,
                    networkType = networkType
                )

                setupKitObservers()

                _walletState.update {
                    it.copy(receiveAddress = kit.receiveAddress)
                }

                Timber.d("initializeWalletIfNeeded: Starting kit, receiveAddress=${kit.receiveAddress}")
                WalletManager.start()

            } catch (e: Exception) {
                Timber.e(e, "Failed to reinitialize wallet")
                _walletState.update {
                    it.copy(
                        syncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
                        error = e.message
                    )
                }
            }
        }
    }

    fun generateNewSeed(seedType: SeedType): List<String> {
        val mnemonic = Mnemonic()

        val words = when (seedType) {
            SeedType.ELECTRUM_25 -> {
                // Electrum 25-word seeds are generated by the Monero wallet itself,
                // not by BIP39 mnemonic. Only BIP39_24 is used for new wallet creation.
                Timber.w("Electrum 25-word generation not supported, falling back to BIP39-24")
                mnemonic.generate(Mnemonic.EntropyStrength.VeryHigh) // 24 words
            }
            SeedType.BIP39_24 -> {
                mnemonic.generate(Mnemonic.EntropyStrength.VeryHigh) // 24 words
            }
        }

        Timber.d("Generated ${words.size}-word seed for type $seedType")
        _pendingSeed.value = PendingSeed(words, seedType)
        return words
    }

    fun createWallet(seed: List<String>, seedType: SeedType) {
        viewModelScope.launch {
            try {
                _walletState.update { it.copy(isInitializing = true, error = null) }

                val newWalletId = UUID.randomUUID().toString()
                val restoreHeight = MoneroKit.restoreHeightForNewWallet().toString()
                val node = getSelectedNode()
                val networkType = NetworkType.NetworkType_Mainnet
                Timber.d("createWallet: walletId=$newWalletId, restoreHeight=$restoreHeight, node=$node, seedType=$seedType, networkType=$networkType")

                val moneroSeed = Seed.Bip39(seed, "")

                val kit = WalletManager.initialize(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = restoreHeight,
                    walletId = newWalletId,
                    node = node,
                    trustNode = false,
                    networkType = networkType
                )

                walletId = newWalletId

                // Save wallet ID and restore height (both keys)
                val restoreHeightLong = restoreHeight.toLongOrNull() ?: 0L
                context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                    .edit()
                    .putString("wallet_id", newWalletId)
                    .putString("restore_height_str", restoreHeight)
                    .putLong("restore_height", restoreHeightLong)
                    .putLong("restore_date_millis", System.currentTimeMillis())
                    .apply()

                // Store seed encrypted for wallet restoration on app restart
                storeSeedEncrypted(seed, SeedType.BIP39_24)

                setupKitObservers()

                _walletState.update {
                    it.copy(
                        isInitializing = false,
                        receiveAddress = kit.receiveAddress
                    )
                }

                Timber.d("createWallet: Wallet created successfully, receiveAddress=${kit.receiveAddress}")

                WalletManager.start()

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

    fun restoreWallet(seed: List<String>, restoreHeight: String?, restoreDateMillis: Long? = null) {
        viewModelScope.launch {
            try {
                _walletState.update { it.copy(isInitializing = true, error = null) }

                val newWalletId = UUID.randomUUID().toString()
                val height = restoreHeight ?: "0"
                val node = getSelectedNode()
                val networkType = NetworkType.NetworkType_Mainnet
                Timber.d("restoreWallet: walletId=$newWalletId, restoreHeight=$height, node=$node, seedWordCount=${seed.size}, networkType=$networkType")

                val moneroSeed = when (seed.size) {
                    25 -> Seed.Electrum(seed, "")
                    24 -> Seed.Bip39(seed, "")
                    else -> throw IllegalArgumentException("Invalid seed word count: ${seed.size}. Only 24 (BIP39) or 25 (Monero legacy) words supported.")
                }

                val kit = WalletManager.initialize(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = height,
                    walletId = newWalletId,
                    node = node,
                    trustNode = false,
                    networkType = networkType
                )

                walletId = newWalletId

                // Save wallet ID and restore height (both keys)
                val heightLong = height.toLongOrNull() ?: 0L
                context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                    .edit()
                    .putString("wallet_id", newWalletId)
                    .putString("restore_height_str", height)
                    .putLong("restore_height", heightLong)
                    .putLong("restore_date_millis", restoreDateMillis ?: 0L)
                    .apply()

                val seedType = when (seed.size) {
                    25 -> SeedType.ELECTRUM_25
                    else -> SeedType.BIP39_24
                }
                storeSeedEncrypted(seed, seedType)

                setupKitObservers()

                _walletState.update {
                    it.copy(
                        isInitializing = false,
                        receiveAddress = kit.receiveAddress
                    )
                }

                Timber.d("restoreWallet: Wallet restored successfully, receiveAddress=${kit.receiveAddress}")

                WalletManager.start()

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

    private var kitObserverJobs: List<Job> = emptyList()

    private fun setupKitObservers() {
        // Cancel any existing collectors to prevent stacking
        kitObserverJobs.forEach { it.cancel() }

        kitObserverJobs = listOf(
            viewModelScope.launch {
                WalletManager.syncStateFlow.collect { syncState ->
                    when (syncState) {
                        is SyncState.NotSynced -> {
                            Timber.d("SyncState: NotSynced, error=${syncState.error}")
                        }
                        is SyncState.Connecting -> {
                            Timber.d("SyncState: Connecting, waiting=${syncState.waiting}")
                        }
                        is SyncState.Syncing -> {
                            Timber.d("SyncState: Syncing, progress=${syncState.progress}")
                        }
                        is SyncState.Synced -> {
                            Timber.d("SyncState: Synced")
                        }
                    }
                    _walletState.update { it.copy(syncState = syncState) }
                }
            },
            viewModelScope.launch {
                WalletManager.balanceFlow.collect { balance ->
                    Timber.d("Balance updated: all=${balance.all}, unlocked=${balance.unlocked}")
                    _walletState.update { it.copy(balance = balance) }
                    // Update balance widget
                    WidgetDataStore.saveBalance(context, balance.all, balance.unlocked)
                    WalletWidget.updateAll(context)
                }
            },
            viewModelScope.launch {
                WalletManager.transactionsFlow.collect { transactions ->
                    Timber.d("Transactions updated: count=${transactions.size}")
                    _walletState.update { it.copy(transactions = transactions) }
                    // Update transactions widget (store last 3)
                    val txString = transactions
                        .sortedByDescending { it.timestamp }
                        .take(3)
                        .joinToString(";") { tx ->
                            val dir = if (tx.direction == io.horizontalsystems.monerokit.model.TransactionInfo.Direction.Direction_In) "in" else "out"
                            "$dir|${tx.amount}|${tx.timestamp}"
                        }
                    WidgetDataStore.saveTransactions(context, txString)
                    WalletWidget.updateAll(context)
                }
            }
        )
    }

    fun setPin(pin: String) {
        // Hash and store PIN securely
        val pinHash = pin.hashCode().toString() // In production, use proper hashing
        context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .edit()
            .putString("pin_hash", pinHash)
            .apply()
        _pin.value = pin
        _isLocked.value = false
        // Only mark wallet as having been fully set up after PIN is also saved
        _walletState.update { it.copy(hasWallet = true) }
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .getString("pin_hash", null) ?: return false

        val enteredHash = enteredPin.hashCode().toString()
        val isValid = storedHash == enteredHash

        if (isValid) {
            _pin.value = enteredPin
            _isLocked.value = false
            initializeWalletIfNeeded()
        }

        return isValid
    }

    fun verifyPinOnly(enteredPin: String): Boolean {
        val storedHash = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .getString("pin_hash", null) ?: return false

        val enteredHash = enteredPin.hashCode().toString()
        return storedHash == enteredHash
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPinOnly(oldPin)) return false

        val pinHash = newPin.hashCode().toString()
        context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .edit()
            .putString("pin_hash", pinHash)
            .apply()
        _pin.value = newPin
        return true
    }

    fun getSeedPhrase(): List<String>? {
        // Return the pending seed if available (during wallet creation)
        _pendingSeed.value?.let { return it.words }

        // Otherwise, retrieve from encrypted storage
        return loadSeedEncrypted()?.first
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("biometrics_enabled", enabled)
            .apply()
    }

    fun setAutoLockTimeout(seconds: Int) {
        context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .edit()
            .putInt("auto_lock_timeout", seconds)
            .apply()
    }

    fun setRestoreHeight(height: Long, restoreDateMillis: Long? = null) {
        val editor = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            .edit()
            .putLong("restore_height", height)
            .putString("restore_height_str", height.toString())
        if (restoreDateMillis != null) {
            editor.putLong("restore_date_millis", restoreDateMillis)
        }
        editor.apply()
    }

    fun changeNode() {
        viewModelScope.launch {
            try {
                _walletState.update { it.copy(syncState = SyncState.Connecting(waiting = false)) }

                // Stop the current kit and release reference (wallet files preserved)
                WalletManager.stopAndRelease()

                // Reinitialize with the new node (wallet files still exist, sync resumes)
                initializeWalletIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "Failed to change node")
                _walletState.update {
                    it.copy(
                        syncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
                        error = "Failed to change node: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshSync() {
        viewModelScope.launch {
            try {
                _walletState.update {
                    it.copy(syncState = SyncState.Connecting(waiting = false))
                }
                WalletManager.stopAndRelease()
                initializeWalletIfNeeded()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh sync")
                _walletState.update {
                    it.copy(
                        syncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
                        error = "Failed to refresh: ${e.message}"
                    )
                }
            }
        }
    }

    fun resetSync() {
        viewModelScope.launch {
            try {
                val seedData = loadSeedEncrypted() ?: run {
                    Timber.w("No encrypted seed found, cannot reset sync")
                    _walletState.update { it.copy(error = "No wallet seed to reset sync") }
                    return@launch
                }
                val savedWalletId = walletId ?: return@launch

                _walletState.update {
                    it.copy(
                        balance = Balance(0, 0),
                        transactions = emptyList(),
                        syncState = SyncState.Connecting(waiting = false)
                    )
                }

                // Stop current kit
                WalletManager.kit?.stop()

                // Delete cached wallet files to force full resync
                MoneroKit.deleteWallet(context, savedWalletId)

                val (seedWords, seedType) = seedData
                val node = getSelectedNode()
                val networkType = NetworkType.NetworkType_Mainnet

                Timber.d("resetSync: Reinitializing wallet from scratch, node=$node")

                val moneroSeed = when (seedType) {
                    SeedType.ELECTRUM_25 -> Seed.Electrum(seedWords, "")
                    SeedType.BIP39_24 -> Seed.Bip39(seedWords, "")
                }

                val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                val syncSettingsHeight = prefs.getLong("restore_height", 0L)
                val creationHeight = prefs.getString("restore_height_str", "0") ?: "0"
                // Prefer SyncSettings value (user's explicit override from date picker),
                // fall back to creation-time height
                val restoreHeight = if (syncSettingsHeight > 0L) {
                    syncSettingsHeight.toString()
                } else {
                    creationHeight
                }

                val kit = WalletManager.initialize(
                    context = context,
                    seed = moneroSeed,
                    restoreDateOrHeight = restoreHeight,
                    walletId = savedWalletId,
                    node = node,
                    trustNode = false,
                    networkType = networkType
                )

                setupKitObservers()

                _walletState.update {
                    it.copy(receiveAddress = kit.receiveAddress)
                }

                WalletManager.start()

                Timber.d("resetSync: Wallet resync started successfully")

            } catch (e: Exception) {
                Timber.e(e, "Failed to reset sync")
                _walletState.update {
                    it.copy(
                        syncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
                        error = "Failed to reset sync: ${e.message}"
                    )
                }
            }
        }
    }

    fun removeWallet() {
        viewModelScope.launch {
            try {
                // Stop and release the kit via WalletManager
                WalletManager.clear()

                // Clear all wallet data from SharedPreferences
                context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()

                // Reset state
                walletId = null
                _pendingSeed.value = null
                _pin.value = null
                _isLocked.value = true
                _walletState.value = WalletState(hasWallet = false)

            } catch (e: Exception) {
                Timber.e(e, "Failed to remove wallet")
                _walletState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Get the user's selected node from SharedPreferences, or fall back to default.
     */
    private fun getSelectedNode(): String {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        val savedNode = prefs.getString("selected_node", null)
        val node = savedNode ?: "xmr-node.cakewallet.com:18081"
        Timber.d("getSelectedNode: savedNode=$savedNode, using node=$node")
        return node
    }

    /**
     * Get debug info from MoneroKit for diagnostics.
     */
    fun getDebugInfo(): String {
        return try {
            val statusMap = WalletManager.kit?.statusInfo()
            if (statusMap != null) {
                statusMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            } else {
                "MoneroKit not initialized"
            }
        } catch (e: Exception) {
            "Error getting debug info: ${e.message}"
        }
    }

    fun lock() {
        _isLocked.value = true
        _pin.value = null
    }

    fun startWallet() {
        viewModelScope.launch {
            try {
                WalletManager.start()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start wallet")
                _walletState.update { it.copy(error = e.message) }
            }
        }
    }

    fun stopWallet() {
        viewModelScope.launch {
            try {
                WalletManager.stop()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop wallet")
            }
        }
    }

    fun send(address: String, amount: Long, memo: String? = null) {
        viewModelScope.launch {
            _sendState.value = SendState.Sending
            try {
                withContext(Dispatchers.IO) {
                    WalletManager.kit?.send(amount, address, memo)
                }
                // MoneroKit.send() doesn't return txHash, we'll show success without it
                // The transaction will appear in the transactions list after sync
                _sendState.value = SendState.Success("")
            } catch (e: Exception) {
                Timber.e(e, "Failed to send transaction")
                _sendState.value = SendState.Error(e.message ?: "Transaction failed")
            }
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    fun estimateFee(address: String, amount: Long): Long {
        return try {
            WalletManager.kit?.estimateFee(amount, address, null) ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate fee")
            0L
        }
    }

    fun getSubaddresses(): List<Subaddress> {
        return WalletManager.kit?.getSubaddresses() ?: emptyList()
    }

    fun createSubaddress(): String? {
        return WalletManager.kit?.createSubaddress()
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
        // Don't stop kit if background sync is enabled — the service keeps it alive
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("background_sync_enabled", false)) {
            WalletManager.stop()
        }
    }
}
