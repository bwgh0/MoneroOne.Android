package one.monero.moneroone.core.username

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Retire, holds and the next free claim (iOS UsernameRetireTests). Every key here is a PUBLIC TEST key from
 * the oah vectors or the registry's e2e harness; no private key is in this file.
 */
class UsernameRetireTest {
    // oah vectors/records.json "alice-r5-tombstone" (the last record of chains.json "rust-alice-full"), signed by
    // the oah test signer and the oah test registrar key.
    private object Vector {
        const val HANDLE = "alice"
        const val GENESIS = "29d0d6dcd5dd31533dbb692300024144b8045b7898aec31427ca48e7c7eaf607"
        const val OWNER = "858fwkeUyVCX5oAPhj7UT61SKx5KeTJYtPDHaHVj36eUE6ryjp9BWcueXomannj372HvoGVPJhxcqErDzgFfa3iRQkKy1pG"
        const val HEAD_SEQ = 4L
        const val PREV = "247d4c3dc48ffa885bfd40b9b4c1a906de318dc863042240694a9167c16c2d69"
        const val UNSIGNED = "4f4148310405616c69636500000005247d4c3dc48ffa885bfd40b9b4c1a906de318dc863042240694a9167c16c2d69"
        const val RECORD_HASH = "73af89cf03e112cd21a3a564b6e4647362ebec8ddc261a8931b744730985c0a0"
        const val MESSAGE = "oah/tombstone/v1:73af89cf03e112cd21a3a564b6e4647362ebec8ddc261a8931b744730985c0a0"
        const val OWNER_SIGNATURE = "SigV2DdB9mU19jey3EBHevPvW3Ed6ZxXzsbkrnSh1Njr58uD5U3NwmLMQneL4asEhmuwJYPWUPgbx8rxb37RLLqL8CzGR"
        const val RECORD = UNSIGNED +
            "4b78e30aa1f7b2a20d50c6de8036349dd7cdcce6c306e6639996cde5b83dd404a1aae89d0b8e5081156d3e5202cac430b034005548c952ea266143a9738c750a" +
            "0f065e50a7e932b2f1d37ad3893994e96a653464f618c8caa84a6de6d405f292009c5c1438fbc2e48f1a411994c979d24ed14c3163d224b9056f6f653c9d9802"
        const val REGISTRAR_KEY = "49bdfa652580e7d75672be3d708a8a0f9c3e424a3ec08818406fb3925ebb017e"
    }

    // The registry e2e harness log key (crates/e2e/tests/common). Its hold notices are
    // `notice_text("monero.one", "alice", <reason>, START)`, as in crates/e2e/tests/valve.rs, signed with Ed25519.
    private object Hold {
        const val KEY = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c"
        const val HELD_AT = 1_790_000_000_000L
        val signatures = mapOf(
            UsernameHoldReason.ORDER to "uHjjTJ_iIgNcuTxutbFsu4or0Dtx63clrdHhlMYZbTZGnRo_hJz2ZcvkQ1fMuwE9X8gBagJKLjF-c3tRtjfXAg",
            UsernameHoldReason.NOTICE to "GEGPqFRotntJHhibqBHdtjKqJYYBXFK8wztVZzjVMR4vWFo3uvaomNwx5i5p7HwlXOpVVwBEkjz195OrhCQYBg",
            UsernameHoldReason.SANCTIONS to "yC2kUxNKfoz_30-vGW_ua0BkBzvJ61aBYZELjNRhtyPKhibi2JlT5tbCbafNTGRWR6hCQJHb9Nwy4It_nUFSCQ",
            UsernameHoldReason.TERMS to "Bz9CTXRJgvDSnHxaiXjfdwiRO8yfYft0vRY7aX3j1UlbZWRUj1geVrHvaXbjJZwbbGChCpDGr2IJl4K0UEyLBg",
        )
        /** The registrar's countersignature of the vector's tombstone, by this key. */
        const val COUNTERSIGNATURE =
            "5129ec30576fbd2cd69f3680fafa5daed05b8b167748c3ce5e2ef59ce18972ac3f480fc3eb999f5c3151137ede6b984e7c43c20d253fbb2cbfb76de407d93f05"
        fun notice(reason: String, handle: String = "alice", zone: String = "monero.one") =
            "oah-registry/hold/v1\nzone $zone\nhandle $handle\nreason $reason\nheld_at 1790000000\n"
        /** The vector's tombstone as this registrar returns it. */
        val record get() = Vector.RECORD.dropLast(128) + COUNTERSIGNATURE
    }

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun b64(hex: String) = UsernameBase64.encode(bytes(hex))
    private val generic = "The username service couldn't complete the request. Please try again."
    private val unavailable = "This username is unavailable."

