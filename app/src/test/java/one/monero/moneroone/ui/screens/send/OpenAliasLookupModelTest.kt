package one.monero.moneroone.ui.screens.send

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import one.monero.moneroone.core.openalias.DnssecStatus
import one.monero.moneroone.core.openalias.OpenAliasFailure
import one.monero.moneroone.core.openalias.OpenAliasName
import one.monero.moneroone.core.openalias.OpenAliasOutcome
import one.monero.moneroone.core.openalias.OpenAliasResult
import one.monero.moneroone.core.openalias.OpenAliasSummary
import one.monero.moneroone.core.openalias.PinState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The address step's classification timing: typed text is classified after
 * the typing delay, Paste, Scan and Return at once, and no row shows while
 * the user types.
 */
class OpenAliasLookupModelTest {

    private val validAddress = "4" + "A".repeat(94)

    /** Typing delays that end only when the test says so. */
    private class Pauses {
        private val open = mutableListOf<CompletableDeferred<Unit>>()
        val count: Int get() = open.size
        var started = 0
            private set

        suspend fun pause() {
            val gate = CompletableDeferred<Unit>()
            started++
            open += gate
            try {
                gate.await()
            } finally {
                open -= gate
            }
        }

        fun end() = open.toList().forEach { it.complete(Unit) }
    }

    private val pauses = Pauses()
    private val lookups = mutableListOf<OpenAliasName>()
    private var outcome: OpenAliasOutcome =
        OpenAliasOutcome.Resolved(OpenAliasResult(validAddress, null, DnssecStatus.VALID))
    private var lookupGate: CompletableDeferred<Unit>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var haptics = 0
    private val announced = mutableListOf<LookupStatus>()

    private val model = OpenAliasLookupModel(
        scope = scope,
        lookup = { name ->
            lookups += name
            lookupGate?.await()
            outcome
        },
        pins = null,
        isValidAddress = { it == validAddress },
        typingPause = { pauses.pause() },
        resolvedFeedback = { haptics++ },
    )

    init {
        scope.launch { model.announcements.collect { announced += it } }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun type(text: String) {
        for (end in 1..text.length) model.onInputChanged(text.take(end), immediate = false)
    }

    @Test
    fun typedTextShowsNoRowUntilThePause() {
        type("donate")
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Address("donate", valid = false), model.status)
    }

    @Test
    fun typedValidAddressIsCheckedAfterThePause() {
        model.onInputChanged(validAddress, immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Address(validAddress, valid = true), model.status)
    }

    @Test
    fun pastedTextIsCheckedAtOnce() {
        model.onInputChanged(validAddress, immediate = true)
        assertEquals(LookupStatus.Address(validAddress, valid = true), model.status)
        model.onInputChanged("not an address", immediate = true)
        assertEquals(LookupStatus.Address("not an address", valid = false), model.status)
        assertEquals(0, pauses.count)
    }

    @Test
    fun aRedRowStaysWhileTheTextIsStillAnInvalidAddress() {
        model.onInputChanged("donate", immediate = true)
        model.onInputChanged("donatex", immediate = false)
        assertEquals(LookupStatus.Address("donate", valid = false), model.status)
        model.onInputChanged("donatexy", immediate = false)
        assertEquals(LookupStatus.Address("donate", valid = false), model.status)
        pauses.end()
        // Changed in place after the pause.
        assertEquals(LookupStatus.Address("donatexy", valid = false), model.status)
    }

