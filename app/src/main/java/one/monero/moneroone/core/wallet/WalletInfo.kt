package one.monero.moneroone.core.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Metadata for one wallet. Mirrors iOS `WalletInfo`.
 *
 * [id] is the app-level identity (random UUID string) used to scope secrets
 * and per-wallet preferences. [derivedWalletId] is the on-disk cache id the
 * kit opens (seed-derived 32-hex for new wallets; the legacy random UUID for
 * the wallet migrated from the single-wallet era — kept verbatim so lockstep
 * and the orphan sweep still hold without renaming files on disk).
 *
 * [deviceWalletId] is reserved for future hardware wallets (iOS Trezor parity).
 */
@Serializable
data class WalletInfo(
    val id: String,
    val name: String,
    val emoji: String = "💰",
    @SerialName("seedType")
    val source: WalletSource = WalletSource.BIP39,
    val createdAt: Long = 0L,
    val restoreHeight: Long = 0L,
    val restoreDateMillis: Long = 0L,
    val syncResetCount: Int = 0,
    val userCreatedSubaddressIndices: List<Int> = emptyList(),
    val cachedPrimaryAddress: String? = null,
    /** Names survive wallet cache rebuilds and stay scoped to this wallet. */
    val addressLabels: Map<Int, String> = emptyMap(),
    /** Cached total balance in atomic units, painted instantly on switch. */
    val cachedBalance: Long? = null,
    /** Cached unlocked balance in atomic units. */
    val cachedUnlockedBalance: Long? = null,
    val derivedWalletId: String? = null,
    val deviceWalletId: String? = null,
    /** Previous wallet files kept for recovery, including transaction keys absent from the seed. */
    val retainedCacheIds: List<String> = emptyList()
) {
    val allCacheIds: Set<String>
        get() = (retainedCacheIds + listOfNotNull(derivedWalletId, deviceWalletId)).toSet()

    val isViewOnly: Boolean
        get() = source.isViewOnly

    val isKeystone: Boolean
        get() = source == WalletSource.VIEW_ONLY && deviceWalletId == "keystone"

    /** Prefix scoping this wallet's entries in encrypted storage / prefs. */
    val keyPrefix: String
        get() = "wallet.$id"
}
