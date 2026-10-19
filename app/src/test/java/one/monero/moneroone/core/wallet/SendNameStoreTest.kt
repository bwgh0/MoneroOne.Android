package one.monero.moneroone.core.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app-wide records of sends between your wallets. */
class SendNameStoreTest {

    private val prefs = FakeSharedPreferences()

    @Test fun writeAndLookUp() {
        val store = SendNameStore(prefs)
        store.recordSend(listOf("t1"), fromWalletId = "a", toWalletId = "b", nowMillis = 5)
        assertEquals(listOf(SendNameRecord("t1", "a", "b", createdAtMillis = 5)), store.records.value)
        // A new store reads what the last one wrote.
        assertEquals(store.records.value, SendNameStore(prefs).records.value)
    }

    @Test fun everyTxidOfASplitSendIsNamed() {
        val store = SendNameStore(prefs)
        store.recordSend(listOf("t1", "t2", ""), fromWalletId = "a", toWalletId = "b")
        assertEquals(listOf("t1", "t2"), store.records.value.map { it.txid })
    }

    @Test fun aTxidIsNamedOnce() {
        val store = SendNameStore(prefs)
        store.recordSend(listOf("t1"), "a", "b")
        store.recordSend(listOf("t1"), "a", "c")
        assertEquals(listOf("c"), store.records.value.map { it.toWalletId })
    }

    @Test fun removingAWalletRemovesTheRecordsThatNameIt() {
        val store = SendNameStore(prefs)
        store.recordSend(listOf("t1"), "a", "b")
        store.recordSend(listOf("t2"), "b", "c")
        store.recordSend(listOf("t3"), "c", "a")
        store.removeWallet("b")
        assertEquals(listOf("t3"), store.records.value.map { it.txid })
        assertEquals(listOf("t3"), SendNameStore(prefs).records.value.map { it.txid })
    }

    @Test fun removeAllWipesTheStore() {
        val store = SendNameStore(prefs)
        store.recordSend(listOf("t1"), "a", "b")
        store.wipe()
        assertTrue(store.records.value.isEmpty())
        assertNull(prefs.getString(SendNameStore.KEY_RECORDS, null))
    }

    @Test fun laterFieldsNeedNoMigration() {
        // A record written by a later version, with a field this one does not know.
        prefs.edit().putString(
            SendNameStore.KEY_RECORDS,
            """[{"txid":"t1","fromWalletId":"a","contactId":"k","usernameId":"joe"}]"""
        ).commit()
        val record = SendNameStore(prefs).records.value.single()
        assertEquals("k", record.contactId)
        assertNull(record.toWalletId)
    }

    @Test fun unreadableRecordsReadAsNone() {
        prefs.edit().putString(SendNameStore.KEY_RECORDS, "not json").commit()
        assertTrue(SendNameStore(prefs).records.value.isEmpty())
    }
}
