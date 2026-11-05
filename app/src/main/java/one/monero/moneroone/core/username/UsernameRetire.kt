package one.monero.moneroone.core.username

import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/** Why the registry holds a name (registry README, "The valve"). */
enum class UsernameHoldReason(val code: String) {
    /** A court order or another binding legal demand. */
    ORDER("order"),
    /** A notice that the registry received (a scam, an impersonation, a trademark misuse). */
    NOTICE("notice"),
    /** A sanctions match. */
    SANCTIONS("sanctions"),
    /** A breach of the Username Terms. */
    TERMS("terms");

    /**
     * A `notice` or `terms` hold lets the holder retire the name. An `order` or `sanctions` hold refuses a
     * retire with the same 451: the law can require the registry to keep those records.
     */
    val allowsRetire: Boolean get() = this == NOTICE || this == TERMS

    companion object {
        fun of(code: String): UsernameHoldReason? = entries.firstOrNull { it.code == code }
    }
}

/** The newest record of a name. A retire signs a tombstone at [seq] + 1 over [recordHash] (lowercase hex). */
data class UsernameHead(val seq: Long, val recordHash: String) {
    companion object {
        /** A head from a lookup's `head.seq` and `head.record_hash`, or null: a u32 seq and 64 lowercase hex digits. */
        fun of(seq: Long?, recordHash: String?): UsernameHead? =
            if (seq != null && seq in 0..UINT32_MAX && recordHash != null && HEX64.matches(recordHash)) UsernameHead(seq, recordHash) else null

        /** `{"seq", "record_hash"}` as the registry sends it, or null. The seq must be a whole JSON number. */
        internal fun of(json: JsonElement?): UsernameHead? {
            val head = json as? JsonObject ?: return null
            val seq = (head["seq"] as? JsonPrimitive)?.takeIf { !it.isString && Regex("-?[0-9]{1,19}").matches(it.content) }
            return of(seq?.content?.toLongOrNull(), head["record_hash"].text())
        }
    }
}

/**
 * A hold whose notice passed the signature check with the pinned registrar key. The handle, the reason and
 * the date ([heldAt], epoch milliseconds) come from the signed notice only; [head] comes from the unsigned
 * part of the answer, and is null when the registry sent none (then no retire can be signed).
 */
data class UsernameHold(val handle: String, val reason: UsernameHoldReason, val heldAt: Long, val head: UsernameHead?) {
    /** The holder may sign a retire: a `notice` or `terms` hold with a head. */
    val allowsRetire: Boolean get() = reason.allowsRetire && head != null

    companion object {
        /** The first line of a hold notice: its domain tag. */
        const val NOTICE_TAG = "oah-registry/hold/v1"

        /** [parse] of a raw answer body. */
        fun parse(body: String, handle: String, registrarKey: String, zone: String = UsernameConfiguration.ZONE): UsernameHold? =
            runCatching { Json.parseToJsonElement(body) }.getOrNull()?.let { parse(it, handle, registrarKey, zone) }

        /**
         * The hold in a `451 name.held` answer for [handle]. Null when the answer is not one, or when its notice
         * fails a check (the signature, the zone, the handle, the reason, the format). The caller then shows its
         * generic error, with no reason.
         */
        fun parse(body: JsonElement, handle: String, registrarKey: String, zone: String = UsernameConfiguration.ZONE): UsernameHold? {
            val answer = body as? JsonObject ?: return null
            if ((answer["error"] as? JsonObject)?.get("code").text() != UsernameException.HELD) return null
            val hold = answer["hold"] as? JsonObject ?: return null
            val notice = hold["notice"].text() ?: return null
            val signature = hold["signature"].text()?.let(UsernameBase64::decode) ?: return null
            if (!UsernameRegistrar.isSigned(notice.toByteArray(Charsets.UTF_8), signature, registrarKey)) return null
            val lines = notice.split("\n")
            if (lines.size != 6 || lines[0] != NOTICE_TAG || lines[1] != "zone $zone" || lines[2] != "handle $handle" ||
                !lines[3].startsWith("reason ") || !lines[4].startsWith("held_at ") || lines[5].isNotEmpty()) return null
            val reason = UsernameHoldReason.of(lines[3].removePrefix("reason ")) ?: return null
            val text = lines[4].removePrefix("held_at ")
            val seconds = text.toLongOrNull()?.takeIf { it.toString() == text } ?: return null
            val heldAt = runCatching { Math.multiplyExact(seconds, 1000L) }.getOrNull() ?: return null
            // The head is optional: a hold without one shows its reason but offers no retire.
            return UsernameHold(handle, reason, heldAt, UsernameHead.of(hold["head"]))
        }
    }
}