    /** The `451 name.held` body of the registry (valve.rs `held_error`). */
    private fun heldJson(reason: UsernameHoldReason, notice: String? = null, signature: String? = null,
                         head: Pair<Long, String>? = Vector.HEAD_SEQ to Vector.PREV, errorReason: String = reason.code,
                         holdReason: String = reason.code): JsonObject = buildJsonObject {
        put("error", buildJsonObject { put("code", "name.held"); put("message", "This name is on hold."); put("reason", errorReason) })
        put("hold", buildJsonObject {
            put("handle", "alice"); put("reason", holdReason); put("held_at", 1_790_000_000)
            put("notice", notice ?: Hold.notice(reason.code))
            put("signature", signature ?: Hold.signatures.getValue(reason))
            put("head", head?.let { (seq, hash) -> buildJsonObject { put("seq", seq); put("record_hash", hash) } } ?: JsonNull)
        })
    }
    private fun heldBody(reason: UsernameHoldReason, notice: String? = null, signature: String? = null,
                         head: Pair<Long, String>? = Vector.HEAD_SEQ to Vector.PREV) = heldJson(reason, notice, signature, head).toString()

    private val now = System.currentTimeMillis()
    private val binding = UsernameBinding("wallet-A", "mainnet", Vector.HANDLE, "Alice", Vector.OWNER, Vector.GENESIS, "record", now)
    /** The registry's lookup of a live alice at the vector's head. */
    private val live = HeldName(emptyList(), handle = "alice", owner = Vector.OWNER, genesisHash = Vector.GENESIS, tombstoned = false,
        headSeq = Vector.HEAD_SEQ, headHash = Vector.PREV)

    private val disk = MemoryUsernames()
    /** Holds alice for wallet-A, claimed on this phone now. */
    private val store = UsernameStore(disk).also { it.accept(binding) }
    private val api = FakeRegistryApi().also { it.store = store }
    private fun client(registrarKey: String = Vector.REGISTRAR_KEY): UsernameRegistry {
        api.registrarKey = registrarKey
        return testRegistry(api, UsernameConfiguration("http://localhost:8787", registrarKey, "c".repeat(64), "test-terms-v1"))
    }
    private val signed = mutableListOf<Pair<String, String>>()
    private val vectorSigner: suspend (String, String) -> String = { message, address -> signed += message to address; Vector.OWNER_SIGNATURE }
    private val noSignature: suspend (String, String) -> String = { _, _ -> fail("Nothing may be signed"); "" }

    private fun retire(client: UsernameRegistry = client(), current: () -> Boolean = { true },
                       sign: suspend (String, String) -> String = vectorSigner) = runBlocking {
        client.retire(store.state.value.binding("wallet-A", "mainnet")!!, store, sign, current)
    }
    private fun failure(block: () -> Unit): UsernameException {
        try { block() } catch (e: UsernameException) { return e }
        fail("Expected a failure"); throw AssertionError()
    }
    private fun ownBinding() = store.state.value.binding("wallet-A", "mainnet")

    // The tombstone

    @Test fun theTombstoneBytesHashAndMessageMatchTheOahVector() {
        val unsigned = UsernameTombstone.unsigned(Vector.HANDLE, Vector.HEAD_SEQ + 1, Vector.PREV)!!
        assertEquals(Vector.UNSIGNED, hex(unsigned))
        assertEquals(Vector.RECORD_HASH, UsernameTombstone.recordHash(unsigned))
        assertEquals(Vector.MESSAGE, UsernameTombstone.signingMessage(unsigned))
        // seq is big-endian: 0x01020304 gives those four bytes after the handle.
        val wide = UsernameTombstone.unsigned("alice", 0x0102_0304L, Vector.PREV)!!
        assertEquals("01020304", hex(wide.copyOfRange(11, 15)))
        assertEquals("ffffffff", hex(UsernameTombstone.unsigned("alice", 0xFFFF_FFFFL, Vector.PREV)!!.copyOfRange(11, 15)))
        // Values the registry would refuse are never signed.
        assertNull(UsernameTombstone.unsigned("alice", 0, Vector.PREV))
        assertNull(UsernameTombstone.unsigned("alice", 0x1_0000_0000L, Vector.PREV))
        assertNull(UsernameTombstone.unsigned("alice", 5, Vector.PREV.uppercase()))
        assertNull(UsernameTombstone.unsigned("alice", 5, Vector.PREV.dropLast(2)))
        for (bad in listOf("Alice", "ab", "abcdefghijklmnop", "a__b", "1abc", "al-ce", "alicé", "")) {
            assertNull(bad, UsernameTombstone.unsigned(bad, 5, Vector.PREV))
        }
        // A name that the blocked list holds today can still retire.
        assertNotNull(UsernameTombstone.unsigned("support", 1, Vector.PREV))
    }

