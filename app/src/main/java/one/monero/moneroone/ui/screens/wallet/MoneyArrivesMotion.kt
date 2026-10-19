package one.monero.moneroone.ui.screens.wallet

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import io.horizontalsystems.monerokit.model.TransactionInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.monero.moneroone.core.wallet.Arrival
import one.monero.moneroone.core.wallet.ArrivalTx
import one.monero.moneroone.core.wallet.MoneyArrivesRules
import one.monero.moneroone.core.wallet.MoneyArrivesStore
import one.monero.moneroone.ui.components.Motion
import one.monero.moneroone.ui.components.rememberReduceMotion
import one.monero.moneroone.ui.components.softTickHaptic
import one.monero.moneroone.ui.components.successHaptic

/** The balance card's Money arrives values (moment 2). */
@Stable
internal class ArrivalPlay {
    /** The hero shows this balance until the roll; null shows the real balance. */
    var held by mutableStateOf<Long?>(null)
    /** The green +amount in the caption slot, while it shows. */
    var plus by mutableStateOf<Arrival?>(null)
    val plusAlpha = Animatable(0f)
    /** The +amount's offset in dp: it rises from 20 into the slot, then 20 more into the balance. */
    val plusRise = Animatable(0f)
    val captionAlpha = Animatable(1f)
    /** First payment: 0 = the label color, 1 = positive green. */
    val heroGreen = Animatable(0f)
    /** First payment: the shine's progress over the digits; below 0 = no shine. */
    val shine = Animatable(-1f)
    /** The payments the last moment showed. */
    var playedHashes: List<String>? = null
    var running = false
    /** Set during composition: the value to hold in the frame the new balance shows, before the play starts. */
    var frameHold: Long? = null

    /** The balance the hero holds now, or null for the real one. */
    val hold: Long? get() = held ?: frameHold

    /**
     * Every payment: the caption fades out and the +amount arrives (0); the
     * +amount rises into the balance and fades, the balance rolls with a
     * soft tick (550); the caption comes back (800). The first payment: the
     * +amount leaves at 650 and the balance turns green; success haptic (800);
     * caption (900); one shine (1000 to 2200); the green goes from 2000.
     */
    suspend fun run(arrival: Arrival, from: Long, reduceMotion: Boolean, view: View) = coroutineScope {
        running = true
        held = from
        plus = arrival
        plusAlpha.snapTo(0f)
        plusRise.snapTo(if (reduceMotion) 0f else Motion.ARRIVE_RISE_DP)
        launch { captionAlpha.animateTo(0f, tween(200, easing = Motion.EaseInOut)) }
        launch { plusAlpha.animateTo(1f, if (reduceMotion) tween(200, easing = Motion.EaseInOut) else Motion.arrive()) }
        if (!reduceMotion) launch { plusRise.animateTo(0f, Motion.arrive()) }
        delay(if (arrival.big) 650L else 550L)
        launch { plusAlpha.animateTo(0f, tween(250, easing = Motion.EaseIn)) }
        if (!reduceMotion) launch { plusRise.animateTo(-Motion.ARRIVE_RISE_DP, tween(250, easing = Motion.EaseIn)) }
        held = null
        frameHold = null
        if (arrival.big) {
            launch { heroGreen.animateTo(1f, Motion.crossFade()) }
            delay(150)
            successHaptic(view)
            delay(100)
        } else {
            softTickHaptic(view)
            delay(250)
        }
        launch { captionAlpha.animateTo(1f, tween(200, easing = Motion.EaseInOut)) }
        if (arrival.big) {
            delay(100)
            if (!reduceMotion) launch {
                shine.snapTo(0f)
                shine.animateTo(1f, tween(1200, easing = Motion.EaseInOut))
                shine.snapTo(-1f)
            }
            delay(1000)
            heroGreen.animateTo(0f, Motion.crossFade())
        } else {
            delay(250)
        }
        plus = null
        running = false
    }
}

