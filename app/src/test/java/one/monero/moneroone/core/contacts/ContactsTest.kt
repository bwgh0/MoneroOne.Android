package one.monero.moneroone.core.contacts

import io.horizontalsystems.monerokit.model.TransactionInfo
import io.horizontalsystems.monerokit.model.Transfer
import one.monero.moneroone.core.wallet.FakeSharedPreferences
import one.monero.moneroone.core.wallet.SendName
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.ui.screens.transactions.TransactionHistoryLogic
import one.monero.moneroone.ui.screens.transactions.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contacts: the store, the matches, the field's card and History's names. */
class ContactsTest {

    private val prefs = FakeSharedPreferences()
    private val addrDana = "4" + "D".repeat(94)
    private val addrSub = "8" + "S".repeat(94)
    private val addrWallet = "4" + "W".repeat(94)
    private val addrPaid = "4" + "P".repeat(94)

    private fun dana() = Contact("1", "Dana Okafor", addrDana, Contact.KIND_ADDRESS)
    private fun ben() = Contact("2", "Ben Carter", "ben@example.org", Contact.KIND_OPEN_ALIAS)
    private fun sent(vararg to: String, amount: Long = 1000, fee: Long = 10) =
        TransactionInfo(1, false, false, amount, fee, 50, "hash-${to.joinToString()}", 100, "", 0, 0, 12, 0, "",
            to.map { Transfer(amount, it) })

    // ---- Store ----

    @Test fun addEditDeleteAndReadBack() {
        val store = ContactStore(prefs)
        val added = store.add(" Dana ", addrDana, Contact.KIND_ADDRESS, nowMillis = 7)
        assertNotNull(added)
        assertEquals("Dana", added!!.name)
        assertEquals(7L, added.createdAtMillis)
        // A new store reads what the last one wrote (the prefs are the encrypted file in the app).
        assertEquals(store.contacts.value, ContactStore(prefs).contacts.value)

        assertTrue(store.update(added.copy(name = "Dana Okafor")))
        assertEquals("Dana Okafor", ContactStore(prefs).contacts.value.single().name)

        assertTrue(store.delete(added.id))
        assertTrue(ContactStore(prefs).contacts.value.isEmpty())
    }

    @Test fun contactsAreSortedAToZAnyCase() {
        val store = ContactStore(prefs)
        store.add("sam", addrSub, Contact.KIND_ADDRESS)
        store.add("Ben", "ben@example.org", Contact.KIND_OPEN_ALIAS)
        store.add("dana", addrDana, Contact.KIND_ADDRESS)
        assertEquals(listOf("Ben", "dana", "sam"), store.contacts.value.map { it.name })
    }

    @Test fun removeAllWipesTheContacts() {
        val store = ContactStore(prefs)
        store.add("Dana", addrDana, Contact.KIND_ADDRESS)
        store.wipe()
        assertTrue(store.contacts.value.isEmpty())
        assertNull(prefs.getString(ContactStore.KEY_CONTACTS, null))
        assertTrue(ContactStore(prefs).contacts.value.isEmpty())
    }

    @Test fun anUnreadableStoreReadsAsNoContacts() {
        prefs.edit().putString(ContactStore.KEY_CONTACTS, "not json").commit()
        assertTrue(ContactStore(prefs).contacts.value.isEmpty())
    }

    @Test fun anUnknownKindIsKept() {
        // A later kind (a username) needs no migration.
        val encoded = """[{"id":"9","name":"Lena","destination":"lena","kind":"username","createdAtMillis":1}]"""
        assertEquals("username", ContactStore.decode(encoded).single().kind)
    }

    // ---- Matches and the field ----

