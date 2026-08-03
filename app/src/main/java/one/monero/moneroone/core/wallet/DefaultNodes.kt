package one.monero.moneroone.core.wallet

import android.content.Context
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

    val URIS = ALL.map { it.uri }

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
     * Not restricted to [isTls] entries on purpose: node.monero.one is currently
     * the only TLS default and its certificate is expired, so preferring TLS here
     * would start every install on a node that cannot connect. Restore the TLS
     * filter once that certificate is renewed.
     */
    fun initial(context: Context): String {
        val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
        prefs.getString(KEY_INITIAL_NODE, null)?.let { return it }

        val candidates = ALL.filterNot { it.uri in FIRST_RUN_EXCLUDED }.ifEmpty { ALL }
        val picked = candidates[Random.nextInt(candidates.size)].uri
        prefs.edit().putString(KEY_INITIAL_NODE, picked).apply()
        return picked
    }

    // node.monero.one's certificate expired 2026-05-26; until it is renewed a
    // first-run install pointed there cannot sync. Still selectable manually.
    private val FIRST_RUN_EXCLUDED = setOf("node.monero.one:443")

    private const val KEY_INITIAL_NODE = "initial_node"
}
