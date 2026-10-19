package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.contacts.Contacts
import timber.log.Timber

/**
 * Who a send went to, written on this phone at send time: a Transfer, or a Send to the saved
 * primary address of one of your wallets. App-wide, not per wallet: one record names both sides
 * of [txid] ("To Savings" in the wallet it left, "From Daily" in the wallet it reached).
 *
 * History looks the names up at display time, so a renamed wallet shows its new name.
 * [contactId] and [recipient] are kept for contacts and names later; the decoder ignores
 * unknown keys and every field after [fromWalletId] has a default, so new fields need no migration.
 */
@Serializable
data class SendNameRecord(
    val txid: String,
    val fromWalletId: String,
    /** The wallet of this app the send went to; null for a send to someone else. */
    val toWalletId: String? = null,
    /** Reserved: the contact the send went to. */
    val contactId: String? = null,
    /** Reserved: the name the send went to (an OpenAlias name). */
    val recipient: String? = null,
    val createdAtMillis: Long = 0L
)

/**
 * The [SendNameRecord]s of this phone, in the plain `monero_wallet` prefs with the wallet list
 * ([WalletStore]): metadata, no keys, and the app has no backup. Removing a wallet removes the
 * records that name it; Remove All removes every record.
 */
class SendNameStore(private val prefs: SharedPreferences) {

    private val _records = MutableStateFlow(decode(prefs.getString(KEY_RECORDS, null)))
    val records: StateFlow<List<SendNameRecord>> = _records.asStateFlow()

    /**
     * Names [toWalletId] as the receiver of each of [txids], sent from [fromWalletId]. Written
     * before the broadcast (commit, not apply), so a send that goes out always has its name.
     * Never throws: a failed write only leaves the rows with today's labels.
     */
    @Synchronized
    fun recordSend(txids: List<String>, fromWalletId: String, toWalletId: String, nowMillis: Long = System.currentTimeMillis()) {
        val ids = txids.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        val next = _records.value.filterNot { it.txid in ids } +
            ids.map { SendNameRecord(txid = it, fromWalletId = fromWalletId, toWalletId = toWalletId, createdAtMillis = nowMillis) }
        save(next)
    }

    /** A removed wallet: every record that names it goes, and its rows elsewhere go back to "Sent" and "Received". */
    @Synchronized
    fun removeWallet(walletId: String) {
        val current = _records.value
        val next = current.filterNot { it.fromWalletId == walletId || it.toWalletId == walletId }
        if (next.size != current.size) save(next)
    }

    /** Remove All. */
    @Synchronized
    fun wipe() {
        try {
            prefs.edit().remove(KEY_RECORDS).commit()
        } catch (e: Exception) {
            Timber.e(e, "SendNames: could not remove the records")
        }
        _records.value = emptyList()
    }

    private fun save(next: List<SendNameRecord>) {
        val ok = try {
            prefs.edit().putString(KEY_RECORDS, json.encodeToString(serializer, next)).commit()
        } catch (e: Exception) {
            Timber.e(e, "SendNames: could not save the records")
            false
        }
        if (ok) _records.value = next
    }

