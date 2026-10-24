package one.monero.moneroone.core.wallet

/** The state of one wallet under To on Transfer (iOS TransferDestinationState). */
sealed interface TransferDestination {
    /** Can be chosen: a send goes to this saved, checked address. */
    data class Ready(val address: String) : TransferDestination

    /** No saved primary address yet (not opened since the update), or one that fails Send's check. */
    data object NeedsOpen : TransferDestination

    /** The wallet's keys are not on this phone. */
    data object NeedsRestore : TransferDestination

    companion object {
        fun of(wallet: WalletInfo, needsRestore: Boolean, isValidAddress: (String) -> Boolean): TransferDestination {
            if (needsRestore) return NeedsRestore
            return SendNames.transferAddress(wallet, isValidAddress)?.let { Ready(it) } ?: NeedsOpen
        }
    }
}

/** A destination on Transfer: the wallet under To and the address the send goes to. */
data class TransferChoice(val wallet: WalletInfo, val address: String)

/**
 * Transfer's To (iOS TransferPartiesCard): This Wallet (the current wallet) first, then every other
 * wallet in switcher order. Transfer does not remember the last choice: each Transfer starts here.
 */
object TransferTo {

    /** With another wallet, To is a menu. With none, To is a plain row fixed on This Wallet. */
    fun hasMenu(wallets: List<WalletInfo>, currentWalletId: String?): Boolean =
        SendNames.transferTargets(wallets, currentWalletId).isNotEmpty()

    /**
     * The wallet To shows: with no other wallet, this one; else the one chosen, or null for
     * "Choose a wallet" (2 or more wallets start with no choice).
     */
    fun shown(wallets: List<WalletInfo>, currentWalletId: String?, chosenId: String?): WalletInfo? {
        val current = wallets.firstOrNull { it.id == currentWalletId } ?: return null
        if (!hasMenu(wallets, currentWalletId)) return current
        return wallets.firstOrNull { it.id == chosenId }
    }

    /**
     * Where the send goes: the wallet To shows, while it is [TransferDestination.Ready]. Null keeps
     * Continue off: nothing chosen yet, a wallet that cannot be chosen, or one removed meanwhile.
     * The address is the wallet's main (primary) address.
     */
    fun resolve(
        wallets: List<WalletInfo>,
        currentWalletId: String?,
        chosenId: String?,
        destinationOf: (WalletInfo) -> TransferDestination
    ): TransferChoice? {
        val wallet = shown(wallets, currentWalletId, chosenId) ?: return null
        val ready = destinationOf(wallet) as? TransferDestination.Ready ?: return null
        return TransferChoice(wallet, ready.address)
    }

    /** This Wallet: the destination is the wallet that sends. */
    fun isThisWallet(choice: WalletInfo?, currentWalletId: String?): Boolean =
        choice != null && choice.id == currentWalletId
}
