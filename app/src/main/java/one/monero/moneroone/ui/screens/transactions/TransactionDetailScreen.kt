package one.monero.moneroone.ui.screens.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.SecondaryButton
import one.monero.moneroone.ui.components.StatusDot
import one.monero.moneroone.ui.components.TransactionStatus
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.PendingOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    walletViewModel: WalletViewModel,
    txId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val walletState by walletViewModel.walletState.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val transaction = walletState.transactions.find { it.hash == txId }
    val isIncoming = transaction?.direction == TransactionInfo.Direction.Direction_In
    val navTitle = if (isIncoming) "Received" else "Sent"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(navTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (transaction == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Transaction not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                TransactionDetailContent(
                    transaction = transaction,
                    formatXmr = walletViewModel::formatXmr,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailContent(
    transaction: TransactionInfo,
    formatXmr: (Long) -> String,
    context: Context
) {
    val isIncoming = transaction.direction == TransactionInfo.Direction.Direction_In
    val amountColor = if (isIncoming) SuccessGreen else MoneroOrange
    val amountPrefix = if (isIncoming) "+" else "-"
    val typeLabel = if (isIncoming) "Received" else "Sent"

    val status = when {
        transaction.isFailed -> TransactionStatus.Failed
        transaction.confirmations == 0L -> TransactionStatus.Pending
        transaction.confirmations < 10 -> TransactionStatus.Locked
        else -> TransactionStatus.Confirmed
    }

    val statusColor = when (status) {
        TransactionStatus.Pending -> PendingOrange
        TransactionStatus.Locked -> MoneroOrange
        TransactionStatus.Confirmed -> SuccessGreen
        TransactionStatus.Failed -> ErrorRed
    }

    val statusText = when (status) {
        TransactionStatus.Pending -> "Pending"
        TransactionStatus.Locked -> "Locked (${transaction.confirmations}/10 confirmations)"
        TransactionStatus.Confirmed -> "Confirmed"
        TransactionStatus.Failed -> "Failed"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Amount Section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Direction icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(amountColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$amountPrefix${formatXmr(transaction.amount)} XMR",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                if (transaction.fee > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fee: ${formatXmr(transaction.fee)} XMR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    StatusDot(color = statusColor, size = 8.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Details Section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Date
                DetailRow(
                    label = "Date",
                    value = formatDate(transaction.timestamp * 1000)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Confirmations
                DetailRow(
                    label = "Confirmations",
                    value = if (transaction.confirmations >= 10) "10+" else transaction.confirmations.toString()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Block height
                DetailRow(
                    label = "Block Height",
                    value = if (transaction.blockheight > 0) transaction.blockheight.toString() else "Pending"
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Transaction ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transaction ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = transaction.hash,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", transaction.hash))
                            Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MoneroOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Section
        SecondaryButton(
            onClick = {
                val explorerUrl = "https://xmrchain.net/tx/${transaction.hash}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            borderColor = MoneroOrange
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MoneroOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View in Block Explorer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MoneroOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