    companion object {
        const val KEY_RECORDS = "send_names.records.v1"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        private val serializer = ListSerializer(SendNameRecord.serializer())

        internal fun decode(encoded: String?): List<SendNameRecord> {
            if (encoded.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString(serializer, encoded)
            } catch (e: Exception) {
                Timber.e("SendNames: the stored records are unreadable (${e.javaClass.simpleName})")
                emptyList()
            }
        }

        @Volatile
        private var instance: SendNameStore? = null

        fun get(context: Context): SendNameStore =
            instance ?: synchronized(this) {
                instance ?: SendNameStore(
                    context.applicationContext.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}

/** The name a History row shows in place of "Sent" or "Received". */
sealed interface SendName {
    /** Sent to another wallet of this app: "To <wallet>", wallet badge. */
    data class ToWallet(val wallet: WalletInfo) : SendName

    /** Sent from another wallet of this app: "From <wallet>", wallet badge. */
    data class FromWallet(val wallet: WalletInfo) : SendName

    /** Sent to this wallet's own address: "To This Wallet". Only the fee left the wallet. */
    data object ToThisWallet : SendName

    /** Sent to a contact's address or OpenAlias name: "To <contact>", person badge. */
    data class ToContact(val contact: Contact) : SendName

    /** Sent to an OpenAlias name that is not a contact: "To <name>". */
    data class ToOpenAlias(val name: String) : SendName
}

/** Transfer between your wallets, and the names History shows for sends between them. */
object SendNames {

    /**
     * The wallet whose saved primary address is [address], else null. Only a saved address
     * counts: a wallet not opened since the update has none, and nothing is derived or guessed.
     */
    fun matchWallet(address: String, wallets: List<WalletInfo>): WalletInfo? {
        val text = address.trim()
        if (text.isEmpty()) return null
        return wallets.firstOrNull { it.cachedPrimaryAddress == text }
    }

    /**
     * wallet2 books a confirmed send to your own account as an outgoing transfer of 0 plus the
     * fee (its "sending to yourself" rule). Sends from before the records existed have no record,
     * so this rule names them too.
     */
    fun isSendToSelf(incoming: Boolean, amount: Long, fee: Long): Boolean =
        !incoming && amount == 0L && fee > 0L

    /**
     * The name of the row of [txid] in the wallet [walletId], or null for today's "Sent" or
     * "Received". Looked up at display time: a renamed wallet shows its new name, and a wallet
     * that is gone names nothing.
     */
    fun nameFor(
        txid: String,
        incoming: Boolean,
        amount: Long,
        fee: Long,
        walletId: String?,
        records: List<SendNameRecord>,
        wallets: List<WalletInfo>,
        /** Where the send went, as wallet2 kept it (only for sends made on this phone). */
        destinations: List<String> = emptyList(),
        contacts: List<Contact> = emptyList(),
        /** OpenAlias pins: the query name and the address last paid for it. */
        pins: Map<String, String> = emptyMap()
    ): SendName? {
        if (walletId == null) return null
        val record = records.lastOrNull { it.txid == txid }
        if (!incoming) {
            val to = record?.takeIf { it.fromWalletId == walletId }?.toWalletId
            if (to == walletId) return SendName.ToThisWallet
            if (to != null) wallets.firstOrNull { it.id == to }?.let { return SendName.ToWallet(it) }
            if (isSendToSelf(incoming, amount, fee)) return SendName.ToThisWallet
            return recipientName(destinations, contacts, pins)
        }
        val from = record?.takeIf { it.toWalletId == walletId && it.fromWalletId != walletId }?.fromWalletId
            ?: return null
        return wallets.firstOrNull { it.id == from }?.let { SendName.FromWallet(it) }
    }

    /**
     * A send to a contact (its address, or the address its OpenAlias name was paid at), else to an
     * OpenAlias name that is not a contact, else null. Looked up at display time, so a deleted
     * contact names nothing again.
     */
    fun recipientName(destinations: List<String>, contacts: List<Contact>, pins: Map<String, String>): SendName? {
        val sentTo = destinations.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (sentTo.isEmpty()) return null
        sentTo.firstNotNullOfOrNull { Contacts.forAddress(it, contacts) }?.let { return SendName.ToContact(it) }
        contacts.firstOrNull { contact ->
            contact.isOpenAlias && Contacts.openAliasQuery(contact)?.let { pins[it] } in sentTo
        }?.let { return SendName.ToContact(it) }
        return pins.entries.firstOrNull { it.value in sentTo }?.let { SendName.ToOpenAlias(it.key) }
    }

    /** What a row shows as its amount: a send to this wallet moved only its fee. */
    fun shownAmount(name: SendName?, amount: Long, fee: Long): Long =
        if (name == SendName.ToThisWallet) fee else amount

    /** The wallets a Transfer can go to: every other wallet, in switcher order. */
    fun transferTargets(wallets: List<WalletInfo>, currentWalletId: String?): List<WalletInfo> =
        wallets.filter { it.id != currentWalletId }

    /**
     * The address a Transfer to [wallet] sends to: its saved primary address, when [isValidAddress]
     * accepts it (Send's address check), else null. Null dims the To row ("Open this wallet once
     * to send to it").
     */
    fun transferAddress(wallet: WalletInfo, isValidAddress: (String) -> Boolean): String? =
        wallet.cachedPrimaryAddress?.trim()?.takeIf { it.isNotEmpty() && isValidAddress(it) }
}
