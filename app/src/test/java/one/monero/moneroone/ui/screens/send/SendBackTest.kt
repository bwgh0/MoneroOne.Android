package one.monero.moneroone.ui.screens.send

import org.junit.Assert.assertEquals
import org.junit.Test

/** System back on the Send steps (iOS parity: Sending, Success and Error have no back). */
class SendBackTest {

    @Test fun addressClosesSend() {
        assertEquals(SendBack.Close, sendBack(SendPhase.ADDRESS))
    }

    @Test fun amountAndReviewGoBackOneStep() {
        assertEquals(SendBack.Step(SendPhase.ADDRESS), sendBack(SendPhase.AMOUNT))
        assertEquals(SendBack.Step(SendPhase.AMOUNT), sendBack(SendPhase.REVIEW))
    }

    @Test fun transferAmountLeavesSend() {
        // A Transfer starts on Amount: back returns to the Transfer screen.
        assertEquals(SendBack.Close, sendBack(SendPhase.AMOUNT, transfer = true))
        assertEquals(SendBack.Step(SendPhase.AMOUNT), sendBack(SendPhase.REVIEW, transfer = true))
        assertEquals(SendBack.Blocked, sendBack(SendPhase.SENDING, transfer = true))
    }

    @Test fun sendingSuccessAndErrorHaveNoBack() {
        for (phase in listOf(SendPhase.SENDING, SendPhase.SUCCESS, SendPhase.ERROR)) {
            assertEquals(phase.name, SendBack.Blocked, sendBack(phase))
        }
    }
}
