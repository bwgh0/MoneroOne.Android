package one.monero.moneroone.ui.screens.transactions

import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.contacts.Contacts
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.ReceiveAddressLogic
import one.monero.moneroone.core.wallet.ReceiveAddressRow
import one.monero.moneroone.core.wallet.SendName
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.data.util.XmrFormat
import java.math.BigDecimal
import java.math.BigInteger

data class TransactionTotals(val count: Int, val received: BigInteger, val sent: BigInteger)
data class FiatTotals(val received: BigDecimal, val sent: BigDecimal)
data class TransactionDetailField(val label: String, val value: String, val secret: Boolean = false)

object TransactionHistoryLogic {
    /**
     * Lazy list keys for transaction rows, one per row and all different.
     * wallet2 can list one transaction more than once (an incoming one paid
     * to two of this wallet's addresses, or a pending row next to its mined
     * one), and a repeated key crashes LazyColumn. A repeat gets a number.
     */
    fun rowKeys(transactions: List<TransactionInfo>): List<String> {
        val seen = HashMap<String, Int>()
        return transactions.map { tx ->
            val key = "${tx.direction}:${tx.hash}"
            val repeat = seen.merge(key, 1, Int::plus)!! - 1
            if (repeat == 0) key else "$key#$repeat"
        }
    }

    /**
     * Transactions up to [asOfMs], inclusive, for a past moment picked in
     * History; every transaction for null. Shared by recent activity and
     * See All, so both stop at the same moment.
     */
    fun through(asOfMs: Long?, transactions: List<TransactionInfo>): List<TransactionInfo> =
        if (asOfMs == null) transactions else transactions.filter { it.timestamp * 1000 <= asOfMs }

    /** [isLink]: whether a transaction belongs to a payment link on this device (the Links filter). */
    fun filter(transactions: List<TransactionInfo>, filter: TransactionFilter, receivingIndex: Int?, query: String,
               addresses: List<ReceiveAddressRow>, isLink: (TransactionInfo) -> Boolean = { false },
               /** The name a row shows ("To Savings", "To Dana"): search finds it too. */
               shownName: (TransactionInfo) -> String? = { null }): List<TransactionInfo> = transactions.filter { tx ->
        val incoming = tx.direction == TransactionInfo.Direction.Direction_In
        val kindMatches = when (filter) {
            TransactionFilter.ALL -> true
            TransactionFilter.RECEIVED -> incoming
            TransactionFilter.SENT -> !incoming
            TransactionFilter.PENDING -> !tx.isFailed && (tx.isPending || tx.confirmations == 0L)
            TransactionFilter.LINKS -> isLink(tx)
        }
        val addressMatches = receivingIndex == null || (incoming && tx.accountIndex == 0 && tx.addressIndex == receivingIndex)
        val row = if (incoming) addresses.firstOrNull { it.index == tx.addressIndex } else null
        kindMatches && addressMatches && (query.isBlank() || run {
            val searchable = listOfNotNull(tx.hash, tx.notes, row?.name, row?.address?.address, tx.address, shownName(tx)) +
                tx.transfers.orEmpty().mapNotNull { it.address }
            searchable.any { it.contains(query.trim(), ignoreCase = true) }
        })
    }.sortedWith(compareBy<TransactionInfo> { !it.isPending && it.confirmations > 0 }.thenByDescending { it.timestamp })

    /**
     * Failed transactions contribute nothing; outgoing totals include fees.
     * A negative fee (see [feeText]) stays signed here and in [fiatTotals]:
     * wallet2 then still reports amount + fee as what left this wallet.
     */
    fun totals(transactions: List<TransactionInfo>): TransactionTotals {
        var received = BigInteger.ZERO
        var sent = BigInteger.ZERO
        val counted = transactions.filterNot { it.isFailed }
        for (tx in counted) {
            val amount = BigInteger.valueOf(tx.amount)
            if (tx.direction == TransactionInfo.Direction.Direction_In) received += amount
            else sent += amount + BigInteger.valueOf(tx.fee)
        }
        return TransactionTotals(counted.size, received, sent)
    }

