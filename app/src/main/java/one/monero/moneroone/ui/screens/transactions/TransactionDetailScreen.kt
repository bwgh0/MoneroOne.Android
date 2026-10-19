package one.monero.moneroone.ui.screens.transactions

import one.monero.moneroone.core.contacts.ContactStore
import one.monero.moneroone.core.wallet.SendName
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.ui.components.rememberSendNameLookup
import one.monero.moneroone.ui.screens.send.ContactEditorDialog
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.util.SeedClipboard
import one.monero.moneroone.core.wallet.*
import one.monero.moneroone.data.util.MoneyFormat
import one.monero.moneroone.ui.components.*
import one.monero.moneroone.ui.theme.*
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.paymentlinks.PaymentLinkTransactionRow
import one.monero.moneroone.paymentlinks.rememberPaymentLinkIndex
import java.text.DateFormat
import java.util.Date

@Composable
fun TransactionDetailScreen(walletViewModel: WalletViewModel, txId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by walletViewModel.walletState.collectAsState()
    val wallet by walletViewModel.activeWallet.collectAsState()
    val history by walletViewModel.priceHistory.collectAsState()
    val price by walletViewModel.currentPrice.collectAsState()
    val currency by walletViewModel.selectedCurrency.collectAsState()
    val transaction = state.transactions.find { it.hash == txId }
    val sendNameOf = rememberSendNameLookup(walletViewModel)
    val sendName = transaction?.let(sendNameOf)
    val wallets by walletViewModel.wallets.collectAsState()
    val contacts by remember { ContactStore.get(context) }.contacts.collectAsState()
    var addingContact by remember { mutableStateOf<String?>(null) }
    val rows = remember(state.addresses, state.transactions, wallet) {
        ReceiveAddressLogic.rows(state.addressesOf(wallet?.id)?.list.orEmpty(), state.transactions, wallet?.addressLabels.orEmpty())
    }
    var txKey by remember(wallet?.id, txId) { mutableStateOf<String?>(null) }
    var keyLoading by remember(wallet?.id, txId) { mutableStateOf(false) }
    var keyUnavailable by remember(wallet?.id, txId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toThisWallet = sendName == SendName.ToThisWallet
    val fields = transaction?.let {
        TransactionHistoryLogic.details(it, rows,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.timestamp * 1000)),
            // A send to this wallet paid nobody: no value rows.
            transactionFiat(it, history, price?.price, currency).takeUnless { toThisWallet },
            price?.price?.let { live -> MoneyFormat.format(it.amount / 1e12 * live, currency, fractionDigits = 2) }?.takeUnless { toThisWallet },
            txKey, sendName)
    }.orEmpty()
    // "Add to Contacts" (ios-18): a send to one known address that is no contact and none of your wallets.
    val newContactDestination = transaction?.let { tx ->
        TransactionHistoryLogic.addToContactsDestination(tx, sendName, contacts) { SendNames.matchWallet(it, wallets) != null }
    }

    fun copy(fieldsToCopy: List<TransactionDetailField>) {
        val text = if (fieldsToCopy.size == 1) fieldsToCopy.single().value else TransactionHistoryLogic.copyAll(fieldsToCopy)
        if (fieldsToCopy.any { it.secret }) SeedClipboard.copy(context, text, tr("Transaction details"))
        else (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(tr("Transaction details"), text))
        Toast.makeText(context, tr("Copied to clipboard"), Toast.LENGTH_SHORT).show()
    }

    // A payment link transaction takes the link's title and shows the link's status.
    val linkIndex = rememberPaymentLinkIndex()
    val link = transaction?.let(linkIndex::match)
    TransactionDetailsContent(
        transaction = transaction, fields = fields, onBack = onBack, onCopy = { copy(listOf(it)) },
        title = link?.title ?: if (toThisWallet) tr("To This Wallet") else null,
        onAddToContacts = newContactDestination?.let { destination -> { addingContact = destination } },
        linkRow = link?.let { { PaymentLinkTransactionRow(it) } },
        onCopyAll = { copy(fields) },
        onExplorer = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xmrchain.net/tx/$txId"))) },
        keyLoading = keyLoading, keyUnavailable = keyUnavailable,
        onShowKey = {
            val id = wallet?.id
            if (id != null && !keyLoading) {
                keyLoading = true
                scope.launch {
                    try {
                        txKey = walletViewModel.transactionKey(id, txId)
                        keyUnavailable = txKey == null
                    } finally { keyLoading = false }
                }
            }
        }
    )
    addingContact?.let { destination ->
        ContactEditorDialog(contact = null, prefill = destination, onDismiss = { addingContact = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailsContent(
    transaction: TransactionInfo?, fields: List<TransactionDetailField>, onBack: () -> Unit,
    onCopy: (TransactionDetailField) -> Unit, onCopyAll: () -> Unit, onExplorer: () -> Unit,
    keyLoading: Boolean, keyUnavailable: Boolean, onShowKey: () -> Unit,
    title: String? = null, linkRow: (@Composable () -> Unit)? = null,
    onAddToContacts: (() -> Unit)? = null
) {
    val incoming = transaction?.direction == TransactionInfo.Direction.Direction_In
    Scaffold(
        containerColor = MoneroTheme.colors.bgGrouped,
        topBar = { TopAppBar(
            title = { Text(title ?: tr(if (incoming) "Received" else "Sent"), style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MoneroTheme.colors.bgGrouped)
        ) }
    ) { padding ->
        if (transaction == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(tr("Transaction not found"))
        } else Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth(), shadow = false, cornerRadius = 16.dp) {
                Column {
                    fields.forEachIndexed { index, field ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            // One TalkBack stop per row, "Fee, Not available", as iOS combines each row.
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(field.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(field.value, style = if (field.secret || field.value.length > 60) MonoCaption else MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (field.label == tr("Amount")) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (field.label == tr("Amount") && incoming) SuccessGreen else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { onCopy(field) }) {
                                Icon(Icons.Default.ContentCopy, tr("Copy %s", field.label), tint = MoneroOrange, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (index != fields.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        if (field.label == tr("Sent to") && onAddToContacts != null) {
                            Text(
                                tr("Add to Contacts"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MoneroOrange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onAddToContacts)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (field.label == tr("Status") && linkRow != null) {
                            linkRow()
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            // iOS TransactionDetailView: the sender of an incoming payment is never known.
            if (incoming) Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Shield, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                Text(tr("Sender address hidden by Monero privacy"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!incoming && fields.none { it.secret }) {
                TextButton(onClick = onShowKey, enabled = !keyLoading, modifier = Modifier.fillMaxWidth()) {
                    if (keyLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(tr("Show transaction key"))
                }
                if (keyUnavailable) Text(tr("Transaction key unavailable on this device"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (fields.any { it.secret }) Text(
                tr("Stored only on the device that originally sent this transaction. Recipients use this key with the transaction ID and destination address to verify the payment."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassButton(onClick = onCopyAll, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, null, tint = MoneroOrange, modifier = Modifier.size(18.dp))
                    Text(tr("Copy All Details"))
                }
            }
            TextButton(onClick = onExplorer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("View in Block Explorer"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
