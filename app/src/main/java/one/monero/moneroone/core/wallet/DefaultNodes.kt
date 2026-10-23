package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import one.monero.moneroone.core.network.isOnionNode

/**
 * Built-in remote nodes, shared by node settings UI and connection failover.
 * Order matters: failover walks this list starting after the node that failed.
 */
object DefaultNodes {

    /** [tls]: the node answers RPC over TLS on its port, as an https:// URL says on iOS. */
    data class Node(val uri: String, val name: String, val tls: Boolean = false)

    // Each TLS mark was checked with get_info over https on the node's own port (2026-10-22).
    // Cake Wallet has a CA-signed certificate; HashVault and Seth For Privacy have the
    // self-signed one monerod makes, as iOS accepts. All of them answer plain HTTP too.
    val ALL = listOf(
        Node("node.monero.one:443", "Monero One", tls = true),
        Node("xmr-node.cakewallet.com:18081", "Cake Wallet", tls = true),
        Node("node.sethforprivacy.com:18089", "Seth For Privacy", tls = true),
        Node("nodes.hashvault.pro:18081", "HashVault", tls = true),
    )

    val TOR = listOf(
        Node("5tvl5acn3sm7id4gzc4mj6n7lrwlyrhssr2r57zkxk6eugxwix4ze4qd.onion:18089", "Monero One (US)"),
        Node("zu3oyzi45x3ul24sncs4245nlpz76jzizm36tvrkfvq2r33azzjv5syd.onion:18089", "Monero One (EU)"),
    )

    val URIS = ALL.map { it.uri }

    /**
     * Auto-select's pick from measured latencies (negative = no answer): the fastest node that
     * answered. As on iOS, it never picks an onion node: only the user's own choice selects one.
     */
    fun fastest(latencies: Map<String, Long>): String? =
        latencies.entries.filter { it.value >= 0 && !isOnionNode(it.key) }.minByOrNull { it.value }?.key

    /** Turning Tor off while an onion node is selected moves the wallet here: the first default, as on iOS. */
    val TOR_OFF_FALLBACK = ALL.first().uri

    /**
     * Whether the app shows and checks [uri] as a TLS node: its lock, its form label and
     * its https latency probe. wallet2 itself opens every node connection with a TLS
     * handshake on any port (epee SSL autodetect) and reads no scheme, so this mark is
     * the app's, as the https:// scheme is on iOS. A default carries its own [Node.tls].
     * A custom node is TLS when the user entered it as https:// ([customTls], from
     * CustomNodeTls). Port 443 is TLS for every node, as before.
     */
    fun isTls(uri: String, customTls: Set<String> = emptySet()): Boolean =
        ALL.any { it.uri == uri && it.tls } || uri in customTls ||
            uri.substringAfterLast(":").toIntOrNull() == 443

    // Fallback for callers without a Context. Prefer [initial].
    const val INITIAL = "xmr-node.cakewallet.com:18081"

    /**
     * Node used before the user (or auto-select) has ever picked one, saved once
     * per install so it stays stable across launches.
     *
     * Every new install starts on node.monero.one over TLS: the first default, as
     * iOS starts on its first default. Installs that already drew a node keep it.
     */
    fun initial(context: Context): String =
        initial(context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE))

    internal fun initial(prefs: SharedPreferences): String {
        prefs.getString(KEY_INITIAL_NODE, null)?.let { return it }

        val picked = ALL.first().uri
        prefs.edit().putString(KEY_INITIAL_NODE, picked).apply()
        return picked
    }

    private const val KEY_INITIAL_NODE = "initial_node"
}
