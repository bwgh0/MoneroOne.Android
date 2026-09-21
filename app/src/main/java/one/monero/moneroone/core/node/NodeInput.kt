package one.monero.moneroone.core.node

sealed interface NodeInput {
    /** [uri] is always bare host:port; inline `user:pass@` input lands in [credentials]. */
    data class Valid(val uri: String, val credentials: NodeCredentials? = null) : NodeInput
    data class Invalid(val message: String) : NodeInput
}

// Standard hostname labels, with underscores tolerated for LAN hosts that use them.
private val NODE_HOST_REGEX = Regex(
    "^[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?" +
        "(\\.[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?)*$"
)

/**
 * Validates custom node input and normalizes it to the host:port form
 * (IPv6 hosts in brackets). Inline `user:pass@` credentials are accepted and
 * split out so they never travel with the URI. Loopback and private-range
 * hosts are deliberately allowed: self-hosted nodes are supported.
 */
fun parseNodeInput(raw: String): NodeInput {
    var value = raw.trim()
    if (value.isEmpty()) return NodeInput.Invalid("Node URI required")
    if (value.any { it.isWhitespace() }) return NodeInput.Invalid("URI must not contain spaces")

    var https = false
    when {
        value.startsWith("http://", ignoreCase = true) -> value = value.substring(7)
        value.startsWith("https://", ignoreCase = true) -> {
            https = true
            value = value.substring(8)
        }
        value.contains("://") -> return NodeInput.Invalid("Only http:// or https:// nodes are supported")
    }

    var credentials: NodeCredentials? = null
    if (value.count { it == '@' } > 1) return NodeInput.Invalid("Credentials must be user:pass@host:port")
    val at = value.indexOf('@')
    if (at >= 0) {
        val userPass = value.substring(0, at)
        value = value.substring(at + 1)
        val colon = userPass.indexOf(':')
        if (colon <= 0 || colon != userPass.lastIndexOf(':') || colon == userPass.length - 1) {
            return NodeInput.Invalid("Credentials must be user:pass@host:port")
        }
        credentials = NodeCredentials(userPass.substring(0, colon), userPass.substring(colon + 1))
        validateNodeCredentials(credentials.username, credentials.password)?.let { return NodeInput.Invalid(it) }
    }

    value = value.removeSuffix("/")
    if (value.contains('/')) return NodeInput.Invalid("Use host:port only, without a path")

    val hostPart: String
    val portText: String
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        val host = if (end > 1) value.substring(1, end) else ""
        if (host.isEmpty() || !host.contains(':') || !host.all { it in "0123456789abcdefABCDEF:." }) {
            return NodeInput.Invalid("Invalid IPv6 address")
        }
        val rest = value.substring(end + 1)
        if (!rest.startsWith(":") || rest.length == 1) {
            return NodeInput.Invalid("Include port (e.g., :18081)")
        }
        portText = rest.substring(1)
        hostPart = "[$host]"
    } else {
        val colon = value.lastIndexOf(':')
        if (colon == -1 || colon == value.length - 1) {
            return NodeInput.Invalid("Include port (e.g., :18081)")
        }
        val host = value.substring(0, colon)
        portText = value.substring(colon + 1)
        if (host.contains(':')) return NodeInput.Invalid("Wrap IPv6 addresses in brackets, e.g. [::1]:18081")
        if (host.isEmpty()) return NodeInput.Invalid("Host required")
        if (host.length > 253 || !NODE_HOST_REGEX.matches(host)) return NodeInput.Invalid("Invalid host name")
        hostPart = host
    }

    val port = if (portText.all { it.isDigit() }) portText.toIntOrNull() else null
    if (port == null || port !in 1..65535) return NodeInput.Invalid("Port must be between 1 and 65535")
    // MoneroKit only speaks TLS on port 443, so any other https:// input would
    // silently downgrade to cleartext — reject instead.
    if (https && port != 443) return NodeInput.Invalid("TLS nodes must use port 443")

    return NodeInput.Valid("$hostPart:$port", credentials)
}

/**
 * Null when the pair is usable (or both blank), otherwise the message to show.
 * The ':' and '@' bans come from MoneroKit's node parser, which carries the
 * login inside the `user:pass@host:port` string and splits on those characters.
 */
fun validateNodeCredentials(username: String, password: String): String? {
    if (username.isEmpty() && password.isEmpty()) return null
    if (username.isEmpty()) return "Username required when a password is set"
    if (password.isEmpty()) return "Password required when a username is set"
    if (username.any { it.isWhitespace() } || password.any { it.isWhitespace() }) {
        return "Credentials must not contain spaces"
    }
    if (username.contains(':') || username.contains('@') || password.contains(':') || password.contains('@')) {
        return "Credentials must not contain ':' or '@'"
    }
    return null
}
