package one.monero.moneroone.core.node

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** RPC login for a node that runs monerod with `--rpc-login`. */
data class NodeCredentials(val username: String, val password: String) {
    // A data class toString would put the password into any log or crash
    // report that prints the holder; never let that happen.
    override fun toString(): String = "NodeCredentials(username=$username, password=***)"
}

/**
 * RPC credentials for custom nodes, kept in the encrypted prefs (the same file
 * as seeds and PIN hashes) under `node.<host:port>.*`. The plain "monero_wallet"
 * prefs only ever hold bare host:port strings; the `user:pass@host:port` form
 * MoneroKit's node parser expects is composed at kit start and goes nowhere else.
 * Mirrors iOS NodeCredentialStore (Keychain), but per node instead of
 * selected-node-only.
 */
class NodeCredentialStore(private val secure: SharedPreferences) {

    fun load(uri: String): NodeCredentials? {
        val username = secure.getString(usernameKey(uri), null)?.takeIf { it.isNotEmpty() } ?: return null
        val password = secure.getString(passwordKey(uri), null) ?: return null
        return NodeCredentials(username, password)
    }

    fun has(uri: String): Boolean = load(uri) != null

    /** Null credentials remove any stored pair for the node. */
    fun save(uri: String, credentials: NodeCredentials?) {
        if (credentials == null) {
            remove(uri)
            return
        }
        secure.edit()
            .putString(usernameKey(uri), credentials.username)
            .putString(passwordKey(uri), credentials.password)
            .apply()
    }

    fun remove(uri: String) {
        secure.edit()
            .remove(usernameKey(uri))
            .remove(passwordKey(uri))
            .apply()
    }

    /** The `[user:pass@]host:port` string MoneroKit's node parser expects. */
    fun kitNodeString(uri: String): String = compose(uri, load(uri))

    /**
     * Builds up to 1.1.0 stored whatever the user typed, so a node entered as
     * user:pass@host:port sat with its password in the plain prefs and in the
     * node list. Move those into the encrypted store and leave bare host:port
     * behind. Idempotent, and free when nothing contains an '@'.
     */
    fun migrateInline(plainPrefs: SharedPreferences) {
        val selected = plainPrefs.getString(KEY_SELECTED_NODE, null)
        val customJson = plainPrefs.getString(KEY_CUSTOM_NODES, null)
        val selectedInline = selected?.contains('@') == true
        val customInline = customJson?.contains('@') == true
        if (!selectedInline && !customInline) return

        val editor = plainPrefs.edit()
        if (selectedInline) {
            val (uri, credentials) = splitInline(selected!!)
            credentials?.let { save(uri, it) }
            editor.putString(KEY_SELECTED_NODE, uri)
        }
        if (customInline) {
            val nodes = runCatching { json.decodeFromString<List<String>>(customJson!!) }.getOrNull()
            if (nodes != null) {
                val bare = nodes.map { entry ->
                    val (uri, credentials) = splitInline(entry)
                    credentials?.let { save(uri, it) }
                    uri
                }.distinct()
                editor.putString(KEY_CUSTOM_NODES, json.encodeToString(bare))
            }
        }
        editor.apply()
    }

    private fun usernameKey(uri: String) = "node.$uri.username"
    private fun passwordKey(uri: String) = "node.$uri.password"

    companion object {
        const val KEY_SELECTED_NODE = "selected_node"
        const val KEY_CUSTOM_NODES = "custom_nodes"
        private val json = Json { ignoreUnknownKeys = true }

        fun compose(uri: String, credentials: NodeCredentials?): String =
            if (credentials == null) uri else "${credentials.username}:${credentials.password}@$uri"

        /**
         * Splits `user:pass@host:port` into the bare URI and its credentials.
         * Credentials come back null when absent or malformed; the URI is
         * always the part after the last '@'.
         */
        fun splitInline(node: String): Pair<String, NodeCredentials?> {
            val at = node.lastIndexOf('@')
            if (at < 0) return node to null
            val uri = node.substring(at + 1)
            val userPass = node.substring(0, at)
            val colon = userPass.indexOf(':')
            if (colon <= 0 || colon == userPass.length - 1) return uri to null
            return uri to NodeCredentials(userPass.substring(0, colon), userPass.substring(colon + 1))
        }
    }
}
