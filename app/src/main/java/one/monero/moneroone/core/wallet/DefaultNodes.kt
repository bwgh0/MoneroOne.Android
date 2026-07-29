package one.monero.moneroone.core.wallet

/**
 * Built-in remote nodes, shared by node settings UI and connection failover.
 * Order matters: failover walks this list starting after the node that failed.
 */
object DefaultNodes {

    data class Node(val uri: String, val name: String)

    val ALL = listOf(
        Node("node.monero.one:443", "Monero One"),
        Node("xmr-node.cakewallet.com:18081", "Cake Wallet"),
        Node("node.sethforprivacy.com:18089", "Seth For Privacy"),
        Node("nodes.hashvault.pro:18081", "HashVault"),
    )

    val URIS = ALL.map { it.uri }

    // Node used before the user (or auto-select) has ever picked one.
    const val INITIAL = "xmr-node.cakewallet.com:18081"
}