/**
 * Looks for new payments while the wallet is [synced] and the Wallet tab is
 * [idle] (iOS rules), and plays one moment for all of them. The hero keeps
 * the value it showed until the moment rolls it.
 */
@Composable
internal fun rememberMoneyArrives(
    walletId: String?,
    sessionId: Long,
    synced: Boolean,
    idle: Boolean,
    balance: Long,
    transactions: List<TransactionInfo>,
    isClaim: (TransactionInfo) -> Boolean,
    isReclaim: (TransactionInfo) -> Boolean
): ArrivalPlay {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember(context) { MoneyArrivesStore.get(context) }
    val play = remember(sessionId) { ArrivalPlay() }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val canLook = walletId != null && synced && idle && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val reclaims = remember(transactions) { transactions.filter(isReclaim).mapTo(HashSet()) { it.hash } }
    val look = remember(walletId, transactions, canLook) {
        if (!canLook || walletId == null) null
        else MoneyArrivesRules.look(
            baseline = store.baseline(walletId),
            txs = transactions.map(ArrivalTx::of),
            createdInApp = store.createdInApp(walletId),
            firstPaymentShown = store.firstPaymentShown(walletId),
            claimHashes = transactions.filter(isClaim).mapTo(HashSet()) { it.hash },
            reclaimHashes = reclaims
        )
    }
    // The hero holds the balance of the last look while payments wait for their moment: during
    // a catch-up sync, on another tab, or while a sheet is open. The play then rolls it up.
    val lastShown = remember(sessionId) { longArrayOf(balance) }
    val waiting = remember(walletId, transactions, play.running, play.playedHashes) {
        val baseline = walletId?.let { store.baseline(it) }
        baseline != null && !play.running &&
            transactions.any { MoneyArrivesRules.isNew(ArrivalTx.of(it), baseline, reclaims) }
    }
    // The balance can publish before the history at a new block: while a payment is in the
    // pool, a rise holds at the last balance until the history shows the payment mined.
    val last = walletId?.let { store.lastBalance(it) }
    val poolRise = last != null && balance > last && !play.running &&
        transactions.any { MoneyArrivesRules.inPool(ArrivalTx.of(it)) }
    play.frameHold = if ((waiting || poolRise) && play.held == null) last ?: lastShown[0] else null
    SideEffect { if (play.held == null && !waiting && !poolRise) lastShown[0] = balance }
    // The last balance the hero showed for real, kept across launches for the hold above.
    val currentBalance by rememberUpdatedState(balance)
    LaunchedEffect(walletId, balance, waiting, poolRise, synced) {
        if (walletId != null && synced && !waiting && !poolRise && !play.running && play.held == null) {
            store.saveBalance(walletId, balance)
        }
    }
    LaunchedEffect(look) {
        val found = look ?: return@LaunchedEffect
        val id = walletId ?: return@LaunchedEffect
        val arrival = found.arrival
        if (arrival == null) {
            if (store.baseline(id) != found.baseline) store.save(id, found.baseline, firstPaymentShown = false)
            return@LaunchedEffect
        }
        if (play.running || play.playedHashes == arrival.hashes) return@LaunchedEffect
        play.playedHashes = arrival.hashes
        val from = store.lastBalance(id) ?: lastShown[0]
        // Saved first: the moment plays once even when the app stops during it.
        store.save(id, found.baseline, firstPaymentShown = true)
        scope.launch {
            play.run(arrival, from, reduceMotion, view)
            store.saveBalance(id, currentBalance)
        }
    }
    return play
}

/**
 * heroShine: one white band, 35% of the width, crosses the digits from
 * -1.5 to +1.5 widths. Drawn SrcAtop, so only the glyphs light up.
 */
internal fun Modifier.heroShine(progress: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress()
        if (p < 0f || p >= 1f) return@drawWithContent
        val w = size.width
        val center = -1.5f * w + p * 3f * w
        val half = w * 0.35f / 2
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                startX = center - half,
                endX = center + half
            ),
            blendMode = BlendMode.SrcAtop
        )
    }
