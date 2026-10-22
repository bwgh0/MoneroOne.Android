package one.monero.moneroone.core.openalias

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import one.monero.moneroone.core.wallet.FakeSharedPreferences
import one.monero.moneroone.core.wallet.SendFlow
import one.monero.moneroone.core.wallet.SendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec section 8.1: classification, TXT decode, OA1 parse and select, sanitize, answer mapping, pins. */
class OpenAliasTest {

    // ---------- Fixtures ----------

    private val getmoneroXmr =
        "888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H"
    private val openaliasXmr =
        "46BeWrHpwXmHDpDEUmZBWZfoQpdc6HaERCNmx1pEYL2rAcuwufPN9rXHHtyUA4QVy66qeFQkn6sfK8aHYjA3jk3o1Bv16em"

    /** Valid on testnet only (the fake check below). */
    private val testnetAddress = "9" + "t".repeat(94)
    private val sendFlow = SendFlow("wallet-1")

    private val getmoneroRecords = listOf(
        "oa1:btc recipient_address=1KTexdemPdxSBcG55heUuTjDRYqbC5ZL8H; recipient_name=Monero Development; tx_description=Donation to Monero Core Team;",
        "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=Monero Development; tx_description=Donation to Monero Core Team;",
    )

    private val openaliasRecords = listOf(
        "oa1:xmr recipient_address=$openaliasXmr; recipient_name=Monero Development; tx_description=Donation to Monero Core Team;",
        "v=spf1 include:spf.improvmx.com ~all",
        "oa1:btc recipient_address=1FhnVJi2V1k4MqXm2nHoEbY5LV7FPai7bb; recipient_name=Monero Development; tx_description=Donation to Monero Core Team;",
    )

    /** Fixture addresses are valid on mainnet; one that starts with 9 is valid on testnet only. */
    private val check: (String) -> AddressCheck = { address ->
        when {
            address.startsWith("9") && address.length == 95 -> AddressCheck.OTHER_NETWORK
            (address.startsWith("4") || address.startsWith("8")) && address.length in listOf(95, 106) -> AddressCheck.VALID
            else -> AddressCheck.INVALID
        }
    }