    @Test fun theRegistrarCountersignatureIsChecked() {
        val unsigned = bytes(Vector.UNSIGNED)
        val record = bytes(Vector.RECORD)
        assertTrue(UsernameTombstone.isCountersigned(record, unsigned, Vector.REGISTRAR_KEY))
        assertFalse(UsernameTombstone.isCountersigned(record, unsigned, Hold.KEY))
        val tampered = record.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFalse(UsernameTombstone.isCountersigned(tampered, unsigned, Vector.REGISTRAR_KEY))
        val other = UsernameTombstone.unsigned("alice", 6, Vector.RECORD_HASH)!!
        assertFalse(UsernameTombstone.isCountersigned(record, other, Vector.REGISTRAR_KEY))
        assertFalse(UsernameTombstone.isCountersigned(record.copyOf(record.size - 1), unsigned, Vector.REGISTRAR_KEY))
        assertTrue(UsernameTombstone.isCountersigned(bytes(Hold.record), unsigned, Hold.KEY))
        assertFalse(UsernameTombstone.isCountersigned(record, unsigned, Vector.REGISTRAR_KEY.uppercase()))
    }

    @Test fun base64UrlDecodeIsStrict() {
        assertArrayEquals(byteArrayOf(0xfb.toByte(), 0xff.toByte()), UsernameBase64.decode("-_8"))
        assertArrayEquals(ByteArray(0), UsernameBase64.decode(""))
        for (bad in listOf("-_8=", "+/8", "AB", "A", "a b", "QUJD\n")) assertNull(bad, UsernameBase64.decode(bad))
    }

    // The hold notice

    @Test fun eachSignedHoldNoticeGivesItsReasonDateAndHead() {
        for (reason in UsernameHoldReason.entries) {
            val hold = UsernameHold.parse(heldBody(reason), "alice", Hold.KEY)!!
            assertEquals(reason, hold.reason)
            assertEquals("alice", hold.handle)
            assertEquals(Hold.HELD_AT, hold.heldAt)
            assertEquals(UsernameHead(4, Vector.PREV), hold.head)
            assertEquals(reason.code, reason == UsernameHoldReason.NOTICE || reason == UsernameHoldReason.TERMS, hold.allowsRetire)
        }
        // A hold with no head shows its reason and offers no retire.
        val headless = UsernameHold.parse(heldBody(UsernameHoldReason.NOTICE, head = null), "alice", Hold.KEY)!!
        assertNull(headless.head)
        assertFalse(headless.allowsRetire)
        // The reason comes from the signed notice, not the unsigned fields.
        val mixed = heldJson(UsernameHoldReason.ORDER, errorReason = "notice", holdReason = "notice")
        assertEquals(UsernameHoldReason.ORDER, UsernameHold.parse(mixed, "alice", Hold.KEY)?.reason)
    }

