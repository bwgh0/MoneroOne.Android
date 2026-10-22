package one.monero.moneroone.core.username

import java.io.IOException

/** In-memory persistence for [UsernameStore]. */
internal class MemoryUsernames : UsernamePersistence {
    var state = UsernameState()
    var fail = false
    override fun read() = state
    override fun write(state: UsernameState) {
        if (fail) throw IllegalStateException("test write failure")
        this.state = state
    }
}

/** The registry, in memory. A record it admits is "admitted:<record hash>". */
internal class FakeRegistryApi : UsernameRegistryApi {
    val handles = mutableMapOf<String, List<String>>()
    var unreachable = false
    /** The registry admits the claim, but its reply never arrives. */
    var loseReply = false
    var claimError: String? = null
    var claims = 0
    private var nonces = 0

    private fun reach() { if (unreachable) throw IOException("test registry unreachable") }
    override suspend fun checkedRules(network: String): String { reach(); return "rules" }
    override suspend fun records(handle: String): List<String>? { reach(); return handles[handle] }
    override suspend fun nonce(handle: String, subaddress: String): String { reach(); return "nonce-${++nonces}" }
    override suspend fun claim(pending: PendingUsernameClaim, deviceProof: String): Pair<String, String> {
        reach()
        claims++
        claimError?.let { throw UsernameException(it) }
        if (pending.handle in handles) throw UsernameException("name.unavailable")
        val record = "admitted:${pending.recordHash}"
        handles[pending.handle] = listOf(record)
        if (loseReply) throw IOException("test reply lost")
        return pending.handle to record
    }
}

/** Stands in for the Rust core: a record belongs to a claim when it carries the claim's hash. */
internal object FakeClaimCore : UsernameClaimCore {
    override fun normalize(input: String) = input.lowercase()
    override fun prepare(handle: String, subaddress: String, nonce: String, network: String, rules: String, rulesHash: String) =
        Triple("record:$handle:$subaddress:$nonce", "hash:$handle:$subaddress:$nonce", "message:$handle")
    override fun isOwnClaim(record: String, pending: PendingUsernameClaim) = record == "admitted:${pending.recordHash}"
}

internal val testConfiguration = UsernameConfiguration("http://localhost:8787", "a".repeat(64), "b".repeat(64))

internal fun testRegistry(api: FakeRegistryApi, configuration: UsernameConfiguration = testConfiguration) =
    UsernameRegistry(configuration, api, FakeClaimCore, developmentClaims = true)
