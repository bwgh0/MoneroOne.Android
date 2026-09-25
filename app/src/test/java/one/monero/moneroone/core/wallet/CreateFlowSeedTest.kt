package one.monero.moneroone.core.wallet

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One recovery phrase per create flow, held for the life of that flow (B1). */
class CreateFlowSeedTest {

    private val pending = MutableStateFlow<PendingSeed?>(null)
    private val flowSeed = CreateFlowSeed(pending)
    private var generated = 0

    private fun newPhrase(): PendingSeed {
        generated++
        return PendingSeed(List(24) { "p$generated-w$it" }, SeedType.BIP39_24)
    }

    @Test
    fun `a new flow issues its phrase into the pending seed`() {
        assertEquals(CreateFlowStart.ISSUE, flowSeed.start("a", savedIssued = false))
        val words = flowSeed.issue("a", ::newPhrase)
        assertEquals(pending.value?.words, words)
        assertTrue(flowSeed.holds("a"))
    }

    @Test
    fun `a recreated screen resumes with the same words`() {
        val words = flowSeed.issue("a", ::newPhrase)
        assertEquals(CreateFlowStart.RESUME, flowSeed.start("a", savedIssued = true))
        assertEquals(words, flowSeed.issue("a", ::newPhrase))
        assertEquals(1, generated)
    }

    @Test
    fun `after process death a flow that showed a phrase restarts`() {
        // A new ViewModel holds nothing; only the screen's saved record is left.
        val afterDeath = CreateFlowSeed(MutableStateFlow(null))
        assertEquals(CreateFlowStart.RESTART, afterDeath.start("a", savedIssued = true))
    }

    @Test
    fun `a flow whose phrase was dropped never gets a second one`() {
        flowSeed.issue("a", ::newPhrase)
        pending.value = null // a wallet switch, or an add that failed after the kit was built
        assertFalse(flowSeed.holds("a"))
        assertNull(flowSeed.issue("a", ::newPhrase))
        assertEquals(1, generated)
        assertEquals(CreateFlowStart.RESTART, flowSeed.start("a", savedIssued = true))
    }

    @Test
    fun `a flow that lost its saved state to the lock screen restarts`() {
        flowSeed.issue("a", ::newPhrase)
        flowSeed.discard("a")
        assertEquals(CreateFlowStart.RESTART, flowSeed.start("a", savedIssued = false))
    }

    @Test
    fun `discard drops only the flow's own phrase`() {
        flowSeed.issue("a", ::newPhrase)
        flowSeed.discard("b")
        assertTrue(flowSeed.holds("a"))
        flowSeed.discard("a")
        assertNull(pending.value)
    }

    @Test
    fun `a new flow after a finished one gets a new phrase`() {
        val first = flowSeed.issue("a", ::newPhrase)
        flowSeed.discard("a")
        assertEquals(CreateFlowStart.ISSUE, flowSeed.start("b", savedIssued = false))
        val second = flowSeed.issue("b", ::newPhrase)
        assertNotEquals(first, second)
        assertFalse(flowSeed.holds("a"))
        assertTrue(flowSeed.holds("b"))
    }
}
