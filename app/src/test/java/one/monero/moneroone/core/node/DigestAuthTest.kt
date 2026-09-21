package one.monero.moneroone.core.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestAuthTest {

    // RFC 2617 section 3.5 worked example.
    private val rfcChallenge = "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
        "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""

    @Test
    fun `rfc 2617 example response`() {
        val header = DigestAuth.authorization(
            challenge = rfcChallenge,
            method = "GET",
            uri = "/dir/index.html",
            credentials = NodeCredentials("Mufasa", "Circle Of Life"),
            cnonce = "0a4f113b",
            nonceCount = 1
        )!!
        assertTrue(header.startsWith("Digest username=\"Mufasa\""))
        assertTrue(header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(header.contains("realm=\"testrealm@host.com\""))
        assertTrue(header.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
        assertTrue(header.contains("uri=\"/dir/index.html\""))
        assertTrue(header.contains("qop=auth"))
        assertTrue(header.contains("nc=00000001"))
        assertTrue(header.contains("cnonce=\"0a4f113b\""))
        assertTrue(header.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
        assertTrue(header.contains("algorithm=MD5"))
    }

    @Test
    fun `monerod style challenge with unquoted algorithm and stale`() {
        // epee's http_server_auth emits this shape for --rpc-login.
        val challenge = "Digest algorithm=MD5,nonce=\"AbCd\",qop=\"auth\",realm=\"monero-rpc\",stale=false"
        val params = DigestAuth.parseChallenge(challenge)!!
        assertEquals("MD5", params["algorithm"])
        assertEquals("AbCd", params["nonce"])
        assertEquals("auth", params["qop"])
        assertEquals("monero-rpc", params["realm"])
        assertEquals("false", params["stale"])

        val header = DigestAuth.authorization(challenge, "GET", "/get_info", NodeCredentials("u", "p"), cnonce = "c")!!
        assertTrue(header.contains("realm=\"monero-rpc\""))
        assertTrue(header.contains("uri=\"/get_info\""))
    }

    @Test
    fun `md5-sess and no-qop variants produce a different but well formed response`() {
        val plainQop = DigestAuth.authorization(rfcChallenge, "GET", "/x", NodeCredentials("u", "p"), cnonce = "c")!!
        val sess = DigestAuth.authorization(
            rfcChallenge.replace("Digest ", "Digest algorithm=MD5-sess, "), "GET", "/x", NodeCredentials("u", "p"), cnonce = "c"
        )!!
        val noQop = DigestAuth.authorization(
            "Digest realm=\"r\", nonce=\"n\"", "GET", "/x", NodeCredentials("u", "p"), cnonce = "c"
        )!!
        val responses = listOf(plainQop, sess, noQop).map { Regex("response=\"([0-9a-f]{32})\"").find(it)!!.groupValues[1] }
        assertEquals(3, responses.toSet().size)
        assertTrue(sess.contains("algorithm=MD5-sess"))
        assertTrue(!noQop.contains("qop=") && !noQop.contains("cnonce="))
    }

    @Test
    fun `unsupported challenges yield null`() {
        assertNull(DigestAuth.authorization("Basic realm=\"x\"", "GET", "/", NodeCredentials("u", "p")))
        assertNull(DigestAuth.authorization("Digest realm=\"x\"", "GET", "/", NodeCredentials("u", "p"))) // no nonce
        assertNull(DigestAuth.authorization("Digest realm=\"x\", nonce=\"n\", qop=\"auth-int\"", "GET", "/", NodeCredentials("u", "p")))
        assertNull(DigestAuth.authorization("Digest realm=\"x\", nonce=\"n\", algorithm=SHA-256", "GET", "/", NodeCredentials("u", "p")))
    }

    @Test
    fun `quotes inside values are escaped in the header`() {
        val header = DigestAuth.authorization("Digest realm=\"r\", nonce=\"n\"", "GET", "/", NodeCredentials("a\"b", "p"))!!
        assertTrue(header.contains("username=\"a\\\"b\""))
    }
}
