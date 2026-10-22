package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.core.username.UsernameTrust
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendAmountLimitTest {
    private val limit = 100_000_000_000L
    private val balance = limit * 10

    @Test fun maxIsOffWhileALimitApplies() {
        assertTrue(maxEnabled(null))
        assertTrue(maxEnabled(UsernameTrust.PUBLISHED))
        for (trust in listOf(UsernameTrust.PENDING, UsernameTrust.STALE, UsernameTrust.EXPIRED)) {
            assertFalse(trust.name, maxEnabled(trust))
        }
    }

    @Test fun noLimitKeepsTheBalanceRule() {
        assertTrue(amountCanContinue(balance, balance, isSweepAll = true, usernameTrust = null))
        assertTrue(amountCanContinue(balance, balance, isSweepAll = true, usernameTrust = UsernameTrust.PUBLISHED))
        assertFalse(amountCanContinue(0, balance, isSweepAll = false, usernameTrust = null))
        assertFalse(amountCanContinue(balance + 1, balance, isSweepAll = false, usernameTrust = null))
    }

    @Test fun aLimitCapsTheAmountAndRefusesSendAll() {
        for (trust in listOf(UsernameTrust.PENDING, UsernameTrust.STALE, UsernameTrust.EXPIRED)) {
            assertTrue(trust.name, amountCanContinue(limit, balance, isSweepAll = false, usernameTrust = trust))
            assertFalse(trust.name, amountCanContinue(limit + 1, balance, isSweepAll = false, usernameTrust = trust))
            assertFalse(trust.name, amountCanContinue(1, balance, isSweepAll = true, usernameTrust = trust))
        }
    }
}
