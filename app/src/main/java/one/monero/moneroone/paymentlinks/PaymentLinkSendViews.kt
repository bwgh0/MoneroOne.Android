package one.monero.moneroone.paymentlinks

import android.content.Context
import android.view.MotionEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SignedTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.FieldHeight
import one.monero.moneroone.ui.components.FieldShape
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.WarningYellow
import java.text.DateFormat
import java.util.Date

/** The spec's bearer warning, shown before every link is funded. */
internal val bearerWarning: String
    get() = tr(
        "Anyone who has this link can claim the money. Send it to one person in a private chat. You can take the money back until someone claims it."
    )

internal fun formatLinkDate(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000))

internal fun formatRecordDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

/** The share text: the amount and the link only. A link's note stays on this phone. */
internal fun shareText(amountXmr: String, link: PaymentLink): String =
    tr("I sent you %s XMR. Tap to claim it in Monero One: %s", amountXmr, link.url())

/**
 * The Send flow's error for a link's funding (founder decision 2026-10-17). The kit's failed
 * commit ("Send Transaction failed: ...") may still have reached the network, so it reads as
 * [linkFundingUnconfirmed]. Any other error shows as it does for a normal send.
 */
internal fun linkFundingErrorText(sendError: String): String =
    if (sendError.startsWith(SEND_COMMIT_FAILED)) linkFundingUnconfirmed() else sendError

/** A link's funding may or may not have reached the network. The same words as iOS. */
internal fun linkFundingUnconfirmed(): String =
    // Send flow error: a payment link's funding may or may not have reached the network.
    tr("The payment could not be confirmed. If it went through, the link appears in Activity.")

private const val SEND_COMMIT_FAILED = "Send Transaction failed"

/**
 * The funding failed before anything reached the network: the kit never built the transaction, or the
 * wallet or the send flow was not ready. Its hidden attempt needs no envelope scan. Any other error
 * (a failed commit, an unknown one) may come after the node took the funding.
 */
internal fun fundingFailedBeforeBroadcast(sendError: String): Boolean =
    PRE_BROADCAST_ERRORS.any { sendError.startsWith(it) }

private val PRE_BROADCAST_ERRORS = listOf(
    // The kit: the transaction was never built, or its wallet was not connected.
    "Create Transaction failed",
    "Wallet is not connected",
    // SendCoordinator.begin: the send never started.
    "The wallet changed.",
    "A transaction is still in progress.",
    "Invalid amount"
)

/**
 * Opens the system share sheet with the spec's share text, and the sealed envelope with the amount
 * as its preview. The link never goes to the clipboard.
 */
internal fun shareLink(context: Context, amountXmr: String, link: PaymentLink) {
    shareLinkWithPreview(context, amountXmr, shareText(amountXmr, link))
}

/**
 * The recipient field's payment-link token (iOS PaymentLinkRecipientToken):
 * Link puts it in the field's place, with the field's fill and radius. A 40
 * brand disc, "Payment link" over "Anyone with the link can claim it", and a
 * clear button that brings the field back with the address typed before.
 */
@Composable
fun PaymentLinkRecipientToken(onClear: () -> Unit, modifier: Modifier = Modifier) {
    val spoken = tr("Recipient, payment link. Anyone with the link can claim it")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // No vertical padding: the 48 clear button and the 40 disc fit the
            // 54 field height, so the buttons below do not move (iOS: same height).
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MoneroOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            // iOS subheadline semibold over caption secondary.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr("Payment link"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    tr("Anyone with the link can claim it"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = tr("Remove payment link"),
                tint = MoneroTheme.colors.labelTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The yellow caution banner (15% tint, triangle glyph, label-color text). */
@Composable
internal fun CautionBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WarningYellow.copy(alpha = 0.15f))
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ReviewRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun RowDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * Send's review step in link mode. Builds the envelope (with its restore
 * check), estimates the fee reserve and the funding fee, and shows the rows
 * the spec lists. [onConfirm] gets the draft and the fee reserve.
 */
