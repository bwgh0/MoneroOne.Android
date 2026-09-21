package one.monero.moneroone.core.node

import one.monero.moneroone.core.wallet.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeCredentialStoreTest {

    private val secure = FakeSharedPreferences()
    private val plain = FakeSharedPreferences()
    private val store = NodeCredentialStore(secure)

    @Test
    fun `save load remove round trip`() {
        assertNull(store.load("node.example.com:18081"))
        assertFalse(store.has("node.example.com:18081"))

        store.save("node.example.com:18081", NodeCredentials("alice", "s3cret"))
        assertEquals(NodeCredentials("alice", "s3cret"), store.load("node.example.com:18081"))
        assertTrue(store.has("node.example.com:18081"))
        assertNull(store.load("other.example.com:18081"))

        store.remove("node.example.com:18081")
        assertNull(store.load("node.example.com:18081"))
    }

    @Test
    fun `saving null credentials clears the stored pair`() {
        store.save("node.example.com:18081", NodeCredentials("alice", "s3cret"))
        store.save("node.example.com:18081", null)
        assertNull(store.load("node.example.com:18081"))
    }

    @Test
    fun `kit node string carries the login only when one is stored`() {
        assertEquals("node.example.com:18081", store.kitNodeString("node.example.com:18081"))
        store.save("node.example.com:18081", NodeCredentials("alice", "s3cret"))
        assertEquals("alice:s3cret@node.example.com:18081", store.kitNodeString("node.example.com:18081"))
    }

    @Test
    fun `credentials never live in the plain prefs`() {
        store.save("node.example.com:18081", NodeCredentials("alice", "s3cret"))
        assertTrue(plain.all.isEmpty())
        assertTrue(secure.all.values.any { it == "s3cret" })
    }

    @Test
    fun `toString hides the password`() {
        val text = NodeCredentials("alice", "s3cret").toString()
        assertTrue(text.contains("alice"))
        assertFalse(text.contains("s3cret"))
    }

    @Test
    fun `splitInline`() {
        assertEquals("host:1" to NodeCredentials("u", "p"), NodeCredentialStore.splitInline("u:p@host:1"))
        assertEquals("host:1" to null, NodeCredentialStore.splitInline("host:1"))
        assertEquals("host:1" to null, NodeCredentialStore.splitInline("u@host:1"))
        assertEquals("host:1" to null, NodeCredentialStore.splitInline("u:@host:1"))
        assertEquals("host:1" to null, NodeCredentialStore.splitInline(":p@host:1"))
    }

    @Test
    fun `migrateInline moves a 1_1_0 style selected node into the encrypted store`() {
        plain.edit().putString("selected_node", "alice:s3cret@lan-node.local:18089").apply()

        store.migrateInline(plain)

        assertEquals("lan-node.local:18089", plain.getString("selected_node", null))
        assertEquals(NodeCredentials("alice", "s3cret"), store.load("lan-node.local:18089"))
    }

    @Test
    fun `migrateInline rewrites the custom node list and keeps bare entries`() {
        plain.edit()
            .putString("custom_nodes", """["alice:s3cret@lan-node.local:18089","node.example.com:18081","bob:pw@lan-node.local:18089"]""")
            .apply()

        store.migrateInline(plain)

        assertEquals("""["lan-node.local:18089","node.example.com:18081"]""", plain.getString("custom_nodes", null))
        // Last write wins for a duplicated host, which matches the list order the user saw.
        assertEquals(NodeCredentials("bob", "pw"), store.load("lan-node.local:18089"))
        assertNull(store.load("node.example.com:18081"))
    }

    @Test
    fun `migrateInline is a no-op without inline credentials and is idempotent`() {
        plain.edit()
            .putString("selected_node", "node.example.com:18081")
            .putString("custom_nodes", """["node.example.com:18081"]""")
            .apply()
        val before = plain.all.toMap()

        store.migrateInline(plain)
        assertEquals(before, plain.all.toMap())
        assertTrue(secure.all.isEmpty())

        plain.edit().putString("selected_node", "alice:s3cret@lan-node.local:18089").apply()
        store.migrateInline(plain)
        store.migrateInline(plain)
        assertEquals("lan-node.local:18089", plain.getString("selected_node", null))
        assertEquals(NodeCredentials("alice", "s3cret"), store.load("lan-node.local:18089"))
    }
}