    @Test fun aHoldNoticeThatFailsACheckGivesNoHold() {
        val order = Hold.signatures.getValue(UsernameHoldReason.ORDER)
        val notHeld = heldJson(UsernameHoldReason.ORDER).let { body ->
            JsonObject(body + ("error" to buildJsonObject { put("code", "write.refused") }))
        }
        val rows: List<Triple<String, JsonElement, Pair<String, String>>> = listOf(
            Triple("another registrar key", heldJson(UsernameHoldReason.ORDER), "alice" to Vector.REGISTRAR_KEY),
            Triple("the reason changed", heldJson(UsernameHoldReason.NOTICE, signature = order), "alice" to Hold.KEY),
            Triple("another notice's signature", heldJson(UsernameHoldReason.ORDER, signature = Hold.signatures[UsernameHoldReason.TERMS]), "alice" to Hold.KEY),
            Triple("another name asked", heldJson(UsernameHoldReason.ORDER), "bobby" to Hold.KEY),
            Triple("another name in the notice", heldJson(UsernameHoldReason.ORDER, notice = Hold.notice("order", handle = "bobby")), "bobby" to Hold.KEY),
            Triple("another zone", heldJson(UsernameHoldReason.ORDER, notice = Hold.notice("order", zone = "example.com")), "alice" to Hold.KEY),
            Triple("an unknown reason", heldJson(UsernameHoldReason.ORDER, notice = Hold.notice("legal")), "alice" to Hold.KEY),
            Triple("a padded signature", heldJson(UsernameHoldReason.ORDER, signature = "$order=="), "alice" to Hold.KEY),
            Triple("a standard base64 signature", heldJson(UsernameHoldReason.ORDER, signature = order.replace("-", "+")), "alice" to Hold.KEY),
            Triple("a short signature", heldJson(UsernameHoldReason.ORDER, signature = order.dropLast(4)), "alice" to Hold.KEY),
            Triple("not name.held", notHeld, "alice" to Hold.KEY),
            Triple("no hold object", buildJsonObject { put("error", buildJsonObject { put("code", "name.held") }) }, "alice" to Hold.KEY),
            Triple("not an object", JsonPrimitive("name.held"), "alice" to Hold.KEY),
        )
        for ((name, body, asked) in rows) assertNull(name, UsernameHold.parse(body, asked.first, asked.second))
        assertNull(UsernameHold.parse("not json", "alice", Hold.KEY))
    }

    @Test fun aHeadNeedsAWholeSeqAndALowercaseHash() {
        fun head(seq: JsonElement?, hash: String? = Vector.PREV) = buildJsonObject {
            seq?.let { put("seq", it) }
            hash?.let { put("record_hash", it) }
        }
        assertEquals(0L, UsernameHead.of(head(JsonPrimitive(0)))?.seq)
        assertEquals(0xFFFF_FFFFL, UsernameHead.of(head(JsonPrimitive(4_294_967_295L)))?.seq)
        for (bad in listOf(head(JsonPrimitive(-1)), head(JsonPrimitive(4_294_967_296L)), head(JsonPrimitive(1.5)), head(JsonPrimitive(true)),
                head(JsonPrimitive("4")), head(JsonPrimitive(4), Vector.PREV.uppercase()), head(JsonPrimitive(4), null), head(null), JsonNull)) {
            assertNull(bad.toString(), UsernameHead.of(bad))
        }
    }

    @Test fun theRegistryAnswerGivesAHoldOnlyForACheckedLookupOrRecordRefusal() {
        val checked = UsernameRegistryAnswer.failure(451, heldBody(UsernameHoldReason.ORDER), "alice", Hold.KEY)
        assertEquals(UsernameException.HELD, checked.code)
        assertEquals(UsernameHoldReason.ORDER, checked.hold?.reason)
        assertEquals(unavailable, checked.message)
        // A notice that fails its check, a hold for a request that names no handle, or another status: generic, no reason.
        val failed = listOf(
            UsernameRegistryAnswer.failure(451, heldBody(UsernameHoldReason.ORDER, signature = Hold.signatures[UsernameHoldReason.NOTICE]), "alice", Hold.KEY),
            UsernameRegistryAnswer.failure(451, heldBody(UsernameHoldReason.ORDER), null, Hold.KEY),
            UsernameRegistryAnswer.failure(403, heldBody(UsernameHoldReason.ORDER), "alice", Hold.KEY),
        )
        for (e in failed) {
            assertEquals("server", e.code); assertNull(e.hold); assertEquals(generic, e.message)
        }
        // Other codes stay as the registry sends them.
        val outdated = UsernameRegistryAnswer.failure(400, """{"error":{"code":"terms.outdated","message":"These Username Terms are out of date."}}""", null, Hold.KEY)
        assertEquals(UsernameException.TERMS_OUTDATED, outdated.code)
        assertEquals(generic, outdated.message)
        assertEquals(UsernameException.RETIRED,
            UsernameRegistryAnswer.failure(409, """{"error":{"code":"chain.record_after_tombstone"}}""", "alice", Hold.KEY).code)
        assertEquals("handle.not_found", UsernameRegistryAnswer.failure(404, """{"error":{"code":"handle.not_found"}}""", "alice", Hold.KEY).code)
        assertEquals("server", UsernameRegistryAnswer.failure(502, "<html>Bad gateway</html>", "alice", Hold.KEY).code)
    }

    // The next free claim

