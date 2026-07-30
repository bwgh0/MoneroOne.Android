package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletMigrationTest {

    private val complete = WalletMigration.LegacySnapshot(
        walletId = "123e4567-e89b-12d3-a456-426614174000",
        pinHash = "80000:aa:bb",
        seedWords = List(24) { "word$it" },
        seedType = SeedType.BIP39_24,
        restoreHeight = 3200000L,
        restoreDateMillis = 1710000000000L,
        selectedAddressIndex = 2
    )

    @Test
    fun `migrated wallet is named Personal Wallet and keeps legacy cache id verbatim`() {
        val info = WalletMigration.buildMigratedWallet(complete, "new-id", 42L)!!
        assertEquals("new-id", info.id)
        assertEquals("Personal Wallet", info.name)
        assertEquals("💰", info.emoji)
        assertEquals(WalletSource.BIP39, info.source)
        assertEquals(42L, info.createdAt)
        assertEquals(3200000L, info.restoreHeight)
        assertEquals(1710000000000L, info.restoreDateMillis)
        assertEquals(0, info.syncResetCount)
        // Legacy UUID cache id kept verbatim — files are NOT renamed on disk.
        assertEquals("123e4567-e89b-12d3-a456-426614174000", info.derivedWalletId)
        assertNull(info.deviceWalletId)
    }

    @Test
    fun `electrum seed maps to legacy source`() {
        val info = WalletMigration.buildMigratedWallet(
            complete.copy(seedType = SeedType.ELECTRUM_25, seedWords = List(25) { "w$it" }),
            "id", 0L
        )!!
        assertEquals(WalletSource.LEGACY, info.source)
    }

    @Test
    fun `incomplete legacy state migrates nothing`() {
        assertNull(WalletMigration.buildMigratedWallet(complete.copy(walletId = null), "id", 0L))
        assertNull(WalletMigration.buildMigratedWallet(complete.copy(pinHash = null), "id", 0L))
        assertNull(WalletMigration.buildMigratedWallet(complete.copy(seedWords = null), "id", 0L))
        assertNull(WalletMigration.buildMigratedWallet(complete.copy(seedWords = emptyList()), "id", 0L))
        assertNull(WalletMigration.buildMigratedWallet(complete.copy(seedType = null), "id", 0L))
    }
}
