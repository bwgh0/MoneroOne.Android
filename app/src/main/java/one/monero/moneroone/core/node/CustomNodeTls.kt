package one.monero.moneroone.core.node

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The custom nodes the user entered as https://, as iOS keeps the scheme in the
 * node URL. The node list, the names, the logins and the selected node all stay
 * keyed by bare host:port, so the marks sit beside them in the same plain prefs,
 * as a JSON list of bare host:port under [KEY_CUSTOM_TLS_NODES], the way
 * [CustomNodeNames] keeps the names. A node saved before this list existed has no
 * mark and keeps its meaning: TLS on port 443 only.
 */
class CustomNodeTls(private val prefs: SharedPreferences) {

    /** Every marked node. Unreadable JSON counts as no marks. */
    fun all(): Set<String> {
        val stored = prefs.getString(KEY_CUSTOM_TLS_NODES, null) ?: return emptySet()
        return runCatching { json.decodeFromString<List<String>>(stored).toSet() }.getOrDefault(emptySet())
    }

    fun has(uri: String): Boolean = uri in all()

    /** Saves the mark of a new node, or of an edit that may have moved it from [oldUri]. */
    fun save(uri: String, tls: Boolean, oldUri: String = uri) {
        val marks = all().toMutableSet()
        marks.remove(oldUri)
        if (tls) marks.add(uri) else marks.remove(uri)
        write(marks)
    }

    fun remove(uri: String) {
        val marks = all()
        if (uri in marks) write(marks - uri)
    }

    private fun write(marks: Set<String>) {
        prefs.edit().apply {
            if (marks.isEmpty()) remove(KEY_CUSTOM_TLS_NODES)
            else putString(KEY_CUSTOM_TLS_NODES, json.encodeToString(marks.sorted()))
        }.apply()
    }

    companion object {
        const val KEY_CUSTOM_TLS_NODES = "custom_tls_nodes"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