    private fun day(y: Int, m: Int, d: Int, h: Int = 0) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun theNextFreeClaimFollowsTheRegistryMonthRule() {
        // The registry's month_of vectors (gates.rs).
        assertEquals(2026 * 12 + 9, UsernameClaimSlot.monthIndex(1_792_627_200_000L))
        assertEquals(1970 * 12, UsernameClaimSlot.monthIndex(0))
        assertEquals(2025 * 12 + 11, UsernameClaimSlot.monthIndex(1_767_225_599_000L))
        assertEquals(2026 * 12, UsernameClaimSlot.monthIndex(1_767_225_600_000L))
        assertEquals(2024 * 12 + 1, UsernameClaimSlot.monthIndex(1_709_164_800_000L))
        // e2e claims.rs: a September 2026 claim (START) blocks the phone through September 2027 (START + 370
        // days) and frees it in October (START + 380).
        val start = 1_790_000_000_000L
        val free = UsernameClaimSlot.nextFreeClaim(start)
        assertEquals(day(2027, 10, 1), free)
        assertTrue(free > start + 370 * 86_400_000L)
        assertTrue(free <= start + 380 * 86_400_000L)
        // The spec's example: a claim on November 2, 2026.
        assertEquals(day(2027, 12, 1), UsernameClaimSlot.nextFreeClaim(day(2026, 11, 2, 12)))
        // The last day of a month rounds up: the mark may be dated next month.
        assertEquals(day(2028, 1, 1), UsernameClaimSlot.nextFreeClaim(day(2026, 11, 30, 12)))
        assertEquals(day(2028, 2, 1), UsernameClaimSlot.nextFreeClaim(day(2026, 12, 31, 23)))
        assertEquals(day(2028, 3, 1), UsernameClaimSlot.nextFreeClaim(day(2027, 2, 27, 0)))
        // Never early: at any claim time, the registry's mark_in_force still holds one millisecond before the
        // date, for a mark from the claim's month or the month after it (a write that crossed midnight).
        var claim = day(2026, 1, 1)
        while (claim < day(2028, 1, 1)) {
            val date = UsernameClaimSlot.nextFreeClaim(claim)
            val mark = UsernameClaimSlot.monthIndex(claim)
            for (m in setOf(mark, UsernameClaimSlot.monthIndex(claim + 3_600_000L))) {
                assertTrue("$claim", UsernameClaimSlot.monthIndex(date - 1) >= m + 12)
                assertTrue("$claim", UsernameClaimSlot.monthIndex(date) > m + 12)
            }
            // At most one month late.
            assertTrue("$claim", UsernameClaimSlot.monthIndex(date) <= mark + 14)
            claim += 11 * 3_600_000L + 17_000L
        }
    }

    @Test fun aClaimSavesTheRegistryDateAndARestoreSavesNone() {
        val claimed = binding.copy(claimedAt = 1_793_620_800_000L) // 2026-11-02 12:00 UTC
        val fresh = UsernameStore(MemoryUsernames())
        fresh.accept(claimed)
        assertEquals(1_827_619_200_000L, fresh.state.value.nextFreeClaim) // 2027-12-01 UTC
        val restored = UsernameStore(MemoryUsernames())
        restored.accept(claimed, usesFreeClaim = false)
        assertNull(restored.state.value.nextFreeClaim)
        assertNull(restored.inviteHiddenUntil())
        assertNull(restored.nameState(claimed).nextFreeClaim)
        // A later saved date stays.
        val later = UsernameStore(MemoryUsernames()).apply { accept(claimed) }
        later.accept(claimed.copy(walletKey = "wallet-B", genesisHash = "other", claimedAt = 1_700_000_000_000L))
        assertEquals(1_827_619_200_000L, later.state.value.nextFreeClaim)
        // Home shows the invite again at the date.
        assertEquals(1_827_619_200_000L, fresh.inviteHiddenUntil(1_827_619_199_999L))
        assertNull(fresh.inviteHiddenUntil(1_827_619_200_000L))
        assertTrue(fresh.state.value.hasLocalClaimSlot(1_827_619_200_000L))
    }

    // Retire