@Composable
fun PaymentLinkReview(
    paymentLinks: PaymentLinksViewModel,
    amount: Long,
    unlockedBalance: Long,
    existingDraft: EnvelopeDraft?,
    onDraft: (EnvelopeDraft) -> Unit,
    formatXmr: (Long) -> String,
    estimateFee: (address: String, amount: Long) -> Long,
    /**
     * Builds and signs the funding transfer (not sent): its fee is the one it pays. Send broadcasts this same
     * transaction. Throws when the build fails.
     */
    prepareFunding: (address: String, amount: Long) -> SignedTransaction,
    /** A new value builds the funding again (the one built here is gone). */
    rebuild: Int = 0,
    xmrPrice: Double?,
    currencySymbol: String,
    sendInProgress: Boolean,
    /** The link's note, trimmed; empty for none. Shown here, never sent. */
    note: String = "",
    onConfirm: (draft: EnvelopeDraft, feeReserve: Long, prepared: SignedTransaction?) -> Unit
) {
    var draft by remember { mutableStateOf(existingDraft?.takeIf { it.amount == amount }) }
    var reserve by remember { mutableLongStateOf(0L) }
    var networkFee by remember { mutableLongStateOf(0L) }
    var prepared by remember { mutableStateOf<SignedTransaction?>(null) }
    // The funding would spend more than the unlocked balance (the build said so, or the estimate does).
    var tooMuch by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt, rebuild) {
        error = null
        prepared = null
        tooMuch = false
        networkFee = 0L
        try {
            val ready = draft ?: paymentLinks.prepare(amount).also { draft = it; onDraft(it) }
            // The reserve pays the claim's sweep (one input): wallet2's estimate fits it. The network fee is the
            // funding's own, built and signed here: the estimate counts one input, and the funding pays for each
            // input it spends. Send broadcasts this same transaction.
            val r = withContext(Dispatchers.IO) { estimateFee(ready.address, amount) }
            if (r <= 0) {
                error = tr("Fee estimation failed")
                return@LaunchedEffect
            }
            reserve = r
            val estimate = withContext(Dispatchers.IO) { estimateFee(ready.address, amount + r) }
            if (estimate > 0 && amount + r + estimate > unlockedBalance) {
                networkFee = estimate
                tooMuch = true
                return@LaunchedEffect
            }
            val built = try {
                withContext(Dispatchers.IO) { prepareFunding(ready.address, amount + r) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // wallet2: "not enough money" / "not enough unlocked money" once the real inputs are counted.
                if (e.message?.contains("not enough", ignoreCase = true) == true) {
                    networkFee = estimate
                    tooMuch = true
                    return@LaunchedEffect
                }
                throw e
            }
            prepared = built
            networkFee = built.fee
            if (built.fee <= 0) error = tr("Fee estimation failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: EnvelopeMismatchException) {
            error = tr("Could not create the link. Nothing was sent.")
        } catch (e: Exception) {
            error = e.message ?: tr("Fee estimation failed")
        }
    }

    val feeReady = error == null && reserve > 0 && networkFee > 0 && draft != null && (prepared != null || tooMuch)
    val total = amount + reserve + networkFee
    val overBalance = feeReady && (tooMuch || total > unlockedBalance)

    fun fiat(atomic: Long): String? = xmrPrice?.let { "≈ $currencySymbol${"%.2f".format(atomic / 1_000_000_000_000.0 * it)}" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                shadow = false,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MoneroOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(tr("Recipient"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tr("Anyone with the link"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    RowDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${formatXmr(amount)} XMR", style = MaterialTheme.typography.headlineMedium)
                        fiat(amount)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // The note, as Send's review shows a memo.
                    if (note.isNotEmpty()) {
                        RowDivider()
                        val spoken = tr("Note, %s", note)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    RowDivider()

                    ReviewRow(tr("Network Fee")) {
                        when {
                            error != null -> Text(tr("Error"), style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                            !feeReady -> CircularProgressIndicator(color = MoneroOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else -> Text("${formatXmr(networkFee)} XMR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ReviewRow(tr("Claim fee reserve")) {
                        if (feeReady) {
                            Text("${formatXmr(reserve)} XMR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        } else if (error == null) {
                            CircularProgressIndicator(color = MoneroOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    Text(
                        tr("Covers the fee the recipient pays to claim it."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ReviewRow(tr("Expires")) {
                        draft?.let {
                            Text(formatLinkDate(it.expiry), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    RowDivider()

                    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(tr("Total"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        if (feeReady) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${formatXmr(total)} XMR",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MoneroOrange
                                )
                                fiat(total)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            CautionBanner(bearerWarning)

            if (overBalance) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    tr("Amount plus network fee exceeds your available balance"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            }
            error?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Retry"))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        @OptIn(ExperimentalComposeUiApi::class)
        PrimaryButton(
            onClick = { draft?.let { onConfirm(it, reserve, prepared) } },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { event -> (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0 },
            enabled = feeReady && !overBalance && prepared != null && !sendInProgress
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(tr("Send"))
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

/** "Link ready" after the funding broadcast: Share (system share sheet) and Done. */
@Composable
fun PaymentLinkReadyPhase(link: PaymentLink, amountXmr: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var showCheck by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); showCheck = true }
    val checkScale by animateFloatAsState(
        if (showCheck) 1f else 0.3f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "linkCheckScale"
    )
    val checkAlpha by animateFloatAsState(if (showCheck) 1f else 0f, tween(300), label = "linkCheckAlpha")

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkAlpha }
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(80.dp))
            }
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                tr("Link ready"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "$amountXmr XMR",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            CautionBanner(bearerWarning)
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(onClick = { shareLink(context, amountXmr, link) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(tr("Share"))
                }
                PrimaryButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text(tr("Done"))
                }
            }
        }
    }
}

/** TalkBack: one spoken label for a status chip. */
internal fun Modifier.spoken(label: String): Modifier = clearAndSetSemantics { contentDescription = label }
