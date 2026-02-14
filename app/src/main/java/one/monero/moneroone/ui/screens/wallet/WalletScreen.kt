package one.monero.moneroone.ui.screens.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.R
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.components.StatusDot
import one.monero.moneroone.ui.components.SyncStatus
import one.monero.moneroone.ui.components.SyncStatusIndicator

import one.monero.moneroone.ui.components.TransactionStatus
import one.monero.moneroone.ui.components.TransactionStatusIndicator
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    walletViewModel: WalletViewModel,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onTransactionClick: (TransactionInfo) -> Unit,
    onSeeAllTransactionsClick: () -> Unit,
    onBalanceClick: (() -> Unit)? = null,
    priceChange24h: Double? = null
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val currentPrice by walletViewModel.currentPrice.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val isOnline by NetworkMonitor.isConnected.collectAsState()

    // Calculate fiat value from balance × current price with correct currency symbol
    val fiatFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val fiatValue = currentPrice?.price?.let { price ->
        val balanceXmr = walletState.balance.all.toDouble() / 1_000_000_000_000.0
        val fiatAmount = balanceXmr * price
        "≈ ${selectedCurrency.symbol}${fiatFormat.format(fiatAmount)}"
    }
    val unlockedFiatValue = currentPrice?.price?.let { price ->
        val unlockedXmr = walletState.balance.unlocked.toDouble() / 1_000_000_000_000.0
        val fiatAmount = unlockedXmr * price
        "${selectedCurrency.symbol}${fiatFormat.format(fiatAmount)}"
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                walletViewModel.refreshSync()
                walletViewModel.refreshPrice()
                delay(1500)
                isRefreshing = false
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Offline banner
        item {
            AnimatedVisibility(visible = !isOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ErrorRed)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You're offline. Some features may be unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        // Greeting header
        item {
            GreetingHeader()
        }

        // Balance card - always show real data (iOS behavior)
        item {
            BalanceCard(
                balance = walletViewModel.formatXmr(walletState.balance.all),
                unlockedBalance = walletViewModel.formatXmr(walletState.balance.unlocked),
                fiatValue = fiatValue,
                unlockedFiatValue = unlockedFiatValue,
                syncState = walletState.syncState,
                priceChange24h = priceChange24h,
                onClick = onBalanceClick
            )
        }

        // Extra space before buttons
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Action buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    label = "Send",
                    color = MoneroOrange,
                    onClick = onSendClick
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowDownward,
                    label = "Receive",
                    color = SuccessGreen,
                    onClick = onReceiveClick
                )
            }
        }

        // Recent activity header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (walletState.transactions.isNotEmpty()) {
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoneroOrange,
                        modifier = Modifier.clickable { onSeeAllTransactionsClick() }
                    )
                }
            }
        }

        if (walletState.transactions.isEmpty()) {
            item {
                EmptyTransactionsCard(
                    isSyncing = walletState.syncState is SyncState.Syncing ||
                        walletState.syncState is SyncState.Connecting
                )
            }
        } else {
            items(
                items = walletState.transactions
                    .sortedWith(
                        compareBy<TransactionInfo> { it.confirmations > 0L }  // pending (0 confirmations) first
                            .thenByDescending { it.timestamp }  // newest first within each group
                    )
                    .take(5),
                key = { it.hash }
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                    formatXmr = walletViewModel::formatXmr
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
    } // PullToRefreshBox
}

@Composable
private fun GreetingHeader() {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Wallet icon (matching iOS - same background as GlassCard)
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Wallet",
                tint = MoneroOrange,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: String,
    unlockedBalance: String,
    fiatValue: String?,
    unlockedFiatValue: String?,
    syncState: SyncState,
    priceChange24h: Double? = null,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Top row with sync status and price change (matching iOS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Sync status
                val status = when (syncState) {
                    is SyncState.Synced -> SyncStatus.Synced
                    is SyncState.Syncing -> SyncStatus.Syncing
                    is SyncState.Connecting -> SyncStatus.Connecting
                    is SyncState.NotSynced -> SyncStatus.NotConnected
                }
                val progress = (syncState as? SyncState.Syncing)?.progress

                SyncStatusIndicator(
                    status = status,
                    progress = progress,
                    syncState = syncState
                )

                // Price change indicator (moved from bottom to top right like iOS)
                if (priceChange24h != null) {
                    PriceChangeIndicator(priceChange = priceChange24h)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Balance row with Monero logo on left (matching iOS)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Monero logo on left of balance
                MoneroLogo(size = 48.dp)

                Spacer(modifier = Modifier.width(16.dp))

                // Balance amount
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = balance,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "XMR",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Fiat value below balance
                    if (fiatValue != null) {
                        Text(
                            text = fiatValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Available (unlocked) balance if different from total
            if (balance != unlockedBalance) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = unlockedBalance,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = " XMR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (unlockedFiatValue != null) {
                        Text(
                            text = " ($unlockedFiatValue)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MoneroOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Locked until recent transactions confirm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoneroOrange
                    )
                }
            }

            // Sync progress bar
            if (syncState is SyncState.Syncing) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (syncState.progress?.toFloat() ?: 0f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MoneroOrange,
                    trackColor = MoneroOrange.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(4.dp))
                val pct = ((syncState.progress ?: 0.0) * 100).toInt()
                val blocks = syncState.remainingBlocks
                Text(
                    text = if (blocks != null && blocks > 0L)
                        "$pct% synced - ${formatBlockCount(blocks)} blocks remaining"
                    else "$pct% synced",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PriceChangeIndicator(priceChange: Double) {
    val isPositive = priceChange >= 0
    val color = if (isPositive) SuccessGreen else ErrorRed
    val icon = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    val sign = if (isPositive) "+" else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "$sign${String.format("%.2f", priceChange)}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun EmptyTransactionsCard(isSyncing: Boolean) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    color = MoneroOrange,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Syncing transactions...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your transactions will appear here once synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No transactions yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionInfo,
    onClick: () -> Unit,
    formatXmr: (Long) -> String
) {
    val isIncoming = transaction.direction == TransactionInfo.Direction.Direction_In
    val iconColor = if (isIncoming) SuccessGreen else MoneroOrange
    val amountPrefix = if (isIncoming) "+" else "-"

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
                    text = formatRelativeTime(transaction.timestamp * 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Amount and status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${formatXmr(transaction.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncoming) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Status indicator
                val status = when {
                    transaction.isFailed -> TransactionStatus.Failed
                    transaction.confirmations == 0L -> TransactionStatus.Pending
                    transaction.confirmations < 10 -> TransactionStatus.Locked
                    else -> TransactionStatus.Confirmed
                }

                TransactionStatusIndicator(status = status)
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

private fun formatBlockCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.2fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> "$count"
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
