package one.monero.moneroone.paymentlinks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.core.wallet.addressesOf
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.fiatAt
import one.monero.moneroone.ui.components.rememberReduceMotion
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.truncateMiddle
import one.monero.moneroone.ui.screens.wallet.WalletRowCard
import one.monero.moneroone.ui.screens.wallet.WalletRowCheck

/** SwiftUI's default ease-in-out (0.35 s), the plain cross-fade iOS uses between states. */
private val CrossFade = tween<Float>(350, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

/** The envelope's entrance waits for the sheet to come to rest, at most this long. */
private const val SHEET_REST_TIMEOUT_MS = 800L

/** What the claim sheet shows. */
private sealed interface ClaimUi {
    /** [amount]: the v1 link's amount or the balance a check saw; null for a v2 link not checked yet. */
    data class Checking(val amount: Long?) : ClaimUi

    /** [estimated]: [amount] is what a claim delivers (the balance less the sweep's fee estimate), shown with "~". */
    data class Waiting(val confirmations: Long, val minutes: Long, val amount: Long, val estimated: Boolean = false) : ClaimUi
    data class Ready(val amount: Long, val estimated: Boolean = false) : ClaimUi
    object Claiming : ClaimUi
    data class Done(val amount: Long?, val txid: String?, val wallet: String?, val reclaim: Boolean) : ClaimUi
    object AlreadyClaimed : ClaimUi
    object NoMoney : ClaimUi
    data class Error(val message: String?) : ClaimUi
    object Missing : ClaimUi
}

/**
 * Shows the claim sheet for the queued link once [canShow] (unlocked, a
 * wallet exists, no setup screen up), and the problem alert for a link that
 * cannot open.
 */
@Composable
fun ClaimSheetHost(paymentLinks: PaymentLinksViewModel, walletViewModel: WalletViewModel, canShow: Boolean) {
    val store = paymentLinks.store
    val pendingId by store.pendingOpen.collectAsState()
    val problem by store.intakeProblem.collectAsState()

    if (canShow) {
        problem?.let { parsed ->
            AlertDialog(
                onDismissRequest = store::clearIntakeProblem,
                title = { Text(tr("Can't Open Payment Link")) },
                text = {
                    Text(
                        if (parsed == PaymentLinkParse.NeedsUpdate) tr("Update Monero One to open this link.")
                        else tr("This link is not complete.")
                    )
                },
                confirmButton = { TextButton(onClick = store::clearIntakeProblem) { Text(tr("OK")) } }
            )
        }
    }
    val id = pendingId
    if (canShow && id != null) {
        ClaimSheet(
            id = id,
            paymentLinks = paymentLinks,
            walletViewModel = walletViewModel,
            onDismiss = {
                store.clearClaimWhenReady(id)
                store.setPendingOpen(null)
                paymentLinks.closeSession()
                paymentLinks.clearSweep()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimSheet(
    id: String,
    paymentLinks: PaymentLinksViewModel,
    walletViewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    val sentList by paymentLinks.sent.collectAsState()
    val receivedList by paymentLinks.received.collectAsState()
    val sent = sentList.firstOrNull { it.id == id }
    val received = receivedList.firstOrNull { it.id == id }
    val activeWallet by walletViewModel.activeWallet.collectAsState()
    val wallets by walletViewModel.wallets.collectAsState()
    val walletState by walletViewModel.walletState.collectAsState()
    val session by paymentLinks.session.collectAsState()
    val sweep by paymentLinks.sweep.collectAsState()

    // Own link: this phone sent it, whichever wallet is open (iOS: a sent record). It is a reclaim, by default
    // into the wallet that funded it.
    val own = sent != null
    val result = sweep?.takeIf { it.id == id }
    // A settled link opens to its result, with no check (iOS parity): checking would stop the main wallet on
    // every open, and a check from before our claim is in a block still sees the money. The link's detail
    // checks once for money left behind ([PaymentLinksViewModel.checkLeftover]).
    val settledBefore = result == null && (sent?.let(LinkStatusRules::isSettled) ?: received?.isSettled ?: false)
    val reclaimedBefore = settledBefore && own && sent?.let(LinkStatusRules::baseStatus) == LinkStatus.RECLAIMED
    val hasWords = sent != null || received?.link != null
    // Ours: the amount sent. Sent to us: what a claim delivers, else the v1 link's amount or the balance.
    val amount = sent?.amount ?: received?.claimAmount
    // Sent to us and checked: the balance less the claim sweep's fee estimate.
    val net = received?.netAmount?.takeIf { !own && it > 0 }
    // A received v2 link carries no expiry (0).
    val expiry = (sent?.expiry ?: received?.expiry)?.takeIf { it > 0 }
    // Where the sweep goes: a wallet's main address, the one the switcher shows. The open wallet's
    // live primary (none while its keys failed a check), or a closed wallet's saved one.
    val openPrimary = walletState.addressesOf(activeWallet?.id)?.takeIf { !it.blocked }
        ?.list?.firstOrNull { it.addressIndex == 0 }?.address
    val destinations = remember(wallets, activeWallet?.id, openPrimary) {
        ClaimDestinations.eligible(wallets, activeWallet?.id, openPrimary, ClaimDestinations::isSavedMainAddress)
    }
    var pickedWalletId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val destination = ClaimDestinations.chosen(destinations, pickedWalletId, reclaim = own, fundingWalletId = sent?.walletId)
    // The wallet list (the switcher's rows) slides in over the claim, from the Ready row only.
    var choosing by rememberSaveable(id) { mutableStateOf(false) }

    // The envelope is open only while the sheet is on screen and still has something to check: closing
    // the sheet or going to the background closes it, so the main wallet runs again. A finished claim
    // keeps its result on screen when the app comes back.
    val wantsSession = hasWords && !settledBefore && result == null
    LifecycleStartEffect(id, wantsSession) {
        if (wantsSession) paymentLinks.openSession(id)
        onStopOrDispose { paymentLinks.closeSession() }
    }
    val dismiss by rememberUpdatedState(onDismiss)

    val current = session?.takeIf { it.id == id }
    val ui: ClaimUi = when {
        result != null -> when (val outcome = result.outcome) {
            is SweepOutcome.Sent -> ClaimUi.Done(outcome.amount, outcome.txid, result.walletName, result.kind == SweepKind.RECLAIM)
            SweepOutcome.AlreadyClaimed -> ClaimUi.AlreadyClaimed
            is SweepOutcome.Failed -> ClaimUi.Error(outcome.message)
        }
        // Our own claim reads Done; a link someone else claimed (or that beat our reclaim) reads Already claimed.
        settledBefore && received?.claimedHere == true ->
            ClaimUi.Done(received?.claimedAmount, received?.claimTxid, received?.claimedTo, reclaim = false)
        reclaimedBefore -> ClaimUi.Done(
            null,
            sent?.reclaimTxid?.takeIf { it.isNotEmpty() },
            wallets.firstOrNull { it.derivedWalletId != null && it.derivedWalletId == sent?.reclaimWallet }?.name,
            reclaim = true
        )
        settledBefore -> ClaimUi.AlreadyClaimed
        !hasWords -> ClaimUi.Missing
        current == null || current.phase == EnvelopeSession.Phase.CHECKING -> ClaimUi.Checking(amount)
        current.phase == EnvelopeSession.Phase.ERROR -> ClaimUi.Error(current.error)
        current.phase == EnvelopeSession.Phase.SWEEPING -> ClaimUi.Claiming
        else -> current.snapshot?.let { snapshot ->
            when (val view = LinkStatusRules.claimView(snapshot)) {
                is ClaimView.Waiting -> ClaimUi.Waiting(view.confirmations, view.minutesLeft, net ?: snapshot.balance, estimated = net != null)
                is ClaimView.Ready -> ClaimUi.Ready(net ?: view.unlocked, estimated = net != null)
                ClaimView.AlreadyClaimed -> ClaimUi.AlreadyClaimed
                ClaimView.NoMoney -> ClaimUi.NoMoney
            }
        } ?: ClaimUi.Checking(amount)
    }

    // The envelope (link-card): it opens on a link that holds money (Waiting, Ready) and stays open
    // while the claim runs. It stays sealed while Checking and on the other states, which keep their message.
    val fiatMode by walletViewModel.fiatMode.collectAsState()
    val price by walletViewModel.currentPrice.collectAsState()
    val currency by walletViewModel.selectedCurrency.collectAsState()
    val money: Pair<Long, Boolean>? = when (ui) {
        is ClaimUi.Waiting -> (if (ui.amount > 0) ui.amount else 0L) to ui.estimated
        is ClaimUi.Ready -> ui.amount to ui.estimated
        else -> null
    }
    val shownFigures = money?.let { (atomic, estimated) ->
        envelopeFigures(walletViewModel.formatXmr(atomic), fiatAt(atomic, price?.price, currency), estimated, fiatMode)
    }
    // While the claim runs, the card keeps the amount it showed.
    var lastFigures by remember(id) { mutableStateOf<EnvelopeFigures?>(null) }
    SideEffect { if (shownFigures != null) lastFigures = shownFigures }
    val figures = shownFigures ?: lastFigures
    val opens = money != null || ui == ClaimUi.Claiming
    val showsEnvelope = ui !is ClaimUi.Done && ui != ClaimUi.Missing
    val reduceMotion = rememberReduceMotion()
    val motion = remember(id) { EnvelopeMotion() }
    // While the link is checked, the light crosses the sealed envelope.
    val checking = ui is ClaimUi.Checking
    SideEffect { motion.checking = checking }

    val ready = ui is ClaimUi.Ready
    val sweeping = current?.phase == EnvelopeSession.Phase.SWEEPING
    val canChoose = destinations.size > 1 && !sweeping
    LaunchedEffect(ready, canChoose) { if (!ready || !canChoose) choosing = false }
    val showList = choosing && ready && canChoose

    // From a ready row at the top of Activity: claim once this sheet's own check finds the link ready.
    val claimWhenReady by paymentLinks.store.claimWhenReady.collectAsState()
    LaunchedEffect(ui, claimWhenReady, destination) {
        if (ui is ClaimUi.Ready && !own && claimWhenReady == id && destination?.address != null) {
            paymentLinks.store.clearClaimWhenReady(id)
            paymentLinks.sweepOpenEnvelope(SweepKind.CLAIM, destination)
        }
    }

    // The sweep's kit stays open until the node answers. The sheet state is keyed on its
    // confirmValueChange: a lambda that captured [ui] made a new, hidden state on each
    // recomposition, and the sheet slid in again (twice after a claim). One lambda, read through State.
    val claiming by rememberUpdatedState(ui == ClaimUi.Claiming)
    val keepWhileClaiming = remember { { value: SheetValue -> value != SheetValue.Hidden || !claiming } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = keepWhileClaiming)
    ModalBottomSheet(
        onDismissRequest = { if (ui != ClaimUi.Claiming) dismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // System back leaves the list for the claim, as the back arrow does; the sheet stays. It must
        // sit in the sheet's content: there it reaches the sheet dialog's back dispatcher, after the
        // sheet's own dismiss callback, so it wins.
        BackHandler(enabled = showList) { choosing = false }
        // The envelope enters once the sheet is at rest, then opens for a link that holds money.
        val view = LocalView.current
        LaunchedEffect(motion) {
            withTimeoutOrNull(SHEET_REST_TIMEOUT_MS) {
                snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
            }
            motion.enter(reduceMotion)
        }
        LaunchedEffect(motion, opens) {
            if (opens) motion.open(view, reduceMotion) else motion.seal()
        }
        LaunchedEffect(motion) { motion.shine(reduceMotion) }
        // The list slides in from the trailing edge and the sheet's height follows (iOS pushes it).
        AnimatedContent(
            targetState = showList,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(Motion.stepForward()) { it } togetherWith slideOutHorizontally(Motion.stepForward()) { -it })
                        .using(SizeTransform(clip = true) { _, _ -> Motion.stepForward() })
                } else {
                    (slideInHorizontally(Motion.stepBack()) { -it } togetherWith slideOutHorizontally(Motion.stepBack()) { it })
                        .using(SizeTransform(clip = true) { _, _ -> Motion.stepBack() })
                }
            },
            label = "claimPane"
        ) { list ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    // The claim scrolls when the envelope and the rest outgrow a small screen; the list scrolls by itself.
                    .then(if (list) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(horizontal = 24.dp).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (list) {
                    WalletList(
                        own = own,
                        destinations = destinations,
                        chosenId = destination?.walletId,
                        balanceOf = { choice ->
                            if (choice.isOpen) "${walletViewModel.formatXmr(walletState.balance.all)} XMR"
                            else "${walletViewModel.formatXmr(wallets.firstOrNull { it.id == choice.walletId }?.cachedBalance ?: 0L)} XMR"
                        },
                        onBack = { choosing = false },
                        onPick = {
                            pickedWalletId = it
                            choosing = false
                        }
                    )
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        if (ui != ClaimUi.Claiming) {
                            DismissTextButton(onClick = dismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text(tr("Close")) }
                        }
                        Text(
                            if (own) tr("This is your own link") else tr("Payment Link"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Crossfade(targetState = showsEnvelope, animationSpec = CrossFade, label = "claimEnvelope") { shows ->
                        if (shows) {
                            LinkEnvelopeHero(
                                motion = motion,
                                face = figures?.card,
                                spoken = figures?.spoken,
                                reduceMotion = reduceMotion,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }
                    Crossfade(targetState = ui, animationSpec = CrossFade, label = "claimState") { state ->
                        ClaimBody(
                            state = state,
                            own = own,
                            line = figures?.line,
                            landed = motion.landed,
                            reduceMotion = reduceMotion,
                            destination = destination,
                            canChoose = canChoose,
                            onChoose = { choosing = true },
                            expiry = expiry,
                            canSweep = destination?.address != null,
                            formatXmr = walletViewModel::formatXmr,
                            onSweep = {
                                destination?.let { paymentLinks.sweepOpenEnvelope(if (own) SweepKind.RECLAIM else SweepKind.CLAIM, it) }
                            },
                            onRetry = {
                                paymentLinks.clearSweep()
                                paymentLinks.recheck(id)
                            },
                            onDone = dismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClaimBody(
    state: ClaimUi,
    own: Boolean,
    line: String?,
    landed: Boolean,
    reduceMotion: Boolean,
    destination: ClaimDestination?,
    canChoose: Boolean,
    onChoose: () -> Unit,
    expiry: Long?,
    canSweep: Boolean,
    formatXmr: (Long) -> String,
    onSweep: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            is ClaimUi.Checking -> {
                // The sealed envelope above; the amount from an earlier check, when there is one.
                Message(tr("Checking the link…"), MaterialTheme.typography.headlineSmall)
                state.amount?.let { EstimatedAmount(formatXmr(it), MaterialTheme.typography.titleLarge) }
            }
            is ClaimUi.Waiting -> {
                // The card shows the amount. The lines wait for it to land, then cross-fade in.
                line?.let { Secondary(it, arrival(landed, rise = false, reduceMotion).clearAndSetSemantics {}) }
                Text(
                    tr("%s/10 confirmations", state.confirmations),
                    style = MaterialTheme.typography.titleMedium,
                    color = MoneroOrange,
                    modifier = arrival(landed, rise = false, reduceMotion)
                )
                Secondary(tr("Ready in about %s min", state.minutes), arrival(landed, rise = false, reduceMotion))
                if (own) {
                    PrimaryButton(
                        containerColor = sheetFill(),
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().then(arrival(landed, rise = true, reduceMotion))
                    ) { Text(tr("Reclaim")) }
                }
            }
            is ClaimUi.Ready -> {
                // The card shows the amount. Once it lands, the lines cross-fade in and the wallet row and the button arrive.
                line?.let { Secondary(it, arrival(landed, rise = false, reduceMotion).clearAndSetSemantics {}) }
                if (!own) expiry?.let { Secondary(tr("Claim by %s", formatLinkDate(it)), arrival(landed, rise = false, reduceMotion)) }
                destination?.let {
                    // 24 above the row and 20 below it, with the column's 12.
                    Spacer(Modifier.height(12.dp))
                    DestinationRow(own, it, canChoose, onChoose, arrival(landed, rise = true, reduceMotion))
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    containerColor = sheetFill(),
                    onClick = onSweep,
                    enabled = canSweep && landed,
                    modifier = Modifier.fillMaxWidth().then(arrival(landed, rise = true, reduceMotion))
                ) {
                    Text(if (own) tr("Reclaim") else tr("Claim"))
                }
            }
            ClaimUi.Claiming -> {
                // The open envelope stays above.
                Message(if (own) tr("Reclaiming…") else tr("Claiming…"), MaterialTheme.typography.headlineSmall)
            }
            is ClaimUi.Done -> {
                Disc(Icons.Default.Check, SuccessGreen)
                Text(
                    if (state.reclaim) tr("Reclaimed") else tr("Claimed"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
                state.amount?.let { Amount("${formatXmr(it)} XMR") }
                state.txid?.takeIf { it.isNotEmpty() }?.let { txid ->
                    Text(truncateMiddle(txid), style = MonoCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.wallet?.takeIf { it.isNotEmpty() }?.let { Secondary(tr("On its way to %s", it)) }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(containerColor = sheetFill(), onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(tr("Done")) }
            }
            // The sealed envelope above, with today's message.
            ClaimUi.AlreadyClaimed -> {
                Message(tr("This link was already claimed."))
                PrimaryButton(containerColor = sheetFill(), onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(tr("Done")) }
            }
            ClaimUi.NoMoney -> {
                Message(tr("No money in this link yet."))
                PrimaryButton(containerColor = sheetFill(), onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Check Again"))
                }
            }
            is ClaimUi.Error -> {
                Message(tr("Could not check this link."))
                state.message?.takeIf { it.isNotBlank() }?.let { Secondary(it) }
                PrimaryButton(containerColor = sheetFill(), onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Retry"))
                }
            }
            ClaimUi.Missing -> {
                Disc(Icons.Default.Error, MoneroTheme.colors.gray)
                Message(tr("This link is not complete."))
                PrimaryButton(containerColor = sheetFill(), onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(tr("Done")) }
            }
        }
    }
}

/**
 * "Claim to" over the wallet switcher's row for the wallet the money goes to: emoji, name and
 * main address, no balance (the claim amount stays the one amount on the sheet). With two or
 * more wallets to choose from, a chevron shows and a tap opens [WalletList]; with one, the row
 * only shows where the money goes. Rows step up one fill so they never match the sheet in dark mode.
 */
@Composable
private fun DestinationRow(own: Boolean, chosen: ClaimDestination, canChoose: Boolean, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (own) tr("Reclaim to") else tr("Claim to")
    // TalkBack: "Claim to, Savings, 44AFFq5k…7nN3bWpQ", and with a choice, a button that chooses a wallet.
    val spoken = listOfNotNull(label, chosen.name, chosen.address?.let(::truncateMiddle)).joinToString(", ")
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).clearAndSetSemantics {}
        )
        WalletRowCard(
            emoji = chosen.emoji,
            name = chosen.name,
            address = chosen.address,
            containerColor = sheetFill(),
            circleColor = sheetCircleFill(),
            onClick = if (canChoose) onChoose else null,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = spoken
                if (canChoose) {
                    role = Role.Button
                    onClick(label = tr("Choose a wallet")) { onChoose(); true }
                }
            }
        ) {
            if (canChoose) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MoneroTheme.colors.labelTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * The wallets a claim can go to, as the switcher lists them: same rows, balances, ring and
 * check on the chosen one, same order, no rename or reorder. A tap picks the wallet and goes back.
 */
@Composable
private fun WalletList(
    own: Boolean,
    destinations: List<ClaimDestination>,
    chosenId: String?,
    balanceOf: (ClaimDestination) -> String,
    onBack: () -> Unit,
    onPick: (String) -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
        }
        Text(
            if (own) tr("Reclaim to") else tr("Claim to"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center).semantics { heading() }
        )
    }
    Spacer(Modifier.height(4.dp))
    // Scrolls when the wallets outgrow the sheet. The 12dp inside the scroll keeps the first and
    // last cards' shadows from being cut, and with the 4 above it leaves the 16 under the title.
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        destinations.forEach { choice ->
            val chosen = choice.walletId == chosenId
            val balance = balanceOf(choice)
            WalletRowCard(
                emoji = choice.emoji,
                name = choice.name,
                address = choice.address,
                balanceText = balance,
                isActive = chosen,
                containerColor = sheetFill(),
                circleColor = sheetCircleFill(),
                onClick = { onPick(choice.walletId) },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "${choice.name}, $balance"
                    selected = chosen
                    onClick { onPick(choice.walletId); true }
                }
            ) {
                Spacer(Modifier.width(12.dp))
                WalletRowCheck(chosen)
            }
        }
    }
}

/**
 * A row or button on the sheet: fillElevated, so in dark mode it steps up from the #1C1C1E sheet
 * (white on the white sheet in light, with the card shadow). iOS glass does this by itself.
 */
@Composable
private fun sheetFill(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** The row's emoji circle one step above the row: the cell fill in light, fillHighest in dark. */
@Composable
private fun sheetCircleFill(): Color =
    if (MoneroTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHighest else CellFill

/**
 * The rest of the sheet waits for the envelope's card (link-card brief): text cross-fades in, and rows
 * and buttons arrive (fade and a 20 dp rise; no rise under Reduce Motion). Shown from the start, it stays.
 */
@Composable
private fun arrival(shown: Boolean, rise: Boolean, reduceMotion: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (rise) Motion.arrive() else Motion.crossFade(),
        label = "arrival"
    )
    val lift = with(LocalDensity.current) { Motion.ARRIVE_RISE_DP.dp.toPx() }
    return Modifier.graphicsLayer {
        alpha = progress
        if (rise && !reduceMotion) translationY = (1f - progress) * lift
    }
}

@Composable
private fun Disc(icon: ImageVector, hue: Color) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(hue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun Amount(text: String, spoken: String? = null, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Text(
        text,
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { if (spoken != null) contentDescription = spoken }
    )
}

/** An estimate: "~" on screen, "About" for TalkBack (it would read "tilde"). */
@Composable
private fun EstimatedAmount(xmr: String, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Amount(tr("~%s XMR", xmr), spoken = tr("About %s XMR", xmr), style = style)
}

@Composable
private fun Message(text: String, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(text, style = style, textAlign = TextAlign.Center)
}

@Composable
private fun Secondary(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
