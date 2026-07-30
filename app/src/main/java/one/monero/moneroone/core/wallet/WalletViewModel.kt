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
import io.horizontalsystems.monerokit.CakeWalletStyleConverter
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.Seed
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.data.Subaddress
import io.horizontalsystems.monerokit.model.NetworkType
import io.horizontalsystems.monerokit.model.TransactionInfo
import io.horizontalsystems.monerokit.toElectrum
import io.horizontalsystems.monerokit.util.Helper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.CurrentPrice
import one.monero.moneroone.data.repository.PriceRepository
import one.monero.moneroone.widget.PriceWidget
import one.monero.moneroone.widget.WalletWidget
import one.monero.moneroone.widget.WidgetDataStore
import timber.log.Timber
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

class DuplicateWalletException(val existingName: String) :
    Exception("This wallet is already added as \"$existingName\"")

class InvalidSeedException :
    Exception("Invalid seed phrase. Check the words and try again.")

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val prefs: SharedPreferences =
        context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)

    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _pendingSeed = MutableStateFlow<PendingSeed?>(null)
    val pendingSeed: StateFlow<PendingSeed?> = _pendingSeed.asStateFlow()

    private val _pin = MutableStateFlow<String?>(null)

    // --- Multi-wallet state ---

    private val _wallets = MutableStateFlow<List<WalletInfo>>(emptyList())
    val wallets: StateFlow<List<WalletInfo>> = _wallets.asStateFlow()

    private val _activeWallet = MutableStateFlow<WalletInfo?>(null)
    val activeWallet: StateFlow<WalletInfo?> = _activeWallet.asStateFlow()

    /**
     * Epoch token bumped on every active-wallet change. Key the per-wallet UI
     * subtree on this so no stale per-wallet state survives a switch.
     */
    private val _walletSessionId = MutableStateFlow(0L)
    val walletSessionId: StateFlow<Long> = _walletSessionId.asStateFlow()

    /**
     * Depth counter of open add-wallet-flow screens; suppresses auto-lock
     * while > 0. A counter (not a boolean) because during navigation
     * transitions the incoming screen composes before the outgoing one
     * disposes — a boolean would be reset to false by the outgoing screen.
     */
    private val _addWalletFlowDepth = MutableStateFlow(0)
    val addWalletFlowActive: StateFlow<Boolean>
        get() = _addWalletFlowActiveView
    private val _addWalletFlowActiveView = MutableStateFlow(false)

    /**
     * Serializes every wallet lifecycle transition (switch / add / delete /
     * reset / refresh / node change / unlock-open). Two transitions
     * interleaving their kit teardown + reopen leaves the kit slot and
     * [_walletState] divergent (M1).
     */
    private val walletMutationMutex = Mutex()

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

    // Encrypted storage for seeds + per-wallet PIN hashes
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

    private val store by lazy { WalletStore(prefs) }
    private val secrets by lazy { WalletSecrets(encryptedPrefs) }

    init {
        loadSelectedCurrency()
        try {
            WalletMigration.migrateIfNeeded(prefs, secrets, store)
        } catch (e: Exception) {
            // Keystore/EncryptedSharedPreferences failures must not crash the
            // constructor into a loop; migration is idempotent and will be
            // retried next launch.
            Timber.e(e, "Wallet migration failed; continuing with existing state")
        }
        loadWalletsFromStore()
        cleanOrphanedWalletCaches()
        // Defer price fetch until a wallet exists. Avoids leaking IP to
        // monero.one before the user has generated/restored a key.
        if (_walletState.value.hasWallet) {
            fetchPrice()
        }
        checkAndApplyAutoLock()
    }

    // =========================================================================
    // Store loading / orphan sweep
    // =========================================================================

    private fun loadWalletsFromStore() {
        var list = store.wallets()

        // Stale onboarding cleanup: a wallet row without a stored seed, or a
        // store where no wallet has a PIN hash yet (crash mid-onboarding),
        // cannot be unlocked — clear it so the user can start fresh.
        // Guarded: a keystore/EncryptedSharedPreferences read failure must
        // NOT crash the constructor or be mistaken for "no seeds stored" —
        // that would wipe live rows. Skip cleanup for this launch instead.
        try {
            val withSeeds = list.filter { secrets.hasSeed(it.id) }
            if (withSeeds.size != list.size) {
                Timber.w("Clearing ${list.size - withSeeds.size} wallet row(s) without stored seed")
                list.filterNot { secrets.hasSeed(it.id) }.forEach { secrets.deleteWalletSecrets(it.id) }
                store.saveWallets(withSeeds)
                list = withSeeds
            }
            if (list.isNotEmpty() && list.none { secrets.pinHash(it.id) != null }) {
                Timber.w("Clearing wallet store: no wallet has a PIN hash (incomplete onboarding)")
                list.forEach { secrets.deleteWalletSecrets(it.id) }
                store.deleteAll()
                list = emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Secret store unreadable; skipping onboarding cleanup this launch")
        }

        _wallets.value = list
        val active = list.firstOrNull { it.id == store.activeWalletId() } ?: list.firstOrNull()
        _activeWallet.value = active
        if (active != null && active.id != store.activeWalletId()) {
            store.setActiveWalletId(active.id)
        }
        refreshHasWallet()
        active?.let { paintCachedState(it) }
    }

    private fun refreshHasWallet() {
        val list = _wallets.value
        val has = list.isNotEmpty() && list.any {
            runCatching { secrets.pinHash(it.id) }.getOrNull() != null
        }
        _walletState.update { it.copy(hasWallet = has) }
    }

    /** Paint cached balance/address so the UI is instant before unlock/sync. */
    private fun paintCachedState(info: WalletInfo) {
        _walletState.update {
            it.copy(
                balance = Balance(info.cachedBalance ?: 0L, info.cachedUnlockedBalance ?: 0L),
                receiveAddress = info.cachedPrimaryAddress ?: ""
            )
        }
    }

    /**
     * Launch sweep of orphaned wallet cache files (port of iOS
     * `cleanOrphanedWalletCaches`). Runs once at startup, after migration.
     * Bails entirely when any wallet's cache id is unresolved.
     */
    private fun cleanOrphanedWalletCaches() {
        if (!store.migrated) return
        val list = _wallets.value
        val knownIds = (list.mapNotNull { it.derivedWalletId } +
            list.mapNotNull { it.deviceWalletId }).toSet()
        val allKnown = list.all { it.derivedWalletId != null || it.deviceWalletId != null }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = Helper.getWalletRoot(context)
                val entries = root.listFiles()?.map { it.name } ?: return@launch
                val orphans = WalletCacheIds.orphanedCacheBaseNames(entries, knownIds, allKnown)
                orphans.forEach { name ->
                    Timber.i("Sweeping orphaned wallet cache: $name")
                    MoneroKit.deleteWallet(context, name)
                }
            } catch (e: Exception) {
                Timber.e(e, "Orphan cache sweep failed")
            }
        }
    }

    // =========================================================================
    // Currency / price
    // =========================================================================

    private fun loadSelectedCurrency() {
        val currencyCode = prefs.getString("selected_currency", Currency.USD.code)
        _selectedCurrency.value = Currency.entries.find { it.code == currencyCode } ?: Currency.USD
    }

    fun checkAndApplyAutoLock() {
        val backgroundTimestamp = prefs.getLong("background_timestamp", 0)
        if (backgroundTimestamp == 0L) return

        val timeoutSeconds = prefs.getInt("auto_lock_timeout", 60)
        val elapsedSeconds = (System.currentTimeMillis() - backgroundTimestamp) / 1000

        // Clear timestamp so we don't re-check
        prefs.edit().remove("background_timestamp").apply()

        // Suppress auto-lock while the add-wallet flow is open (iOS parity).
        if (_addWalletFlowDepth.value > 0) return

        val shouldLock = when {
            timeoutSeconds == 0 -> true    // IMMEDIATE
            timeoutSeconds == -1 -> false  // NEVER
            else -> elapsedSeconds >= timeoutSeconds
        }

        if (shouldLock) {
            Timber.d("Auto-lock triggered: elapsed=${elapsedSeconds}s, timeout=${timeoutSeconds}s")
            _isLocked.value = true
        }
    }

    fun setAddWalletFlowActive(active: Boolean) {
        _addWalletFlowDepth.update { (it + if (active) 1 else -1).coerceAtLeast(0) }
        val open = _addWalletFlowDepth.value > 0
        _addWalletFlowActiveView.value = open
        if (!open) {
            // Add-wallet flow fully closed (completed OR abandoned). A seed
            // generated for a wallet that was never created must not survive —
            // a stale pendingSeed would otherwise be served by getSeedPhrase()
            // and end up "backed up" as an existing wallet's seed.
            _pendingSeed.value = null
        }
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
                        // Fetch 24h chart data for widget sparkline
                        priceRepository.fetchChartData(one.monero.moneroone.ui.screens.chart.TimeRange.DAY, currency)
                            .onSuccess { points ->
                                WidgetDataStore.saveChartPoints(context, points.map { it.price })
                            }
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
            prefs.edit().putString("selected_currency", currency.code).apply()
        } else if (currency != null) {
            _selectedCurrency.value = currency
            // Also persist even if same currency (defensive)
            prefs.edit().putString("selected_currency", currency.code).apply()
        }
        fetchPrice()
    }

    // =========================================================================
    // Kit lifecycle
    // =========================================================================

    fun unlockWithBiometrics() {
        _isLocked.value = false
        viewModelScope.launch { walletMutationMutex.withLock { openActiveWallet() } }
    }

    /**
     * Open (or resume) the kit for the active wallet. The cache id used to
     * open is ALWAYS the wallet's persisted derivedWalletId — derived and
     * persisted through the single shared function if missing (lockstep).
     */
    private suspend fun openActiveWallet() {
        val active = _activeWallet.value ?: run {
            Timber.w("openActiveWallet: no active wallet")
            return
        }

        val seedData = secrets.loadSeed(active.id) ?: run {
            Timber.w("openActiveWallet: no stored seed for wallet ${active.id}")
            return
        }

        var info = active
        if (info.derivedWalletId == null) {
            // Legacy row without an id: derive + persist BEFORE opening.
            info = mergeWalletUpdate(active.id) {
                it.copy(
                    derivedWalletId = it.derivedWalletId
                        ?: WalletCacheIds.derivedWalletId(seedData.first, it.syncResetCount)
                )
            } ?: return
            Timber.i("Populated missing derivedWalletId for wallet ${info.id}")
        }
        val cacheId = info.derivedWalletId!!

        // Kit already running for this exact wallet: just start it.
        if (WalletManager.kit != null && WalletManager.currentWalletId == cacheId) {
            WalletManager.start()
            return
        }

        try {
            _walletState.update { it.copy(syncState = SyncState.Connecting(waiting = false)) }

            val (seedWords, seedType) = seedData
            val node = getSelectedNode()
            Timber.d("openActiveWallet: wallet=${info.id} cacheId=$cacheId node=$node seedType=$seedType")

            val kit = WalletManager.initialize(
                context = context,
                seed = moneroSeed(seedWords, seedType),
                restoreDateOrHeight = info.restoreHeight.toString(),
                walletId = cacheId,
                node = node,
                trustNode = false,
                networkType = NetworkType.NetworkType_Mainnet
            )

            setupKitObservers(kit)

            _walletState.update { it.copy(receiveAddress = kit.receiveAddress) }

            WalletManager.start()
            ensureUserSubaddresses(info, kit)
            persistPrimaryAddress(kit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open wallet")
            _walletState.update {
                it.copy(
                    syncState = SyncState.NotSynced(MoneroKit.SyncError.NotStarted),
                    error = e.message
                )
            }
        }
    }

    private fun moneroSeed(words: List<String>, type: SeedType): Seed = when (type) {
        SeedType.ELECTRUM_25 -> Seed.Electrum(words, "")
        SeedType.BIP39_24 -> Seed.Bip39(words, "")
    }

    /** Re-create user-created subaddresses after a cache rebuild (iOS parity). */
    private suspend fun ensureUserSubaddresses(info: WalletInfo, kit: MoneroKit) {
        val maxIdx = info.userCreatedSubaddressIndices.maxOrNull() ?: return
        withContext(Dispatchers.IO) {
            try {
                var count = kit.getSubaddresses().size
                while (count <= maxIdx) {
                    kit.createSubaddress() ?: break
                    count++
                }
            } catch (e: Exception) {
                Timber.w(e, "ensureUserSubaddresses failed")
            }
        }
    }

    /** Cache the primary (index 0) address for instant display on switch. */
    private suspend fun persistPrimaryAddress(kit: MoneroKit) {
        val primary = withContext(Dispatchers.IO) {
            runCatching { kit.getSubaddresses().firstOrNull()?.address }.getOrNull()
        } ?: return
        val active = _activeWallet.value ?: return
        mergeWalletUpdate(active.id) { it.copy(cachedPrimaryAddress = primary) }
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

    fun nextWalletName(): String = WalletStore.nextWalletName(_wallets.value.map { it.name })

    /**
     * Create a new wallet from a fresh seed. Returns true on success.
     * Rejects duplicate seeds by derived cache id.
     */
    suspend fun createWallet(
        seed: List<String>,
        seedType: SeedType,
        name: String? = null,
        emoji: String = "💰"
    ): Boolean = addWalletInternal(
        seed = seed,
        seedType = seedType,
        name = name,
        emoji = emoji,
        restoreHeight = MoneroKit.restoreHeightForNewWallet(),
        restoreDateMillis = System.currentTimeMillis()
    )

    /**
     * Restore a wallet from an existing seed. Returns true on success.
     */
    suspend fun restoreWallet(
        seed: List<String>,
        restoreHeight: String?,
        restoreDateMillis: Long? = null,
        name: String? = null,
        emoji: String = "💰"
    ): Boolean {
        // Normalize: the same seed typed with different casing/whitespace must
        // derive the same cache id (dedupe) and convert cleanly.
        val normalized = seed.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val seedType = when (normalized.size) {
            25 -> SeedType.ELECTRUM_25
            24 -> SeedType.BIP39_24
            else -> {
                _walletState.update {
                    it.copy(error = "Invalid seed word count: ${normalized.size}. Only 24 (BIP39) or 25 (Monero legacy) words supported.")
                }
                return false
            }
        }
        return addWalletInternal(
            seed = normalized,
            seedType = seedType,
            name = name,
            emoji = emoji,
            restoreHeight = restoreHeight?.toLongOrNull() ?: 0L,
            restoreDateMillis = restoreDateMillis ?: 0L
        )
    }

    private suspend fun addWalletInternal(
        seed: List<String>,
        seedType: SeedType,
        name: String?,
        emoji: String,
        restoreHeight: Long,
        restoreDateMillis: Long
    ): Boolean = walletMutationMutex.withLock {
        val previousActive = _activeWallet.value
        var persisted: WalletInfo? = null
        try {
            _walletState.update { it.copy(isInitializing = true, error = null) }

            // Duplicate-seed rejection against EVERY row's base derivation —
            // stored id equality alone misses migrated (legacy UUID id) and
            // reset (sha(seed+N)) wallets.
            val derived = WalletCacheIds.derivedWalletId(seed, 0)
            WalletCacheIds.findWalletWithSeed(seed, _wallets.value) { id ->
                secrets.loadSeed(id)?.first
            }?.let { throw DuplicateWalletException(it.name) }

            // Validate + convert the seed BEFORE anything is persisted or the
            // running kit is touched. For BIP39 this runs the eager
            // Bip39→Electrum conversion that MoneroKit.getInstance would do,
            // which throws for any invalid mnemonic — a typo'd restore must
            // fail here, not after the wallet is stored as active.
            val kitSeed = try {
                moneroSeed(seed, seedType).toElectrum()
            } catch (e: Exception) {
                Timber.w(e, "addWallet: seed validation failed")
                throw InvalidSeedException()
            }

            // Snapshot the outgoing wallet's live data before switching away.
            snapshotActiveWalletCache()

            val info = WalletInfo(
                id = UUID.randomUUID().toString(),
                name = name?.trim().takeUnless { it.isNullOrEmpty() } ?: nextWalletName(),
                emoji = emoji.ifEmpty { "💰" },
                source = WalletSource.fromSeedType(seedType),
                createdAt = System.currentTimeMillis(),
                restoreHeight = restoreHeight,
                restoreDateMillis = restoreDateMillis,
                derivedWalletId = derived
            )
            Timber.d("addWallet: id=${info.id} cacheId=$derived name=${info.name} height=$restoreHeight")

            // Secrets first, then store (a row without a seed is cleaned at launch).
            secrets.saveSeed(info.id, seed, seedType)
            // One app-wide PIN: 2nd+ wallets inherit the existing hash.
            existingPinHash()?.let { secrets.savePinHash(info.id, it) }

            store.addWallet(info)
            store.setActiveWalletId(info.id)
            _wallets.value = store.wallets()
            _activeWallet.value = info
            _walletSessionId.value += 1
            persisted = info

            cancelKitObservers()

            val kit = WalletManager.initialize(
                context = context,
                seed = kitSeed,
                restoreDateOrHeight = restoreHeight.toString(),
                walletId = derived,
                node = getSelectedNode(),
                trustNode = false,
                networkType = NetworkType.NetworkType_Mainnet
            )

            setupKitObservers(kit)
            refreshHasWallet()

            _walletState.update {
                it.copy(
                    isInitializing = false,
                    balance = Balance(0, 0),
                    transactions = emptyList(),
                    receiveAddress = kit.receiveAddress,
                    error = null
                )
            }

            _pendingSeed.value = null

            WalletManager.start()
            persistPrimaryAddress(kit)
            return true
        } catch (e: DuplicateWalletException) {
            Timber.w("addWallet rejected: duplicate of ${e.existingName}")
            _walletState.update { it.copy(isInitializing = false, error = e.message) }
            return false
        } catch (e: Exception) {
            Timber.e(e, "Failed to add wallet")
            // Roll back cleanly: remove the half-added wallet and put the
            // previous active wallet back in charge (its kit was already torn
            // down by WalletManager.initialize, so reopen it).
            persisted?.let { rollbackFailedAdd(it, previousActive) }
            _walletState.update {
                it.copy(isInitializing = false, error = e.message ?: "Failed to create wallet")
            }
            return false
        }
    }

    /**
     * Undo a wallet add that failed after persisting: wipe the new row's
     * secrets + store entry, restore the previous active wallet, and reopen
     * its kit so the session keeps running (H1: an invalid restore must not
     * brick the session or steal active).
     */
    private suspend fun rollbackFailedAdd(added: WalletInfo, previous: WalletInfo?) {
        Timber.w("Rolling back failed wallet add ${added.id} (${added.name})")
        try {
            secrets.deleteWalletSecrets(added.id)
            store.removeWallet(added.id)
            store.setActiveWalletId(previous?.id)
            val list = store.wallets()
            _wallets.value = list
            val prev = previous?.let { p -> list.firstOrNull { it.id == p.id } }
            _activeWallet.value = prev
            _walletSessionId.value += 1
            refreshHasWallet()
            if (prev != null) {
                paintCachedState(prev)
                openActiveWallet()
            }
        } catch (e: Exception) {
            Timber.e(e, "rollbackFailedAdd failed")
        }
    }

    private fun existingPinHash(): String? {
        _wallets.value.forEach { w ->
            secrets.pinHash(w.id)?.let { return it }
        }
        return null
    }

    // =========================================================================
    // Two-phase instant switch (iOS 2dc6eb7 port)
    // =========================================================================

    fun switchWallet(id: String) {
        val outgoing = _activeWallet.value
        if (outgoing?.id == id) return
        val target = _wallets.value.firstOrNull { it.id == id } ?: return
        // Any lifecycle transition in flight (another switch, delete, reset,
        // node change, add): drop the tap instead of interleaving.
        if (!walletMutationMutex.tryLock()) return

        // ---- Phase 1: synchronous repaint under the new identity ----
        val oldKit = WalletManager.kit
        val outgoingBalance = oldKit?.balance

        // Cancel old collectors BEFORE anything async — the old wallet must
        // not republish into the new wallet's UI, even for a frame.
        cancelKitObservers()
        failoverJob?.cancel()
        failoverAttempts = 0

        _activeWallet.value = target
        store.setActiveWalletId(target.id)
        _walletState.update {
            it.copy(
                balance = Balance(target.cachedBalance ?: 0L, target.cachedUnlockedBalance ?: 0L),
                receiveAddress = target.cachedPrimaryAddress ?: "",
                transactions = emptyList(),
                subaddresses = emptyList(),
                syncState = SyncState.Connecting(waiting = false),
                error = null
            )
        }
        // No in-memory seed may survive the switch.
        _pendingSeed.value = null
        _walletSessionId.value += 1

        // Widget shows the new active wallet's cached data immediately.
        WidgetDataStore.saveBalance(context, target.cachedBalance ?: 0L, target.cachedUnlockedBalance ?: 0L)
        WidgetDataStore.saveSyncStatus(context, "connecting")
        WalletWidget.updateAll(context)

        // ---- Phase 2: async persist outgoing + full teardown + reopen ----
        viewModelScope.launch {
            try {
                if (outgoing != null && oldKit != null && outgoingBalance != null) {
                    val primary = withContext(Dispatchers.IO) {
                        runCatching { oldKit.getSubaddresses().firstOrNull()?.address }.getOrNull()
                    }
                    // Merge cached fields onto the freshest row — writing the
                    // captured copy back would revert a concurrent rename etc.
                    mergeWalletUpdate(outgoing.id) {
                        it.copy(
                            cachedBalance = outgoingBalance.all,
                            cachedUnlockedBalance = outgoingBalance.unlocked,
                            cachedPrimaryAddress = primary ?: it.cachedPrimaryAddress
                        )
                    }
                }
                // KitManager allows exactly one running kit — await teardown
                // before opening the new wallet.
                WalletManager.stopAndRelease()
                openActiveWallet()
            } catch (e: Exception) {
                Timber.e(e, "switchWallet phase 2 failed")
                _walletState.update { it.copy(error = e.message) }
            } finally {
                walletMutationMutex.unlock()
            }
        }
    }

    /**
     * Read-modify-write a wallet row against the FRESH store state (M2):
     * re-reading right before the write means an update that landed while a
     * caller was suspended (rename, restore height, subaddress indices) is
     * never reverted by writing back a stale captured copy.
     * Returns the resulting row, or null when the wallet no longer exists.
     */
    private fun mergeWalletUpdate(id: String, transform: (WalletInfo) -> WalletInfo): WalletInfo? {
        val row = store.wallets().firstOrNull { it.id == id } ?: return null
        val updated = transform(row)
        if (updated != row) {
            store.updateWallet(updated)
            _wallets.value = store.wallets()
            if (_activeWallet.value?.id == id) {
                _activeWallet.value = updated
            }
        }
        return updated
    }

    /** Persist live balance/address of the active wallet into the store. */
    private suspend fun snapshotActiveWalletCache() {
        val active = _activeWallet.value ?: return
        val kit = WalletManager.kit ?: return
        if (WalletManager.currentWalletId != active.derivedWalletId) return
        val balance = kit.balance
        val primary = withContext(Dispatchers.IO) {
            runCatching { kit.getSubaddresses().firstOrNull()?.address }.getOrNull()
        }
        // Merge ONLY the cached fields onto the freshest row.
        mergeWalletUpdate(active.id) {
            it.copy(
                cachedBalance = balance.all,
                cachedUnlockedBalance = balance.unlocked,
                cachedPrimaryAddress = primary ?: it.cachedPrimaryAddress
            )
        }
    }

    // =========================================================================
    // Rename / delete
    // =========================================================================

    fun renameWallet(id: String, name: String, emoji: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mergeWalletUpdate(id) { it.copy(name = trimmed, emoji = emoji.ifEmpty { it.emoji }) }
    }

    /**
     * Delete one wallet: per-wallet secret wipe + store removal + cancel all
     * per-wallet work. The cache files are intentionally LEFT on disk for the
     * next launch sweep (iOS parity). Auto-switches to the first remaining
     * wallet, or falls back to Welcome when none remain.
     */
    fun deleteWallet(id: String) {
        val target = _wallets.value.firstOrNull { it.id == id } ?: return
        val wasActive = _activeWallet.value?.id == id
        Timber.i("deleteWallet: ${target.id} (${target.name}), active=$wasActive")

        viewModelScope.launch {
            walletMutationMutex.withLock { deleteWalletLocked(id, wasActive) }
        }
    }

    private suspend fun deleteWalletLocked(id: String, wasActive: Boolean) {
        // Cancel per-wallet work first so nothing fires against the next wallet.
        if (wasActive) {
            cancelKitObservers()
            failoverJob?.cancel()
            failoverAttempts = 0
        }

        secrets.deleteWalletSecrets(id)
        store.removeWallet(id)
        prefs.edit().remove("wallet.$id.selected_address_index").apply()
        val remaining = store.wallets()
        _wallets.value = remaining

        if (!wasActive) {
            refreshHasWallet()
            return
        }

        WalletManager.clear()

        val next = remaining.firstOrNull()
        if (next != null) {
            store.setActiveWalletId(next.id)
            _activeWallet.value = next
            _walletState.update {
                it.copy(
                    balance = Balance(next.cachedBalance ?: 0L, next.cachedUnlockedBalance ?: 0L),
                    receiveAddress = next.cachedPrimaryAddress ?: "",
                    transactions = emptyList(),
                    subaddresses = emptyList(),
                    syncState = SyncState.Connecting(waiting = false),
                    error = null
                )
            }
            _walletSessionId.value += 1
            refreshHasWallet()
            openActiveWallet()
        } else {
            store.setActiveWalletId(null)
            _activeWallet.value = null
            _pendingSeed.value = null
            _pin.value = null
            _isLocked.value = false
            _walletSessionId.value += 1
            _walletState.value = WalletState(hasWallet = false)
        }
    }

    /**
     * Full local wipe of ALL wallets (PIN brute-force protection / "Forgot
     * PIN"). Wallet caches are deleted immediately. Global app settings
     * (theme, currency, nodes, auto-lock) are PRESERVED.
     */
    fun removeWallet() {
        viewModelScope.launch {
            try {
                cancelKitObservers()
                failoverJob?.cancel()
                WalletManager.clear()

                val all = store.wallets()
                withContext(Dispatchers.IO) {
                    all.forEach { w ->
                        w.derivedWalletId?.let { MoneroKit.deleteWallet(context, it) }
                    }
                }
                all.forEach { secrets.deleteWalletSecrets(it.id) }
                prefs.edit().apply {
                    all.forEach { remove("wallet.${it.id}.selected_address_index") }
                }.apply()
                store.deleteAll()
                resetFailedAttempts()

                // Reset state
                _wallets.value = emptyList()
                _activeWallet.value = null
                _pendingSeed.value = null
                _pin.value = null
                _isLocked.value = true
                _walletSessionId.value += 1
                _walletState.value = WalletState(hasWallet = false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove wallets")
                _walletState.update { it.copy(error = e.message) }
            }
        }
    }

    // =========================================================================
    // Kit observers
    // =========================================================================

    private var kitObserverJobs: List<Job> = emptyList()

    private fun cancelKitObservers() {
        kitObserverJobs.forEach { it.cancel() }
        kitObserverJobs = emptyList()
    }

    /**
     * Observe the given kit instance DIRECTLY (not the WalletManager mirror
     * flows) so a replaced kit can never replay stale values into a new
     * wallet's UI. Collectors are cancelled on every re-init/switch/delete.
     */
    private fun setupKitObservers(kit: MoneroKit) {
        cancelKitObservers()

        kitObserverJobs = listOf(
            viewModelScope.launch {
                kit.syncStateFlow.collect { syncState ->
                    // Skip the constructor default so it can't stomp the
                    // Connecting state painted by the switch/open path.
                    if (syncState is SyncState.NotSynced && syncState.error is MoneroKit.SyncError.NotStarted) {
                        return@collect
                    }
                    when (syncState) {
                        is SyncState.NotSynced -> {
                            Timber.d("SyncState: NotSynced, error=${syncState.error}")
                            maybeFailover(syncState)
                        }
                        is SyncState.Connecting -> {
                            Timber.d("SyncState: Connecting, waiting=${syncState.waiting}")
                        }
                        is SyncState.Syncing -> {
                            Timber.d("SyncState: Syncing, progress=${syncState.progress}")
                            failoverAttempts = 0
                        }
                        is SyncState.Synced -> {
                            Timber.d("SyncState: Synced")
                            failoverAttempts = 0
                            // Balance is authoritative once synced (covers the
                            // drained-wallet edge the drop(1) below can hide).
                            _walletState.update { it.copy(balance = kit.balance) }
                            snapshotActiveWalletCache()
                        }
                    }
                    _walletState.update { it.copy(syncState = syncState) }
                    val statusKey = when (syncState) {
                        is SyncState.Synced -> "synced"
                        is SyncState.Syncing -> "syncing"
                        is SyncState.Connecting -> "connecting"
                        else -> "offline"
                    }
                    WidgetDataStore.saveSyncStatus(context, statusKey)
                    WalletWidget.updateAll(context)
                }
            },
            viewModelScope.launch {
                // drop(1): the StateFlow's initial Balance(0,0) default must not
                // overwrite the cached balance painted during a switch.
                kit.balanceFlow.drop(1).collect { balance ->
                    Timber.d("Balance updated")
                    _walletState.update { it.copy(balance = balance) }
                    persistCachedBalance(balance)
                    // Update balance widget
                    WidgetDataStore.saveBalance(context, balance.all, balance.unlocked)
                    WalletWidget.updateAll(context)
                }
            },
            viewModelScope.launch {
                kit.allTransactionsFlow.drop(1).collect { transactions ->
                    Timber.d("Transactions updated: count=${transactions.size}")
                    _walletState.update { it.copy(transactions = transactions) }
                    // Update transactions widget (store last 4 for the iOS-style large layout)
                    val txString = transactions
                        .sortedByDescending { it.timestamp }
                        .take(4)
                        .joinToString(";") { tx ->
                            val dir = if (tx.direction == TransactionInfo.Direction.Direction_In) "in" else "out"
                            "$dir|${tx.amount}|${tx.timestamp}"
                        }
                    WidgetDataStore.saveTransactions(context, txString)
                    WalletWidget.updateAll(context)
                }
            }
        )
    }

    private fun persistCachedBalance(balance: Balance) {
        val active = _activeWallet.value ?: return
        mergeWalletUpdate(active.id) {
            it.copy(cachedBalance = balance.all, cachedUnlockedBalance = balance.unlocked)
        }
    }

    // --- PBKDF2 PIN hashing ---

    private companion object {
        const val FAILOVER_RETRY_DELAY_MS = 5_000L
        const val PBKDF2_ITERATIONS = 80_000
        const val PBKDF2_KEY_LENGTH = 256
        const val PBKDF2_SALT_LENGTH = 16
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

        // PIN rate limiting thresholds
        const val RATE_LIMIT_TIER1_ATTEMPTS = 5   // 30s lockout
        const val RATE_LIMIT_TIER2_ATTEMPTS = 10  // 5min lockout
        const val RATE_LIMIT_WIPE_ATTEMPTS = 20   // wallet wipe
        const val RATE_LIMIT_TIER1_DELAY_MS = 30_000L
        const val RATE_LIMIT_TIER2_DELAY_MS = 300_000L
    }

    // --- PIN rate limiting (GLOBAL — lockout is app-wide) ---

    private val _pinLockoutSeconds = MutableStateFlow(0L)
    val pinLockoutSeconds: StateFlow<Long> = _pinLockoutSeconds.asStateFlow()

    private val _pinAttemptsRemaining = MutableStateFlow(RATE_LIMIT_WIPE_ATTEMPTS)
    val pinAttemptsRemaining: StateFlow<Int> = _pinAttemptsRemaining.asStateFlow()

    private fun getFailedAttempts(): Int {
        return encryptedPrefs.getInt("pin_fail_count", 0)
    }

    private fun getLastFailTimestamp(): Long {
        return encryptedPrefs.getLong("pin_fail_timestamp", 0L)
    }

    private fun recordFailedAttempt() {
        val count = getFailedAttempts() + 1
        encryptedPrefs.edit()
            .putInt("pin_fail_count", count)
            .putLong("pin_fail_timestamp", System.currentTimeMillis())
            .apply()
        _pinAttemptsRemaining.value = (RATE_LIMIT_WIPE_ATTEMPTS - count).coerceAtLeast(0)
    }

    private fun resetFailedAttempts() {
        encryptedPrefs.edit()
            .putInt("pin_fail_count", 0)
            .putLong("pin_fail_timestamp", 0L)
            .apply()
        _pinAttemptsRemaining.value = RATE_LIMIT_WIPE_ATTEMPTS
        _pinLockoutSeconds.value = 0L
    }

    fun shouldWipeWallet(): Boolean {
        return getFailedAttempts() >= RATE_LIMIT_WIPE_ATTEMPTS
    }

    fun getRemainingLockoutMs(): Long {
        val attempts = getFailedAttempts()
        val lastFail = getLastFailTimestamp()
        if (attempts < RATE_LIMIT_TIER1_ATTEMPTS || lastFail == 0L) return 0L

        val delayMs = when {
            attempts >= RATE_LIMIT_TIER2_ATTEMPTS -> RATE_LIMIT_TIER2_DELAY_MS
            attempts >= RATE_LIMIT_TIER1_ATTEMPTS -> RATE_LIMIT_TIER1_DELAY_MS
            else -> 0L
        }
        val elapsed = System.currentTimeMillis() - lastFail
        return (delayMs - elapsed).coerceAtLeast(0L)
    }

    fun refreshLockoutState() {
        val remaining = getRemainingLockoutMs()
        _pinLockoutSeconds.value = (remaining + 999) / 1000 // ceil to seconds
        _pinAttemptsRemaining.value = (RATE_LIMIT_WIPE_ATTEMPTS - getFailedAttempts()).coerceAtLeast(0)
    }

    private suspend fun hashPin(pin: String): String = withContext(Dispatchers.Default) {
        val salt = ByteArray(PBKDF2_SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val hash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        // Format: iterations:salt:hash
        "$PBKDF2_ITERATIONS:$saltHex:$hashHex"
    }

    private suspend fun verifyPinHash(pin: String, stored: String): Boolean = withContext(Dispatchers.Default) {
        val parts = stored.split(":")
        when (parts.size) {
            1 -> {
                // Legacy format: String.hashCode()
                pin.hashCode().toString() == stored
            }
            2 -> {
                // Old PBKDF2 format (salt:hash) — used 600k iterations
                val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val expectedHash = parts[1]
                val spec = PBEKeySpec(pin.toCharArray(), salt, 600_000, PBKDF2_KEY_LENGTH)
                val actualHash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                    .joinToString("") { "%02x".format(it) }
                MessageDigest.isEqual(
                    expectedHash.toByteArray(Charsets.UTF_8),
                    actualHash.toByteArray(Charsets.UTF_8)
                )
            }
            3 -> {
                // Current format: iterations:salt:hash
                val iterations = parts[0].toIntOrNull() ?: return@withContext false
                val salt = parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val expectedHash = parts[2]
                val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH)
                val actualHash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                    .joinToString("") { "%02x".format(it) }
                MessageDigest.isEqual(
                    expectedHash.toByteArray(Charsets.UTF_8),
                    actualHash.toByteArray(Charsets.UTF_8)
                )
            }
            else -> false
        }
    }

    private fun storedPinHash(): String? {
        _activeWallet.value?.let { active ->
            secrets.pinHash(active.id)?.let { return it }
        }
        return existingPinHash()
    }

    private suspend fun migratePinIfNeeded(pin: String, storedHash: String) {
        val needsMigration = when (storedHash.split(":").size) {
            1 -> true    // Legacy hashCode
            2 -> true    // Old 600k format without iteration count
            3 -> {
                // Current format — migrate if iteration count changed
                val iterations = storedHash.split(":")[0].toIntOrNull()
                iterations != PBKDF2_ITERATIONS
            }
            else -> false
        }
        if (needsMigration) {
            val newHash = hashPin(pin)
            _wallets.value.forEach { secrets.savePinHash(it.id, newHash) }
            Timber.d("PIN hash migrated to current format ($PBKDF2_ITERATIONS iterations)")
        }
    }

    /**
     * Set the app-wide PIN. The hash is written to EVERY wallet's per-wallet
     * entry in encrypted storage (iOS keeps per-wallet pinhash/salt).
     */
    suspend fun setPin(pin: String) {
        val pinHash = hashPin(pin)
        _wallets.value.forEach { secrets.savePinHash(it.id, pinHash) }
        _pin.value = pin
        _isLocked.value = false
        // Only mark wallet as having been fully set up after PIN is also saved
        refreshHasWallet()
        fetchPrice()
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        // Check rate limiting
        if (getRemainingLockoutMs() > 0) return false

        val storedHash = storedPinHash() ?: return false

        val isValid = verifyPinHash(enteredPin, storedHash)

        if (isValid) {
            resetFailedAttempts()
            migratePinIfNeeded(enteredPin, storedHash)
            _pin.value = enteredPin
            _isLocked.value = false
            viewModelScope.launch { walletMutationMutex.withLock { openActiveWallet() } }
        } else {
            recordFailedAttempt()
            refreshLockoutState()
        }

        return isValid
    }

    suspend fun verifyPinOnly(enteredPin: String): Boolean {
        val storedHash = storedPinHash() ?: return false

        val isValid = verifyPinHash(enteredPin, storedHash)
        if (isValid) {
            migratePinIfNeeded(enteredPin, storedHash)
        }
        return isValid
    }

    /**
     * Change the app-wide PIN. Two-pass over all wallets (port of iOS
     * `reencryptAllWallets`): first verify every wallet's secret is readable,
     * then write the new hash for every wallet — no partial state on failure.
     */
    suspend fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPinOnly(oldPin)) return false

        val allWallets = _wallets.value
        // Pass 1: every wallet's seed must be readable before anything is written.
        val readable = allWallets.all { it.isViewOnly || secrets.hasSeed(it.id) }
        if (!readable) {
            Timber.e("changePin aborted: not all wallet secrets readable")
            return false
        }

        // Pass 2: write the new hash everywhere.
        val pinHash = hashPin(newPin)
        allWallets.forEach { secrets.savePinHash(it.id, pinHash) }
        _pin.value = newPin
        return true
    }

    // =========================================================================
    // Secret export (gated on expected wallet id — iOS a31683d)
    // =========================================================================

    /**
     * Seed of the ACTIVE wallet. Pass [expectedWalletId] (captured when the
     * secret-export screen opened) so a mid-view wallet switch can never leak
     * another wallet's seed; returns null on mismatch.
     */
    fun getSeedPhrase(expectedWalletId: String? = null): List<String>? {
        val active = _activeWallet.value
        if (expectedWalletId != null) {
            // Export bound to an EXISTING wallet: the id gate applies before
            // ANY shortcut. A pending (not-yet-created) seed must never
            // satisfy an existing wallet's backup — that is exactly how the
            // wrong seed gets backed up (iOS a31683d class).
            if (active == null || active.id != expectedWalletId) {
                Timber.w("getSeedPhrase: wallet mismatch (expected $expectedWalletId, active ${active?.id})")
                return null
            }
            return secrets.loadSeed(active.id)?.first
        }
        // Ungated call: only meaningful during wallet creation, where the
        // pending seed is the wallet being created.
        _pendingSeed.value?.let { return it.words }
        return active?.let { secrets.loadSeed(it.id)?.first }
    }

    fun getSeedType(expectedWalletId: String? = null): SeedType? {
        val active = _activeWallet.value
        if (expectedWalletId != null) {
            if (active == null || active.id != expectedWalletId) return null
            return secrets.loadSeed(active.id)?.second
        }
        _pendingSeed.value?.let { return it.type }
        return active?.let { secrets.loadSeed(it.id)?.second }
    }

    fun getElectrumSeedPhrase(expectedWalletId: String? = null): List<String>? {
        val active = _activeWallet.value ?: return null
        if (expectedWalletId != null && active.id != expectedWalletId) return null
        val (words, type) = secrets.loadSeed(active.id) ?: return null
        if (type != SeedType.BIP39_24) return null
        return CakeWalletStyleConverter.getLegacySeedFromBip39(words, "")
    }

    // =========================================================================
    // Settings
    // =========================================================================

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometrics_enabled", enabled).apply()
    }

    fun setAutoLockTimeout(seconds: Int) {
        prefs.edit().putInt("auto_lock_timeout", seconds).apply()
    }

    /** Per-wallet restore height (persisted on the active WalletInfo). */
    fun setRestoreHeight(height: Long, restoreDateMillis: Long? = null) {
        val active = _activeWallet.value ?: return
        mergeWalletUpdate(active.id) {
            it.copy(
                restoreHeight = height,
                restoreDateMillis = restoreDateMillis ?: it.restoreDateMillis
            )
        }
    }

    /** Per-wallet selected receive-address index. */
    fun selectedAddressIndex(): Int {
        val active = _activeWallet.value ?: return 0
        return prefs.getInt("wallet.${active.id}.selected_address_index", 0)
    }

    fun setSelectedAddressIndex(index: Int) {
        val active = _activeWallet.value ?: return
        prefs.edit().putInt("wallet.${active.id}.selected_address_index", index).apply()
    }

    fun changeNode(resetFailover: Boolean = true) {
        if (resetFailover) failoverAttempts = 0
        viewModelScope.launch {
            walletMutationMutex.withLock {
                try {
                    _walletState.update { it.copy(syncState = SyncState.Connecting(waiting = false)) }

                    cancelKitObservers()
                    // Stop the current kit and release reference (wallet files preserved)
                    WalletManager.stopAndRelease()

                    // Reinitialize with the new node (wallet files still exist, sync resumes)
                    openActiveWallet()
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
    }

    private var failoverJob: Job? = null
    private var failoverAttempts = 0

    // Support P1: "disconnected, always — on any node". Retrying the same dead
    // node can never self-heal, so when a connection fails and auto-select is on,
    // advance to the next default node. Bounded to one full pass over the list;
    // a successful sync (or a manual node change) resets the budget.
    private fun maybeFailover(state: SyncState.NotSynced) {
        if (state.error is MoneroKit.SyncError.NotStarted) return
        if (_wallets.value.isEmpty() || _activeWallet.value == null) return
        if (!prefs.getBoolean("auto_select_node", true)) return
        if (failoverAttempts >= DefaultNodes.URIS.size) return
        if (failoverJob?.isActive == true) return

        failoverJob = viewModelScope.launch {
            delay(FAILOVER_RETRY_DELAY_MS)
            val current = getSelectedNode()
            val next = DefaultNodes.URIS[(DefaultNodes.URIS.indexOf(current) + 1).mod(DefaultNodes.URIS.size)]
            failoverAttempts++
            Timber.w("Node failover $failoverAttempts/${DefaultNodes.URIS.size}: $current -> $next (${state.error.message})")
            prefs.edit().putString("selected_node", next).apply()
            changeNode(resetFailover = false)
        }
    }

    fun refreshSync() {
        failoverAttempts = 0
        viewModelScope.launch {
            walletMutationMutex.withLock {
                try {
                    _walletState.update {
                        it.copy(syncState = SyncState.Connecting(waiting = false))
                    }
                    cancelKitObservers()
                    WalletManager.stopAndRelease()
                    openActiveWallet()
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
    }

    /**
     * Reset sync data for the ACTIVE wallet: bump syncResetCount, re-derive
     * the cache id through the single shared function and PERSIST it BEFORE
     * touching disk or reopening (iOS 6f1053f lockstep rule — getting this
     * order wrong makes the launch sweep delete a live cache).
     */
    fun resetSync() {
        failoverAttempts = 0
        viewModelScope.launch {
            walletMutationMutex.withLock { resetSyncLocked() }
        }
    }

    private suspend fun resetSyncLocked() {
        try {
            val active = _activeWallet.value ?: return
            val seedData = secrets.loadSeed(active.id) ?: run {
                Timber.w("No stored seed, cannot reset sync")
                _walletState.update { it.copy(error = "No wallet seed to reset sync") }
                return
            }

            val oldCacheId = active.derivedWalletId
            val newCount = active.syncResetCount + 1
            val newCacheId = WalletCacheIds.derivedWalletId(seedData.first, newCount)

            // Persist the new id FIRST — lockstep with what open will use.
            // Merge onto the freshest stored row (M2).
            mergeWalletUpdate(active.id) {
                it.copy(syncResetCount = newCount, derivedWalletId = newCacheId)
            } ?: return
            Timber.i("resetSync: count=$newCount cacheId $oldCacheId -> $newCacheId")

            _walletState.update {
                it.copy(
                    balance = Balance(0, 0),
                    transactions = emptyList(),
                    syncState = SyncState.Connecting(waiting = false)
                )
            }

            cancelKitObservers()
            WalletManager.stopAndRelease()

            // Delete ONLY this wallet's old cache files.
            oldCacheId?.let {
                withContext(Dispatchers.IO) { MoneroKit.deleteWallet(context, it) }
            }

            openActiveWallet()
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

    /**
     * Get the user's selected node from SharedPreferences, or fall back to default.
     */
    private fun getSelectedNode(): String {
        val savedNode = prefs.getString("selected_node", null)
        val node = savedNode ?: DefaultNodes.INITIAL
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
        viewModelScope.launch { snapshotActiveWalletCache() }
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

    fun send(address: String, amount: Long, memo: String? = null, isSweepAll: Boolean = false) {
        viewModelScope.launch {
            _sendState.value = SendState.Sending
            try {
                // Null kit (switch/reset/node-change teardown window) must be a
                // LOUD failure — a silent no-op here reported Success for a
                // payment that was never sent (M3).
                val kit = WalletManager.kit
                if (kit == null || WalletManager.currentWalletId != _activeWallet.value?.derivedWalletId) {
                    _sendState.value = SendState.Error(
                        "Wallet is not connected yet. Wait for the wallet to reconnect and try again."
                    )
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    kit.send(amount, address, memo, sweepAll = isSweepAll)
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

    fun estimateFee(address: String, amount: Long, isSweepAll: Boolean = false): Long {
        return try {
            WalletManager.kit?.estimateFee(amount, address, null, sweepAll = isSweepAll) ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate fee")
            0L
        }
    }

    fun getSubaddresses(): List<Subaddress> {
        return WalletManager.kit?.getSubaddresses() ?: emptyList()
    }

    fun createSubaddress(): String? {
        val result = WalletManager.kit?.createSubaddress() ?: return null
        // Track user-created subaddress indices per wallet so they can be
        // re-created after a sync reset rebuilds the cache.
        val active = _activeWallet.value ?: return result
        val count = try {
            WalletManager.kit?.getSubaddresses()?.size ?: 0
        } catch (e: Exception) {
            0
        }
        val index = count - 1
        if (index > 0) {
            mergeWalletUpdate(active.id) {
                if (index in it.userCreatedSubaddressIndices) it
                else it.copy(userCreatedSubaddressIndices = it.userCreatedSubaddressIndices + index)
            }
        }
        return result
    }

    fun formatXmr(atomicUnits: Long): String {
        val xmr = BigDecimal(atomicUnits).divide(BigDecimal(1_000_000_000_000L))
        val full = xmr.setScale(12, java.math.RoundingMode.DOWN).stripTrailingZeros()
        // Always show at least 4 decimal places
        return if (full.scale() < 4) full.setScale(4).toPlainString() else full.toPlainString()
    }

    fun parseXmr(xmrString: String): Long {
        return try {
            val xmr = BigDecimal(xmrString)
            xmr.multiply(BigDecimal(1_000_000_000_000L))
                .setScale(0, java.math.RoundingMode.DOWN)
                .longValueExact()
        } catch (e: ArithmeticException) {
            -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Zero sensitive in-memory state
        _pin.value = null
        _pendingSeed.value = null
        // Don't stop kit if background sync is enabled — the service keeps it alive
        if (!prefs.getBoolean("background_sync_enabled", false)) {
            WalletManager.stop()
        }
    }
}
