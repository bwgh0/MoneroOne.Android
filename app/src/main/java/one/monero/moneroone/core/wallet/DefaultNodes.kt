package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import one.monero.moneroone.core.network.isOnionNode
import kotlin.random.Random

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

    // MoneroKit (Node.getAddress) speaks TLS only on port 443; every other port
    // is cleartext HTTP. Keep this predicate in sync with that convention.
    fun isTls(uri: String): Boolean = uri.substringAfterLast(":").toIntOrNull() == 443

    // Fallback for callers without a Context. Prefer [initial] so first-run
    // traffic is not concentrated on one operator.
    const val INITIAL = "xmr-node.cakewallet.com:18081"

    /**
     * Node used before the user (or auto-select) has ever picked one, drawn once
     * per install and then persisted so it stays stable across launches.
     *
     * Drawn from every entry in [ALL], TLS (node.monero.one) and cleartext alike.
     */
    fun initial(context: Context): String =
        initial(context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE))

    internal fun initial(prefs: SharedPreferences, random: Random = Random): String {
        prefs.getString(KEY_INITIAL_NODE, null)?.let { return it }

        val picked = ALL[random.nextInt(ALL.size)].uri
        prefs.edit().putString(KEY_INITIAL_NODE, picked).apply()
        return picked
    }

    private const val KEY_INITIAL_NODE = "initial_node"
}
