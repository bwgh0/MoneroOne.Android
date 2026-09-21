package one.monero.moneroone.core.node

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Client side of HTTP Digest authentication (RFC 2617), enough for monerod's
 * `--rpc-login`: MD5 or MD5-sess, qop "auth" or none. HttpURLConnection has
 * no digest support of its own, and the kit's OkHttp digest client is not
 * visible to the app module.
 */
object DigestAuth {

    /**
     * The Authorization header answering one WWW-Authenticate [challenge], or
     * null when the challenge is not a digest one this client can satisfy.
     */
    fun authorization(
        challenge: String,
        method: String,
        uri: String,
        credentials: NodeCredentials,
        cnonce: String = newCnonce(),
        nonceCount: Int = 1
    ): String? {
        val params = parseChallenge(challenge) ?: return null
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val algorithm = params["algorithm"] ?: "MD5"
        val offeredQop = params["qop"]
        val qop = offeredQop?.split(',')?.map { it.trim() }?.firstOrNull { it.equals("auth", ignoreCase = true) }
        if (offeredQop != null && qop == null) return null // only auth-int on offer
        val nc = "%08x".format(nonceCount)

        var ha1 = md5("${credentials.username}:$realm:${credentials.password}")
        when (algorithm.lowercase()) {
            "md5" -> Unit
            "md5-sess" -> ha1 = md5("$ha1:$nonce:$cnonce")
            else -> return null
        }
        val ha2 = md5("$method:$uri")
        val response = if (qop != null) {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        return buildString {
            append("Digest username=\"").append(quote(credentials.username)).append("\"")
            append(", realm=\"").append(quote(realm)).append("\"")
            append(", nonce=\"").append(quote(nonce)).append("\"")
            append(", uri=\"").append(uri).append("\"")
            if (qop != null) {
                append(", qop=").append(qop)
                append(", nc=").append(nc)
                append(", cnonce=\"").append(cnonce).append("\"")
            }
            append(", response=\"").append(response).append("\"")
            append(", algorithm=").append(algorithm)
            params["opaque"]?.let { append(", opaque=\"").append(quote(it)).append("\"") }
        }
    }

    /** Lower-cased parameter map of a `Digest ...` challenge, or null for any other scheme. */
    internal fun parseChallenge(header: String): Map<String, String>? {
        val trimmed = header.trim()
        if (!trimmed.startsWith("Digest ", ignoreCase = true)) return null
        val result = mutableMapOf<String, String>()
        PARAM.findAll(trimmed.substring(7)).forEach { match ->
            val quoted = match.groups[2]?.value
            val value = quoted?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: match.groupValues[3].trim()
            result[match.groupValues[1].lowercase()] = value
        }
        return result
    }

    private val PARAM = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^,\s]*))""")

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).toHex()

    private fun quote(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun newCnonce(): String = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
