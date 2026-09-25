package one.monero.moneroone.core.wallet

import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.data.Subaddress

// TEMPORARY stand-ins for the kit API that fix/kit-subaddress-safety adds. Kit members shadow them;
// delete this file when that kit lands.

internal val MoneroKit.isWalletOpen: Boolean
    get() = getSubaddress(0, 0) != null

internal fun MoneroKit.seedPrimaryAddress(): String? = null

internal fun MoneroKit.openWalletPrimaryAddress(): String? = getSubaddress(0, 0)?.address

internal fun MoneroKit.addSubaddress(): Subaddress? {
    val address = createSubaddress() ?: return null
    return getSubaddresses().lastOrNull { it.address == address }
}