    @Test fun retireSignsTheTombstoneAtTheHeadAndForgetsTheName() {
        store.savePending(PendingUsernameClaim("wallet-B", "mainnet", "bobby", "Bobby", "b", "", "", "", "", "", "", ""))
        val free = store.state.value.nextFreeClaim!!
        assertTrue(store.nameState(ownBinding()!!).retireAllowed)
        api.found["alice"] = live
        api.appendReply = "alice" to b64(Vector.RECORD)
        retire()
        assertEquals(listOf("lookup alice", "append alice"), api.calls)
        assertEquals(listOf(Vector.MESSAGE to Vector.OWNER), signed)
        assertEquals(listOf(Triple("alice", b64(Vector.UNSIGNED), listOf(Vector.OWNER_SIGNATURE))), api.appended)
        // The name is gone; another wallet's saved claim and the free claim date stay, also after a relaunch.
        for (state in listOf(store.state.value, UsernameStore(disk).state.value)) {
            assertNull(state.binding("wallet-A", "mainnet"))
            assertEquals("bobby", state.pending?.handle)
            assertEquals(free, state.nextFreeClaim)
            assertFalse(state.hasLocalClaimSlot())
        }
        assertEquals(free, store.inviteHiddenUntil())
    }

    @Test fun retireForgetsTheNamesOwnSavedClaim() {
        store.savePending(PendingUsernameClaim("wallet-A", "mainnet", "alice", "Alice", Vector.OWNER, "", "", Vector.GENESIS, "", "", "", ""))
        api.found["alice"] = live
        api.appendReply = "alice" to b64(Vector.RECORD)
        retire()
        assertNull(store.state.value.pending)
        assertTrue(store.state.value.bindings.isEmpty())
    }

    @Test fun aNoticeOrTermsHoldRetiresAtTheHoldsHead() {
        for (reason in listOf(UsernameHoldReason.NOTICE, UsernameHoldReason.TERMS)) {
            val store = UsernameStore(MemoryUsernames()).also { it.accept(binding) }
            val api = FakeRegistryApi().apply {
                registrarKey = Hold.KEY
                refusals["alice"] = 451 to heldBody(reason)
                appendReply = "alice" to b64(Hold.record)
            }
            runBlocking {
                testRegistry(api, UsernameConfiguration("http://localhost:8787", Hold.KEY, "c".repeat(64), "test-terms-v1"))
                    .retire(binding, store, vectorSigner) { true }
            }
            assertEquals(reason.code, b64(Vector.UNSIGNED), api.appended.single().second)
            assertTrue(reason.code, store.state.value.bindings.isEmpty())
            assertTrue(reason.code, store.holds.value.isEmpty())
        }
    }

    @Test fun anOrderOrSanctionsHoldOffersNoRetireAndSignsNothing() {
        for (reason in listOf(UsernameHoldReason.ORDER, UsernameHoldReason.SANCTIONS)) {
            api.calls.clear()
            api.refusals["alice"] = 451 to heldBody(reason)
            val e = failure { retire(client(Hold.KEY), sign = noSignature) }
            assertEquals(UsernameException.HELD, e.code)
            assertEquals(reason, e.hold?.reason)
            assertEquals(listOf("lookup alice"), api.calls)
            // The name and the hold stay; the @name screen shows the hold.
            val state = store.nameState(ownBinding()!!)
            assertEquals(reason, state.heldReason)
            assertEquals(Hold.HELD_AT, state.heldAt)
            assertFalse(state.retireAllowed)
        }
    }

    @Test fun aHoldThatTightensBeforeThePostKeepsTheName() {
        api.found["alice"] = live
        api.appendRefusal = 451 to heldBody(UsernameHoldReason.SANCTIONS)
        val e = failure { retire(client(Hold.KEY)) }
        assertEquals(UsernameHoldReason.SANCTIONS, e.hold?.reason)
        assertNotNull(ownBinding())
        assertFalse(store.nameState(binding).retireAllowed)
    }

    @Test fun aHoldWithABadSignatureGivesTheGenericErrorAndNoReason() {
        api.refusals["alice"] = 451 to heldBody(UsernameHoldReason.NOTICE, signature = Hold.signatures[UsernameHoldReason.ORDER])
        val e = failure { retire(client(Hold.KEY), sign = noSignature) }
        assertEquals(generic, e.message)
        assertNull(e.hold)
        assertTrue(store.holds.value.isEmpty())
        assertNotNull(ownBinding())
        assertTrue(store.nameState(binding).retireAllowed)
    }