    /** No partial fiat sum is presented as a complete total. */
    fun fiatTotals(transactions: List<TransactionInfo>, priceAt: (Long) -> Double?): FiatTotals? {
        var received = BigDecimal.ZERO
        var sent = BigDecimal.ZERO
        for (tx in transactions.filterNot { it.isFailed }) {
            val price = priceAt(tx.timestamp)?.takeIf { it.isFinite() && it > 0 } ?: return null
            val incoming = tx.direction == TransactionInfo.Direction.Direction_In
            val atomic = BigInteger.valueOf(tx.amount) + if (incoming) BigInteger.ZERO else BigInteger.valueOf(tx.fee)
            val value = BigDecimal(atomic).movePointLeft(12) * BigDecimal.valueOf(price)
            if (incoming) received += value else sent += value
        }
        return FiatTotals(received, sent)
    }

    /**
     * The subaddress an incoming transaction arrived on, for TalkBack on its
     * row: null for sends and for the main address, so a wallet that uses
     * one address keeps quiet rows. The detail screen always names it.
     */
    fun receivedOnName(tx: TransactionInfo, addresses: List<ReceiveAddressRow>): String? {
        if (tx.direction != TransactionInfo.Direction.Direction_In || tx.accountIndex != 0 || tx.addressIndex == 0) return null
        return addresses.firstOrNull { it.index == tx.addressIndex }?.name
            ?: ReceiveAddressLogic.name(tx.addressIndex, tx.subaddressLabel.orEmpty())
    }

    /**
     * What TalkBack says for a transaction row, in the order the row shows
     * it (iOS TransactionAmountText.spoken): "Received 1.2345 XMR on
     * Savings, worth $150.23 at the time, 5 minutes ago, Confirmed", or in
     * Fiat Mode "Received $150.23 at the time, 1.2345 XMR on Savings, …".
     */
    fun rowLabel(tx: TransactionInfo, fiatMode: Boolean, fiatValue: String?, date: String, receivedOn: String?): String {
        val incoming = tx.direction == TransactionInfo.Direction.Direction_In
        return "${tr(if (incoming) "Received" else "Sent")} ${rowAmount(tx, fiatMode, fiatValue, receivedOn)}, $date, ${rowStatus(tx)}"
    }

    /** The amount part of [rowLabel]: "1.2345 XMR on Savings, worth $150.23 at the time". */
    fun rowAmount(tx: TransactionInfo, fiatMode: Boolean, fiatValue: String?, receivedOn: String?, amount: Long = tx.amount): String {
        val on = receivedOn?.let { tr(" on %s", it) }.orEmpty()
        return if (fiatMode && fiatValue != null) {
            tr("%s at the time, %s XMR", fiatValue, XmrFormat.compact(amount)) + on
        } else {
            "${XmrFormat.format(amount)} XMR" + on + fiatValue?.let { tr(", worth %s at the time", it) }.orEmpty()
        }
    }

    /** The title of a send between your wallets, in place of "Sent" or "Received". */
    fun sendNameTitle(name: SendName): String = when (name) {
        is SendName.ToWallet -> tr("To %s", name.wallet.name)
        is SendName.FromWallet -> tr("From %s", name.wallet.name)
        SendName.ToThisWallet -> tr("To This Wallet")
        is SendName.ToContact -> tr("To %s", name.contact.name)
        is SendName.ToOpenAlias -> tr("To %s", name.name)
    }

    /**
     * [rowLabel] for a named row: "To Savings, 1.2345 XMR, …". [fiatValue] is the value of
     * [SendNames.shownAmount], the fee alone for a send to this wallet.
     */
    fun namedRowLabel(name: SendName, tx: TransactionInfo, fiatMode: Boolean, fiatValue: String?, date: String, receivedOn: String?): String =
        "${sendNameTitle(name)}, ${rowAmount(tx, fiatMode, fiatValue, receivedOn, SendNames.shownAmount(name, tx.amount, tx.fee))}, $date, ${rowStatus(tx)}"

    /**
     * What "Add to Contacts" fills in on a Sent detail (ios-18): the OpenAlias name the send used,
     * else its one destination address. Null when the destination is unknown, or is already a
     * contact or one of your wallets (then the row is hidden).
     */
    fun addToContactsDestination(tx: TransactionInfo, sendName: SendName?, contacts: List<Contact>,
                                 isWallet: (String) -> Boolean): String? {
        if (tx.direction == TransactionInfo.Direction.Direction_In) return null
        when (sendName) {
            is SendName.ToOpenAlias -> return sendName.name
            null -> Unit
            else -> return null
        }
        val destinations = tx.transfers.orEmpty().mapNotNull { it.address?.trim() }.filter { it.isNotEmpty() }.distinct()
        val address = when (destinations.size) {
            0 -> tx.address?.trim()?.takeIf { it.isNotEmpty() }
            1 -> destinations.single()
            else -> null
        } ?: return null
        if (Contacts.forAddress(address, contacts) != null || isWallet(address)) return null
        return address
    }

