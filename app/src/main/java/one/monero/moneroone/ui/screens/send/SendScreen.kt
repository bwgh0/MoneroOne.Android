package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.contacts.ContactStore
import one.monero.moneroone.core.contacts.Contacts
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.SendWait
import one.monero.moneroone.data.util.AmountInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.SignedTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.openalias.OpenAlias
import one.monero.moneroone.core.openalias.OpenAliasName
import one.monero.moneroone.core.openalias.OpenAliasSummary
import one.monero.moneroone.core.openalias.RecipientInput
import one.monero.moneroone.core.util.NetworkMonitor
import one.monero.moneroone.core.wallet.ReviewFee
import one.monero.moneroone.core.wallet.ReviewFeeResult
import one.monero.moneroone.core.wallet.ReviewFeeState
import one.monero.moneroone.core.wallet.SendState
import one.monero.moneroone.core.wallet.SendFlow
import one.monero.moneroone.core.wallet.SendRequest
import one.monero.moneroone.core.wallet.SeedValidation
import one.monero.moneroone.core.wallet.SendNameStore
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.paymentlinks.EnvelopeDraft
import one.monero.moneroone.paymentlinks.LinkNames
import one.monero.moneroone.paymentlinks.PaymentLinkCodec
import one.monero.moneroone.paymentlinks.PaymentLinkParse
import one.monero.moneroone.paymentlinks.EnvelopeCardFace
import one.monero.moneroone.paymentlinks.LinkSendResult
import one.monero.moneroone.paymentlinks.envelopeFigures
import one.monero.moneroone.paymentlinks.shareLink
import one.monero.moneroone.paymentlinks.PaymentLinkRecipientToken
import one.monero.moneroone.paymentlinks.PaymentLinkReview
import one.monero.moneroone.paymentlinks.PaymentLinksViewModel
import one.monero.moneroone.paymentlinks.fundingFailedBeforeBroadcast
import one.monero.moneroone.paymentlinks.linkFundingErrorText
import one.monero.moneroone.paymentlinks.linkFundingUnconfirmed
import one.monero.moneroone.ui.components.AuthGateDialog
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.FieldShape
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.OnNavEntryEnd
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.ShrinkToFitText
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.TabularFigures
import one.monero.moneroone.ui.theme.WarningYellow
import one.monero.moneroone.ui.theme.truncateMiddle

internal enum class SendPhase { ADDRESS, AMOUNT, REVIEW, SENDING, SUCCESS, ERROR }

/** Where Send's pre-filled amount came from; the amount step says which (iOS AmountPrefill). */
enum class SendPrefillSource { QR_CODE, LINK }

/** A `monero:` link that arrived while Send is open, already checked by the caller. */
class OpenSendLink(val address: String, val amount: String?)

/**
 * Whether an open Send takes a new payment link (iOS
 * SendFlowPhase.acceptsPaymentRequest): yes until the user confirms. From
 * Confirm on, the link must not touch this flow.
 */
internal fun acceptsPaymentLink(phase: SendPhase, confirmed: Boolean): Boolean =
    !confirmed && phase in listOf(SendPhase.ADDRESS, SendPhase.AMOUNT, SendPhase.REVIEW)

/** Keeps a Send's identity in saved state, so a recreated screen still gets its own send result. */
private val SendFlowSaver = listSaver<SendFlow, String?>(
    save = { listOf(it.walletId, it.id) },
    restore = { SendFlow(walletId = it[0], id = requireNotNull(it[1])) }
)

/** What system back does on a Send step. */
internal sealed interface SendBack {
    /** Leave Send. */
    object Close : SendBack

    /** Go back one step. */
    data class Step(val to: SendPhase) : SendBack

    /** Nothing: the step has no back. */
    object Blocked : SendBack
}

/**
 * System back on each Send step (iOS SendFlowView). Address closes Send; Amount and Review go
 * back one step. Sending, Success and Error have no back: back must never leave a broadcast in
 * flight, and Success and Error leave only through their own buttons (Done, Try Again, Close).
 * A [transfer] starts on Amount, so back there leaves Send for the Transfer screen.
 */