/** Strict base64url without padding, as the registry encodes records and Ed25519 signatures. */
internal object UsernameBase64 {
    fun encode(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    /** Null for padding, another alphabet or a non-canonical form. */
    fun decode(text: String): ByteArray? {
        if (text.length % 4 == 1 || !text.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return null
        return runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull()?.takeIf { encode(it) == text }
    }
}

/** The registrar's Ed25519 signatures, checked with the pinned registrar key ([UsernameConfiguration.registrarKey]). */
internal object UsernameRegistrar {
    /** True when [signature] is the registrar's 64-byte Ed25519 signature of [message], under the 64-hex-digit public [key]. */
    fun isSigned(message: ByteArray, signature: ByteArray, key: String): Boolean {
        if (signature.size != 64 || !HEX64.matches(key)) return false
        return runCatching { Ed25519Verify(hexBytes(key)).verify(signature, message) }.isSuccess
    }
}

/**
 * The OAH1 tombstone (oah SPEC.md and oah-core `record.rs`):
 *
 *     header = "OAH1" type:u8 (4) handle_len:u8 handle seq:u32be prev:32
 *     body   = (empty)
 *     record_hash = SHA-256(header || body)
 *     message     = "oah/tombstone/v1:" || lowercase hex(record_hash)
 *
 * oah-mobile has no tombstone call, and this header is the whole record, so the app builds it here. The
 * tests check it against the oah vector `alice-r5-tombstone`.
 */
object UsernameTombstone {
    /**
     * The unsigned record, or null when a value is not valid: a handle that is not a lowercase label, a [seq]
     * outside 1 to 2^32 - 1, or a [prev] that is not 64 lowercase hex digits.
     */
    fun unsigned(handle: String, seq: Long, prev: String): ByteArray? {
        if (!isLabel(handle) || seq !in 1..UINT32_MAX || !HEX64.matches(prev)) return null
        val name = handle.toByteArray(Charsets.US_ASCII)
        return "OAH1".toByteArray(Charsets.US_ASCII) + byteArrayOf(4, name.size.toByte()) + name +
            byteArrayOf((seq shr 24).toByte(), (seq shr 16).toByte(), (seq shr 8).toByte(), seq.toByte()) + hexBytes(prev)
    }

    fun recordHash(unsigned: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(unsigned).hex()

    /** The ASCII text the owner signs, with the wallet's existing signer. */
    fun signingMessage(unsigned: ByteArray): String = "oah/tombstone/v1:" + recordHash(unsigned)

    /**
     * True when the registry's reply is this tombstone: the same unsigned bytes, one owner signature, and the
     * registrar's countersignature of the signing message.
     */
    fun isCountersigned(record: ByteArray, unsigned: ByteArray, registrarKey: String): Boolean {
        if (record.size != unsigned.size + 128 || !record.copyOfRange(0, unsigned.size).contentEquals(unsigned)) return false
        return UsernameRegistrar.isSigned(signingMessage(unsigned).toByteArray(Charsets.US_ASCII),
            record.copyOfRange(record.size - 64, record.size), registrarKey)
    }

    /**
     * True when a lookup's chain ([records], oldest first) ends in a tombstone of [binding]'s name that the
     * registrar countersigned with [registrarKey]: the tombstone this phone saved before its POST ([attempt]),
     * or one at seq 1 over the name's genesis (a retire of the claim's chain from another phone). The lookup's
     * `tombstoned` flag, a 404 and a `chain.record_after_tombstone` refusal are not signed, so they prove no
     * retire (audit F12). [UsernameRegistry.retire] accepts a 404 only after an earlier saved retire.
     */
    fun endsRetired(records: List<HeldRecord>, binding: UsernameBinding, attempt: UsernameRetireAttempt?, registrarKey: String): Boolean {
        val last = records.lastOrNull()?.record?.let(UsernameBase64::decode) ?: return false
        val saved = attempt?.takeIf { it.isFor(binding) }?.unsigned?.let(UsernameBase64::decode)
        return listOfNotNull(saved, unsigned(binding.handle, 1, binding.genesisHash)).any { isCountersigned(last, it, registrarKey) }
    }

    /**
     * oah-core `validate_label`: 3 to 15 of a-z, 0-9 and _, a letter first, no `__`. The blocked-name list is
     * not checked: a name that the list blocks later can still retire.
     */
    fun isLabel(handle: String): Boolean =
        handle.length in 3..15 && handle[0] in 'a'..'z' && handle.all { it in 'a'..'z' || it in '0'..'9' || it == '_' } && "__" !in handle
}

/**
 * The `retired` object of a `404 handle.not_found`, as `GET /v1/handles/{h}?genesis=<64 hex>` sends it for a name
 * that a tombstone retired and the epoch erased (registry README, "Retired statement"). Not checked: a field that is
 * missing or of another type is null, and [UsernameRetired.proves] then refuses it.
 */
data class UsernameRetiredAnswer(val handle: String?, val genesisHash: String?, val statement: String?, val signature: String?)

/**
 * The registrar's signed statement that a name, with its genesis, was retired and erased (audit F12b). After the
 * erasure a plain lookup answers the unsigned 404 of a name that never existed. The registrar signs, with its log key
 * (the pinned [UsernameConfiguration.registrarKey], as for a hold notice), the text
 *
 *     "oah-registry/retired/v1\nzone <zone>\nhandle <handle>\ngenesis_hash <64 lowercase hex>\n"
 *
 * The app builds these bytes itself from its own name and genesis hash, and never uses the server's text.
 */
object UsernameRetired {
    /** The first line of a retired statement: its domain tag. */
    const val TAG = "oah-registry/retired/v1"

    /** The statement bytes for [handle] with [genesisHash], in [zone]. */
    fun text(handle: String, genesisHash: String, zone: String = UsernameConfiguration.ZONE): String =
        "$TAG\nzone $zone\nhandle $handle\ngenesis_hash $genesisHash\n"

    /** The `retired` object of an answer with HTTP [status] and body [text], or null: not a 404 `handle.not_found` with one. */
    fun answer(status: Int, text: String): UsernameRetiredAnswer? {
        if (status != 404) return null
        val body = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        if ((body["error"] as? JsonObject)?.get("code").text() != UsernameException.NOT_FOUND) return null
        val retired = body["retired"] as? JsonObject ?: return null
        return UsernameRetiredAnswer(retired["handle"].text(), retired["genesis_hash"].text(), retired["statement"].text(),
            retired["signature"].text())
    }

    /**
     * True when [answer] proves that [binding]'s name, with its genesis, was retired and erased: its handle and
     * genesis hash are the binding's, its statement is the exact text that the app builds ([text], in [zone]), and its
     * signature (strict base64url, 64 bytes) is the registrar's Ed25519 signature of those bytes under [registrarKey].
     * Anything else proves nothing, and the name stays.
     */
    fun proves(answer: UsernameRetiredAnswer, binding: UsernameBinding, registrarKey: String,
               zone: String = UsernameConfiguration.ZONE): Boolean {
        if (!canAsk(binding)) return false
        val text = text(binding.handle, binding.genesisHash, zone)
        if (answer.handle != binding.handle || answer.genesisHash != binding.genesisHash || answer.statement != text) return false
        val signature = answer.signature?.let(UsernameBase64::decode) ?: return false
        return UsernameRegistrar.isSigned(text.toByteArray(Charsets.UTF_8), signature, registrarKey)
    }

    /** The binding can ask for its statement: a lowercase label and a genesis hash of 64 lowercase hex digits. */
    internal fun canAsk(binding: UsernameBinding): Boolean = UsernameTombstone.isLabel(binding.handle) && HEX64.matches(binding.genesisHash)
}

/**
 * One free name per phone per 12 months (registry README, "Claim-slot safety"). The platforms date a phone
 * mark by its UTC month only, and the registry keeps the mark in force while `month(now) <= month(mark) + 12`
 * (`mark_in_force`). So the phone is free again at the first instant of the 13th month after the month of
 * the claim.
 */
object UsernameClaimSlot {
    /** `claims.period_months` of the pinned rules (`rulesHash`). */
    const val PERIOD_MONTHS = 12
    /**
     * The mark is written before the reply, and a phone clock can be a little off. A claim in the last day of
     * a month may be dated in the next month, so the month is read one day later: the date may be late, never
     * early.
     */
    const val MARGIN_MILLIS = 86_400_000L

    /** The first instant (epoch milliseconds) this phone can claim again after a claim at [claimedAt]. */
    fun nextFreeClaim(claimedAt: Long): Long = startOfMonth(monthIndex(claimedAt + MARGIN_MILLIS) + PERIOD_MONTHS + 1)

    /** A UTC month as `year * 12 + (month - 1)`, as the registry counts it. */
    fun monthIndex(millis: Long): Int {
        val date = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)
        return date.year * 12 + date.monthValue - 1
    }

    /** The first instant (epoch milliseconds) of a UTC month index. */
    fun startOfMonth(index: Int): Long =
        LocalDate.of(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}

/** The registry's answer for the wallet's own name ([UsernameRegistry.status]). */
sealed interface UsernameNameStatus {
    /** The name is live, at this head. */
    data class Live(val head: UsernameHead) : UsernameNameStatus
    /** The registry holds the name, and the notice passed its check. */
    data class Held(val hold: UsernameHold) : UsernameNameStatus
    /**
     * The registrar signed the retire of this name: the chain ends in a tombstone of this name that the registrar
     * countersigned ([UsernameTombstone.endsRetired]; the erasure runs at the next epoch), or, after a 404, the
     * registrar's retired statement for this name and genesis ([UsernameRetired.proves]; the epoch erased it).
     * [UsernameRegistry.retire] then forgets the name and signs nothing.
     */
    data object Retired : UsernameNameStatus
    /**
     * The registry answered 404, also when asked with the genesis hash, with no valid retired statement: erased,
     * or never registered there. The answer is not signed, so the name stays on this phone.
     * [UsernameRegistry.retire] forgets it only when this phone saved a retire in an earlier try (its reply was
     * lost, then the epoch erased the name).
     */
    data object NotFound : UsernameNameStatus
}

/**
 * What the @name screen shows for the wallet's own name. Read it with [UsernameStore.nameState]; it changes
 * when the store's state or holds change.
 */
data class UsernameNameState(
    /** The checked hold, or null while the name is live or its state is not known. A hold whose notice fails its check is never set. */
    val hold: UsernameHold? = null,
    /**
     * The date for the retire confirmation ("You can claim a new name after ..."): this phone's next free
     * claim (epoch milliseconds), from the claim that used it. Null when the app knows of no claim that blocks
     * this phone now (a name that came back on a new phone, or a fresh install). Show it as "after" the local
     * day of this instant: that day ends after the instant in every time zone, so the text never names a date
     * too early.
     */
    val nextFreeClaim: Long? = null,
) {
    /** The reason for the gray On hold row. */
    val heldReason: UsernameHoldReason? get() = hold?.reason
    /** Epoch milliseconds. */
    val heldAt: Long? get() = hold?.heldAt
    /** The red Retire Name row shows: a live name, or a `notice` or `terms` hold. An `order` or `sanctions` hold offers no action. */
    val retireAllowed: Boolean get() = hold?.allowsRetire ?: true
}

internal const val UINT32_MAX = 0xFFFF_FFFFL
private val HEX64 = Regex("[0-9a-f]{64}")

/** A JSON string's text, or null for another type. */
internal fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun hexBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