    /** The status word a row shows beside its amount. */
    fun rowStatus(tx: TransactionInfo): String = when {
        tx.isFailed -> tr("Failed")
        tx.confirmations == 0L -> tr("Pending")
        tx.confirmations < 10 -> tr("Locked")
        else -> tr("Confirmed")
    }

    fun status(tx: TransactionInfo): String = when {
        tx.isFailed -> tr("Failed")
        tx.isPending || tx.confirmations == 0L -> tr("Pending")
        tx.confirmations < 10 -> tr("Locked (%s/10 confirmations)", tx.confirmations)
        else -> tr("Confirmed")
    }

    /**
     * The Fee row of a sent transaction. wallet2 cannot compute the fee of a
     * pre-RingCT send that spent inputs from another wallet: the fee wraps
     * and arrives negative (iOS MoneroKit stores 0 for it). Only sent
     * transactions show a fee, so a coinbase, whose fee is 0, never gets here.
     */
    fun feeText(fee: Long): String = if (fee > 0) "${XmrFormat.format(fee)} XMR" else tr("Not available")

    /** These same fields render the screen and Copy All, so the exported values cannot drift. */
    fun details(tx: TransactionInfo, addresses: List<ReceiveAddressRow>, date: String,
                atTime: String?, today: String?, txKey: String?,
                /** A send to this wallet moved only its fee: its detail reads "To This Wallet" and shows the fee. */
                sendName: SendName? = null): List<TransactionDetailField> = buildList {
        val incoming = tx.direction == TransactionInfo.Direction.Direction_In
        fun field(label: String, value: String?, secret: Boolean = false) {
            if (!value.isNullOrBlank()) add(TransactionDetailField(label, value, secret))
        }
        field(tr("Type"), if (sendName == SendName.ToThisWallet) tr("To This Wallet") else tr(if (incoming) "Received" else "Sent"))
        // A send to this wallet moved only its fee, pending and confirmed, as its row shows.
        field(tr("Amount"), "${if (incoming) "+" else "−"}${XmrFormat.format(SendNames.shownAmount(sendName, tx.amount, tx.fee))} XMR")
        if (!incoming) field(tr("Fee"), feeText(tx.fee))
        field(tr(if (incoming) "Value when received" else "Value when sent"), atTime)
        field(tr("Value today"), today)
        field(tr("Status"), status(tx))
        field(tr("Confirmations"), tx.confirmations.toString())
        field(tr("Date"), date)
        field(tr("Block Height"), if (tx.blockheight > 0) tx.blockheight.toString() else tr("Pending"))
        field(tr("Memo"), tx.notes)
        field(tr("Transaction ID"), tx.hash)
        if (incoming) {
            val row = addresses.firstOrNull { it.index == tx.addressIndex && tx.accountIndex == 0 }
            val name = row?.name ?: ReceiveAddressLogic.name(tx.addressIndex, tx.subaddressLabel.orEmpty())
            field(tr("Received on (%s)", name), row?.address?.address ?: tx.address ?: tr("Unavailable"))
        } else {
            val destinations = tx.transfers.orEmpty().filter { !it.address.isNullOrBlank() }
            if (destinations.isEmpty()) field(tr("Recipient"), tx.address?.takeIf { it.isNotBlank() }
                ?: tr("Not available for transactions sent before this wallet was restored"))
            destinations.forEachIndexed { index, transfer ->
                val label = if (destinations.size == 1) tr("Sent to") else tr("Sent to (%s of %s)", index + 1, destinations.size)
                field(label, if (destinations.size == 1) transfer.address else "${transfer.address}\n${XmrFormat.format(transfer.amount)} XMR")
            }
        }
        field(tr("Transaction Key"), txKey, secret = true)
        field(tr("Block Explorer"), "https://xmrchain.net/tx/${tx.hash}")
    }

    fun copyAll(fields: List<TransactionDetailField>): String = fields.joinToString("\n") { "${it.label}: ${it.value}" }
}