    @Test fun matchesByNameOrDestinationAnyCase() {
        val all = listOf(dana(), ben())
        assertEquals(listOf(dana()), Contacts.matches("da", all))
        assertEquals(listOf(dana()), Contacts.matches("OKAF", all))
        assertEquals(listOf(ben()), Contacts.matches("BEN@", all))
        assertEquals(listOf(dana()), Contacts.matches(addrDana.substring(0, 10).lowercase(), all))
        assertTrue(Contacts.matches("  ", all).isEmpty())
        assertTrue(Contacts.matches("zz", all).isEmpty())
    }

    @Test fun aPastedAddressThatIsAContactsShowsItsCard() {
        val all = listOf(dana(), ben())
        assertEquals(dana(), Contacts.forInput(" $addrDana\n", all))
        assertEquals(ben(), Contacts.forInput("Ben@Example.org", all))
        assertNull(Contacts.forInput(addrSub, all))
    }

    @Test fun kindFollowsSendsCheck() {
        assertEquals(Contact.KIND_ADDRESS, Contacts.kindOf(addrDana) { true })
        assertNull(Contacts.kindOf(addrDana) { false })
        assertEquals(Contact.KIND_OPEN_ALIAS, Contacts.kindOf("ben@example.org") { false })
        assertNull(Contacts.kindOf("") { true })
    }

    // ---- History ----

    @Test fun aSendToAContactsAddressNamesTheContact() {
        val name = SendNames.recipientName(listOf(addrDana), listOf(dana()), emptyMap())
        assertEquals(SendName.ToContact(dana()), name)
        assertEquals("To Dana Okafor", TransactionHistoryLogic.sendNameTitle(name!!))
    }

    @Test fun aSendToAContactsSubaddressNamesTheContact() {
        val sub = Contact("3", "Recipient Sub", addrSub, Contact.KIND_ADDRESS)
        assertEquals(SendName.ToContact(sub), SendNames.recipientName(listOf(addrSub), listOf(sub), emptyMap()))
    }

    @Test fun aSendToAnOpenAliasContactNamesTheContact() {
        val pins = mapOf("ben.example.org" to addrPaid)
        assertEquals(SendName.ToContact(ben()), SendNames.recipientName(listOf(addrPaid), listOf(ben()), pins))
    }

    @Test fun aSendToAnUnsavedOpenAliasNameShowsTheName() {
        val pins = mapOf("donate.getmonero.org" to addrPaid)
        val name = SendNames.recipientName(listOf(addrPaid), emptyList(), pins)
        assertEquals(SendName.ToOpenAlias("donate.getmonero.org"), name)
        assertEquals("To donate.getmonero.org", TransactionHistoryLogic.sendNameTitle(name!!))
    }

    @Test fun afterADeleteTheRowReadsSentAgain() {
        assertNull(SendNames.recipientName(listOf(addrDana), emptyList(), emptyMap()))
        val name = SendNames.nameFor("t", incoming = false, amount = 5, fee = 1, walletId = "a", records = emptyList(),
            wallets = emptyList(), destinations = listOf(addrDana), contacts = emptyList())
        assertNull(name)
    }

    @Test fun aWalletRecordWinsOverAContact() {
        val savings = WalletInfo(id = "b", name = "Savings", emoji = "🦉", cachedPrimaryAddress = addrWallet)
        val record = one.monero.moneroone.core.wallet.SendNameRecord("t", "a", "b")
        val name = SendNames.nameFor("t", incoming = false, amount = 5, fee = 1, walletId = "a", records = listOf(record),
            wallets = listOf(savings), destinations = listOf(addrWallet),
            contacts = listOf(Contact("4", "Not Savings", addrWallet)))
        assertEquals(SendName.ToWallet(savings), name)
    }

    @Test fun searchFindsARowByTheNameItShows() {
        val toDana = sent(addrDana)
        val other = sent(addrSub)
        val shown: (TransactionInfo) -> String? = { tx ->
            SendNames.recipientName(tx.transfers.map { it.address }, listOf(dana()), emptyMap())
                ?.let(TransactionHistoryLogic::sendNameTitle)
        }
        assertEquals(listOf(toDana), TransactionHistoryLogic.filter(listOf(toDana, other), TransactionFilter.ALL, null, "dana", emptyList(), shownName = shown))
        val savings: (TransactionInfo) -> String? = { "To Savings" }
        assertEquals(listOf(other), TransactionHistoryLogic.filter(listOf(other), TransactionFilter.ALL, null, "savings", emptyList(), shownName = savings))
    }