    @Test
    fun aRedRowGoesAtOnceWhenAnInvalidAddressBecomesAnInvalidName() {
        model.onInputChanged("donate", immediate = true)
        model.onInputChanged("donate@", immediate = false)
        // "Invalid address" goes at once: the name row never shows with it.
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Rejected(reserved = false), model.status)
    }

    @Test
    fun aRedRowGoesAtOnceWhenAnInvalidNameBecomesAnInvalidAddress() {
        model.onInputChanged("donate@", immediate = true)
        assertEquals(LookupStatus.Rejected(reserved = false), model.status)
        model.onInputChanged("donate", immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Address("donate", valid = false), model.status)

        // The reserved row is a name row too.
        model.onInputChanged("monero.one", immediate = true)
        assertEquals(LookupStatus.Rejected(reserved = true), model.status)
        model.onInputChanged("joe", immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
    }

    @Test
    fun aRedRowGoesAtOnceWhenTheTextBecomesAName() {
        model.onInputChanged("donate@", immediate = true)
        assertEquals(LookupStatus.Rejected(reserved = false), model.status)
        model.onInputChanged("donate@ge", immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
        type("donate@getmonero.org")
        assertTrue(lookups.isEmpty())
        pauses.end()
        assertEquals(listOf("donate.getmonero.org"), lookups.map { it.query })
        assertTrue(model.status is LookupStatus.Resolved)
    }

    @Test
    fun aRedRowGoesAtOnceWhenTheTextBecomesAValidAddress() {
        model.onInputChanged(validAddress.dropLast(1), immediate = true)
        assertEquals(LookupStatus.Address(validAddress.dropLast(1), valid = false), model.status)
        model.onInputChanged(validAddress, immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Address(validAddress, valid = true), model.status)
    }

    @Test
    fun aGreenRowGoesOnTheNextKey() {
        model.onInputChanged(validAddress, immediate = true)
        model.onInputChanged(validAddress + "x", immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Address(validAddress + "x", valid = false), model.status)
    }

    @Test
    fun aFailedLookupRowGoesOnTheNextKey() {
        outcome = OpenAliasOutcome.Failed(OpenAliasFailure.NETWORK)
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertTrue(model.status is LookupStatus.Failed)
        model.onInputChanged("donate@getmonero.or", immediate = false)
        assertEquals(LookupStatus.Idle, model.status)
    }

    @Test
    fun theSameTextKeepsTheRow() {
        model.onInputChanged("donate", immediate = true)
        model.onInputChanged("donate ", immediate = false)
        assertEquals(LookupStatus.Address("donate", valid = false), model.status)
        assertEquals(0, pauses.count)
    }

    @Test
    fun aTrailingDotStartsTheDelayAgain() {
        type("donate@getmonero")
        val before = pauses.started
        model.onInputChanged("donate@getmonero.", immediate = false)
        assertEquals(before + 1, pauses.started)
        assertEquals(1, pauses.count)
        assertTrue(lookups.isEmpty())
        pauses.end()
        assertEquals(1, lookups.size)
        assertEquals("donate@getmonero", lookups[0].display)
    }

    @Test
    fun reservedAndInvalidNamesWaitForThePauseWhenTyped() {
        type("monero.one")
        assertEquals(LookupStatus.Idle, model.status)
        pauses.end()
        assertEquals(LookupStatus.Rejected(reserved = true), model.status)

        // The reserved row stays until the invalid-name row takes its place.
        model.onInputChanged("a@@b.org", immediate = false)
        assertEquals(LookupStatus.Rejected(reserved = true), model.status)
        pauses.end()
        assertEquals(LookupStatus.Rejected(reserved = false), model.status)

        model.onInputChanged("a.b.monero.one", immediate = true)
        assertEquals(LookupStatus.Rejected(reserved = true), model.status)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun returnStartsATypedLookupAtOnce() {
        type("donate@getmonero.org")
        assertTrue(lookups.isEmpty())
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertEquals(1, lookups.size)
        assertEquals(0, pauses.count)
    }

    @Test
    fun clearingCancelsALookupInProgress() {
        lookupGate = CompletableDeferred()
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertTrue(model.status is LookupStatus.Running)
        model.onInputChanged("", immediate = true)
        assertEquals(LookupStatus.Idle, model.status)
        lookupGate?.complete(Unit)
        assertEquals(LookupStatus.Idle, model.status)
    }

    @Test
    fun aResolveFiresOneHaptic() {
        lookupGate = CompletableDeferred()
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertEquals(0, haptics)
        lookupGate?.complete(Unit)
        assertTrue(model.status is LookupStatus.Resolved)
        assertEquals(1, haptics)

        // A failed lookup plays none.
        outcome = OpenAliasOutcome.Failed(OpenAliasFailure.NO_RECORD)
        model.onInputChanged("donate@example.org", immediate = true)
        assertTrue(model.status is LookupStatus.Failed)
        assertEquals(1, haptics)
    }

    @Test
    fun theEndOfALookupIsAnnouncedOnce() {
        val record = OpenAliasResult(validAddress, "Monero Development", DnssecStatus.VALID, "Coffee")
        outcome = OpenAliasOutcome.Resolved(record)
        model.onInputChanged("donate@getmonero.org", immediate = true)
        val name = OpenAliasName(display = "donate@getmonero.org", query = "donate.getmonero.org")
        assertEquals(listOf<LookupStatus>(LookupStatus.Resolved(name, record, PinState.None)), announced)
        // The summary carries the record name and the description.
        val summary = (model.status as LookupStatus.Resolved).summary()
        assertEquals("Monero Development", summary.recipientName)
        assertEquals("Coffee", summary.txDescription)

        // The same name with other case keeps the result, with no second announcement.
        model.onInputChanged("Donate@GetMonero.org", immediate = true)
        assertEquals(1, announced.size)

        outcome = OpenAliasOutcome.Failed(OpenAliasFailure.TIMEOUT)
        model.onInputChanged("donate@example.org", immediate = true)
        assertEquals(LookupStatus.Failed(OpenAliasName("donate@example.org", "donate.example.org"), OpenAliasFailure.TIMEOUT), announced.last())
        assertEquals(2, announced.size)
    }

    @Test
    fun returnAfterAFailureLooksUpAgain() {
        outcome = OpenAliasOutcome.Failed(OpenAliasFailure.NETWORK)
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertTrue(model.status is LookupStatus.Failed)
        outcome = OpenAliasOutcome.Resolved(OpenAliasResult(validAddress, null, DnssecStatus.VALID))
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertEquals(2, lookups.size)
        assertTrue(model.status is LookupStatus.Resolved)
    }

    @Test
    fun aRestoredNameStartsNoNewLookup() {
        val name = OpenAliasName(display = "donate@getmonero.org", query = "donate.getmonero.org")
        val record = OpenAliasResult(validAddress, "Monero Development", DnssecStatus.VALID, "Coffee")
        val restored = LookupStatus.Resolved(name, record, PinState.Same(1_700_000_000L))
        model.restore(restored)

        // Send classifies the saved field text at once when it is made again.
        model.onInputChanged("donate@getmonero.org", immediate = true)
        assertEquals(restored, model.status)
        assertEquals(0, lookups.size)
        assertEquals(0, haptics)
        assertTrue(announced.isEmpty())

        // Other text starts a lookup as usual.
        model.onInputChanged("donate@example.org", immediate = true)
        assertEquals(1, lookups.size)
    }

    @Test
    fun theSummaryKeepsEachValueThroughSavedState() {
        val scope = SaverScope { true }
        val name = OpenAliasName(display = "donate@getmonero.org", query = "donate.getmonero.org")
        val summaries = listOf(
            OpenAliasSummary(name, validAddress, DnssecStatus.VALID, PinState.None),
            OpenAliasSummary(name, validAddress, DnssecStatus.OFF, PinState.Same(1_700_000_000L), "Monero Development", "Coffee"),
            OpenAliasSummary(name, validAddress, DnssecStatus.VALID, PinState.Changed(1_600_000_000L), recipientName = "Monero"),
        )
        for (summary in summaries) {
            val saved = with(OpenAliasSummarySaver) { scope.save(summary) }!!
            // Saved state holds text, numbers and null only.
            assertTrue(saved.all { it == null || it is String || it is Long })
            assertEquals(summary, OpenAliasSummarySaver.restore(saved))
        }
        assertNull(with(OpenAliasSummarySaver) { scope.save(null) })
    }
}
