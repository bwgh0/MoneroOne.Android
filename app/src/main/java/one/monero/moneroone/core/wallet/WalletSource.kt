package one.monero.moneroone.core.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a wallet's keys come from. Mirrors iOS `WalletSource`
 * (persisted under the legacy JSON key "seedType").
 *
 * VIEW_ONLY exists in the data model for forward compatibility with the iOS
 * view-only restore flow, even though the Android UI does not offer it yet.
 */
@Serializable
enum class WalletSource {
    @SerialName("bip39")
    BIP39,

    @SerialName("legacy")
    LEGACY,

    @SerialName("viewOnly")
    VIEW_ONLY;

    val isViewOnly: Boolean
        get() = this == VIEW_ONLY

    fun toSeedType(): SeedType? = when (this) {
        BIP39 -> SeedType.BIP39_24
        LEGACY -> SeedType.ELECTRUM_25
        VIEW_ONLY -> null
    }

    companion object {
        fun fromSeedType(type: SeedType): WalletSource = when (type) {
            SeedType.BIP39_24 -> BIP39
            SeedType.ELECTRUM_25 -> LEGACY
        }

        fun fromWordCount(count: Int): WalletSource? = when (count) {
            24 -> BIP39
            25 -> LEGACY
            else -> null
        }
    }
}