internal fun sendBack(phase: SendPhase, transfer: Boolean = false): SendBack = when (phase) {
    SendPhase.ADDRESS -> SendBack.Close
    SendPhase.AMOUNT -> if (transfer) SendBack.Close else SendBack.Step(SendPhase.ADDRESS)
    SendPhase.REVIEW -> SendBack.Step(SendPhase.AMOUNT)
    SendPhase.SENDING, SendPhase.SUCCESS, SendPhase.ERROR -> SendBack.Blocked
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    walletViewModel: WalletViewModel,
    initialAddress: String? = null,
    initialAmount: String? = null,
    prefillSource: SendPrefillSource? = null,
    incomingLink: OpenSendLink? = null,
    onIncomingLink: (link: OpenSendLink, accepted: Boolean) -> Unit = { _, _ -> },
    initialName: String? = null,
    /** A Transfer: Send's amount step for the wallet with this id, at its saved primary address. */
    transferToId: String? = null,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onSent: () -> Unit,
    // Payment links: the Link capsule, and claim links pasted into the recipient field.
    paymentLinks: PaymentLinksViewModel? = null,
    onClaimLink: (String) -> Unit = {}
) {
    val walletState by walletViewModel.walletState.collectAsState()
    val flow = rememberSaveable(saver = SendFlowSaver) { SendFlow(walletViewModel.activeWallet.value?.id) }
    val sharedSendState by walletViewModel.sendState.collectAsState()
    val sendState = sharedSendState.takeIf { it.flow == flow } ?: SendState.Idle
    val wallets by walletViewModel.wallets.collectAsState()

    // A Transfer goes to the other wallet's saved primary address, checked as a typed address is.
    // Read once: the destination never changes during this Send. Without one, Send closes.
    val isTransfer = transferToId != null
    val transferTo = remember { transferToId?.let { id -> walletViewModel.wallets.value.firstOrNull { it.id == id } } }
    val transferAddress = remember { transferTo?.let { wallet -> SendNames.transferAddress(wallet) { isValidMoneroAddress(it) } } }
    if (isTransfer && transferAddress == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val nameStore = SendNameStore.get(LocalContext.current)

    // Pre-fill arrives via QR / deep link and is untrusted: it must pass the
    // same checks as manual entry or be dropped, so the flow can never skip
    // the phase where the dropped value would have been entered.
    val prefilledAddress = remember {
        transferAddress ?: initialAddress?.trim()?.takeIf { isValidMoneroAddress(it) }
    }
    // An OpenAlias name from a QR code: the flow starts on the address step and
    // looks the name up at once.
    val prefilledName = remember {
        initialName?.trim()?.takeIf { name ->
            prefilledAddress == null &&
                OpenAlias.classify(name).let { it is RecipientInput.Name || it == RecipientInput.Reserved }
        }
    }
    val prefilledAmount = remember {
        initialAmount?.trim()?.takeIf { walletViewModel.parseXmr(it) > 0 }
    }
    // Only skip past the amount step once the balance is actually known to cover
    // it; an unloaded balance reads as 0, which must not silently drop a legitimate
    // amount nor let an over-balance one reach review unchecked.
    val prefillCoveredByBalance = remember {
        val parsed = prefilledAmount?.let { walletViewModel.parseXmr(it) } ?: 0L
        parsed > 0 && parsed <= walletState.balance.unlocked
    }

    // Determine starting phase based on pre-fill. A payment link stops on
    // the amount step, never the review: it is someone else's text, so the
    // user confirms the amount first (iOS SendFlowView.apply).
    val startPhase = when {
        prefilledAddress != null && prefillCoveredByBalance && prefillSource != SendPrefillSource.LINK -> SendPhase.REVIEW
        prefilledAddress != null -> SendPhase.AMOUNT
        else -> SendPhase.ADDRESS
    }

    // Saved state keeps the step and what the user entered through a
    // rotation, a dark mode switch or a font size change.
    var phase by rememberSaveable { mutableStateOf(startPhase) }
    var navigatingForward by remember { mutableStateOf(true) }

    // Send flow state
    var address by rememberSaveable { mutableStateOf(prefilledAddress ?: "") }
    // The field text (an address or an OpenAlias name). `address` stays the
    // only value that reaches the fee estimate and the send.
    var recipientInput by rememberSaveable { mutableStateOf(prefilledAddress ?: prefilledName ?: "") }
    var openAliasSummary by rememberSaveable(stateSaver = OpenAliasSummarySaver) { mutableStateOf<OpenAliasSummary?>(null) }
    val openAlias = rememberOpenAliasLookup()
    val contactStore = ContactStore.get(LocalContext.current)
    val contacts by contactStore.contacts.collectAsState()
    // A prefilled address or name (from a scan) is classified at once, like a paste.
    LaunchedEffect(Unit) { if (recipientInput.isNotEmpty()) openAlias.onInputChanged(recipientInput, immediate = true) }
    var amount by rememberSaveable { mutableStateOf(prefilledAmount ?: "") }
    // What the recipient gets, kept for the Sending and Sent amount (a sweep sends the balance less the fee).
    var sentAtomic by rememberSaveable { mutableLongStateOf(0L) }
    var isSweepAll by rememberSaveable { mutableStateOf(false) }
    var memo by rememberSaveable { mutableStateOf("") }
    // A link's note: this phone only, never in the link or the tx memo.
    var linkNote by rememberSaveable { mutableStateOf("") }
    // The confirmation stays in memory only: after a recreation, Review
    // shows again and Send asks for a new confirmation.
    var sendInProgress by remember { mutableStateOf(false) }
    // Re-authentication is required between confirming and broadcasting: an
    // unlocked, unattended phone must not be able to drain the wallet.
    var showAuthGate by remember { mutableStateOf(false) }
    var amountPrefilledFromQR by rememberSaveable { mutableStateOf(prefilledAmount != null) }
    var amountSource by rememberSaveable { mutableStateOf(prefillSource) }

    // Fee state, built on the REVIEW phase: the review builds and signs the transfer, shows its real fee, and
    // Send broadcasts that same transfer (reviewFee.prepared).
    var reviewFee by remember { mutableStateOf(ReviewFeeState()) }
    // Bumped to build the review's transfer again (the one it built is gone).
    var reviewBuild by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Link mode: the recipient is "Anyone with the link" and the funds go to a new envelope.
    // Saved with the step, so a recreated Review stays a link review. The draft and the signed
    // funding stay in memory only: after a recreation, Review builds them again.
    var linkMode by rememberSaveable { mutableStateOf(false) }
    var linkDraft by remember { mutableStateOf<EnvelopeDraft?>(null) }
    var linkFeeReserve by remember { mutableLongStateOf(0L) }
    // The funding the review built and signed (its real fee shows there); Send broadcasts this one.
    var linkPrepared by remember { mutableStateOf<SignedTransaction?>(null) }
    // Bumped to build the funding again (the one the review built is gone).
    var linkRebuild by remember { mutableIntStateOf(0) }
    var linkFundingError by remember { mutableStateOf<String?>(null) }
    val canSendWithLink = paymentLinks != null && walletViewModel.activeWallet.value?.isViewOnly == false

    // React to send state changes from ViewModel
    LaunchedEffect(sendState) {
        when (sendState) {
            is SendState.Sending -> phase = SendPhase.SENDING
            is SendState.Success -> {
                if (linkMode) linkDraft?.let { paymentLinks?.fundingFinished(it.link.id, success = true) }
                phase = SendPhase.SUCCESS; sendInProgress = false
                openAlias.onSendState(sendState, openAliasSummary, address)
            }
            is SendState.Error -> {
                // A failure before the broadcast needs no envelope scan; any other may have reached the network.
                val error = (sendState as? SendState.Error)?.message.orEmpty()
                if (linkMode) linkDraft?.let {
                    paymentLinks?.fundingFinished(it.link.id, success = false, beforeBroadcast = fundingFailedBeforeBroadcast(error))
                }
                phase = SendPhase.ERROR; sendInProgress = false
            }
            // Sending, restored with no send in progress: the process stopped
            // during the send, so its result is lost. Leave Send. The wallet
            // shows the payment if it went out.
            else -> if (phase == SendPhase.SENDING) onBack()
        }
    }

    // Send clears its result when it leaves the back stack. A recreation also
    // disposes the screen, but the recreated screen still needs the result.
    OnNavEntryEnd("send-flow") { walletViewModel.resetSendState(flow) }

    // Send opens at any time; Review builds once the main wallet runs and is synced. A link check stops the main
    // wallet, so the one that runs stops and no new one starts while Send is open.
    val sendWaitGate = rememberSendWaitGate(walletViewModel)
    DisposableEffect(paymentLinks) {
        paymentLinks?.sendOpened()
        onDispose { paymentLinks?.sendClosed() }
    }
    // Retry on the offline or node banner: open the wallet again (offline, the wait goes on by itself).
    val retryWait: () -> Unit = { if (NetworkMonitor.isConnected.value) walletViewModel.refreshSync() }

    fun goForward(to: SendPhase) {
        navigatingForward = true
        phase = to
    }

    fun goBack(to: SendPhase) {
        // Leaving the review drops the transfer it built: it never goes out, and the next review builds its own.
        // The fee it showed stays while the review slides away.
        if (phase == SendPhase.REVIEW) reviewFee = reviewFee.copy(prepared = null)
        navigatingForward = false
        phase = to
    }

    // A link tapped while Send is open replaces what is on screen and stops on
    // the amount step (iOS SendFlowView.apply). Once the user has confirmed, it
    // is refused: holding it would fill the next Send the user opens on purpose.
    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        // A Transfer keeps its wallet: a link never replaces it.
        val accepted = !isTransfer && acceptsPaymentLink(phase, confirmed = sendInProgress)
        if (accepted) {
            linkMode = false
            linkDraft = null
            address = link.address
            // A link holds an address only (iOS SendFlowView.apply): the field
            // shows it, its row shows at once, and no looked-up name stays.
            recipientInput = link.address
            openAlias.onInputChanged(link.address, immediate = true)
            openAliasSummary = null
            amount = link.amount?.trim()?.takeIf { walletViewModel.parseXmr(it) > 0 } ?: ""
            amountPrefilledFromQR = amount.isNotEmpty()
            amountSource = SendPrefillSource.LINK
            isSweepAll = false
            reviewFee = ReviewFeeState()
            when (phase) {
                SendPhase.ADDRESS -> goForward(SendPhase.AMOUNT)
                SendPhase.REVIEW -> goBack(SendPhase.AMOUNT)
                else -> {}
            }
        }
        onIncomingLink(link, accepted)
    }

    // System back button and gesture. Always enabled, so on Sending, Success and Error it
    // takes the back and does nothing, as iOS (the Send screen must not close mid-broadcast).
    BackHandler {
        when (val back = sendBack(phase, transfer = isTransfer)) {
            SendBack.Close -> onBack()
            is SendBack.Step -> goBack(back.to)
            SendBack.Blocked -> {}
        }
    }

    // A Send to the saved address of one of your wallets reads as a Transfer: the title and Review
    // name the wallet. A wallet wins over a contact; a contact over an OpenAlias name.
    val toWalletShown = transferTo ?: if (!linkMode && openAliasSummary == null) SendNames.matchWallet(address, wallets) else null
    val sendContact = if (linkMode || toWalletShown != null) null else Contacts.forInput(recipientInput, contacts)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (phase) {
                            SendPhase.ADDRESS -> tr("Send XMR")
                            SendPhase.AMOUNT -> if (linkMode) tr("Send with a link") else toWalletShown?.let { tr("Send to %s", it.name) }
                                ?: sendContact?.let { tr("Send to %s", it.name) }
                                ?: openAliasSummary?.let { openAliasAmountTitle(it) }
                                ?: if (address.length > 20) tr("Send to %s", truncateMiddle(address)) else tr("Amount")
                            SendPhase.REVIEW -> tr("Review")
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (phase in listOf(SendPhase.ADDRESS, SendPhase.AMOUNT, SendPhase.REVIEW)) {
                        IconButton(onClick = {
                            when (phase) {
                                SendPhase.ADDRESS -> onBack()
                                SendPhase.AMOUNT -> if (isTransfer) onBack() else goBack(SendPhase.ADDRESS)
                                SendPhase.REVIEW -> goBack(SendPhase.AMOUNT)
                                else -> {}
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                val direction = if (navigatingForward) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                    initialOffsetX = { it * direction }
                ) + fadeIn(animationSpec = tween(200)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                            targetOffsetX = { -it * direction }
                        ) + fadeOut(animationSpec = tween(150))
                    )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Sending and Sent are one screen: the amount stays put while the spinner becomes the check
            // (a link: the check becomes the note in its envelope).
            contentKey = { if (it == SendPhase.SENDING || it == SendPhase.SUCCESS) "status" else it },
            label = "sendPhase"
        ) { currentPhase ->
            when (currentPhase) {
                SendPhase.ADDRESS -> AddressPhase(
                    input = recipientInput,
                    lookup = openAlias,
                    wallets = wallets,
                    contacts = contacts,
                    onInputChange = { text, immediate ->
                        // A pasted claim link opens the claim sheet (which explains a cut-off one); it is never
                        // an address or a name. Typed by hand, a link opens once it is whole.
                        val pasted = text.length - recipientInput.length > 1
                        val link = PaymentLinkCodec.parse(text)
                        val whole = link is PaymentLinkParse.Valid || link == PaymentLinkParse.NeedsUpdate
                        if (paymentLinks != null && (whole || pasted && link != PaymentLinkParse.NotALink)) {
                            // The start of the link typed so far is no address either.
                            if (PaymentLinkCodec.isClaimLink(recipientInput)) {
                                recipientInput = ""
                                openAlias.onInputChanged("", true)
                            }
                            onClaimLink(text.trim())
                        } else {
                            recipientInput = text
                            openAlias.onInputChanged(text, immediate)
                        }
                    },
                    onScanQr = onScanQr,
                    onContinueAddress = {
                        // A link sends to a new envelope: the typed recipient is not the address.
                        if (!linkMode) address = recipientInput.trim()
                        openAliasSummary = null
                        goForward(SendPhase.AMOUNT)
                    },
                    onContinueName = { summary ->
                        address = summary.address
                        openAliasSummary = summary
                        goForward(SendPhase.AMOUNT)
                    },
                    linkMode = linkMode,
                    showLinkButton = canSendWithLink,
                    onLinkModeChange = { on ->
                        linkMode = on
                        linkDraft = null
                        isSweepAll = false
                    }
                )
                SendPhase.AMOUNT -> {
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    AmountPhase(
                        amount = amount,
                        onAmountChange = { amount = it; isSweepAll = false; amountPrefilledFromQR = false },
                        isSweepAll = isSweepAll,
                        onMaxTap = {
                            amount = walletViewModel.formatXmr(walletState.balance.unlocked)
                            isSweepAll = true
                            amountPrefilledFromQR = false
                        },
                        availableBalance = walletViewModel.formatXmr(walletState.balance.unlocked),
                        unlockedBalance = walletState.balance.unlocked,
                        parseXmr = walletViewModel::parseXmr,
                        amountPrefilledFromQR = amountPrefilledFromQR,
                        prefillSource = amountSource,
                        xmrPrice = currentPrice?.price,
                        currencySymbol = selectedCurrency.symbol,
                        memo = if (linkMode) linkNote else memo,
                        onMemoChange = { if (linkMode) linkNote = it.take(LinkNames.NOTE_LIMIT) else memo = it },
                        onContinue = { goForward(SendPhase.REVIEW) },
                        linkMode = linkMode,
                        notePlaceholder = if (linkMode) remember(paymentLinks) { paymentLinks?.nextName() } else null
                    )
                }
                SendPhase.REVIEW -> if (linkMode && paymentLinks != null) {
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    PaymentLinkReview(
                        paymentLinks = paymentLinks,
                        amount = walletViewModel.parseXmr(amount),
                        unlockedBalance = walletState.balance.unlocked,
                        existingDraft = linkDraft,
                        onDraft = { linkDraft = it },
                        formatXmr = walletViewModel::formatXmr,
                        estimateFee = { to, amt -> walletViewModel.estimateFee(to, amt) },
                        prepareFunding = { to, amt -> walletViewModel.prepareSend(to, amt, memo = "Payment link") },
                        gate = sendWaitGate,
                        onRetryWait = retryWait,
                        rebuild = linkRebuild,
                        xmrPrice = currentPrice?.price,
                        currencySymbol = selectedCurrency.symbol,
                        sendInProgress = sendInProgress,
                        note = LinkNames.cleanNote(linkNote).orEmpty(),
                        onConfirm = { draft, reserve, prepared ->
                            linkDraft = draft
                            linkFeeReserve = reserve
                            linkPrepared = prepared
                            sendInProgress = true
                            showAuthGate = true
                        }
                    )
                } else {
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    ReviewPhase(
                        address = address,
                        openAliasSummary = openAliasSummary,
                        fromWallet = if (toWalletShown != null) walletViewModel.activeWallet.value else null,
                        toWallet = toWalletShown,
                        toContact = sendContact,
                        amount = amount,
                        memo = memo,
                        isSweepAll = isSweepAll,
                        amountPrefilledFromQR = amountPrefilledFromQR,
                        onUpgradeToSweepAll = { isSweepAll = true },
                        reviewFee = reviewFee,
                        onReviewFee = { reviewFee = it },
                        rebuild = reviewBuild,
                        unlockedBalance = walletState.balance.unlocked,
                        formatXmr = walletViewModel::formatXmr,
                        parseXmr = walletViewModel::parseXmr,
                        buildFee = { amt, sweep, unlocked ->
                            ReviewFee.build(
                                address, amt, sweep, unlocked,
                                estimate = { a, s -> walletViewModel.estimateFee(address, a, isSweepAll = s) },
                                prepare = { a, s -> walletViewModel.prepareSend(address, a, isSweepAll = s) }
                            )
                        },
                        xmrPrice = currentPrice?.price,
                        currencySymbol = selectedCurrency.symbol,
                        sendInProgress = sendInProgress,
                        gate = sendWaitGate,
                        onRetryWait = retryWait,
                        onConfirm = {
                        sendInProgress = true
                        showAuthGate = true
                    }
                )
                }
                SendPhase.SENDING, SendPhase.SUCCESS -> {
                    val draft = linkDraft
                    val fiatMode by walletViewModel.fiatMode.collectAsState()
                    val currentPrice by walletViewModel.currentPrice.collectAsState()
                    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
                    val context = LocalContext.current
                    // A link's amount is the note's: what the link holds.
                    val sent = if (linkMode) draft?.amount ?: 0L else sentAtomic
                    val xmr = walletViewModel.formatXmr(sent)
                    val fiat = currentPrice?.price?.let { "${selectedCurrency.symbol}${"%.2f".format(sent / 1_000_000_000_000.0 * it)}" }
                    val amount = when {
                        sent <= 0L -> null
                        fiatMode && fiat != null -> SentAmount(fiat, null, "$xmr XMR")
                        else -> SentAmount(xmr, "XMR", fiat?.let { "≈ $it" })
                    }
                    SendStatusPhase(
                        // A link with no draft keeps its sending screen (it has nothing to share).
                        success = currentPhase == SendPhase.SUCCESS && (!linkMode || draft != null),
                        amount = amount,
                        txHash = (sendState as? SendState.Success)?.txHash ?: "",
                        note = transferTo?.let { tr("It shows in %s when you next open that wallet.", it.name) },
                        onDone = onSent,
                        link = if (linkMode && draft != null && amount != null) {
                            LinkSendResult(
                                face = EnvelopeCardFace(amount.big, unit = amount.unit != null),
                                spoken = envelopeFigures(xmr, fiat, estimated = false, fiatMode = fiatMode).spoken,
                                onShare = { shareLink(context, xmr, draft.link) }
                            )
                        } else {
                            null
                        }
                    )
                }
                SendPhase.ERROR -> ErrorPhase(
                    message = linkFundingError
                        ?: (sendState as? SendState.Error)?.message?.let { if (linkMode) linkFundingErrorText(it) else it }
                        ?: tr("Transaction Failed"),
                    canRetry = !linkMode,
                    onRetry = {
                        linkFundingError = null
                        walletViewModel.resetSendState(flow)
                        goBack(SendPhase.REVIEW)
                    },
                    onClose = {
                        walletViewModel.resetSendState(flow)
                        onBack()
                    }
                )
            }
        }
    }

    if (showAuthGate) {
        AuthGateDialog(
            walletViewModel = walletViewModel,
            title = tr("Confirm transaction"),
            subtitle = tr("Confirm this transaction"),
            onAuthenticated = {
                showAuthGate = false
                val draft = linkDraft
                if (linkMode && paymentLinks != null && draft != null) scope.launch {
                    val id = draft.link.id
                    val prepared = linkPrepared
                    val record = paymentLinks.store.sent(id)
                    when {
                        // The funding the review built is gone (the wallet restarted meanwhile): the review builds
                        // it again and shows its fee. Nothing is saved or sent.
                        record == null && (prepared == null || !withContext(Dispatchers.IO) { walletViewModel.isPrepared(prepared) }) -> {
                            linkPrepared = null
                            linkRebuild++
                            sendInProgress = false
                        }
                        // A saved record consumed this link's one funding attempt, which may have gone out.
                        record != null -> {
                            linkFundingError = linkFundingUnconfirmed()
                            phase = SendPhase.ERROR
                            sendInProgress = false
                        }
                        // The record is saved before any broadcast; without it, nothing is sent.
                        !paymentLinks.recordBeforeFunding(
                            draft, walletViewModel.activeWallet.value?.id.orEmpty(), linkFeeReserve, note = linkNote
                        ) -> {
                            // Send flow error: the link's record could not be saved, so no funding went out.
                            linkFundingError = tr("Could not save the payment link. Nothing was sent.")
                            phase = SendPhase.ERROR
                            sendInProgress = false
                        }
                        // The signed funding's txids go on the record before the broadcast: a funding whose
                        // broadcast times out is still found by its txid once it shows.
                        // The transaction the review built goes out as it is: its fee is the one the review showed.
                        else -> walletViewModel.send(
                            flow,
                            draft.address,
                            prepared?.amount ?: (draft.amount + linkFeeReserve),
                            memo = "Payment link",
                            beforeCommit = { signed -> paymentLinks.fundingSigned(id, signed.txids) },
                            prepared = prepared,
                            // The sending screen plays the chime with its check.
                            playChime = false
                        )
                    }
                } else {
                    val prepared = reviewFee.prepared
                    scope.launch {
                        // The transfer the review built is gone (the wallet closed meanwhile): the review builds it
                        // again and shows its fee. Nothing is sent.
                        if (prepared == null || !withContext(Dispatchers.IO) { walletViewModel.isPrepared(prepared) }) {
                            sendInProgress = false
                            reviewBuild++
                            return@launch
                        }
                        // A send to one of your wallets (a Transfer, or its saved address typed, pasted or scanned)
                        // names both sides in History. The name is saved before the broadcast.
                        val fromId = flow.walletId
                        val toId = transferTo?.id ?: SendNames.matchWallet(address, wallets)?.id
                        // The transfer the review built goes out as it is: its fee is the one the review showed.
                        sentAtomic = prepared.sentAmount
                        // The Sent screen plays the chime when its circle closes.
                        walletViewModel.send(
                            flow,
                            SendRequest.of(address, walletViewModel.parseXmr(amount), memo, isSweepAll),
                            prepared = prepared,
                            beforeCommit = if (fromId != null && toId != null) {
                                { signed -> nameStore.recordSend(signed.txids, fromId, toId) }
                            } else null,
                            playChime = false
                        )
                    }
                }
            },
            onCancel = {
                showAuthGate = false
                sendInProgress = false
            }
        )
    }
}

// ============================================================
// PHASE 1: ADDRESS
// ============================================================

@Composable
private fun AddressPhase(
    input: String,
    lookup: OpenAliasLookupModel,
    onInputChange: (text: String, immediate: Boolean) -> Unit,
    onScanQr: () -> Unit,
    onContinueAddress: () -> Unit,
    onContinueName: (OpenAliasSummary) -> Unit,
    linkMode: Boolean = false,
    /** The Link capsule: any wallet that can send (view-only wallets do not see it). */
    showLinkButton: Boolean = false,
    onLinkModeChange: (Boolean) -> Unit = {},
    /** Your wallets: an address that is one's saved primary address shows that wallet's card. */
    wallets: List<WalletInfo> = emptyList(),
    /** Your contacts: one's address or OpenAlias name shows its card; typing lists the matches. */
    contacts: List<Contact> = emptyList()
) {
    val kind = remember(input) { OpenAlias.classify(input) }
    val ownWallet = remember(input, wallets) {
        (kind as? RecipientInput.Address)?.text
            ?.takeIf { isValidMoneroAddress(it) }
            ?.let { SendNames.matchWallet(it, wallets) }
    }
    // A wallet match wins over a contact match.
    val contact = remember(input, contacts, ownWallet) { if (ownWallet != null) null else Contacts.forInput(input, contacts) }
    var showContacts by rememberSaveable { mutableStateOf(false) }
    // The contact picked last: its row keeps the check when the sheet opens again.
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    val status = lookup.status
    val resolved = (status as? LookupStatus.Resolved)?.takeIf { kind is RecipientInput.Name }
    val resolvedSummary = resolved?.summary()
    // While the field holds text and no card, the contacts that match show under it (ios-12).
    val matches = remember(input, contacts, contact, ownWallet, resolvedSummary, linkMode) {
        if (linkMode || contact != null || ownWallet != null || resolvedSummary != null) emptyList() else Contacts.matches(input, contacts)
    }
    val lookingUp = (status as? LookupStatus.Running)?.name?.takeIf { kind is RecipientInput.Name }
    val fieldFocus = remember { FocusRequester() }
    // Set by the card's clear button: the field that comes back gets the cursor.
    var focusFieldOnShow by remember { mutableStateOf(false) }
    val isValid = linkMode || when (kind) {
        // Continue follows the text at once; the row under the field waits for the typing delay.
        is RecipientInput.Address -> isValidMoneroAddress(kind.text)
        is RecipientInput.Name -> resolved != null
        else -> false
    }
    // iOS SendAddressStep: no sending while offline (canContinue needs a network).
    val isOnline by NetworkMonitor.isConnected.collectAsState()
    var confirming by remember { mutableStateOf<LookupStatus.Resolved?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "addressAlpha"
    )
    val offsetY by animateFloatAsState(
        if (visible) 0f else 20f,
        animationSpec = tween(400),
        label = "addressOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // iOS ErrorBanner(.offline) at the top of the step: gray tint, label
        // text, 24 above the field. It appears without an animation, as on iOS.
        if (!isOnline) {
            val gray = MoneroTheme.colors.gray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(gray.copy(alpha = 0.1f))
                    .padding(16.dp)
                    .clearAndSetSemantics { contentDescription = tr("Offline: no internet connection") },
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
                    text = tr("No internet connection. Cannot send."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = tr("Recipient"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Only the field changes: it cross-fades with the payment-link token in
        // place (iOS: opacity, easeInOut 0.2 s), and the buttons below follow its height.
        val fieldMotion = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        Crossfade(
            targetState = linkMode,
            animationSpec = tween(200, easing = fieldMotion),
            modifier = Modifier.animateContentSize(tween(200, easing = fieldMotion)),
            label = "recipient"
        ) { link ->
            if (link) {
                PaymentLinkRecipientToken(onClear = { onLinkModeChange(false) })
            } else {
            // Address field. A resolved name turns it into the recipient card,
            // inside the same rounded field, with the iOS step-forward spring.
            AnimatedContent(
                targetState = ownWallet ?: contact ?: resolvedSummary,
                contentKey = { card -> when (card) { null -> 0; is WalletInfo -> 2; is Contact -> 3; else -> 1 } },
                transitionSpec = {
                    fadeIn(openAliasCardSpring()) togetherWith fadeOut(openAliasCardSpring()) using
                        SizeTransform(clip = false) { _, _ -> openAliasCardSpring(IntSize.VisibilityThreshold) }
                },
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, FieldShape),
                label = "recipientField"
            ) { card ->
                if (card is OpenAliasSummary) {
                    OpenAliasRecipientCard(card, onClear = {
                        focusFieldOnShow = true
                        onInputChange("", true)
                    })
                } else if (card is WalletInfo) {
                    OwnWalletRecipientCard(card, onClear = {
                        focusFieldOnShow = true
                        onInputChange("", true)
                    })
                } else if (card is Contact) {
                    ContactRecipientCard(card, onClear = {
                        focusFieldOnShow = true
                        onInputChange("", true)
                    })
                } else {
                    // Addresses and names are mono caption, as on iOS. TalkBack reads the
                    // typed text, then this label (checked with TalkBack 14.2).
                    val fieldLabel = tr("Recipient address or OpenAlias name")
                    MoneroTextField(
                        value = input,
                        onValueChange = { onInputChange(it, false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .semantics { contentDescription = fieldLabel },
                        placeholder = { Text(tr("Name, address or OpenAlias"), style = MonoCaption) },
                        textStyle = MonoCaption,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            onInputChange(input, true)
                            defaultKeyboardAction(ImeAction.Done)
                        }),
                        trailingIcon = if (input.isNotEmpty()) {
                            { RecipientFieldTrailing(lookingUp, onClear = { onInputChange("", true) }) }
                        } else {
                            { ContactsFieldButton(onClick = { showContacts = true }) }
                        },
                        singleLine = true
                    )
                    LaunchedEffect(Unit) {
                        if (focusFieldOnShow) {
                            focusFieldOnShow = false
                            fieldFocus.requestFocus()
                        }
                    }
                }
            }
            }
        }

        // Inline validation indicator (addresses). On the name path the
        // OpenAlias row takes its place, in a slot one row high while the
        // field has text, so the buttons keep their positions (as on iOS). A link has no recipient to check.
        if (!linkMode && input.isNotEmpty() && matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AddressCheckRow(valid = false, modifier = Modifier.alpha(0f).clearAndSetSemantics { })
                AddressCheckSlot(status)
                OpenAliasStatusRow(status = status, onRetry = lookup::retry)
                OpenAliasResolvedLines(resolvedSummary)
            }
        }

        // The gap above the buttons, and where TalkBack hears a lookup end.
        OpenAliasAnnouncement(lookup, Modifier.fillMaxWidth().height(16.dp))

        // Scan QR, Paste and Link fill the recipient. Scan QR and Paste leave a link
        // for the address they bring. Three capsules stack at large text (iOS AX sizes).
        val linkHint = tr("Sends the money as a link anyone can claim")
        val narrow = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
        // They step aside while the field has typed text and no card (ios-11, ios-12).
        val stackButtons = LocalDensity.current.fontScale >= 1.5f
        val buttonsShown = linkMode || input.isEmpty() || ownWallet != null || contact != null || resolvedSummary != null
        AnimatedVisibility(visible = buttonsShown, enter = fadeIn(openAliasFadeSpec), exit = fadeOut(openAliasFadeSpec)) {
        RecipientButtons(stack = stackButtons) {
            PrimaryButton(onClick = onScanQr, contentPadding = narrow) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(tr("Scan QR"))
            }
            PrimaryButton(
                onClick = {
                    clipboardManager.getText()?.text?.let {
                        if (linkMode) onLinkModeChange(false)
                        onInputChange(it.trim(), true)
                    }
                },
                contentPadding = narrow
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(tr("Paste"))
            }
            if (showLinkButton) {
                // TalkBack and Voice Access use the visible "Link"; the action label explains it.
                PrimaryButton(
                    onClick = { onLinkModeChange(true) },
                    enabled = !linkMode,
                    contentPadding = narrow,
                    modifier = if (linkMode) Modifier else Modifier.semantics {
                        onClick(label = linkHint) { onLinkModeChange(true); true }
                    }
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Link"))
                }
            }
        }
        }

        if (matches.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(matches, key = { it.id }) { match ->
                    ContactRow(match, onClick = { onInputChange(match.destination, true) })
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Continue button
        PrimaryButton(
            onClick = {
                when {
                    linkMode || kind is RecipientInput.Address -> onContinueAddress()
                    resolved == null -> Unit
                    resolved.needsConfirmation -> confirming = resolved
                    else -> onContinueName(resolved.summary())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(openAliasContinueHint(running = kind is RecipientInput.Name && status is LookupStatus.Running)),
            enabled = isValid && isOnline
        ) {
            Text(tr("Continue"))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showContacts) {
        ContactsSheet(
            selectedId = contact?.id ?: pickedId,
            onPick = { picked ->
                showContacts = false
                pickedId = picked.id
                onInputChange(picked.destination, true)
            },
            onDismiss = { showContacts = false }
        )
    }

    confirming?.let { request ->
        OpenAliasContinueDialog(
            request = request,
            onConfirm = {
                confirming = null
                onContinueName(request.summary())
            },
            onDismiss = { confirming = null }
        )
    }
}

/**
 * The recipient buttons: equal capsules 12 apart in one row, or one under
 * another when [stack] is set or a label would not fit its share of the row
 * (like iOS ViewThatFits).
 */
@Composable
private fun RecipientButtons(stack: Boolean, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val count = measurables.size.coerceAtLeast(1)
        val width = constraints.maxWidth
        val cell = (width - gap * (count - 1)) / count
        val inRow = !stack && measurables.all { it.maxIntrinsicWidth(constraints.maxHeight) <= cell }
        if (inRow) {
            val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (cell + gap), (height - p.height) / 2) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                placeables.forEach { p -> p.placeRelative(0, y); y += p.height + gap }
            }
        }
    }
}

// ============================================================
// PHASE 2: AMOUNT
// ============================================================

@Composable
private fun AmountPhase(
    amount: String,
    onAmountChange: (String) -> Unit,
    isSweepAll: Boolean,
    onMaxTap: () -> Unit,
    availableBalance: String,
    unlockedBalance: Long,
    parseXmr: (String) -> Long,
    amountPrefilledFromQR: Boolean,
    prefillSource: SendPrefillSource?,
    xmrPrice: Double?,
    currencySymbol: String,
    memo: String,
    onMemoChange: (String) -> Unit,
    onContinue: () -> Unit,
    // Link mode: no Max (the envelope needs its fee reserve). The memo field becomes the link's
    // note, which stays on this phone (the tx memo stays "Payment link").
    linkMode: Boolean = false,
    /** Link mode: the name the link gets without a note ("Link 9"). */
    notePlaceholder: String? = null
) {
    val parsedAmount = parseXmr(amount)
    val canContinue = parsedAmount > 0 && parsedAmount <= unlockedBalance
    val clipboardManager = LocalClipboardManager.current

    // Fiat mode toggle. Saved, as the amount is, through a recreation.
    var isFiatMode by rememberSaveable { mutableStateOf(false) }
    var fiatString by rememberSaveable { mutableStateOf("") }
    var showMemo by rememberSaveable { mutableStateOf(memo.isNotEmpty()) }

    // Sync fiat ↔ XMR when toggling or typing
    fun syncFiatFromXmr() {
        val price = xmrPrice ?: return
        val xmrVal = amount.toDoubleOrNull() ?: 0.0
        fiatString = if (xmrVal > 0) "%.2f".format(java.util.Locale.US, xmrVal * price) else ""
    }

    fun syncXmrFromFiat() {
        val price = xmrPrice ?: return
        if (price <= 0) return
        val fiatVal = fiatString.toDoubleOrNull() ?: 0.0
        val xmr = fiatVal / price
        onAmountChange(if (xmr > 0) "%.12f".format(java.util.Locale.US, xmr).trimEnd('0').trimEnd('.') else "")
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "amountAlpha")
    val offsetY by animateFloatAsState(if (visible) 0f else 20f, tween(400), label = "amountOffset")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Where the amount came from (iOS SendAmountStep); the donation's
        // suggested amount has no note, as on iOS.
        val prefillNote = when (prefillSource) {
            SendPrefillSource.QR_CODE -> tr("Amount pre-filled from QR code")
            SendPrefillSource.LINK -> tr("Amount pre-filled from payment link")
            null -> null
        }
        if (amountPrefilledFromQR && prefillNote != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(14.dp))
                Text(prefillNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Large amount display
        if (isFiatMode) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Shrinks like iOS (minimumScaleFactor 0.4) instead of clipping.
                ShrinkToFitText(
                    text = fiatString.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayLarge,
                    minScale = 0.4f,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                )
            }
            // XMR conversion below
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "≈ ${amount.ifEmpty { "0" }} XMR",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                // Shrinks like iOS (minimumScaleFactor 0.4) instead of clipping.
                ShrinkToFitText(
                    text = amount.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayLarge,
                    minScale = 0.4f,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "XMR",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fiat toggle capsule
        if (xmrPrice != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f))
                    .clickable {
                        if (isFiatMode) {
                            // Switching back to XMR mode — sync XMR from fiat
                            syncXmrFromFiat()
                            isFiatMode = false
                        } else {
                            // Switching to fiat mode — sync fiat from XMR
                            syncFiatFromXmr()
                            isFiatMode = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFiatMode) {
                        "≈ ${amount.ifEmpty { "0" }} XMR"
                    } else {
                        val fiatVal = (amount.toDoubleOrNull() ?: 0.0) * xmrPrice
                        "≈ ${currencySymbol}${"%.2f".format(fiatVal)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("⇅", style = MaterialTheme.typography.bodyMedium, color = MoneroOrange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Available balance + Paste/Max buttons. The buttons keep their size
        // and the balance wraps in the width that is left, as on iOS.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Available: %s XMR", availableBalance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f))
                    .clickable {
                        // An address or a link on the clipboard is not an amount: Paste leaves the field.
                        clipboardManager.getText()?.text?.let(AmountInput::pasted)?.let { filtered ->
                            if (isFiatMode) { fiatString = filtered; syncXmrFromFiat() }
                            else onAmountChange(filtered)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(14.dp))
                Text(tr("Paste"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
            }
            if (!linkMode) Spacer(modifier = Modifier.width(8.dp))
            if (!linkMode) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MoneroOrange.copy(alpha = 0.15f))
                    .clickable {
                        isFiatMode = false
                        onMaxTap()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(tr("Max"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
            }
        }

        // Add memo
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMemo = !showMemo }
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(memoToggleText(showMemo, linkMode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (showMemo) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = showMemo) {
            MoneroTextField(
                value = memo,
                onValueChange = onMemoChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(notePlaceholder?.takeIf { linkMode } ?: tr("Add a note")) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Numeric keypad
        NumericKeypad(
            onKey = { key ->
                if (isFiatMode) {
                    when (key) {
                        "⌫" -> { if (fiatString.isNotEmpty()) { fiatString = fiatString.dropLast(1); syncXmrFromFiat() } }
                        "." -> { if (!fiatString.contains(".")) { fiatString = if (fiatString.isEmpty()) "0." else "$fiatString."; syncXmrFromFiat() } }
                        else -> {
                            val newFiat = if (fiatString == "0" && key != ".") key else fiatString + key
                            val parts = newFiat.split(".")
                            if (parts.size <= 1 || parts[1].length <= 2) { fiatString = newFiat; syncXmrFromFiat() }
                        }
                    }
                } else {
                    when (key) {
                        "⌫" -> if (amount.isNotEmpty()) onAmountChange(amount.dropLast(1))
                        "." -> {
                            if (!amount.contains(".")) onAmountChange(if (amount.isEmpty()) "0." else "$amount.")
                        }
                        else -> {
                            val newAmount = if (amount == "0" && key != ".") key else amount + key
                            val parts = newAmount.split(".")
                            if (parts.size <= 1 || parts[1].length <= 12) onAmountChange(newAmount)
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Continue button (below keypad, matching iOS)
        PrimaryButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = canContinue
        ) {
            Text(tr("Continue"))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ============================================================
// PHASE 3: REVIEW
// ============================================================

@Composable
private fun ReviewPhase(
    address: String,
    openAliasSummary: OpenAliasSummary?,
    /** A Transfer: the wallet it leaves, over [toWallet]. */
    fromWallet: WalletInfo? = null,
    /** A Transfer: the wallet it goes to, at [address]. Null keeps the Recipient row. */
    toWallet: WalletInfo? = null,
    /** A send to a contact: Review names the contact, as it names a wallet. */
    toContact: Contact? = null,
    amount: String,
    memo: String,
    isSweepAll: Boolean,
    amountPrefilledFromQR: Boolean,
    onUpgradeToSweepAll: () -> Unit,
    reviewFee: ReviewFeeState,
    onReviewFee: (ReviewFeeState) -> Unit,
    /** A new value builds the transfer again (the one built here is gone). */
    rebuild: Int,
    unlockedBalance: Long,
    formatXmr: (Long) -> String,
    parseXmr: (String) -> Long,
    /** Builds and signs the transfer (amount, sweep, unlocked balance). Blocking: called off Main. */
    buildFee: (Long, Boolean, Long) -> ReviewFeeResult,
    xmrPrice: Double?,
    currencySymbol: String,
    sendInProgress: Boolean,
    /** The wait for the main wallet: the review builds only once it runs and is synced. */
    gate: SendWaitGate,
    /** Retry on the slot's offline or node banner. */
    onRetryWait: () -> Unit,
    onConfirm: () -> Unit
) {
    val parsedAmount = parseXmr(amount)
    // The balance when the build starts: the wait for the wallet can outlast the one Review opened with.
    val currentUnlocked by rememberUpdatedState(unlockedBalance)
    var attempt by remember { mutableIntStateOf(0) }
    val wait by gate.collectAsState()

    // Build and sign the transfer when the review opens, and again for a sweep or when the one built is gone.
    // wallet2's fee estimate counts one input; the fee shown is the one this transfer pays, and Send broadcasts it.
    // The build waits until the main wallet runs and is synced (a link check stops it for a moment).
    LaunchedEffect(isSweepAll, rebuild, attempt) {
        onReviewFee(ReviewFeeState(loading = true))
        var result: ReviewFeeResult
        while (true) {
            gate.awaitReady()
            result = try {
                withContext(Dispatchers.IO) { buildFee(parsedAmount, isSweepAll, currentUnlocked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReviewFeeResult.Failed(e.message)
            }
            // The wallet stopped while the transfer was built, or the network just came back: build again
            // instead of failing.
            if (result is ReviewFeeResult.Failed && gate.retriesFailure()) continue
            break
        }
        when (result) {
            is ReviewFeeResult.Ready -> onReviewFee(ReviewFeeState(fee = result.prepared.fee, prepared = result.prepared))
            // Auto-upgrade to sweep-all if amount + fee > balance (this effect then builds the sweep). An amount
            // the user did not type must never silently become the full balance — that upgrade requires an
            // explicit tap instead.
            is ReviewFeeResult.OverBalance ->
                if (!amountPrefilledFromQR) onUpgradeToSweepAll()
                else onReviewFee(ReviewFeeState(fee = result.fee, overBalance = true))
            is ReviewFeeResult.Failed -> onReviewFee(ReviewFeeState(error = result.message ?: tr("Fee estimation failed")))
        }
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "reviewAlpha")
    val offsetY by animateFloatAsState(if (visible) 0f else 20f, tween(400), label = "reviewOffset")

    val feeReady = reviewFee.ready
    val feeLoading = reviewFee.loading
    val feeError = reviewFee.error
    val estimatedFee = reviewFee.fee
    val needsSweepAllChoice = amountPrefilledFromQR && !isSweepAll && feeReady && reviewFee.overBalance

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() }
    ) {
        // The details scroll and the Send button stays below them, as iOS
        // puts them in a ScrollView: a long name or description, or a large
        // text size, never pushes the button off the screen.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // QR warning
            if (amountPrefilledFromQR && !isSweepAll) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(14.dp))
                    Text(tr("Amount pre-filled from QR code"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Transaction card: iOS review table (radius 16, fill, no shadow)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                shadow = false,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    if (toWallet != null) {
                        // A Transfer: From over To, each wallet's emoji, name and address 8…8.
                        fromWallet?.let { from ->
                            ReviewWalletRow(
                                label = tr("From"),
                                wallet = from,
                                address = from.cachedPrimaryAddress?.takeIf { SeedValidation.isPlausiblePrimaryAddress(it) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        ReviewWalletRow(label = tr("To"), wallet = toWallet, address = address)
                    } else if (toContact != null) {
                        ReviewContactRow(toContact, address)
                        // A name keeps its checks (signature, pin) under the row.
                        openAliasSummary?.let { OpenAliasReviewNotes(it, modifier = Modifier.padding(start = 52.dp)) }
                    } else {
                    // Recipient: the transaction-list disc (40, tint 0.15, 20dp glyph).
                    // A name shows its picture, the name, the OpenAlias name and
                    // every character of the address.
                    Row(
                        verticalAlignment = if (openAliasSummary == null) Alignment.CenterVertically else Alignment.Top,
                        modifier = openAliasRecipientSemantics(openAliasSummary, address)
                    ) {
                        if (openAliasSummary != null) {
                            RecipientAvatar(openAliasSummary, diameter = 40.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MoneroOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = if (openAliasSummary != null) Arrangement.spacedBy(2.dp) else Arrangement.Top) {
                            Text(tr("Recipient"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (openAliasSummary != null) {
                                OpenAliasReviewNames(openAliasSummary)
                            } else {
                                Text(
                                    text = truncateMiddle(address),
                                    style = MonoCaption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // The notes start at the text column: the 40 dp disc plus the 12 dp gap.
                    openAliasSummary?.let { OpenAliasReviewNotes(it, modifier = Modifier.padding(start = 52.dp)) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount (centered)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isSweepAll) {
                            Text(tr("All Funds"), style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (feeReady) {
                                // What the built sweep sends: the balance it spends less its fee.
                                val sendAmount = reviewFee.prepared?.sentAmount ?: (unlockedBalance - estimatedFee).coerceAtLeast(0)
                                Text(
                                    "${formatXmr(sendAmount)} XMR",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                xmrPrice?.let { price ->
                                    val fiat = atomicToXmr(sendAmount) * price
                                    Text(
                                        "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                ValuePlaceholder(width = 132.dp, style = MaterialTheme.typography.bodyMedium)
                                if (xmrPrice != null) ValuePlaceholder(width = 56.dp, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                "${formatXmr(parsedAmount)} XMR",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            xmrPrice?.let { price ->
                                val fiat = atomicToXmr(parsedAmount) * price
                                Text(
                                    "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Fee row
                    ReviewDetailRow(
                        label = { Text(tr("Network Fee"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        // Gray placeholders hold the row until the fee is known; an error shows in the status slot.
                        FillIn(
                            ready = feeReady,
                            placeholder = {
                                Column(horizontalAlignment = Alignment.End) {
                                    ValuePlaceholder(width = 104.dp, style = MaterialTheme.typography.bodyMedium)
                                    if (xmrPrice != null) ValuePlaceholder(width = 44.dp, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${formatXmr(estimatedFee)} XMR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                xmrPrice?.let { price ->
                                    val fiat = atomicToXmr(estimatedFee) * price
                                    Text(
                                        "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Total row
                    ReviewDetailRow(
                        label = { Text(tr("Total"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    ) {
                        FillIn(
                            ready = feeReady,
                            placeholder = {
                                Column(horizontalAlignment = Alignment.End) {
                                    ValuePlaceholder(width = 132.dp, style = MaterialTheme.typography.titleSmall)
                                    if (xmrPrice != null) ValuePlaceholder(width = 56.dp, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        ) {
                            val total = reviewFee.prepared?.total ?: (parsedAmount + estimatedFee)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${formatXmr(total)} XMR",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MoneroOrange
                                )
                                xmrPrice?.let { price ->
                                    val fiat = atomicToXmr(total) * price
                                    Text(
                                        "≈ ${currencySymbol}${"%.2f".format(fiat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // The description in the OpenAlias record.
                    openAliasSummary?.txDescription?.let { description ->
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        OpenAliasDescriptionRow(description)
                    }

                    // Memo: the last row, as on iOS, when the user wrote one.
                    if (memo.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        ReviewMemoRow(memo)
                    }
                }
            }

            // Pre-filled amount + fee no longer fits the balance
            if (needsSweepAllChoice) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(18.dp))
                            Text(
                                tr("Amount plus network fee exceeds your available balance"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onUpgradeToSweepAll,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(tr("Send all funds instead"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MoneroOrange)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // One status slot directly above Send: the wait for the wallet, or a real failure.
        SendStatusSlot(
            wait = wait,
            pending = feeLoading,
            error = feeError,
            onRetryWait = onRetryWait,
            onRetryBuild = { attempt++ }
        )

        // Send button
        @OptIn(ExperimentalComposeUiApi::class)
        PrimaryButton(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .sendWaitHint(feeLoading && wait != SendWait.Ready)
                .pointerInteropFilter { event ->
                    (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0
                },
            enabled = feeReady && reviewFee.prepared != null && !sendInProgress && !needsSweepAllChoice
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(tr("Send"))
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

/**
 * The memo toggle's text, as on iOS (SendAmountStep.memoToggleTitle): "Hide memo" while the memo field shows,
 * else "Add memo". In link mode the field holds the link's note: "Hide note" or "Add note".
 */
internal fun memoToggleText(showMemo: Boolean, linkMode: Boolean = false): String = when {
    linkMode -> if (showMemo) tr("Hide note") else tr("Add note")
    else -> if (showMemo) tr("Hide memo") else tr("Add memo")
}

/**
 * The memo row of the Review card, as on iOS: the note icon and the memo.
 * TalkBack reads "Memo" and the memo in one stop.
 */
@Composable
private fun ReviewMemoRow(memo: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = tr("Memo"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(memo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The least room between a Review row's label and its value on one line. */
private val ReviewRowGap = 16.dp

/** The space between the label and the value when the value goes under the label. */
private val ReviewRowStackGap = 2.dp

/**
 * A Review card row: the label at the start and the value at the end.
 * When the two do not fit on one line with [ReviewRowGap] between them, as
 * at large font sizes, the value goes under the label at the end. iOS does
 * the same with ViewThatFits. Each part can still wrap in its own width.
 */
@Composable
private fun ReviewDetailRow(
    label: @Composable () -> Unit,
    value: @Composable () -> Unit
) {
    Layout(
        content = {
            Box { label() }
            Column(horizontalAlignment = Alignment.End) { value() }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val labelMeasurable = measurables[0]
        val valueMeasurable = measurables[1]
        val width = constraints.maxWidth
        val gap = ReviewRowGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val oneLine = labelMeasurable.maxIntrinsicWidth(constraints.maxHeight) + gap +
            valueMeasurable.maxIntrinsicWidth(constraints.maxHeight) <= width
        if (oneLine) {
            val valuePlaceable = valueMeasurable.measure(loose)
            val labelPlaceable = labelMeasurable.measure(
                loose.copy(maxWidth = (width - gap - valuePlaceable.width).coerceAtLeast(0))
            )
            layout(width, maxOf(labelPlaceable.height, valuePlaceable.height)) {
                labelPlaceable.placeRelative(0, 0)
                valuePlaceable.placeRelative(width - valuePlaceable.width, 0)
            }
        } else {
            val labelPlaceable = labelMeasurable.measure(loose)
            val valuePlaceable = valueMeasurable.measure(loose)
            val valueTop = labelPlaceable.height + ReviewRowStackGap.roundToPx()
            layout(width, valueTop + valuePlaceable.height) {
                labelPlaceable.placeRelative(0, 0)
                valuePlaceable.placeRelative(width - valuePlaceable.width, valueTop)
            }
        }
    }
}

// ============================================================
// PHASE 4: STATUS SCREENS
// ============================================================

@Composable
private fun ErrorPhase(message: String, onRetry: () -> Unit, onClose: () -> Unit, canRetry: Boolean = true) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape).background(ErrorRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Error, contentDescription = tr("Error"), tint = ErrorRed, modifier = Modifier.size(80.dp))
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(tr("Transaction Failed"), style = MaterialTheme.typography.headlineSmall, color = ErrorRed)

            Spacer(modifier = Modifier.height(12.dp))

            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(48.dp))

            // Only ordinary address sends can retry. A link is funded once.
            if (canRetry) {
                PrimaryButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Retry"))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            DismissTextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Close"))
            }
        }
    }
}

// ============================================================
// SHARED COMPONENTS
// ============================================================

@Composable
private fun NumericKeypad(onKey: (String) -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    GlassButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = tr("Delete"),
                                modifier = Modifier.align(Alignment.Center).size(24.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TabularFigures),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Share of the circle each GradientSpinner ring draws (tokens.json motion.loops). */
private const val SpinnerOuterTrim = 0.7f
private const val SpinnerInnerTrim = 0.4f

@Composable
internal fun GradientSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val orange = MoneroOrange  // read in the drawBehind lambdas, which are not composable

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "outerRotation"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(2140, easing = LinearEasing)),
        label = "innerRotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        // Pulsing background
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(CircleShape)
                .background(MoneroOrange.copy(alpha = 0.08f))
        )

        // Outer ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .rotate(outerRotation)
                .drawBehind {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                orange.copy(alpha = 0f),
                                orange,
                                orange.copy(alpha = 0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = SpinnerOuterTrim * 360f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
        )

        // Inner ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .rotate(innerRotation)
                .drawBehind {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                orange.copy(alpha = 0f),
                                orange.copy(alpha = 0.6f),
                                orange.copy(alpha = 0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = SpinnerInnerTrim * 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
        )

        // Center icon
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = MoneroOrange,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun atomicToXmr(atomic: Long): Double {
    return atomic.toDouble() / 1_000_000_000_000.0
}

/** The end of the recipient field: the spinner while a name lookup runs, then the clear button. */
@Composable
private fun RecipientFieldTrailing(lookingUp: OpenAliasName?, onClear: () -> Unit) {
    // The last name stays on the spinner while it fades out.
    val last = remember { arrayOfNulls<OpenAliasName>(1) }
    if (lookingUp != null) last[0] = lookingUp
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = lookingUp != null,
            enter = fadeIn(openAliasFadeSpec),
            exit = fadeOut(openAliasFadeSpec)
        ) {
            last[0]?.let { OpenAliasLookupSpinner(it) }
        }
        OpenAliasClearButton(onClear = onClear)
    }
}

/** The "Valid address" / "Invalid address" row under the recipient field. */
@Composable
private fun AddressCheckRow(valid: Boolean, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 6.dp)
    ) {
        Icon(
            if (valid) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (valid) SuccessGreen else ErrorRed,
            modifier = Modifier.size(14.dp)
        )
        Text(
            if (valid) tr("Valid address") else tr("Invalid address"),
            style = MaterialTheme.typography.bodySmall,
            color = if (valid) SuccessGreen else ErrorRed
        )
    }
}

/**
 * The address row fades in and out, as on iOS. When the name row takes its
 * place, it goes at once, so the two never show together.
 */
@Composable
internal fun AddressCheckSlot(status: LookupStatus) {
    val checked = status as? LookupStatus.Address
    // The last row stays in place while it fades out.
    val last = remember { arrayOfNulls<LookupStatus.Address>(1) }
    if (checked != null) last[0] = checked
    AnimatedVisibility(
        visible = checked != null,
        enter = fadeIn(openAliasFadeSpec),
        exit = if (status is LookupStatus.Idle) fadeOut(openAliasFadeSpec) else ExitTransition.None
    ) {
        last[0]?.let { AddressCheckRow(valid = it.valid) }
    }
}

internal fun isValidMoneroAddress(address: String): Boolean {
    if (address.length !in listOf(95, 106)) return false
    if (!address.startsWith("4") && !address.startsWith("8")) return false
    return try {
        MoneroKit.validateAddress(address)
        true
    } catch (e: Exception) {
        false
    }
}
