package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.ui.screens.wallet.WhatsNewTransferLoop.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/** The What's New Transfer loop (iOS WhatsNewTests, the Transfer loop tests). */
class WhatsNewTransferLoopTest {
    private val loop = WhatsNewTransferLoop
    private lateinit var locale: Locale

    @Before
    fun setUp() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(locale)

    @Test
    fun theStepsBeginAsOnIos() {
        assertEquals(
            listOf(Step.START, Step.PRESS, Step.AMOUNT, Step.SENT, Step.RECEIVED, Step.FADE),
            Step.entries.toList()
        )
        val begins = listOf(0.0, 1.75, 1.85, 2.85, 3.35, 5.55)
        Step.entries.zip(begins).forEach { (step, begin) -> assertEquals("$step", begin, loop.begin(step), 1e-9) }
        assertEquals(5.9, loop.cycleDuration, 1e-9)
    }

    @Test
    fun aTimeOnABeginShowsThatStepAndTheLoopRepeats() {
        assertEquals(Step.START, loop.step(0.0))
        assertEquals(Step.START, loop.step(1.74))
        assertEquals(Step.PRESS, loop.step(1.75))
        assertEquals(Step.AMOUNT, loop.step(1.85))
        assertEquals(Step.SENT, loop.step(2.85))
        assertEquals(Step.RECEIVED, loop.step(3.35))
        assertEquals(Step.FADE, loop.step(5.55))
        assertEquals(Step.START, loop.step(5.9))
        assertEquals(Step.PRESS, loop.step(5.9 + 1.75))
        assertEquals(Step.START, loop.step(-1.0))
    }

    @Test
    fun theBalancesMoveByTheAmountAndTheStillShowsTheResult() {
        assertEquals(BigDecimal("1284.503917"), loop.fromBalance(Step.AMOUNT))
        assertEquals(BigDecimal("1034.503917"), loop.fromBalance(Step.SENT))
        assertEquals(BigDecimal("0.25"), loop.toBalance(Step.SENT))
        assertEquals(BigDecimal("250.25"), loop.toBalance(Step.RECEIVED))
        assertEquals(Step.RECEIVED, loop.stillStep)
        assertTrue(loop.showsAmount(loop.stillStep))
        assertFalse(loop.showsAmount(Step.START))
        assertFalse(loop.showsAmount(Step.FADE))
        assertEquals(0.96f, loop.buttonScale(Step.PRESS))
        assertEquals(1f, loop.buttonScale(Step.AMOUNT))
        assertEquals(0f, loop.pictureOpacity(Step.FADE))
        assertEquals(1f, loop.pictureOpacity(Step.START))
    }

    @Test
    fun amountsShowXmrWithoutFiatMode() {
        assertEquals("1,284.503917 XMR", loop.amountText(loop.fromBalance(Step.START), null))
        assertEquals("1,034.503917 XMR", loop.amountText(loop.fromBalance(Step.RECEIVED), null))
        assertEquals("0.2500 XMR", loop.amountText(loop.toBalance(Step.START), null))
        assertEquals("250.2500 XMR", loop.amountText(loop.toBalance(Step.RECEIVED), null))
        assertEquals("250.0000 XMR", loop.amountText(loop.AMOUNT, null))
    }

    @Test
    fun amountsShowFiatInFiatModeWithAUsablePrice() {
        val fiat = WhatsNewFiat.of(fiatMode = true, price = 150.0, symbol = "$")
        assertEquals(WhatsNewFiat(150.0, "$"), fiat)
        assertEquals("$37,500.00", loop.amountText(loop.AMOUNT, fiat))
        assertEquals("$192,675.59", loop.amountText(loop.fromBalance(Step.START), fiat))
        assertEquals("$37.50", loop.amountText(loop.toBalance(Step.START), fiat))
        assertEquals("€37.50", loop.amountText(loop.toBalance(Step.START), WhatsNewFiat(150.0, "€")))
    }

    @Test
    fun withoutFiatModeOrAUsablePriceThePictureShowsXmr() {
        assertNull(WhatsNewFiat.of(fiatMode = false, price = 150.0, symbol = "$"))
        assertNull(WhatsNewFiat.of(fiatMode = true, price = null, symbol = "$"))
        assertNull(WhatsNewFiat.of(fiatMode = true, price = 0.0, symbol = "$"))
        assertNull(WhatsNewFiat.of(fiatMode = true, price = Double.NaN, symbol = "$"))
    }

    @Test
    fun eachBalanceRollsInTheDirectionItChanges() {
        // iOS numericText(value:): the From balance falls, its new digits come in from above.
        assertTrue(loop.countsDown(loop::fromBalance))
        assertFalse(one.monero.moneroone.ui.components.rollsUp(loop.countsDown(loop::fromBalance), '2', '0', false))
        // The To balance rises, its new digits come in from below.
        assertFalse(loop.countsDown(loop::toBalance))
        assertTrue(one.monero.moneroone.ui.components.rollsUp(loop.countsDown(loop::toBalance), null, '2', true))
    }

    @Test
    fun theToBalanceKeepsItsSharedEndWhenItGrows() {
        // "0.2500 XMR" to "250.2500 XMR": only "25" comes in; the old text is the new one's end.
        val xmr = one.monero.moneroone.ui.components.rollDiff(
            loop.amountText(loop.toBalance(Step.SENT), null),
            loop.amountText(loop.toBalance(Step.RECEIVED), null)
        )
        assertEquals(one.monero.moneroone.ui.components.RollDiff(start = 0, oldEnd = 0, newEnd = 2), xmr)
        // Fiat Mode: "$37.50" to "$37,537.50": "$37" stays, ",537" comes in, ".50" keeps its characters.
        val fiat = WhatsNewFiat(150.0, "$")
        val from = loop.amountText(loop.toBalance(Step.SENT), fiat)
        val to = loop.amountText(loop.toBalance(Step.RECEIVED), fiat)
        assertEquals("$37.50" to "$37,537.50", from to to)
        val diff = one.monero.moneroone.ui.components.rollDiff(from, to)
        assertEquals(from.substring(0, diff.start) + from.substring(diff.oldEnd), to.substring(0, diff.start) + to.substring(diff.newEnd))
        assertEquals(to.length - from.length, (diff.newEnd - diff.start) - (diff.oldEnd - diff.start))
    }
}
