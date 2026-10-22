package one.monero.moneroone.core.username

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.wallet.SecurePrefs
import java.util.Calendar
import java.util.UUID

@Serializable
data class UsernameBinding(
    val walletKey: String, val network: String, val handle: String, val displayHandle: String,
    val subaddress: String, val genesisHash: String, val record: String, val claimedAt: Long
) {
    val shortName get() = "@$displayHandle"
    val externalAddress get() = "$handle@monero.one"
    val shareUrl get() = "https://monero.one/u/$handle"
}

@Serializable
data class PendingUsernameClaim(
    val walletKey: String, val network: String, val handle: String, val displayHandle: String,
    val subaddress: String, val nonce: String, val unsignedRecord: String, val recordHash: String,
    val ownerSignature: String, val registrarKey: String, val rules: String, val rulesHash: String
)

@Serializable
data class UsernameState(
    val bindings: List<UsernameBinding> = emptyList(), val pending: PendingUsernameClaim? = null,
    val nextFreeClaim: Long? = null, val deviceId: String = UUID.randomUUID().toString()
) {
    fun binding(walletKey: String?, network: String) = bindings.firstOrNull {
        !walletKey.isNullOrEmpty() && it.walletKey == walletKey && it.network == network
    }
    fun hasLocalClaimSlot(now: Long = System.currentTimeMillis()) = nextFreeClaim == null || nextFreeClaim <= now
}

interface UsernamePersistence {
    fun read(): UsernameState
    fun write(state: UsernameState)
}

/** One atomic encrypted write precedes the claim POST, so an ambiguous reply is recoverable. */
class UsernameStore(private val persistence: UsernamePersistence) {
    private val mutable = MutableStateFlow(UsernameState())
    val state = mutable.asStateFlow()
    var storageError = false
        private set
    internal val claimMutex = Mutex()
    init { reload() }
    @Synchronized fun reload() {
        try { mutable.value = persistence.read(); storageError = false }
        catch (_: Exception) { storageError = true; mutable.value = UsernameState() }
    }
    @Synchronized fun savePending(pending: PendingUsernameClaim?) = save(mutable.value.copy(pending = pending))
    /** A deleted wallet takes its pending claim along. [walletKey] null: every wallet was removed. */
    @Synchronized fun clearPending(walletKey: String?) {
        val pending = mutable.value.pending ?: return
        if (walletKey == null || pending.walletKey == walletKey) save(mutable.value.copy(pending = null))
    }
    @Synchronized fun accept(binding: UsernameBinding) {
        val old = mutable.value.binding(binding.walletKey, binding.network)
        if (old != null && old.genesisHash != binding.genesisHash) throw UsernameException("verification")
        val next = Calendar.getInstance().apply { timeInMillis = binding.claimedAt; add(Calendar.MONTH, 12) }.timeInMillis
        save(mutable.value.copy(
            bindings = mutable.value.bindings.filterNot { it.walletKey == binding.walletKey && it.network == binding.network } + binding,
            pending = null, nextFreeClaim = next
        ))
    }
    private fun save(state: UsernameState) {
        if (storageError) throw UsernameException("storage")
        try { persistence.write(state) } catch (_: Exception) { throw UsernameException("storage") }
        mutable.value = state
    }
    companion object {
        @Volatile private var instance: UsernameStore? = null
        fun get(context: Context): UsernameStore = instance ?: synchronized(this) {
            instance ?: UsernameStore(object : UsernamePersistence {
                // Deferred opening lets a locked/broken Keystore fail closed rather than crash Home.
                private val prefs get() = SecurePrefs.open(context.applicationContext)
                private val key = if (one.monero.moneroone.BuildConfig.DEBUG) "usernames.development.v1" else "usernames.production.v1"
                override fun read() = prefs.getString(key, null)?.let { Json.decodeFromString<UsernameState>(it) } ?: UsernameState()
                override fun write(state: UsernameState) {
                    if (!prefs.edit().putString(key, Json.encodeToString(state)).commit()) throw UsernameException("storage")
                }
            }).also { instance = it }
        }
    }
}

class UsernameException(val code: String) : Exception(when (code) {
    "storage" -> "Your username could not be saved securely. Unlock the app and try again."
    "wallet_changed" -> "The wallet changed. Open this flow from the wallet you want to use."
    "name.unavailable" -> "This username is unavailable."
    "device.slot_used" -> "A free username isn't available on this phone."
    "pending" -> "Finish checking the previous claim before choosing another username."
    "verification" -> "The username response could not be verified. Please try again."
    "nonce.invalid" -> "The request expired. Please claim again."
    "handle.blocked", "handle.reserved" -> "This username is reserved to protect users from scams."
    else -> "The username service couldn't complete the request. Please try again."
})
