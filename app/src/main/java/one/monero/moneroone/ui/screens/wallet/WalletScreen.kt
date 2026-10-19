package one.monero.moneroone.ui.screens.wallet

import one.monero.moneroone.core.locale.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.horizontalsystems.monerokit.SyncState
import io.horizontalsystems.monerokit.model.TransactionInfo
import one.monero.moneroone.R
import one.monero.moneroone.paymentlinks.PaymentLinkActivityRules
import one.monero.moneroone.paymentlinks.PaymentLinkIndex
import one.monero.moneroone.paymentlinks.OpenLinkRules
import one.monero.moneroone.paymentlinks.OpenLinksLines
import one.monero.moneroone.paymentlinks.PaymentLinkStore
import one.monero.moneroone.paymentlinks.PaymentLinksViewModel
import one.monero.moneroone.paymentlinks.PendingLinkState
import one.monero.moneroone.paymentlinks.linkRowText
import one.monero.moneroone.paymentlinks.LinkRowAmounts
import one.monero.moneroone.ui.components.ActivityRow
import one.monero.moneroone.paymentlinks.rememberDaemonHeight
import one.monero.moneroone.paymentlinks.rememberPaymentLinkIndex
import one.monero.moneroone.core.wallet.ReceiveAddressLogic
import one.monero.moneroone.core.wallet.SendState
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.core.wallet.addressesOf
import one.monero.moneroone.ui.components.rememberChartDateFormats
import one.monero.moneroone.ui.screens.chart.HistoryPrices
import one.monero.moneroone.ui.screens.chart.TimeRange
import one.monero.moneroone.ui.screens.transactions.TransactionHistoryLogic
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.RollingText
import one.monero.moneroone.ui.components.ShrinkToFitText
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroLogo
import one.monero.moneroone.ui.components.MoneroRefreshIndicator
import one.monero.moneroone.ui.components.StatusDot
import one.monero.moneroone.ui.components.SyncStatus
import one.monero.moneroone.ui.components.SyncStatusIndicator