    @Test fun aReplyThatFailsItsCheckKeepsTheNameAndARetryFinishes() {
        val tampered = bytes(Vector.RECORD).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        api.found["alice"] = live
        api.appendReply = "alice" to UsernameBase64.encode(tampered)
        assertEquals("verification", failure { retire() }.code)
        assertNotNull(ownBinding())
        // Another name in the reply, or a record that is not strict base64url, fails the same way.
        for (reply in listOf("alicf" to b64(Vector.RECORD), "alice" to b64(Vector.RECORD) + "=")) {
            api.appendReply = reply
            assertEquals(reply.toString(), "verification", failure { retire() }.code)
            assertNotNull(ownBinding())
        }
        // The retire landed: the next try finds the tombstone and signs nothing.
        api.found["alice"] = live.copy(tombstoned = true, owner = null)
        api.calls.clear()
        retire(sign = noSignature)
        assertEquals(listOf("lookup alice"), api.calls)
        assertTrue(store.state.value.bindings.isEmpty())
    }

    @Test fun aNameThatIsGoneOrRetiredAlreadyIsForgotten() {
        // Erased at an epoch.
        val erased = UsernameStore(MemoryUsernames()).also { it.accept(binding) }
        runBlocking { testRegistry(FakeRegistryApi()).retire(binding, erased, noSignature) { true } }
        assertTrue(erased.state.value.bindings.isEmpty())
        assertNotNull(erased.state.value.nextFreeClaim)
        // The lookup ran before an earlier retire landed.
        api.found["alice"] = live
        api.appendRefusal = 409 to """{"error":{"code":"chain.record_after_tombstone"}}"""
        retire()
        assertTrue(store.state.value.bindings.isEmpty())
        assertNotNull(store.state.value.nextFreeClaim)
    }

    @Test fun retireStopsOnALookupThatIsNotThisName() {
        val rows = listOf(
            "another genesis" to live.copy(genesisHash = Vector.PREV),
            "another owner" to live.copy(owner = "another-address"),
            "another name" to live.copy(handle = "alicf"),
            "no head" to live.copy(headSeq = null, headHash = null),
            "a genesis head that is not ours" to live.copy(headSeq = 0, headHash = Vector.PREV),
            "an uppercase head" to live.copy(headHash = Vector.PREV.uppercase()),
            "no tombstone flag" to live.copy(tombstoned = null),
        )
        for ((name, reply) in rows) {
            api.found["alice"] = reply
            assertEquals(name, "verification", failure { retire(sign = noSignature) }.code)
            assertNotNull(name, ownBinding())
        }
        // A seq that cannot grow signs nothing.
        api.found["alice"] = live.copy(headSeq = 0xFFFF_FFFFL)
        assertEquals("verification", failure { retire(sign = noSignature) }.code)
    }

    @Test fun aWalletChangeStopsTheRetireBeforeItPosts() {
        api.found["alice"] = live
        var calls = 0
        assertEquals("wallet_changed", failure { retire(current = { ++calls < 3 }) }.code)
        assertEquals(listOf("lookup alice"), api.calls)
        assertNotNull(ownBinding())
        // Another wallet's binding, or a binding the store no longer holds, is not this wallet's name.
        assertEquals("wallet_changed", failure {
            runBlocking { client().retire(binding.copy(walletKey = "wallet-B"), store, noSignature) { true } }
        }.code)
        assertEquals("wallet_changed", failure {
            runBlocking { client().retire(binding.copy(walletKey = ""), store, noSignature) { true } }
        }.code)
    }

