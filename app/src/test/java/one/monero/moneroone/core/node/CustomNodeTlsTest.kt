package one.monero.moneroone.core.node

import one.monero.moneroone.core.wallet.DefaultNodes
import one.monero.moneroone.core.wallet.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomNodeTlsTest {

    private val plain = FakeSharedPreferences()
    private val tls = CustomNodeTls(plain)

    // Not a default: a default carries its own TLS mark.
    private val custom = "my-node.example.com:18089"

    @Test
    fun `an https node is marked under its bare uri`() {
        tls.save(custom, tls = true)

        assertTrue(tls.has(custom))
        assertEquals("""["$custom"]""", plain.getString("custom_tls_nodes", null))
        assertTrue(DefaultNodes.isTls(custom, tls.all()))
    }

    @Test
    fun `a node saved before the marks keeps its meaning`() {
        // An older build left only the bare list: no mark, so TLS only on port 443.
        plain.edit().putString("custom_nodes", """["$custom","node.example.com:443"]""").apply()

        assertFalse(DefaultNodes.isTls(custom, tls.all()))
        assertTrue(DefaultNodes.isTls("node.example.com:443", tls.all()))
    }

    @Test
    fun `marks never touch the custom node list older builds read`() {
        plain.edit().putString("custom_nodes", """["$custom"]""").apply()

        tls.save(custom, tls = true)
        tls.save("node.example.com:18081", tls = true, oldUri = custom)
        tls.remove("node.example.com:18081")

        assertEquals("""["$custom"]""", plain.getString("custom_nodes", null))
    }

    @Test
    fun `an edit moves the mark or drops it`() {
        tls.save(custom, tls = true)

        tls.save("node.example.com:18089", tls = true, oldUri = custom)
        assertEquals(setOf("node.example.com:18089"), tls.all())

        tls.save("node.example.com:18089", tls = false)
        assertNull(plain.getString("custom_tls_nodes", null))
    }

    @Test
    fun `delete removes the mark`() {
        tls.save(custom, tls = true)
        tls.save("node.example.com:18081", tls = true)

        tls.remove(custom)

        assertEquals(setOf("node.example.com:18081"), tls.all())
    }

    @Test
    fun `unreadable json counts as no marks`() {
        plain.edit().putString("custom_tls_nodes", "{not json").apply()

        assertEquals(emptySet<String>(), tls.all())
        tls.save(custom, tls = true)
        assertEquals(setOf(custom), tls.all())
    }
}