import one.monero.moneroone.ui.components.TransactionStatus
import one.monero.moneroone.ui.components.TransactionStatusIndicator
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SystemFill
import one.monero.moneroone.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Banner slide and fade (tokens.json motion.curves.banner). */
private const val BannerMs = 350

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    walletViewModel: WalletViewModel,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onTransactionClick: (TransactionInfo) -> Unit,
    /** Opens every transaction, up to [asOf] (ms) when History shows the past. */
    onSeeAllTransactionsClick: (asOf: Long?) -> Unit,
    history: BalanceHistoryState,
    historyModel: BalanceHistoryModel,
    historyPrices: HistoryPrices,
    onFetchHistoryRange: (TimeRange) -> Unit,
    onAddWalletClick: () -> Unit = {},
    /** Checks open sent links so the balance card's line stays true. */
    paymentLinks: PaymentLinksViewModel? = null,
    /** Opens Activity with the Links filter on. */
    onOpenLinks: () -> Unit = {},
    /** A payment link alert shows over the home. */
    linkAlertShowing: Boolean = false
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val currentPrice by walletViewModel.currentPrice.collectAsState()
    val fiatMode by walletViewModel.fiatMode.collectAsState()
    val priceHistory by walletViewModel.priceHistory.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
    val wallets by walletViewModel.wallets.collectAsState()
    val activeWallet by walletViewModel.activeWallet.collectAsState()
    val walletSessionId by walletViewModel.walletSessionId.collectAsState()
    val linkIndex = rememberPaymentLinkIndex()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val isOnline by NetworkMonitor.isConnected.collectAsState()

    // Fiat amounts with the selected currency's symbol, the same way for now and the past.
    val fiatFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val formatFiat: (Double) -> String = { amount -> "${selectedCurrency.symbol}${fiatFormat.format(amount)}" }
    val liveFiat: (Long) -> String? = { atomic -> currentPrice?.price?.let { formatFiat(atomic / 1_000_000_000_000.0 * it) } }

    // History: the ledger it draws, and the past moment picked on it, if any.
    val ledger = rememberBalanceLedger(walletState.balance.all, walletState.transactions, walletSessionId)
    val historicalPoint = historyModel.shown
        ?.takeIf { it.range == history.range }
        ?.let { PortfolioHistory.historicalSelection(history.timestamp, it.points) }
    val asOf = historicalPoint?.timestamp
    val addressRows = remember(walletState.addresses, walletState.transactions, activeWallet?.addressLabels) {
        ReceiveAddressLogic.rows(walletState.addressesOf(activeWallet?.id)?.list.orEmpty(), walletState.transactions, activeWallet?.addressLabels.orEmpty())
    }
    // The newest five by the moment picked (iOS TransactionListLogic.through).
    val sortedTransactions = remember(walletState.transactions) {
        walletState.transactions.sortedWith(
            compareBy<TransactionInfo> { it.confirmations > 0L }  // pending (0 confirmations) first
                .thenByDescending { it.timestamp }  // newest first within each group
        )
    }
    val recentTransactions = remember(sortedTransactions, asOf) {
        if (asOf == null) sortedTransactions.take(5)
        else sortedTransactions.asSequence().filter { it.timestamp * 1000 <= asOf }.take(5).toList()
    }

    // Links that still hold money: a line in the balance card (spec Round 3). A new block can expire one.
    val linkStore = PaymentLinkStore.get(LocalContext.current)
    val daemonHeight = rememberDaemonHeight()
    // A link whose funding wallet is gone (deleted, or restored under a new id) counts for the open wallet.
    val walletIds = remember(wallets) { wallets.mapTo(HashSet()) { it.id } }
    val openLinks = remember(linkIndex, activeWallet?.id, daemonHeight, walletIds) {
        OpenLinkRules.openLinks(linkIndex.sent, linkIndex.received, activeWallet?.id, System.currentTimeMillis() / 1000, walletIds)
    }
    // A look at the open sent links, so a claimed one leaves the line: when the tab appears, comes
    // back to the foreground or shows another wallet, then every 10 minutes while it shows. A look
    // stops the main wallet for a moment, so it waits until the wallet is synced, no send runs and
    // History shows now, and never runs per block.
    if (paymentLinks != null && openLinks.sent.isNotEmpty()) {
        val walletId = activeWallet?.id
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val sendState by walletViewModel.sendState.collectAsState()
        val canLook by rememberUpdatedState(
            walletState.syncState is SyncState.Synced && sendState !is SendState.Sending && asOf == null
        )
        val currentWalletIds by rememberUpdatedState(walletIds)
        LaunchedEffect(paymentLinks, walletId) {
            if (walletId == null) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    snapshotFlow { canLook }.first { it }
                    paymentLinks.lookAtOpenLinks(walletId, currentWalletIds)
                    delay(PaymentLinksViewModel.WALLET_TAB_LOOK_EVERY_MS)
                }
            }
        }
    }
    // A link check stops the synced main wallet for a moment; the card keeps saying Synced meanwhile.
    val heldWallet = paymentLinks?.holdSyncStatus?.collectAsState()?.value
    val shownSyncState = if (heldWallet != null && heldWallet == activeWallet?.id) SyncState.Synced else walletState.syncState
    val claimOpenLink: () -> Unit = {
        val only = openLinks.received.singleOrNull()
        if (only == null) {
            onOpenLinks()
        } else {
            val ready = PaymentLinkActivityRules.pendingState(only, daemonHeight, System.currentTimeMillis()) == PendingLinkState.Ready
            linkStore.openClaim(only.id, autoClaim = ready)
        }
    }

    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = refreshState,
        indicator = {
            MoneroRefreshIndicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
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
    // The switcher's open/closed flag outlives a wallet switch so the collapse
    // can animate (iOS batches prepareSwitchToWallet + isExpanded = false in
    // one withAnimation). Scroll position is the per-wallet state that must
    // reset: a fresh LazyListState per wallet session epoch.
    var switcherExpanded by remember { mutableStateOf(false) }
    val listState = remember(walletSessionId) { LazyListState() }

    // The rating ask after the first send, only while nothing else is open on the home.
    val claimSheetOpen = paymentLinks?.store?.pendingOpen?.collectAsState()?.value != null
    val linkProblemShowing = paymentLinks?.store?.intakeProblem?.collectAsState()?.value != null
    ReviewPromptAfterSend(
        isIdle = !switcherExpanded && !history.expanded && !claimSheetOpen && !linkProblemShowing && !linkAlertShowing
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header, then the dashboard 12dp below it, in one item so the list's
        // 20dp item gap stays out of it. The header row starts right under the
        // status bar: iOS pins it there in a safeAreaBar(spacing: 12).
        item {
            Column {
            GreetingHeader(
                activeWallet = activeWallet,
                expanded = switcherExpanded,
                onToggleSwitcher = { switcherExpanded = !switcherExpanded }
            )

            // Offline banner, 8dp under the greeting as in the iOS header:
            // slides down from the top and fades in, and leaves the same way
            // (tokens.json motion.curves.banner, tween 350). Its slot grows
            // and shrinks on the same tween, so the dashboard below moves
            // with it instead of jumping, as SwiftUI does on iOS.
            AnimatedVisibility(
                visible = !isOnline,
                enter = slideInVertically(tween(BannerMs)) { -it } + fadeIn(tween(BannerMs)) +
                    expandVertically(tween(BannerMs), expandFrom = Alignment.Top),
                exit = slideOutVertically(tween(BannerMs)) { -it } + fadeOut(tween(BannerMs)) +
                    shrinkVertically(tween(BannerMs), shrinkTowards = Alignment.Top)
            ) {
                // Offline is a neutral state, not an error: gray tint, label text.
                val gray = MoneroTheme.colors.gray
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(gray.copy(alpha = 0.1f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = tr("You're offline. Some features may be unavailable."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balance card + actions <-> wallet rows (iOS WalletView: rows slide in
            // from the trailing edge on .snappy(0.35) while the balance block
            // collapses; reversed on the way back). Recent activity below hides
            // instantly, as on iOS.
            AnimatedContent(
                targetState = switcherExpanded,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(Motion.snappySlow()) { it } + fadeIn(Motion.snappySlow()))
                            .togetherWith(slideOutHorizontally(Motion.snappySlow()) { -it / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(Motion.snappySlow()) { -it / 4 } + fadeIn(Motion.snappySlow()))
                            .togetherWith(slideOutHorizontally(Motion.snappySlow()) { it } + fadeOut(tween(160)))
                    }.using(SizeTransform(clip = true) { _, _ -> Motion.snappySlow() })
                },
                contentAlignment = Alignment.TopStart,
                label = "dashboardHead"
            ) { expanded ->
                // The outgoing content stays laid out under the incoming one
                // for the length of the spring. Without this a wallet tapped
                // while the rows are still sliding in hits the balance card
                // underneath and opens the chart (iOS: allowsHitTesting(false)).
                val exiting = transition.targetState == EnterExitState.PostExit
                Box(
                    // The rows sit 20dp under the header, the card 12dp: on
                    // iOS the collapsed balance block keeps its 8pt VStack
                    // gap above the rows.
                    modifier = Modifier.padding(top = if (expanded) 8.dp else 0.dp).pointerInput(exiting) {
                        if (exiting) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                ) {
                if (expanded) {
                    WalletManagerRows(
                        wallets = wallets,
                        activeWallet = activeWallet,
                        liveBalanceText = "${walletViewModel.formatXmr(walletState.balance.all)} XMR",
                        formatXmr = walletViewModel::formatXmr,
                        onSwitch = { wallet ->
                            // Tapping the active row just closes the list. Otherwise
                            // collapse only when the switch was taken; a refused tap
                            // keeps the rows open (iOS prepareSwitchToWallet).
                            if (wallet.id == activeWallet?.id || walletViewModel.switchWallet(wallet.id)) {
                                switcherExpanded = false
                            }
                        },
                        onDelete = { wallet ->
                            walletViewModel.deleteWallet(wallet.id)
                        },
                        onRename = { wallet, name, emoji ->
                            walletViewModel.renameWallet(wallet.id, name, emoji)
                        },
                        onMove = walletViewModel::reorderWallets,
                        onAddWallet = {
                            switcherExpanded = false
                            onAddWalletClick()
                        }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        // A new wallet gets a new card: History closed, its chart not built (iOS .id(walletSessionId)).
                        key(walletSessionId) {
                            BalanceCard(
                                balance = walletState.balance.all,
                                unlockedBalance = walletState.balance.unlocked,
                                formatXmr = walletViewModel::formatXmr,
                                liveFiat = liveFiat,
                                formatFiat = formatFiat,
                                fiatMode = fiatMode,
                                syncState = shownSyncState,
                                isOnline = isOnline,
                                nodeLoginFailed = walletState.nodeLoginFailed,
                                history = history,
                                historicalPoint = historicalPoint,
                                openLinks = { isPast ->
                                    // Kept in place, hidden, while History shows the past.
                                    AnimatedVisibility(
                                        visible = !openLinks.isEmpty,
                                        enter = fadeIn(Motion.snappy()) + expandVertically(Motion.snappy(), expandFrom = Alignment.Top),
                                        exit = fadeOut(Motion.snappy()) + shrinkVertically(Motion.snappy(), shrinkTowards = Alignment.Top)
                                    ) {
                                        // The last links shown stay while the line leaves.
                                        val shown = remember { mutableStateOf(openLinks) }
                                        if (!openLinks.isEmpty) shown.value = openLinks
                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer { alpha = if (isPast) 0f else 1f }
                                                .then(if (isPast) Modifier.clearAndSetSemantics {} else Modifier)
                                        ) {
                                            OpenLinksLines(
                                                open = shown.value,
                                                // Fiat mode leads with fiat, like the balance above.
                                                formatAmount = { atomic ->
                                                    liveFiat(atomic)?.takeIf { fiatMode } ?: "${walletViewModel.formatXmr(atomic)} XMR"
                                                },
                                                enabled = !isPast,
                                                onOpenLinks = { history.timestamp = null; onOpenLinks() },
                                                onClaim = claimOpenLink
                                            )
                                        }
                                    }
                                }
                            ) { isActive ->
                                BalanceHistoryChart(
                                    balance = walletState.balance.all,
                                    ledger = ledger,
                                    isActive = isActive,
                                    model = historyModel,
                                    walletSessionId = walletSessionId,
                                    prices = historyPrices,
                                    state = history,
                                    onFetchRange = onFetchHistoryRange
                                )
                            }
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ArrowUpward,
                                label = tr("Send"),
                                color = MoneroOrange,
                                onClick = {
                                    history.timestamp = null
                                    onSendClick()
                                }
                            )
                            ActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.ArrowDownward,
                                label = tr("Receive"),
                                color = SuccessGreen,
                                onClick = {
                                    history.timestamp = null
                                    onReceiveClick()
                                }
                            )
                        }
                    }
                }
                } // hit-test guard
            }
            } // header + dashboard
        }

        if (!switcherExpanded) {

        // Recent activity header. While History is open it says which moment
        // the rows are from; the line arrives with the chart, so scrubbing
        // never moves the rows below.
        item(key = "recentHeader") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { heading() }
                        .testTag("wallet.activityHeader"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tr("Recent Activity"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    AnimatedVisibility(
                        visible = history.expanded,
                        enter = fadeIn(Motion.snappy()) + expandVertically(Motion.snappy(), expandFrom = Alignment.Top),
                        exit = fadeOut(Motion.snappy()) + shrinkVertically(Motion.snappy(), shrinkTowards = Alignment.Top)
                    ) {
                        ActivityAsOfText(asOf = asOf, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (recentTransactions.isNotEmpty()) {
                    Text(
                        text = tr("See All"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoneroOrange,
                        modifier = Modifier.clickable { onSeeAllTransactionsClick(asOf) }
                    )
                }
            }
        }

        // Open links live in the balance card and the Links filter, not here (spec Round 3).
        if (recentTransactions.isEmpty()) {
            item(key = "recentEmpty") {
                if (asOf != null) {
                    PastEmptyTransactionsCard()
                } else {
                    EmptyTransactionsCard(
                        isSyncing = shownSyncState is SyncState.Syncing ||
                            shownSyncState is SyncState.Connecting
                    )
                }
            }
        } else {
            items(
                items = recentTransactions,
                key = { it.hash }
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    linkIndex = linkIndex,
                    onClick = { onTransactionClick(transaction) },
                    fiatMode = fiatMode,
                    fiatValue = one.monero.moneroone.ui.components.transactionFiat(transaction, priceHistory, currentPrice?.price, selectedCurrency),
                    fiatOf = { one.monero.moneroone.ui.components.amountFiat(it, transaction.timestamp, priceHistory, currentPrice?.price, selectedCurrency) },
                    receivedOn = TransactionHistoryLogic.receivedOnName(transaction, addressRows)
                )
            }
        }

        } // switcherExpanded else

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
    } // PullToRefreshBox
}

@Composable
private fun GreetingHeader(
    activeWallet: one.monero.moneroone.core.wallet.WalletInfo?,
    expanded: Boolean,
    onToggleSwitcher: () -> Unit
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> tr("Good Morning")
        in 12..16 -> tr("Good Afternoon")
        else -> tr("Good Evening")
    }

    // The greeting and the chip stay put whether the list is open or not;
    // only the content below swaps. The chip's ring shows the open state.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // largeTitle on one line; like iOS (minimumScaleFactor 0.7) it shrinks,
        // never below 70%, when a long greeting or a large font scale would
        // run into the wallet chip.
        ShrinkToFitText(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            minScale = 0.7f,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )

        WalletSwitcherButton(
            wallet = activeWallet,
            expanded = expanded,
            onToggle = onToggleSwitcher
        )
    }
}

@Composable
internal fun BalanceCard(
    balance: Long,
    unlockedBalance: Long,
    formatXmr: (Long) -> String,
    /** An amount's value at the live price; null without one. */
    liveFiat: (Long) -> String?,
    formatFiat: (Double) -> String,
    fiatMode: Boolean,
    syncState: SyncState,
    isOnline: Boolean,
    /** The sync stopped on a node that rejected the RPC login: the status names the login. */
    nodeLoginFailed: Boolean = false,
    history: BalanceHistoryState,
    /** The past moment picked on the chart; null is now. */
    historicalPoint: PortfolioPoint?,
    /** The open payment links line, last in the card; told when History shows the past. */
    openLinks: @Composable (isPast: Boolean) -> Unit = {},
    historyChart: @Composable (isActive: Boolean) -> Unit
) {
    val dates = rememberChartDateFormats()
    val isPast = historicalPoint != null
    val shownBalance = historicalPoint?.balance ?: balance
    // A past amount takes the price sampled at that same moment.
    val fiatBalance = if (historicalPoint != null) formatFiat(historicalPoint.value) else liveFiat(balance)
    // Fiat Mode puts fiat on top and XMR under it; with no price yet XMR stays on top.
    val fiatFirst = fiatMode && fiatBalance != null
    val xmrText = formatXmr(shownBalance)
    val hero = if (fiatFirst) fiatBalance!! else xmrText
    val caption = if (fiatFirst) "$xmrText XMR" else fiatBalance?.let { "≈ $it" }
    val balanceLabel = when {
        historicalPoint != null ->
            tr("Historical balance: %s XMR, %s, as of %s", xmrText, fiatBalance.orEmpty(), dates.abbreviatedDateTime(historicalPoint.timestamp))
        fiatFirst -> tr("Balance: %s, %s XMR", fiatBalance, xmrText)
        else -> tr("Balance: %s XMR%s", xmrText, fiatBalance?.let { tr(", approximately %s", it) }.orEmpty())
    }
    // Digits roll briskly while a finger scrubs the chart, a little slower for live changes.
    val digitMs = if (isPast) SCRUB_DIGIT_MS else Motion.DIGIT_MS

    // The chart is built on first open and then kept, closed, so reopening
    // and switching ranges never rebuild it. The first open builds it
    // closed, then opens it once laid out.
    var isHistoryMounted by remember { mutableStateOf(history.expanded) }
    var opensHistoryOnMount by remember { mutableStateOf(false) }
    val toggleHistory: () -> Unit = {
        when {
            history.expanded -> {
                // Back to now first, so the activity list swaps its rows in
                // place while the card closes around it.
                history.timestamp = null
                history.expanded = false
            }
            isHistoryMounted -> history.expanded = true
            else -> {
                opensHistoryOnMount = true
                isHistoryMounted = true
            }
        }
    }

    val currentToggle by rememberUpdatedState(toggleHistory)
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("wallet.balanceCard")) {
        Column(
            // A tap anywhere on the card opens and closes History, as the
            // History button does. The buttons keep their own taps, the open
            // History keeps its taps and scrub, and a drag still scrolls.
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { currentToggle() } }
                .padding(24.dp)
        ) {
            // Status row. The past covers the sync status without replacing
            // it, so the row keeps its height while scrubbing.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val status = when (syncState) {
                        is SyncState.Synced -> SyncStatus.Synced
                        is SyncState.Syncing -> SyncStatus.Syncing
                        is SyncState.Connecting -> SyncStatus.Connecting
                        is SyncState.NotSynced -> SyncStatus.NotConnected
                    }
                    SyncStatusIndicator(
                        status = status,
                        progress = (syncState as? SyncState.Syncing)?.progress,
                        syncState = syncState,
                        isOnline = isOnline,
                        nodeLoginFailed = nodeLoginFailed,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (isPast) 0f else 1f }
                            .then(if (isPast) Modifier.clearAndSetSemantics {} else Modifier)
                    )
                    if (isPast) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ShrinkToFitText(
                                text = tr("Historical balance"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minScale = 0.8f
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                HistoryToggle(expanded = history.expanded, onToggle = toggleHistory)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Balance row with the Monero logo on the left (matching iOS).
            Row(
                modifier = Modifier
                    .testTag("wallet.balanceValue")
                    .clearAndSetSemantics {
                        contentDescription = balanceLabel
                        customActions = listOf(
                            CustomAccessibilityAction(tr(if (history.expanded) "Hide history" else "Show history")) {
                                toggleHistory()
                                true
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                MoneroLogo(size = 48.dp)

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    // One height for every amount, so the card does not
                    // move as a scrub changes how many digits there are.
                    val heroStyle = MaterialTheme.typography.displaySmall
                    val heroHeight = with(LocalDensity.current) { (heroStyle.fontSize * 1.25f).toDp() }
                    Row(
                        modifier = Modifier.height(heroHeight),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val balanceFontSize = when {
                            hero.length <= 10 -> 32.sp
                            hero.length <= 13 -> 26.sp
                            hero.length <= 16 -> 22.sp
                            else -> 18.sp
                        }
                        // Digits roll on change (iOS .contentTransition(.numericText())).
                        RollingText(
                            text = hero,
                            style = heroStyle,
                            fontSize = balanceFontSize,
                            durationMillis = digitMs
                        )
                        if (!fiatFirst) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "XMR",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // The other currency below the amount.
                    if (caption != null) {
                        RollingText(
                            text = caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            durationMillis = digitMs
                        )
                    }
                }
            }

            if (isHistoryMounted || history.expanded) {
                HistoryReveal(
                    expanded = history.expanded,
                    onMounted = {
                        if (opensHistoryOnMount) {
                            opensHistoryOnMount = false
                            history.expanded = true
                        }
                    }
                ) {
                    historyChart(history.expanded)
                }
            }

            // Available (unlocked) balance if different from total. Kept in
            // place, hidden, while viewing the past, so the card does not shrink.
            if (balance != unlockedBalance) {
                val unlockedXmr = formatXmr(unlockedBalance)
                val unlockedFiat = liveFiat(unlockedBalance)
                val amounts = if (fiatFirst && unlockedFiat != null) "$unlockedFiat, $unlockedXmr XMR"
                    else "$unlockedXmr XMR" + unlockedFiat?.let { tr(", approximately %s", it) }.orEmpty()
                Column(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (isPast) 0f else 1f }
                        .clearAndSetSemantics {
                            if (!isPast) contentDescription = tr("Available balance: %s. Some funds locked until recent transactions confirm.", amounts)
                        }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Available:") + " ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (fiatFirst && unlockedFiat != null)
                                "$unlockedFiat ($unlockedXmr XMR)"
                            else "$unlockedXmr XMR" + (unlockedFiat?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                            text = tr("Locked until recent transactions confirm"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MoneroOrange
                        )
                    }
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
                    trackColor = SystemFill,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(4.dp))
                val pct = ((syncState.progress ?: 0.0) * 100).toInt()
                val blocks = syncState.remainingBlocks
                Text(
                    text = if (blocks != null && blocks > 0L)
                        tr("%s%% synced - %s blocks remaining", pct, formatBlockCount(blocks))
                    else tr("%s%% synced", pct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            openLinks(isPast)
        }
    }
}

/**
 * The card's History button: a brand capsule with a chevron that turns
 * as History opens. Compact in the status row; touch finds it within the
 * 48 dp minimum target all the same.
 */
@Composable
private fun HistoryToggle(expanded: Boolean, onToggle: () -> Unit) {
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "historyChevron")
    val onClickLabel = tr(if (expanded) "Hide history" else "Show history")
    Row(
        modifier = Modifier
            .testTag("wallet.historyToggle")
            .clip(CapsuleShape)
            .background(MoneroOrange.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = tr("Balance history")
                stateDescription = tr(if (expanded) "Expanded" else "Collapsed")
                role = Role.Button
                onClick(label = onClickLabel) { onToggle(); true }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ShowChart, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(14.dp))
        Text(
            text = tr("History"),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MoneroOrange,
            maxLines = 1,
            softWrap = false
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MoneroOrange,
            modifier = Modifier.size(14.dp).rotate(chevron)
        )
    }
}

/**
 * Lives under the amount. It keeps its full layout while closed; only its
 * height, clip and opacity animate, on the card's spring (iOS snappy 0.3),
 * so opening and closing never lay the chart out again and everything
 * below moves with the card. The clip cuts the top and bottom only, so
 * markers at the sides are whole while it opens.
 */
@Composable
private fun HistoryReveal(expanded: Boolean, onMounted: () -> Unit, content: @Composable () -> Unit) {
    val reveal = animateFloatAsState(if (expanded) 1f else 0f, Motion.snappy(), label = "historyReveal")
    val overflow = with(LocalDensity.current) { REVEAL_SIDE_OVERFLOW.toPx() }
    val clip = remember(overflow) {
        GenericShape { size, _ -> addRect(Rect(-overflow, 0f, size.width + overflow, size.height)) }
    }
    LaunchedEffect(Unit) { onMounted() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.clip = true
                shape = clip
                alpha = reveal.value.coerceIn(0f, 1f)
            }
            // A tap on the open History is the chart's: after the chart, Now
            // and the ranges see it, it stops here, so a near miss does not
            // reach the card and close History. Only while open: closed, this
            // 0 dp box would still catch taps within the 48 dp touch target.
            .then(
                if (expanded) Modifier.pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                } else Modifier
            )
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val height = (placeable.height * reveal.value).roundToInt().coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        content()
    }
}

/**
 * "As of now" while History shows the present, or the moment picked on the
 * chart. Its digits roll like the balance above while a finger scrubs.
 */
@Composable
private fun ActivityAsOfText(asOf: Long?, style: androidx.compose.ui.text.TextStyle) {
    val dates = rememberChartDateFormats()
    RollingText(
        text = asOf?.let { tr("As of %s", dates.abbreviatedDateTime(it)) } ?: tr("As of now"),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        durationMillis = SCRUB_DIGIT_MS
    )
}

/** Recent activity at a past moment before any transaction. */
@Composable
private fun PastEmptyTransactionsCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = tr("No transactions by this time"),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = tr("Move forward in history or return to Now."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** iOS rolls digits in 0.1 s while a finger scrubs the History chart. */
private const val SCRUB_DIGIT_MS = 100

/** How far chart markers may reach past the card's sides while History opens (iOS HistoryRevealClip). */
private val REVEAL_SIDE_OVERFLOW = 24.dp

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
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // iOS arrow.up/down.circle.fill at 20pt
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
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
                    text = tr("Syncing transactions..."),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr("Your transactions will appear here once synced"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = tr("No transactions yet"),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionInfo,
    /** Payment link rows show the link's name, amount and status. */
    linkIndex: PaymentLinkIndex,
    onClick: () -> Unit,
    fiatMode: Boolean,
    fiatValue: String?,
    /** An amount's value at the time of [transaction]. */
    fiatOf: (Long) -> String?,
    /** The subaddress an incoming payment arrived on, spoken only; null for sends and the main address. */
    receivedOn: String?
) {
    val isIncoming = transaction.direction == TransactionInfo.Direction.Direction_In
    val date = formatRelativeTime(transaction.timestamp * 1000)
    val link = linkRowText(transaction, linkIndex, date, fiatMode, fiatOf, TransactionHistoryLogic.rowStatus(transaction))
    ActivityRow(
        incoming = isIncoming,
        title = link?.title ?: if (isIncoming) tr("Received") else tr("Sent"),
        subtitle = date,
        spoken = link?.spoken ?: TransactionHistoryLogic.rowLabel(transaction, fiatMode, fiatValue, date, receivedOn),
        onClick = onClick,
        linkBadge = link != null,
        confirmations = transaction.confirmations,
        failed = transaction.isFailed
    ) {
        if (link != null) {
            LinkRowAmounts(link, one.monero.moneroone.ui.components.transactionStatus(transaction))
        } else {
            one.monero.moneroone.ui.components.TransactionAmounts(transaction = transaction, fiatMode = fiatMode, fiatValue = fiatValue)
        }
    }
}

/** "1.2K", "3.45M": a block count for a sync caption. */
internal fun formatBlockCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.2fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> "$count"
}

private fun formatRelativeTime(timestamp: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        timestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()
