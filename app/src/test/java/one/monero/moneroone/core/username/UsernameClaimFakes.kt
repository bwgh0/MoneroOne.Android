package one.monero.moneroone.core.username

import java.io.IOException
import java.util.Base64

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
    /** Full lookup replies (a restore reads the head, owner and tombstone). They win over [handles]. */
    val found = mutableMapOf<String, HeldName>()
    /**
     * Refusals of a lookup, by handle: (HTTP status, body). They become failures as in the HTTP client: a
     * `451 name.held` whose notice passes the check with [registrarKey] throws "name.held" with its hold.
     */
    val refusals = mutableMapOf<String, Pair<Int, String>>()
    /** The pinned registrar key of the HTTP client's hold check. */
    var registrarKey = testConfiguration.registrarKey
    /** Each request, in order: "info", "lookup <handle>", "nonce", "claim" or "append <handle>". */
    val calls = mutableListOf<String>()
    var unreachable = false
    /** The registry admits the claim, but its reply never arrives. */
    var loseReply = false
    var claimError: String? = null
    var claims = 0
    /** Runs after each records lookup, to change the registry between two lookups. */
    var afterRecords: (() -> Unit)? = null
    /** The pending claim saved on the phone when the POST went out. */
    var savedAtPost: PendingUsernameClaim? = null
    /** The device object of the last POST. */
    var device: DeviceProof? = null
    var store: UsernameStore? = null
    private var nonces = 0

    private fun reach() { if (unreachable) throw IOException("test registry unreachable") }
    override suspend fun checkedRules(network: String): String { reach(); calls += "info"; return "rules" }
    override suspend fun lookup(handle: String): HeldName? {
        reach()
        calls += "lookup $handle"
        refusals[handle]?.let { (status, body) -> throw UsernameRegistryAnswer.failure(status, body, handle, registrarKey) }
        return (found[handle] ?: handles[handle]?.let { rows -> HeldName(rows.map { HeldRecord(it) }) }).also { afterRecords?.invoke() }
    }
    /** 32 bytes in lowercase hex, as the registry sends them. */
    override suspend fun nonce(handle: String, subaddress: String): String { reach(); calls += "nonce"; return "%064x".format(++nonces) }
    override suspend fun claim(pending: PendingUsernameClaim, device: DeviceProof): Pair<String, String> {
        reach()
        calls += "claim"
        claims++
        savedAtPost = store?.state?.value?.pending
        this.device = device
        claimError?.let { throw UsernameException(it) }
        if (pending.handle in handles) throw UsernameException("name.unavailable")
        val record = "admitted:${pending.recordHash}"
        handles[pending.handle] = listOf(record)
        if (loseReply) throw IOException("test reply lost")
        return pending.handle to record
    }
    /** The posted owner records: (handle, record, owner signatures). */
    val appended = mutableListOf<Triple<String, String, List<String>>>()
    /** The reply to an owner record: (handle, record). With neither this nor [appendRefusal], a post fails the test. */
    var appendReply: Pair<String, String>? = null
    /** The registry refuses the owner record: (HTTP status, body), mapped as in [refusals]. */
    var appendRefusal: Pair<Int, String>? = null
    /** The registry admits the owner record, but its reply never arrives. */
    var loseAppendReply = false
    /** The saved retires on the phone when the owner record went out. */
    var retiresAtPost: List<UsernameRetireAttempt>? = null
    override suspend fun appendRecord(handle: String, record: String, ownerSignatures: List<String>): Pair<String, String> {
        reach()
        calls += "append $handle"
        appended += Triple(handle, record, ownerSignatures)
        retiresAtPost = store?.state?.value?.retires
        appendRefusal?.let { (status, body) -> throw UsernameRegistryAnswer.failure(status, body, handle, registrarKey) }
        if (loseAppendReply) throw IOException("test reply lost")
        return appendReply ?: throw AssertionError("No owner record may be posted")
    }
}

/**
 * Play Integrity, in memory. Each prepare (Google's warmup) makes a new provider with the next number; a
 * provider's token is "token-<number>".
 */
internal class FakePlayIntegrity : StandardIntegrityPlay {
    var prepares = 0
        private set
    /** Each token request, in order: (provider number, request hash). */
    val requests = mutableListOf<Pair<Int, String>>()
    /** The error codes of the next requests, in order. */
    val requestErrors = ArrayDeque<Int>()
    /** The error code of every prepare, while set. */
    var prepareError: Int? = null
    override suspend fun prepare(): StandardIntegrityProvider {
        prepareError?.let { throw PlayIntegrityError(it) }
        val provider = ++prepares
        return StandardIntegrityProvider { requestHash ->
            requests += provider to requestHash
            requestErrors.removeFirstOrNull()?.let { throw PlayIntegrityError(it) }
            "token-$provider"
        }
    }
}

/**
 * A genesis record as the fake core reads it: what the real one's verify_claim checks (the record hash,
 * handle, owner subaddress, network, registrar key and rules hash), in one string.
 */
internal fun genesisRecord(handle: String, owner: String, hash: String, network: String = "mainnet",
                           registrarKey: String = testConfiguration.registrarKey, rulesHash: String = testConfiguration.rulesHash) =
    "genesis:$handle:$owner:$network:$hash:$registrarKey:$rulesHash"

/** Stands in for the Rust core: a record belongs to a claim when it carries the claim's hash. */
internal object FakeClaimCore : UsernameClaimCore {
    override fun normalize(input: String) = input.lowercase()
    /** The unsigned record is base64url without padding, as the core makes it. */
    override fun prepare(handle: String, subaddress: String, nonce: String, network: String, rules: String, rulesHash: String) =
        Triple(Base64.getUrlEncoder().withoutPadding().encodeToString("record:$handle:$subaddress:$nonce".toByteArray()),
            "hash:$handle:$subaddress:$nonce", "message:$handle")
    override fun isOwnClaim(record: String, pending: PendingUsernameClaim) = record == "admitted:${pending.recordHash}" ||
        record == genesisRecord(pending.handle, pending.subaddress, pending.recordHash, pending.network, pending.registrarKey, pending.rulesHash)
}

internal val testConfiguration = UsernameConfiguration("http://localhost:8787", "a".repeat(64), "b".repeat(64), "local-development-v1")

/** The test wallet's private view key: the bytes 0x00 to 0x1f. */
internal val testViewKey get() = ByteArray(32) { it.toByte() }
/** Its claim key: the shared test vector of both apps. */
internal const val testClaimKey = "6f19b34dced9cf703d2e0222e085d6a475695543cce6b912af942a541524ba6a"

/** [developmentClaims] is a debug build; [integrity] null is a build without a cloud project number. */
internal fun testRegistry(api: FakeRegistryApi, configuration: UsernameConfiguration = testConfiguration, developmentClaims: Boolean = true,
                          integrity: IntegrityTokenSource? = null) =
    UsernameRegistry(configuration, api, FakeClaimCore, UsernameDeviceGate(developmentClaims, integrity))