    // ---- Add to Contacts ----

    @Test fun addToContactsFillsTheAddressOrTheOpenAliasName() {
        assertEquals(addrSub, TransactionHistoryLogic.addToContactsDestination(sent(addrSub), null, emptyList()) { false })
        assertEquals("donate.getmonero.org",
            TransactionHistoryLogic.addToContactsDestination(sent(addrPaid), SendName.ToOpenAlias("donate.getmonero.org"), emptyList()) { false })
    }

    @Test fun addToContactsHidesForAContactAWalletOrAnUnknownDestination() {
        assertNull(TransactionHistoryLogic.addToContactsDestination(sent(addrDana), SendName.ToContact(dana()), listOf(dana())) { false })
        assertNull(TransactionHistoryLogic.addToContactsDestination(sent(addrDana), null, listOf(dana())) { false })
        assertNull(TransactionHistoryLogic.addToContactsDestination(sent(addrWallet), null, emptyList()) { it == addrWallet })
        assertNull(TransactionHistoryLogic.addToContactsDestination(sent(), null, emptyList()) { false })
        assertNull(TransactionHistoryLogic.addToContactsDestination(sent(addrSub), SendName.ToThisWallet, emptyList()) { false })
    }

    // ---- Follow-up 1: a send to this wallet ----

    @Test fun aSendToThisWalletsDetailShowsTheFeeAsItsAmount() {
        // Pending: wallet2 still books the whole amount; the detail shows only the fee, as the row does.
        val pending = sent(addrWallet, amount = 500_000_000_000, fee = 30_000_000)
        val fields = TransactionHistoryLogic.details(pending, emptyList(), "date", "\$0.01", "\$0.02", null, SendName.ToThisWallet)
        assertEquals("To This Wallet", fields.first { it.label == "Type" }.value)
        assertEquals("−${one.monero.moneroone.data.util.XmrFormat.format(30_000_000)} XMR", fields.first { it.label == "Amount" }.value)
        assertTrue(fields.first { it.label == "Fee" }.value.contains("0.00003"))
        assertEquals("\$0.01", fields.first { it.label == "Value when sent" }.value)
        assertEquals("\$0.02", fields.first { it.label == "Value today" }.value)
        // Confirmed: wallet2 books 0 plus the fee; the same fee shows.
        val confirmed = sent(addrWallet, amount = 0, fee = 30_000_000)
        val after = TransactionHistoryLogic.details(confirmed, emptyList(), "date", null, null, null, SendName.ToThisWallet)
        assertEquals(fields.first { it.label == "Amount" }.value, after.first { it.label == "Amount" }.value)
    }

    @Test fun anOpenAliasRowShowsTheNameAsTyped() {
        val store = one.monero.moneroone.core.openalias.OpenAliasPinStore(FakeSharedPreferences())
        store.record("donate.getmonero.org", addrPaid, display = "donate@getmonero.org")
        store.record("old.example.org", addrSub)
        assertEquals(mapOf("donate.getmonero.org" to "donate@getmonero.org", "old.example.org" to "old.example.org"), store.pinnedNames())
        val name = SendNames.recipientName(listOf(addrPaid), emptyList(), store.pinnedAddresses(), store.pinnedNames())
        assertEquals("To donate@getmonero.org", TransactionHistoryLogic.sendNameTitle(name!!))
        // An older pin, written before the typed form was kept, shows the lookup form.
        val old = SendNames.recipientName(listOf(addrSub), emptyList(), store.pinnedAddresses(), store.pinnedNames())
        assertEquals(SendName.ToOpenAlias("old.example.org"), old)
    }
}
