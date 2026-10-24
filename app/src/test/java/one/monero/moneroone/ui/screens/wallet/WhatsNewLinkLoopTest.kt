package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.paymentlinks.ENVELOPE_ARRIVE
import one.monero.moneroone.paymentlinks.EnvelopeGeometry
import one.monero.moneroone.paymentlinks.PACE
import one.monero.moneroone.paymentlinks.StampGeometry
import one.monero.moneroone.paymentlinks.TOOL_AWAY_X
import one.monero.moneroone.paymentlinks.TOOL_AWAY_Y
import one.monero.moneroone.paymentlinks.TOOL_HOVER
import one.monero.moneroone.paymentlinks.TOOL_TILT
import one.monero.moneroone.ui.screens.wallet.WhatsNewLinkLoop.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The What's New envelope loop (iOS WhatsNewTests, the envelope loop tests). */
class WhatsNewLinkLoopTest {
    private val loop = WhatsNewLinkLoop

    @Test
    fun theEnvelopeLoopPlaysTheSendInOrder() {
        assertEquals(
            listOf(
                Step.START, Step.ARRIVE, Step.NOTE_IN, Step.CLOSE, Step.FLAP_OVER,
                Step.DROP, Step.HOVER, Step.PRESS, Step.CONTACT, Step.LIFT, Step.MARK, Step.AWAY, Step.SWEEP, Step.FADE,
            ),
            Step.entries.toList()
        )
        // The start: the note alone, risen where the send's check turns into it; the open envelope
        // hidden below; no seal, stamp or foil.
        val start = loop.pose(Step.START)
        assertEquals(-EnvelopeGeometry.CARD_RISE, start.noteY, 0f)
        assertEquals(EnvelopeGeometry.DROP, start.noteLayerY, 0f)
        assertEquals(1f, start.noteAlpha, 0f)
        assertEquals(1f, start.noteShape, 0f)
        assertEquals(0f, start.noteTint, 0f)
        assertEquals(0f, start.envelopeAlpha, 0f)
        assertEquals(EnvelopeGeometry.DROP + ENVELOPE_ARRIVE, start.envelopeY, 0f)
        assertEquals(180f, start.flapDegrees, 0f)
        assertEquals(0f, start.sealAlpha, 0f)
        assertEquals(0.3f, start.sealScale, 0f)
        assertEquals(0f, start.toolAlpha, 0f)
        assertEquals(TOOL_AWAY_X, start.toolX, 0f)
        assertEquals(TOOL_AWAY_Y, start.toolY, 0f)
        assertEquals(TOOL_TILT, start.toolTilt, 0f)
        assertEquals(-0.35f, start.foilReveal, 0f)
        // The envelope comes up open, the note slides in, the flap closes in the middle.
        assertEquals(EnvelopeGeometry.DROP, loop.pose(Step.ARRIVE).envelopeY, 0f)
        assertEquals(1f, loop.pose(Step.ARRIVE).envelopeAlpha, 0f)
        assertEquals(0f, loop.pose(Step.NOTE_IN).noteY, 0f)
        val closed = loop.pose(Step.CLOSE)
        assertEquals(0f, closed.flapDegrees, 0f)
        assertEquals(0f, closed.envelopeY, 0f)
        assertEquals(0f, closed.noteLayerY, 0f)
        // The stamp hovers, presses, lifts and goes; the wax spreads and shows its mark.
        assertEquals(TOOL_HOVER, loop.pose(Step.HOVER).toolY, 0f)
        assertEquals(0f, loop.pose(Step.PRESS).toolHeight, 0f)
        assertEquals(1f, loop.pose(Step.CONTACT).sealScale, 0f)
        assertEquals(1f, loop.pose(Step.MARK).sealMark, 0f)
        assertEquals(0f, loop.pose(Step.AWAY).toolAlpha, 0f)
        assertEquals(1.35f, loop.pose(Step.SWEEP).foilReveal, 0f)
        assertEquals(0f, loop.opacity(Step.FADE), 0f)
        assertEquals(1f, loop.opacity(Step.SWEEP), 0f)
    }

