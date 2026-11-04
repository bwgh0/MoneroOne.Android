package one.monero.moneroone.core.username

import one.monero.moneroone.core.locale.tr
import android.content.Context
import io.horizontalsystems.monerokit.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.wallet.SecurePrefs
import one.monero.moneroone.core.wallet.SyncRecovery
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

/**
 * A retire this phone signed for a name: the unsigned tombstone (base64url), saved before the POST. A later
 * lookup whose chain ends in the registrar's countersignature of these exact bytes proves that the retire
 * landed, also when the POST's reply was lost.
 */
@Serializable
data class UsernameRetireAttempt(
    val walletKey: String, val network: String, val handle: String, val genesisHash: String, val unsigned: String
) {
    fun isFor(binding: UsernameBinding) = walletKey == binding.walletKey && network == binding.network &&
        handle == binding.handle && genesisHash == binding.genesisHash
}

@Serializable
data class UsernameState(
    val bindings: List<UsernameBinding> = emptyList(), val pending: PendingUsernameClaim? = null,
    val nextFreeClaim: Long? = null, val deviceId: String = UUID.randomUUID().toString(),
    val retires: List<UsernameRetireAttempt> = emptyList()
) {
    fun binding(walletKey: String?, network: String) = bindings.firstOrNull {
        !walletKey.isNullOrEmpty() && it.walletKey == walletKey && it.network == network
    }
    /** The retire this phone signed last for [binding], or null. */
    fun retireAttempt(binding: UsernameBinding) = retires.lastOrNull { it.isFor(binding) }
    fun hasLocalClaimSlot(now: Long = System.currentTimeMillis()) = inviteHiddenUntil(now) == null
    /** This phone's next free claim while it is still ahead, else null. Home hides the invite until then. */
    fun inviteHiddenUntil(now: Long = System.currentTimeMillis()): Long? = nextFreeClaim?.takeIf { it > now }
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
    /**
     * [usesFreeClaim] is false for a name the wallet already held (a restore): no claim ran on this phone,
     * so its free claim date stays.
     */
    @Synchronized fun accept(binding: UsernameBinding, usesFreeClaim: Boolean = true) {
        val old = mutable.value.binding(binding.walletKey, binding.network)
        if (old != null && old.genesisHash != binding.genesisHash) throw UsernameException("verification")
        // The registry's rule, rounded up. A later saved date stays.
        val saved = mutable.value.nextFreeClaim
        val next = if (!usesFreeClaim) saved
            else UsernameClaimSlot.nextFreeClaim(binding.claimedAt).let { free -> maxOf(free, saved ?: free) }
        save(mutable.value.copy(
            bindings = mutable.value.bindings.filterNot { it.walletKey == binding.walletKey && it.network == binding.network } + binding,
            pending = null, nextFreeClaim = next
        ))
    }
    /**
     * Saves the unsigned tombstone of a retire before its POST. It replaces an earlier attempt for the same
     * name: a tombstone at the same head has the same bytes, and the registry refuses one at an older head.
     */
    @Synchronized fun saveRetire(attempt: UsernameRetireAttempt) {
        val state = mutable.value
        save(state.copy(retires = state.retires.filterNot {
            it.walletKey == attempt.walletKey && it.network == attempt.network && it.handle == attempt.handle &&
                it.genesisHash == attempt.genesisHash
        } + attempt))
    }
    /**
     * After a retire that an answer proved ([UsernameRegistry.retire]): the name, its
     * saved claim, its saved retire and its hold go. The free claim date stays, so Home hides the invite until
     * the phone can claim again.
     */
    @Synchronized fun forget(binding: UsernameBinding) {
        val state = mutable.value
        val pending = state.pending?.takeUnless {
            it.walletKey == binding.walletKey && it.network == binding.network && it.handle == binding.handle
        }
        save(state.copy(bindings = state.bindings.filterNot {
            it.walletKey == binding.walletKey && it.network == binding.network && it.genesisHash == binding.genesisHash
        }, pending = pending, retires = state.retires.filterNot { it.isFor(binding) }))
        heldNames.update { it - holdKey(binding.handle, binding.network) }
    }

    private val heldNames = MutableStateFlow<Map<String, UsernameHold>>(emptyMap())
    /**
     * The checked hold of each name the registry holds, by "network:handle". Kept in memory: the @name screen
     * checks again when it opens ([UsernameRegistry.status]).
     */
    val holds: StateFlow<Map<String, UsernameHold>> = heldNames.asStateFlow()
    /** Saves the hold of a name, or clears it (null) when the registry shows the name live again or gone. */
    fun noteHold(hold: UsernameHold?, handle: String, network: String) {
        val key = holdKey(handle, network)
        heldNames.update { if (hold == null) it - key else it + (key to hold) }
    }
    fun hold(binding: UsernameBinding): UsernameHold? = heldNames.value[holdKey(binding.handle, binding.network)]
    private fun holdKey(handle: String, network: String) = "$network:$handle"
    /** This phone's next free claim while it is still ahead, else null. Home hides the invite until then. */
    fun inviteHiddenUntil(now: Long = System.currentTimeMillis()): Long? = mutable.value.inviteHiddenUntil(now)
    /** What the @name screen shows for this wallet's name. */
    fun nameState(binding: UsernameBinding, now: Long = System.currentTimeMillis()) =
        UsernameNameState(hold(binding), inviteHiddenUntil(now))

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

/**
 * A username failure, by registry code or app code. [hold] is set for [HELD] only: a `451 name.held` whose
 * notice passed the check with the pinned registrar key. Its text is the taken text: in the claim flow the
 * name is not free; the @name screen shows the hold from [UsernameStore.nameState] instead. [TERMS_OUTDATED]
 * (`400 terms.outdated`): the registry no longer accepts this build's Username Terms version, and only an app
 * update helps; the saved claim stays, and its text stays the generic error until the update text lands.
 */
class UsernameException(val code: String, val hold: UsernameHold? = null) : Exception(when (code) {
    "storage" -> tr("Your username could not be saved securely. Unlock the app and try again.")
    "wallet_changed" -> tr("The wallet changed. Open this flow from the wallet you want to use.")
    "wallet.opening" -> tr("Your wallet is still opening. Try again in a moment.")
    "wallet.offline" -> tr("Could not reach the node. Check your connection and try again.")
    "name.unavailable", HELD -> tr("This username is unavailable.")
    "device.slot_used" -> tr("A free username isn't available on this phone.")
    "device.invalid", "device.unsupported" -> tr("This phone can't claim a username.")
    "device.unavailable" -> tr("The claim could not be completed. Please try again.")
    "geo.blocked" -> tr("Username registration isn't available in your region.")
    "not_configured" -> tr("Username registration isn't available yet.")
    "pending" -> tr("Finish checking the previous claim before choosing another username.")
    "verification" -> tr("The username response could not be verified. Please try again.")
    "nonce.invalid" -> tr("The request expired. Please claim again.")
    "handle.blocked", "handle.reserved" -> tr("This username is reserved to protect users from scams.")
    else -> tr("The username service couldn't complete the request. Please try again.")
}) {
    companion object {
        const val HELD = "name.held"
        const val TERMS_OUTDATED = "terms.outdated"
        /** A tombstone already ends the chain: an earlier retire landed. The refusal is not signed. */
        const val RETIRED = "chain.record_after_tombstone"
        /** `404 handle.not_found`: the registry has no such name. The answer is not signed. */
        const val NOT_FOUND = "handle.not_found"

        /**
         * A claim step needs the wallet file open, and it is not, though the wallet is the same: it is
         * still opening or connecting, or its node failed and the sync stopped ([SyncRecovery.reopens]).
         */
        fun walletNotReady(syncState: SyncState) =
            UsernameException(if (SyncRecovery.reopens(syncState)) "wallet.offline" else "wallet.opening")
    }
}