    @Test fun aRunningClaimBlocksARetire() {
        api.found["alice"] = live
        runBlocking { store.claimMutex.lock() }
        try { assertEquals("pending", failure { retire(sign = noSignature) }.code) } finally { store.claimMutex.unlock() }
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aSignatureThatIsNotSigV2IsNeverPosted() {
        api.found["alice"] = live
        assertEquals("verification", failure { retire(sign = { _, _ -> "Signature" }) }.code)
        assertTrue(api.appended.isEmpty())
        assertNotNull(ownBinding())
    }

    // The state of the own name

    @Test fun statusSavesAHoldAndALiveAnswerClearsIt() = runBlocking {
        api.refusals["alice"] = 451 to heldBody(UsernameHoldReason.ORDER)
        val held = client(Hold.KEY).status(binding, store)
        assertEquals(UsernameHoldReason.ORDER, (held as UsernameNameStatus.Held).hold.reason)
        assertEquals(UsernameHoldReason.ORDER, store.nameState(binding).heldReason)
        api.refusals.clear()
        api.found["alice"] = live
        assertEquals(UsernameNameStatus.Live(UsernameHead(4, Vector.PREV)), client(Hold.KEY).status(binding, store))
        assertNull(store.nameState(binding).hold)
        assertTrue(store.nameState(binding).retireAllowed)
        // Retired and gone.
        api.found["alice"] = live.copy(tombstoned = true, owner = null)
        assertEquals(UsernameNameStatus.Retired, client(Hold.KEY).status(binding, store))
        api.found.clear()
        assertEquals(UsernameNameStatus.NotFound, client(Hold.KEY).status(binding, store))
        // Nothing else changed: status signs and saves nothing.
        assertNotNull(ownBinding())
        assertTrue(api.appended.isEmpty())
    }

    @Test fun anUnreachableRegistryKeepsTheKnownHold() = runBlocking {
        api.refusals["alice"] = 451 to heldBody(UsernameHoldReason.TERMS)
        client(Hold.KEY).status(binding, store)
        api.unreachable = true
        try { client(Hold.KEY).status(binding, store); fail("Answered while unreachable") } catch (_: java.io.IOException) { }
        assertEquals(UsernameHoldReason.TERMS, store.nameState(binding).heldReason)
        assertTrue(store.nameState(binding).retireAllowed)
    }

    @Test fun theNameStateGivesTheConfirmationDate() {
        val state = store.nameState(binding, now)
        assertEquals(UsernameClaimSlot.nextFreeClaim(now), state.nextFreeClaim)
        assertNull(state.heldReason)
        assertNull(state.heldAt)
        assertTrue(state.retireAllowed)
        // Past the date, the phone can claim again and the confirmation names no date.
        assertNull(store.nameState(binding, state.nextFreeClaim!!).nextFreeClaim)
    }

    @Test fun aHeldNameKeepsTheSavedClaimInClaimAndRecheck() {
        val store = UsernameStore(MemoryUsernames())
        val config = UsernameConfiguration("http://localhost:8787", Hold.KEY, "c".repeat(64), "test-terms-v1")
        val pending = PendingUsernameClaim("wallet-A", "mainnet", "alice", "Alice", Vector.OWNER, "0".repeat(64), "request",
            Vector.GENESIS, "signature", Hold.KEY, "rules", config.rulesHash)
        store.savePending(pending)
        for (wallet in listOf("wallet-A", "wallet-B")) {
            val api = FakeRegistryApi().apply {
                registrarKey = Hold.KEY
                refusals["alice"] = 451 to heldBody(UsernameHoldReason.NOTICE, head = 0L to Vector.GENESIS)
            }
            val e = failure {
                runBlocking {
                    testRegistry(api, config).claim(if (wallet == "wallet-A") "Alice" else "Bobby", wallet, "mainnet", store,
                        makeSubaddress = { fail("No new address"); "" }, sign = noSignature,
                        claimKey = { fail("No claim key"); "" }, ownsSubaddress = { fail("No wallet check"); false },
                        isCurrentWallet = { true })
                }
            }
            // The same name shows the held (taken) text; another name waits for the saved claim.
            assertEquals(wallet, if (wallet == "wallet-A") UsernameException.HELD else "pending", e.code)
            assertEquals(wallet, if (wallet == "wallet-A") unavailable else UsernameException("pending").message, e.message)
            assertEquals(wallet, listOf("lookup alice"), api.calls)
            assertEquals(wallet, pending, store.state.value.pending)
            assertEquals(wallet, UsernameHoldReason.NOTICE, store.holds.value.values.single().reason)
        }
    }

    @Test fun aFreeNameLookupThatIsHeldStopsTheClaimBeforeANonce() {
        val store = UsernameStore(MemoryUsernames())
        val api = FakeRegistryApi().apply {
            registrarKey = Hold.KEY
            refusals["alice"] = 451 to heldBody(UsernameHoldReason.ORDER)
        }
        val config = UsernameConfiguration("http://localhost:8787", Hold.KEY, "c".repeat(64), "test-terms-v1")
        val e = failure {
            runBlocking {
                testRegistry(api, config).claim("alice", "wallet-A", "mainnet", store,
                    makeSubaddress = { fail("No new address"); "" }, sign = noSignature,
                    claimKey = { fail("No claim key"); "" }, ownsSubaddress = { fail("No wallet check"); false },
                    isCurrentWallet = { true })
            }
        }
        assertEquals(UsernameHoldReason.ORDER, e.hold?.reason)
        assertEquals(unavailable, e.message)
        assertEquals(listOf("info", "lookup alice"), api.calls)
        assertNull(store.state.value.pending)
    }
}