    @Test
    fun theEnvelopeLoopKeepsTheHerosTimingWithRests() {
        assertEquals(1.3f, PACE, 0f)
        // The hero's beats from the envelope to the sweep (LinkPacking's PACK_ times, pace 1), at its pace.
        val beats = listOf(
            Step.ARRIVE to 1.10, Step.NOTE_IN to 1.40, Step.CLOSE to 1.75, Step.FLAP_OVER to 1.87,
            Step.DROP to 2.10, Step.HOVER to 2.25, Step.PRESS to 2.65, Step.CONTACT to 2.74,
            Step.LIFT to 2.90, Step.MARK to 2.93, Step.AWAY to 3.00, Step.SWEEP to 3.10,
        )
        for ((step, beat) in beats) {
            assertEquals(step.name, (beat - 1.10) * 1.3, loop.begin(step) - loop.begin(Step.ARRIVE), 1e-4)
        }
        // The iOS begins (round 5c).
        val ios = listOf(
            Step.START to 0.0, Step.ARRIVE to 0.95, Step.NOTE_IN to 1.34, Step.CLOSE to 1.795,
            Step.FLAP_OVER to 1.951, Step.DROP to 2.25, Step.HOVER to 2.445, Step.PRESS to 2.965,
            Step.CONTACT to 3.082, Step.LIFT to 3.29, Step.MARK to 3.329, Step.AWAY to 3.42,
            Step.SWEEP to 3.55, Step.FADE to 6.75,
        )
        for ((step, begin) in ios) assertEquals(step.name, begin, loop.begin(step), 1e-3)
        // The fade-in and a rest on the note before the envelope; the sweep and a rest before the fade.
        assertEquals(0.35 + 0.6, loop.begin(Step.ARRIVE), 1e-4)
        assertEquals(1.2 + 2.0, loop.begin(Step.FADE) - loop.begin(Step.SWEEP), 1e-4)
        assertEquals(7.1, loop.cycleDuration, 1e-3)
        val begins = Step.entries.map(loop::begin)
        assertEquals(begins.sorted(), begins)
        assertEquals(begins.size, begins.toSet().size)
        // The step at a time: on a begin it is that step; the cycle wraps.
        assertEquals(Step.START, loop.step(0.0))
        assertEquals(Step.ARRIVE, loop.step(0.95))
        assertEquals(Step.CLOSE, loop.step(loop.begin(Step.CLOSE)))
        assertEquals(Step.PRESS, loop.step(loop.begin(Step.PRESS)))
        assertEquals(Step.CONTACT, loop.step(loop.begin(Step.CONTACT)))
        assertEquals(Step.FADE, loop.step(6.9))
        assertEquals(Step.START, loop.step(loop.cycleDuration))
        assertEquals(Step.ARRIVE, loop.step(loop.cycleDuration + 0.95))
    }

    @Test
    fun theEnvelopeLoopStaysInsideTheCard() {
        // Phones 360 and 412 dp wide: the sheet's sides 24, the card 16:10, the picture 16 inside it.
        for (phone in listOf(360f, 412f)) {
            val cardW = phone - 2 * 24f
            val cardH = cardW * 10f / 16f
            val padding = 16f
            val boxW = cardW - 2 * padding
            val boxH = cardH - 2 * padding
            val k = linkPictureScale(boxW, boxH)
            val dp = k * LINK_W / EnvelopeGeometry.W
            // The card's edges, in envelope units from the sealed envelope's top (it sits in the middle).
            val gap = (boxH - LINK_H * k) / 2 + padding
            val top = -(LINK_HEADROOM + gap / dp)
            val bottom = EnvelopeGeometry.H + LINK_HEADROOM + gap / dp
            assertEquals(EnvelopeGeometry.H, top + bottom, 1e-3f)
            // At least 8 dp clear of the edges: the risen note at the top, the open envelope's bottom.
            val openDrop = EnvelopeGeometry.DROP / EnvelopeGeometry.ENV_SCALE
            val noteTop = openDrop + EnvelopeGeometry.CARD_Y - EnvelopeGeometry.CARD_RISE
            val openBottom = openDrop + EnvelopeGeometry.H
            assertTrue("note top on $phone", (noteTop - top) * dp > 8f)
            assertTrue("open bottom on $phone", (bottom - openBottom) * dp > 8f)
            // The stamp's top over the wax keeps at least 8 dp.
            val stampTop = EnvelopeGeometry.SEAL_Y + EnvelopeGeometry.SEAL / 2 - StampGeometry.CONTACT_Y + TOOL_HOVER
            assertTrue("stamp top on $phone", (stampTop - top) * dp > 8f)
        }
    }

    @Test
    fun animationsOffShowTheSealedEnvelopeStill() {
        // The hero's end, the last frame before the fade: closed in the middle, the seal pressed with
        // its ring and mark, the foil shown, the stamp gone.
        assertEquals(Step.SWEEP, loop.stillStep)
        assertEquals(Step.entries.indexOf(Step.FADE), Step.entries.indexOf(loop.stillStep) + 1)
        val still = loop.pose(loop.stillStep)
        assertEquals(1f, still.envelopeAlpha, 0f)
        assertEquals(0f, still.envelopeY, 0f)
        assertEquals(0f, still.flapDegrees, 0f)
        assertEquals(1f, still.sealAlpha, 0f)
        assertEquals(1f, still.sealScale, 0f)
        assertEquals(1f, still.sealMark, 0f)
        assertEquals(0f, still.sealDome, 0f)
        assertEquals(0f, still.toolAlpha, 0f)
        assertEquals(-1f, still.sweep, 0f)
        assertEquals(1.35f, still.foilReveal, 0f)
        assertEquals(1f, loop.opacity(loop.stillStep), 0f)
    }
}
