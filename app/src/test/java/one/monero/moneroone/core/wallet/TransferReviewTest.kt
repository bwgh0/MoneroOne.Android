package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review and Sent of a send to one of your wallets (iOS TransferParties, SendFlowView successNote). */
class TransferReviewTest {

    private val current = WalletInfo(id = "a", name = "QA Transfer", emoji = "💰", cachedPrimaryAddress = "4" + "A".repeat(94))
    private val other = WalletInfo(id = "b", name = "Wallet 3", emoji = "🐷", cachedPrimaryAddress = "4" + "B".repeat(94))

    @Test
    fun aSendBackToThisWalletReadsThisWalletWithNoTotalAndTheNote() {
        assertTrue(TransferReview.isToSelf(current, current))
        assertEquals("This Wallet", TransferReview.toName(current, current))
        assertFalse(TransferReview.showsTotal(current, current))
        assertEquals(
            "Sends XMR back to this wallet as a new payment. You can spend it again in about 20 minutes.",
            TransferReview.note(current, current)
        )
    }

    @Test
    fun aSendToAnotherWalletKeepsItsNameTheTotalAndNoNote() {
        assertFalse(TransferReview.isToSelf(current, other))
        assertEquals("Wallet 3", TransferReview.toName(current, other))
        assertTrue(TransferReview.showsTotal(current, other))
        assertNull(TransferReview.note(current, other))
    }

    @Test
    fun aSendToAnAddressKeepsTheTotalAndHasNoNote() {
        assertTrue(TransferReview.showsTotal(null, null))
        assertNull(TransferReview.note(null, null))
        assertTrue(TransferReview.showsTotal(current, null))
    }

    @Test
    fun sentToThisWalletSaysWhenItCanBeSpentAgain() {
        assertEquals("You can spend it again in about 20 minutes.", TransferReview.sentNote(current, current.id))
    }

    @Test
    fun sentToAnotherWalletNamesThatWallet() {
        assertEquals("It shows in Wallet 3 when you next open that wallet.", TransferReview.sentNote(other, current.id))
    }

    @Test
    fun aSendHasNoSentNote() {
        assertNull(TransferReview.sentNote(null, current.id))
    }
}