    /** TXT RDATA as the kit returns it: hex of length-prefixed character-strings. */
    private fun rdata(vararg strings: String): String {
        val bytes = mutableListOf<Byte>()
        for (s in strings) {
            val b = s.toByteArray(Charsets.UTF_8)
            require(b.size <= 255)
            bytes += b.size.toByte()
            bytes += b.toList()
        }
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** One record of any length, split into 255-byte strings as DNS servers do. */
    private fun record(text: String): String {
        val b = text.toByteArray(Charsets.UTF_8)
        val chunks = b.toList().chunked(255).map { String(it.toByteArray(), Charsets.UTF_8) }
        return rdata(*chunks.toTypedArray())
    }

    private fun okJson(
        records: List<String>,
        secure: Boolean = true,
        bogus: Boolean = false,
        rcode: Int = 0,
        havedata: Boolean = records.isNotEmpty(),
        nxdomain: Boolean = false,
    ): String {
        val list = records.joinToString(",") { "\"$it\"" }
        return """{"status":"ok","rcode":$rcode,"havedata":$havedata,"nxdomain":$nxdomain,"secure":$secure,"bogus":$bogus,"why_bogus":"","records":[$list]}"""
    }

    private fun resolved(address: String, name: String?, dnssec: DnssecStatus, description: String? = null) =
        OpenAliasOutcome.Resolved(OpenAliasResult(address, name, dnssec, description))

    private val donation = "Donation to Monero Core Team"

    private fun failed(failure: OpenAliasFailure) = OpenAliasOutcome.Failed(failure)

    // ---------- 4.1 Classification ----------

    private fun assertName(input: String, query: String, display: String) {
        assertEquals(input, RecipientInput.Name(OpenAliasName(display = display, query = query)), OpenAlias.classify(input))
    }

    @Test
    fun usernameFormsUseTheSignaturePath() {
        for (input in listOf("@Joe", "joe@monero.one", "joe.monero.one")) {
            val result = OpenAlias.classify(input) as RecipientInput.Name
            assertEquals("@joe", result.name.display)
            assertTrue(result.name.isUsername)
        }
        assertEquals("@work.joe", (OpenAlias.classify("@work.Joe") as RecipientInput.Name).name.display)
        for (input in listOf("@jo", "@abcdefghijklmnop", "@joe__smith", "@jóe", "@@joe", "@joe/path")) {
            assertEquals(RecipientInput.InvalidName, OpenAlias.classify(input))
        }
    }

    @Test
    fun classifyNames() {
        assertName("donate@getmonero.org", "donate.getmonero.org", "donate@getmonero.org")
        assertName(" Donate@GetMonero.org ", "donate.getmonero.org", "donate@getmonero.org")
        assertName("donate.getmonero.org", "donate.getmonero.org", "donate.getmonero.org")
        assertName("openalias.org.", "openalias.org", "openalias.org")
        assertName("_pay.example.com", "_pay.example.com", "_pay.example.com")
        assertName("pay.xn--mnchen-3ya.de", "pay.xn--mnchen-3ya.de", "pay.xn--mnchen-3ya.de")
        assertName("pay.example.xn--p1ai", "pay.example.xn--p1ai", "pay.example.xn--p1ai")
    }

    @Test
    fun classifyAddresses() {
        assertEquals(RecipientInput.Address(getmoneroXmr), OpenAlias.classify(getmoneroXmr))
        val link = "monero:$openaliasXmr?tx_amount=1"
        assertEquals(RecipientInput.Address(link), OpenAlias.classify(link))
        assertEquals(RecipientInput.Address("joe"), OpenAlias.classify("joe"))
    }

    @Test
    fun classifyEmpty() {
        assertEquals(RecipientInput.Empty, OpenAlias.classify(""))
        assertEquals(RecipientInput.Empty, OpenAlias.classify("  \n "))
    }

    @Test
    fun classifyReserved() {
        for (input in listOf("monero.one", "a.b.monero.one", "MONERO.ONE")) {
            assertEquals(input, RecipientInput.Reserved, OpenAlias.classify(input))
        }
    }

    @Test
    fun classifyOperatorNames() {
        val donate = RecipientInput.Name(OpenAliasName(display = "donate@monero.one", query = "donate.monero.one"))
        for (input in listOf("donate@monero.one", " DONATE@Monero.One ", "donate@monero.one.")) {
            assertEquals(input, donate, OpenAlias.classify(input))
        }
        // Only the name@monero.one form, and only the names on the list.
        for (input in listOf("donate.monero.one", "donate@pay.monero.one", "@donate")) {
            assertEquals(input, RecipientInput.Reserved, OpenAlias.classify(input))
        }
    }

    @Test
    fun classifyInvalidNames() {
        val longLabel = "a".repeat(64) + ".com"
        // 63 + 1 + 63 + 1 + 63 + 1 + 58 + 4 = 254 characters.
        val longName = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com"
        assertEquals(254, longName.length)
        val invalid = listOf(
            "@ab.org", "joe@", "a@b@c.com",
            "dоnate@getmonero.org", // Cyrillic "о"
            "-pay.example.com", "a..b.com", "pay.example.123",
            "pay.onion", "joe@localhost",
            "donate.getmonero.org:443", "pay.example.com/x",
            longLabel, longName,
        )
        for (input in invalid) {
            assertEquals(input, RecipientInput.InvalidName, OpenAlias.classify(input))
        }
    }

    @Test
    fun classifyLengthLimitsPassAtTheBoundary() {
        val label63 = "a".repeat(63) + ".com"
        assertTrue(OpenAlias.classify(label63) is RecipientInput.Name)
        val name253 = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com"
        assertEquals(253, name253.length)
        assertTrue(OpenAlias.classify(name253) is RecipientInput.Name)
    }

    // ---------- 4.2 TXT decode ----------

    @Test
    fun decodeOneString() {
        assertEquals("oa1:xmr a=b", OpenAlias.decodeTxt(rdata("oa1:xmr a=b")))
    }

    @Test
    fun decodeTwoStringsJoined() {
        assertEquals("oa1:xmr recipient_address=abc", OpenAlias.decodeTxt(rdata("oa1:xmr recipient_", "address=abc")))
    }

    @Test
    fun decodeLengthPastTheEndIsDropped() {
        assertNull(OpenAlias.decodeTxt("05616263"))
    }

    @Test
    fun decodeEmptyRdataIsDropped() {
        assertNull(OpenAlias.decodeTxt(""))
    }

    @Test
    fun decodeBadUtf8IsDropped() {
        assertNull(OpenAlias.decodeTxt("01ff"))
    }

    @Test
    fun decodeBadHexIsDropped() {
        assertNull(OpenAlias.decodeTxt("0g61"))
        assertNull(OpenAlias.decodeTxt("016"))
    }

    // ---------- 4.3 and 4.4 Parse and select ----------

    private fun select(records: List<String>, dnssec: DnssecStatus = DnssecStatus.VALID) =
        OpenAlias.select(records, dnssec, check)

    @Test
    fun getmoneroFixture() {
        assertEquals(resolved(getmoneroXmr, "Monero Development", DnssecStatus.VALID, donation), select(getmoneroRecords))
    }

    @Test
    fun openaliasFixture() {
        assertEquals(
            resolved(openaliasXmr, "Monero Development", DnssecStatus.OFF, donation),
            select(openaliasRecords, DnssecStatus.OFF)
        )
    }

    @Test
    fun capitalPrefixIsAccepted() {
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID), select(listOf("OA1:XMR recipient_address=$getmoneroXmr;")))
    }

    @Test
    fun longerPrefixIsSkipped() {
        assertEquals(failed(OpenAliasFailure.NO_RECORD), select(listOf("oa1:xmrfoo recipient_address=$getmoneroXmr;")))
    }

    @Test
    fun quotedValueWithSemicolonAndEscapedQuote() {
        val parsed = OpenAlias.parseOa1("oa1:xmr recipient_address=$getmoneroXmr; recipient_name=\"Joe; \\\"The\\\" Dev\";")
        assertEquals(listOf(getmoneroXmr), parsed?.addresses)
        assertEquals("Joe; \"The\" Dev", parsed?.recipientName)
    }

    @Test
    fun txDescription() {
        val first = "oa1:xmr recipient_address=$getmoneroXmr; tx_description=Coffee; tx_description=Tea;"
        assertEquals("Coffee", OpenAlias.parseOa1(first)?.txDescription)
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID, "Coffee"), select(listOf(first)))

        // Sanitized like the name.
        val hidden = "oa1:xmr recipient_address=$getmoneroXmr; tx_description=Cof\u202Efee\n for  you;"
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID, "Coffee for you"), select(listOf(hidden)))

        // Records that disagree give no description; a record without one does not count.
        val second = "oa1:xmr recipient_address=$getmoneroXmr; tx_description=Lunch;"
        val none = "oa1:xmr recipient_address=$getmoneroXmr;"
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID), select(listOf(first, second)))
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID, "Coffee"), select(listOf(first, none)))
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID), select(listOf(none)))
    }

    @Test
    fun aNameThatSanitizesToNothingDoesNotCount() {
        val records = listOf(
            "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=Joe;",
            "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=\u200B;",
        )
        assertEquals(resolved(getmoneroXmr, "Joe", DnssecStatus.VALID), select(records))
    }

    @Test
    fun missingClosingQuoteSkipsTheRecord() {
        val text = "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=\"Joe;"
        assertNull(OpenAlias.parseOa1(text))
        assertEquals(failed(OpenAliasFailure.NO_RECORD), select(listOf(text)))
    }

    @Test
    fun twoRecordsWithTheSameAddressAreAccepted() {
        val records = listOf(
            "oa1:xmr recipient_address=$getmoneroXmr;",
            "oa1:xmr recipient_address=$getmoneroXmr; tx_description=again;",
        )
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID, "again"), select(records))
    }

    @Test
    fun twoRecordsWithDifferentAddressesAreRefused() {
        val records = listOf(
            "oa1:xmr recipient_address=$getmoneroXmr;",
            "oa1:xmr recipient_address=$openaliasXmr;",
        )
        assertEquals(failed(OpenAliasFailure.SEVERAL_ADDRESSES), select(records))
    }

    @Test
    fun oneRecordWithTwoAddressesIsRefused() {
        val records = listOf("oa1:xmr recipient_address=$getmoneroXmr; recipient_address=$openaliasXmr;")
        assertEquals(failed(OpenAliasFailure.SEVERAL_ADDRESSES), select(records))
    }

    @Test
    fun recordWithNoAddressGivesNoRecord() {
        assertEquals(failed(OpenAliasFailure.NO_RECORD), select(listOf("oa1:xmr recipient_name=Joe;")))
    }

    @Test
    fun paymentIdIsRefused() {
        val records = listOf("oa1:xmr recipient_address=$getmoneroXmr; tx_payment_id=abcdef0123456789;")
        assertEquals(failed(OpenAliasFailure.PAYMENT_ID), select(records))
    }

    @Test
    fun amountIsIgnored() {
        val records = listOf("oa1:xmr recipient_address=$getmoneroXmr; tx_amount=1.5;")
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID), select(records))
    }

    @Test
    fun invalidAddress() {
        assertEquals(failed(OpenAliasFailure.INVALID_ADDRESS), select(listOf("oa1:xmr recipient_address=4abc;")))
    }

    @Test
    fun testnetAddressOnMainnet() {
        assertEquals(failed(OpenAliasFailure.WRONG_NETWORK), select(listOf("oa1:xmr recipient_address=$testnetAddress;")))
    }

    @Test
    fun differentNamesGiveNoName() {
        val records = listOf(
            "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=Alice;",
            "oa1:xmr recipient_address=$getmoneroXmr; recipient_name=Bob;",
        )
        assertEquals(resolved(getmoneroXmr, null, DnssecStatus.VALID), select(records))
    }

    @Test
    fun segmentWithoutEqualsIsSkipped() {
        val records = listOf("oa1:xmr junk; recipient_address = $getmoneroXmr ; recipient_name=Joe")
        assertEquals(resolved(getmoneroXmr, "Joe", DnssecStatus.VALID), select(records))
    }

    // ---------- 4.5 Sanitize ----------

    @Test
    fun sanitizeRemovesBidiAndZeroWidth() {
        assertEquals("Monero Dev", OpenAlias.sanitizeName("Mon‮ero​ Dev"))
    }

    @Test
    fun sanitizeTurnsLineBreakIntoSpace() {
        assertEquals("Monero Development", OpenAlias.sanitizeName("Monero\nDevelopment"))
        assertEquals("Monero Development", OpenAlias.sanitizeName("  Monero \r\n\t Development  "))
    }

    @Test
    fun sanitizeCapsAtSixtyFourCharacters() {
        val seventy = "abcdefghij".repeat(7)
        assertEquals(seventy.take(64) + "…", OpenAlias.sanitizeName(seventy))
    }

    @Test
    fun sanitizeRemovesInvisibleCharacters() {
        // Bidi override and isolate, zero-width space and joiner, BOM, soft hyphen.
        assertEquals("Monero Dev", OpenAlias.sanitizeName("\u202EMon\u2066ero\u200B\u200D De\uFEFFv\u00AD"))
        // The Hangul filler is a letter, but it draws nothing.
        assertEquals("MoneroOne", OpenAlias.sanitizeName("Monero\u3164One"))
        assertNull(OpenAlias.sanitizeName("\u3164\u200B \uFEFF"))
        // Line and paragraph separators are whitespace: one space.
        assertEquals("Monero One", OpenAlias.sanitizeName("Monero\u2028\u2029One"))
        // Accents and other scripts stay.
        assertEquals("Cafe\u0301 Mon\u00E9ro", OpenAlias.sanitizeName("Cafe\u0301 Mon\u00E9ro"))
        assertEquals("モネロ 開発", OpenAlias.sanitizeName("モネロ 開発"))
    }

    @Test
    fun sanitizeCaps() {
        val sixtyFour = "a".repeat(64)
        assertEquals(sixtyFour, OpenAlias.sanitizeName(sixtyFour))
        // No space before the "…".
        assertEquals("a".repeat(63) + "…", OpenAlias.sanitizeName("a".repeat(63) + " bbbbbb"))
        // The cap counts characters, not code points.
        val accents = "e\u0301".repeat(64)
        assertEquals(accents, OpenAlias.sanitizeName(accents))
        assertEquals("e\u0301".repeat(64) + "…", OpenAlias.sanitizeName(accents + "e\u0301"))
        // Descriptions cap at 100.
        val long = "abcdefghij".repeat(11)
        assertEquals(long.take(100) + "…", OpenAlias.sanitizeDescription(long))
        assertEquals(long.take(100), OpenAlias.sanitizeDescription(long.take(100)))
    }

    // ---------- Monogram ----------

    @Test
    fun initials() {
        val display = "donate@getmonero.org"
        assertEquals("MD", OpenAlias.initials("Monero Development", display))
        assertEquals("MC", OpenAlias.initials("monero  core team", display))
        assertEquals("M", OpenAlias.initials("Monero", display))
        assertEquals("PF", OpenAlias.initials("\uD83C\uDF89 Party Fund", display))
        assertEquals("MD", OpenAlias.initials("(Monero) 42 dev", display))
        assertEquals("\u00C9V", OpenAlias.initials("\u00E9lan vital", display))
        // A letter with a two-letter capital stays as it is.
        assertEquals("\u00DF", OpenAlias.initials("\u00DFar", display))
        // No Turkish dotted capital, whatever the phone's language.
        assertEquals("I", OpenAlias.initials("istanbul", display))
    }

    @Test
    fun initialsFromTheName() {
        assertEquals("D", OpenAlias.initials(null, "donate@getmonero.org"))
        assertEquals("D", OpenAlias.initials("1234 !!", "donate@getmonero.org"))
        assertEquals("G", OpenAlias.initials(null, "getmonero.org"))
        assertEquals("P", OpenAlias.initials(null, "_pay.example.com"))
        assertNull(OpenAlias.initials(null, "1234@example.com"))
        assertNull(OpenAlias.initials("42", "42.example.com"))
    }

    // ---------- Recipient card ----------

    @Test
    fun summaryText() {
        val name = OpenAliasName(display = "donate@getmonero.org", query = "donate.getmonero.org")
        val named = OpenAliasSummary(name, getmoneroXmr, DnssecStatus.VALID, PinState.None, recipientName = "Monero Development")
        assertEquals("Monero Development, donate@getmonero.org", named.spokenName)
        assertEquals("MD", named.initials)

        val bare = OpenAliasSummary(name, getmoneroXmr, DnssecStatus.OFF, PinState.Same(1_700_000_000L))
        assertEquals("donate@getmonero.org", bare.spokenName)
        assertEquals("D", bare.initials)
    }

    @Test
    fun moneroOneLogoOnlyWhenDnssecValid() {
        val donate = OpenAliasName(display = "donate@monero.one", query = "donate.monero.one")
        val valid = OpenAliasSummary(donate, getmoneroXmr, DnssecStatus.VALID, PinState.None, recipientName = "Monero One")
        assertTrue(valid.showsMoneroOneLogo)
        // Unsigned: the monogram, like any other name (founder, 2026-10-01).
        val unsigned = valid.copy(dnssec = DnssecStatus.OFF)
        assertFalse(unsigned.showsMoneroOneLogo)
        assertEquals("MO", unsigned.initials)
    }

    @Test
    fun moneroOneLogoFollowsTheNameNotTheRecord() {
        // A record that calls itself Monero One gets a monogram.
        val getmonero = OpenAliasName(display = "donate@getmonero.org", query = "donate.getmonero.org")
        val posing = OpenAliasSummary(getmonero, getmoneroXmr, DnssecStatus.VALID, PinState.None, recipientName = "Monero One")
        assertFalse(posing.showsMoneroOneLogo)
        for (display in listOf("donate@monero.one.example.com", "donate@monero.on", "joe@monero.one", "donate.monero.one")) {
            val name = OpenAliasName(display = display, query = display.replace('@', '.'))
            assertFalse(display, OpenAliasSummary(name, getmoneroXmr, DnssecStatus.VALID, PinState.None).showsMoneroOneLogo)
        }
        assertTrue(OpenAlias.isOperatorName("donate@monero.one"))
        assertFalse(OpenAlias.isOperatorName("joe@monero.one"))
        assertFalse(OpenAlias.isOperatorName("donate.monero.one"))
    }

    @Test
    fun sanitizeNothingLeftGivesNoName() {
        assertNull(OpenAlias.sanitizeName("\u0000\u0007​‮"))
    }

    // ---------- 3.7 Answer mapping ----------

    private fun map(json: String, synthesized: Int = 0, torOn: Boolean = false) =
        OpenAlias.mapAnswer(json, synthesized, torOn, check)

    private val getmoneroHex = getmoneroRecords.map(::record)

    @Test
    fun rule1ParseFailureIsNetworkError() {
        assertEquals(failed(OpenAliasFailure.NETWORK), map("not json"))
        assertEquals(failed(OpenAliasFailure.TOR), map("not json", torOn = true))
        assertEquals(failed(OpenAliasFailure.NETWORK), map("""{"status":"ok"}"""))
    }

    @Test
    fun rule2Timeout() {
        assertEquals(failed(OpenAliasFailure.TIMEOUT), map("""{"status":"timeout"}"""))
        assertEquals(failed(OpenAliasFailure.TIMEOUT), map("""{"status":"timeout"}""", torOn = true))
    }

    @Test
    fun rule3ErrorOrOtherStatus() {
        assertEquals(failed(OpenAliasFailure.NETWORK), map("""{"status":"error","error":"bad name"}"""))
        assertEquals(failed(OpenAliasFailure.TOR), map("""{"status":"error","error":"x"}""", torOn = true))
        assertEquals(failed(OpenAliasFailure.NETWORK), map("""{"status":"other"}"""))
    }

    @Test
    fun rule4SynthesizedWithBogusIsNetworkError() {
        val bogus = okJson(emptyList(), secure = false, bogus = true)
        assertEquals(failed(OpenAliasFailure.NETWORK), map(bogus, synthesized = 1))
        assertEquals(failed(OpenAliasFailure.TOR), map(bogus, synthesized = 1, torOn = true))
        assertEquals(failed(OpenAliasFailure.NETWORK), map(okJson(emptyList(), secure = false, rcode = 2), synthesized = 2))
    }

    @Test
    fun rule4SynthesizedWithGoodSecureAnswerStands() {
        assertEquals(
            resolved(getmoneroXmr, "Monero Development", DnssecStatus.VALID, donation),
            map(okJson(getmoneroHex, secure = true), synthesized = 1)
        )
    }

    @Test
    fun rule5BogusIsDnssecFailed() {
        assertEquals(failed(OpenAliasFailure.DNSSEC_FAILED), map(okJson(emptyList(), secure = false, bogus = true, rcode = 2)))
    }

    @Test
    fun rule6NxdomainIsNoRecord() {
        assertEquals(failed(OpenAliasFailure.NO_RECORD), map(okJson(emptyList(), rcode = 3)))
        assertEquals(failed(OpenAliasFailure.NO_RECORD), map(okJson(emptyList(), nxdomain = true)))
    }

    @Test
    fun rule7OtherRcodeIsNetworkError() {
        assertEquals(failed(OpenAliasFailure.NETWORK), map(okJson(emptyList(), secure = false, rcode = 2)))
        assertEquals(failed(OpenAliasFailure.TOR), map(okJson(emptyList(), secure = false, rcode = 5), torOn = true))
    }

    @Test
    fun rule8NoDataIsNoRecord() {
        assertEquals(failed(OpenAliasFailure.NO_RECORD), map(okJson(emptyList(), havedata = false)))
        assertEquals(failed(OpenAliasFailure.NO_RECORD), map(okJson(emptyList(), havedata = true)))
    }

    @Test
    fun rule9SecureGivesDnssecValid() {
        assertEquals(
            resolved(getmoneroXmr, "Monero Development", DnssecStatus.VALID, donation),
            map(okJson(getmoneroHex, secure = true))
        )
    }

    @Test
    fun rule9InsecureGivesDnssecOff() {
        val hex = openaliasRecords.map(::record)
        assertEquals(resolved(openaliasXmr, "Monero Development", DnssecStatus.OFF, donation), map(okJson(hex, secure = false)))
    }

    @Test
    fun rule9RecordsWithNoXmrGiveNoRecord() {
        val hex = listOf(record("v=spf1 include:spf.improvmx.com ~all"), record("google-site-verification=abc"))
        assertEquals(failed(OpenAliasFailure.NO_RECORD), map(okJson(hex)))
    }

    // ---------- 4.6 Pins ----------

    private class Clock(var now: Long = 1_700_000_000L) {
        fun tick(): Long = now++
    }

    private fun summary(input: String, address: String): OpenAliasSummary {
        val name = (OpenAlias.classify(input) as RecipientInput.Name).name
        return OpenAliasSummary(name, address, DnssecStatus.VALID, PinState.None)
    }

    @Test
    fun pinStates() {
        val clock = Clock()
        val store = OpenAliasPinStore(FakeSharedPreferences(), clock::tick)
        assertEquals(PinState.None, store.state("donate.getmonero.org", getmoneroXmr))
        store.record("donate.getmonero.org", getmoneroXmr)
        assertEquals(PinState.Same(1_700_000_000L), store.state("donate.getmonero.org", getmoneroXmr))
        assertEquals(PinState.Changed(1_700_000_000L), store.state("donate.getmonero.org", openaliasXmr))
    }

    @Test
    fun pinsAreStoredAsSpecJson() {
        val prefs = FakeSharedPreferences()
        OpenAliasPinStore(prefs) { 1234L }.record("donate.getmonero.org", getmoneroXmr)
        val stored = Json.parseToJsonElement(prefs.getString("pins_mainnet", null)!!).jsonObject
        val entry = stored["donate.getmonero.org"]!!.jsonObject
        assertEquals(getmoneroXmr, entry["address"]!!.jsonPrimitive.content)
        assertEquals(1234L, entry["pinnedAt"]!!.jsonPrimitive.long)
        assertEquals("openalias_pins", OpenAliasPinStore.PREFS_NAME)
    }

    @Test
    fun atAndDotFormsShareOnePin() {
        val store = OpenAliasPinStore(FakeSharedPreferences(), Clock()::tick)
        assertTrue(store.recordAfterSend(SendState.Success(sendFlow, "tx"), summary("donate@getmonero.org", getmoneroXmr), getmoneroXmr))
        val dotted = (OpenAlias.classify("donate.getmonero.org") as RecipientInput.Name).name
        assertTrue(store.state(dotted.query, getmoneroXmr) is PinState.Same)
    }

    @Test
    fun entry501RemovesTheOldest() {
        val clock = Clock()
        val store = OpenAliasPinStore(FakeSharedPreferences(), clock::tick)
        for (i in 0 until 500) store.record("n$i.example.com", getmoneroXmr)
        assertEquals(500, store.count())
        store.record("n500.example.com", getmoneroXmr)
        assertEquals(500, store.count())
        assertEquals(PinState.None, store.state("n0.example.com", getmoneroXmr))
        assertTrue(store.state("n1.example.com", getmoneroXmr) is PinState.Same)
        assertTrue(store.state("n500.example.com", getmoneroXmr) is PinState.Same)
    }

    @Test
    fun clearAllEmptiesBothNetworks() {
        val store = OpenAliasPinStore(FakeSharedPreferences(), Clock()::tick)
        store.record("donate.getmonero.org", getmoneroXmr, OpenAliasPinStore.Network.MAINNET)
        store.record("donate.getmonero.org", testnetAddress, OpenAliasPinStore.Network.TESTNET)
        store.clearAll()
        assertEquals(PinState.None, store.state("donate.getmonero.org", getmoneroXmr, OpenAliasPinStore.Network.MAINNET))
        assertEquals(PinState.None, store.state("donate.getmonero.org", testnetAddress, OpenAliasPinStore.Network.TESTNET))
    }

    @Test
    fun flowWritesAPinOnlyOnSuccess() {
        val store = OpenAliasPinStore(FakeSharedPreferences(), Clock()::tick)
        val named = summary("donate@getmonero.org", getmoneroXmr)
        assertFalse(store.recordAfterSend(SendState.Idle, named, getmoneroXmr))
        assertFalse(store.recordAfterSend(SendState.Sending(sendFlow), named, getmoneroXmr))
        assertFalse(store.recordAfterSend(SendState.Error(sendFlow, "failed"), named, getmoneroXmr))
        assertFalse(store.recordAfterSend(SendState.Success(sendFlow, "tx"), null, getmoneroXmr))
        // A send to an address other than the resolved one writes no pin.
        assertFalse(store.recordAfterSend(SendState.Success(sendFlow, "tx"), named, testnetAddress))
        assertEquals(0, store.count())
        assertTrue(store.recordAfterSend(SendState.Success(sendFlow, "tx"), named, getmoneroXmr))
        assertTrue(store.state("donate.getmonero.org", getmoneroXmr) is PinState.Same)
    }
}
