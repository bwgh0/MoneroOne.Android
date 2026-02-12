package one.monero.moneroone.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.StatusDot
import one.monero.moneroone.ui.components.TransactionStatus
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.PendingOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.ErrorRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class TransactionFilter(val label: String) {
    ALL("All"),
    RECEIVED("Received"),
    SENT("Sent"),
    PENDING("Pending")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onTransactionClick: (String) -> Unit
) {
    val walletState by walletViewModel.walletState.collectAsState()
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = remember(walletState.transactions, selectedFilter, searchQuery) {
        walletState.transactions
            .filter { tx ->
                when (selectedFilter) {
                    TransactionFilter.ALL -> true
                    TransactionFilter.RECEIVED -> tx.direction == TransactionInfo.Direction.Direction_In
                    TransactionFilter.SENT -> tx.direction == TransactionInfo.Direction.Direction_Out
                    TransactionFilter.PENDING -> tx.confirmations == 0L
                }
            }
            .filter { tx ->
                if (searchQuery.isBlank()) true
                else tx.hash.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareBy<TransactionInfo> { it.confirmations > 0L }  // pending (0 confirmations) first
                    .thenByDescending { it.timestamp }  // newest first within each group
            )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("All Transactions") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by transaction ID...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoneroOrange,
                    cursorColor = MoneroOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MoneroOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredTransactions.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching transactions" else "No transactions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try a different search term",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = filteredTransactions,
                        key = { it.hash }
                    ) { transaction ->
                        TransactionListItem(
                            transaction = transaction,
                            formatXmr = walletViewModel::formatXmr,
                            onClick = { onTransactionClick(transaction.hash) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TransactionListItem(
    transaction: TransactionInfo,
    formatXmr: (Long) -> String,
    onClick: () -> Unit
) {
    val isIncoming = transaction.direction == TransactionInfo.Direction.Direction_In
    val iconColor = if (isIncoming) SuccessGreen else MoneroOrange
    val amountPrefix = if (isIncoming) "+" else "-"

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

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIncoming) "Received" else "Sent",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(transaction.timestamp * 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${formatXmr(transaction.amount)} XMR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncoming) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = statusColor, size = 6.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (status) {
                            TransactionStatus.Pending -> "Pending"
                            TransactionStatus.Locked -> "Locked"
                            TransactionStatus.Confirmed -> "Confirmed"
                            TransactionStatus.Failed -> "Failed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.DAYS.toMillis(1) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
