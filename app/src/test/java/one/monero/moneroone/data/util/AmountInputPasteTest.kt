package one.monero.moneroone.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputPasteTest {

    @Test
    fun oneAmountPastes() {
        assertEquals("1.5", AmountInput.pasted("1.5", '.'))
        assertEquals("0.123456789012", AmountInput.pasted(" 0.123456789012\n", '.'))
        assertEquals("12", AmountInput.pasted("12", '.'))
        assertEquals(".5", AmountInput.pasted(".5", '.'))
    }

    @Test
    fun theLocaleSeparatorOrADotPastes() {
        assertEquals("12.30", AmountInput.pasted("12,30", ','))
        assertEquals("12.30", AmountInput.pasted("12.30", ','))
        assertNull(AmountInput.pasted("12,30", '.'))
    }

    @Test
    fun anAddressLeavesTheAmount() {
        val address = "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A"
        assertNull(AmountInput.pasted(address, '.'))
        assertNull(AmountInput.pasted("monero:$address?tx_amount=1.5", '.'))
    }

    @Test
    fun digitsAreNeverTakenOutOfOtherText() {
        assertNull(AmountInput.pasted("", '.'))
        assertNull(AmountInput.pasted(".", '.'))
        assertNull(AmountInput.pasted("hello", '.'))
        assertNull(AmountInput.pasted("1.5 XMR", '.'))
        assertNull(AmountInput.pasted("$12.30", '.'))
        assertNull(AmountInput.pasted("1,234.56", '.'))
        assertNull(AmountInput.pasted("1.2.3", '.'))
        assertNull(AmountInput.pasted("1 000", '.'))
        assertNull(AmountInput.pasted("0.1234567890123", '.'))
    }
}
