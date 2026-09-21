package one.monero.moneroone.core.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeInputTest {

    private fun valid(raw: String): NodeInput.Valid {
        val parsed = parseNodeInput(raw)
        assertTrue("expected Valid for '$raw', got $parsed", parsed is NodeInput.Valid)
        return parsed as NodeInput.Valid
    }

    private fun invalid(raw: String): String {
        val parsed = parseNodeInput(raw)
        assertTrue("expected Invalid for '$raw', got $parsed", parsed is NodeInput.Invalid)
        return (parsed as NodeInput.Invalid).message
    }

    @Test
    fun `bare host and port pass through without credentials`() {
        val parsed = valid("node.example.com:18081")
        assertEquals("node.example.com:18081", parsed.uri)
        assertNull(parsed.credentials)
    }

    @Test
    fun `scheme is stripped and https requires 443`() {
        assertEquals("node.example.com:443", valid("https://node.example.com:443/").uri)
        assertEquals("node.example.com:18081", valid("http://node.example.com:18081").uri)
        assertEquals("TLS nodes must use port 443", invalid("https://node.example.com:18081"))
    }

    @Test
    fun `inline credentials are split out of the uri`() {
        val parsed = valid("alice:s3cret@lan-node.local:18089")
        assertEquals("lan-node.local:18089", parsed.uri)
        assertEquals(NodeCredentials("alice", "s3cret"), parsed.credentials)
    }

    @Test
    fun `inline credentials survive a scheme prefix`() {
        val parsed = valid("http://alice:s3cret@node.example.com:18081")
        assertEquals("node.example.com:18081", parsed.uri)
        assertEquals(NodeCredentials("alice", "s3cret"), parsed.credentials)
    }

    @Test
    fun `malformed inline credentials are rejected`() {
        assertEquals("Credentials must be user:pass@host:port", invalid("alice@node.example.com:18081"))
        assertEquals("Credentials must be user:pass@host:port", invalid("alice:@node.example.com:18081"))
        assertEquals("Credentials must be user:pass@host:port", invalid(":pw@node.example.com:18081"))
        assertEquals("Credentials must be user:pass@host:port", invalid("a:b@c@node.example.com:18081"))
        assertEquals("Credentials must be user:pass@host:port", invalid("a:b:c@node.example.com:18081"))
    }

    @Test
    fun `ipv6 hosts keep their brackets`() {
        assertEquals("[::1]:18081", valid("[::1]:18081").uri)
        assertEquals("Wrap IPv6 addresses in brackets, e.g. [::1]:18081", invalid("::1:18081"))
    }

    @Test
    fun `paths, spaces and bad ports are rejected`() {
        assertEquals("Use host:port only, without a path", invalid("node.example.com:18081/json_rpc"))
        assertEquals("URI must not contain spaces", invalid("node.example .com:18081"))
        assertEquals("Include port (e.g., :18081)", invalid("node.example.com"))
        assertEquals("Port must be between 1 and 65535", invalid("node.example.com:99999"))
        assertEquals("Node URI required", invalid("   "))
    }

    @Test
    fun `credential validation`() {
        assertNull(validateNodeCredentials("", ""))
        assertNull(validateNodeCredentials("alice", "s3cret"))
        assertEquals("Username required when a password is set", validateNodeCredentials("", "pw"))
        assertEquals("Password required when a username is set", validateNodeCredentials("alice", ""))
        assertEquals("Credentials must not contain spaces", validateNodeCredentials("al ice", "pw"))
        assertEquals("Credentials must not contain spaces", validateNodeCredentials("alice", "p w"))
        assertEquals("Credentials must not contain ':' or '@'", validateNodeCredentials("al:ice", "pw"))
        assertEquals("Credentials must not contain ':' or '@'", validateNodeCredentials("alice", "p@w"))
        assertEquals("Credentials must not contain ':' or '@'", validateNodeCredentials("alice", "p:w"))
    }
}
