package one.monero.moneroone.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.model.HistoricalPrice
import one.monero.moneroone.data.model.PriceHistory
import one.monero.moneroone.data.util.MoneyFormat
import one.monero.moneroone.data.util.XmrFormat
import one.monero.moneroone.ui.theme.SuccessGreen

fun transactionFiat(tx: TransactionInfo, history: List<HistoricalPrice>, live: Double?, currency: Currency): String? =
    amountFiat(tx.amount, tx.timestamp, history, live, currency)

/** [atomic] in the user's currency at the price of [timestamp] (seconds). */
fun amountFiat(atomic: Long, timestamp: Long, history: List<HistoricalPrice>, live: Double?, currency: Currency): String? =
    PriceHistory.priceAt(timestamp, history, live, System.currentTimeMillis() / 1000)?.let { fiatAt(atomic, it, currency) }

/** [atomic] in the user's currency at [price]. */
fun fiatAt(atomic: Long, price: Double?, currency: Currency): String? =
    price?.let { MoneyFormat.format(atomic / 1_000_000_000_000.0 * it, currency, fractionDigits = 2) }

/** A row's transaction status: Failed, Pending (0 confirmations), Locked (under 10) or Confirmed. */
fun transactionStatus(transaction: TransactionInfo): TransactionStatus = when {
    transaction.isFailed -> TransactionStatus.Failed
    transaction.confirmations == 0L -> TransactionStatus.Pending
    transaction.confirmations < 10 -> TransactionStatus.Locked
    else -> TransactionStatus.Confirmed
}

/** Shared by the dashboard and history so their amount and spoken order agree. */
@Composable
fun TransactionAmounts(
    transaction: TransactionInfo,
    fiatMode: Boolean,
    fiatValue: String?,
    /** What the row shows: the fee alone for a send to this wallet ([SendNames.shownAmount]). */
    amount: Long = transaction.amount
) {
    val incoming = transaction.direction == TransactionInfo.Direction.Direction_In
    val sign = if (incoming) "+" else "−"
    val fiatFirst = fiatMode && fiatValue != null
    val status = transactionStatus(transaction)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = sign + if (fiatFirst) fiatValue else "${XmrFormat.format(amount)} XMR",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (incoming) SuccessGreen else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
        TransactionStatusIndicator(status = status)
        val secondary = if (fiatFirst) "${XmrFormat.compact(amount)} XMR" else fiatValue
        if (secondary != null) Text(
            text = secondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * The names of the open wallet's sends between your wallets ("To Savings", "From Daily",
 * "To This Wallet"), looked up at display time from the app-wide records, so renames show.
 */
@Composable
fun rememberSendNameLookup(walletViewModel: one.monero.moneroone.core.wallet.WalletViewModel): (TransactionInfo) -> one.monero.moneroone.core.wallet.SendName? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember { one.monero.moneroone.core.wallet.SendNameStore.get(context) }
    val records by store.records.collectAsState()
    val wallets by walletViewModel.wallets.collectAsState()
    val active by walletViewModel.activeWallet.collectAsState()
    val contacts by androidx.compose.runtime.remember { one.monero.moneroone.core.contacts.ContactStore.get(context) }
        .contacts.collectAsState()
    val transactions = walletViewModel.walletState.collectAsState().value.transactions
    val walletId = active?.id
    // OpenAlias pins change after a send, so they are read again when the history changes.
    val pinStore = androidx.compose.runtime.remember { one.monero.moneroone.core.openalias.OpenAliasPinStore.create(context.applicationContext) }
    val pins = androidx.compose.runtime.remember(transactions) { pinStore.pinnedAddresses() }
    val pinNames = androidx.compose.runtime.remember(transactions) { pinStore.pinnedNames() }
    return androidx.compose.runtime.remember(records, wallets, walletId, contacts, pins, pinNames) {
        { tx ->
            SendNames.nameFor(
                txid = tx.hash.orEmpty(),
                incoming = tx.direction == TransactionInfo.Direction.Direction_In,
                amount = tx.amount,
                fee = tx.fee,
                walletId = walletId,
                records = records,
                wallets = wallets,
                destinations = tx.transfers.orEmpty().mapNotNull { it.address } + listOfNotNull(tx.address),
                contacts = contacts,
                pins = pins,
                pinNames = pinNames
            )
        }
    }
}
