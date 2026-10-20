package one.monero.moneroone.ui.screens.wallet

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import one.monero.moneroone.core.wallet.HeroHold
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
    /** The amount the hero leaves out until the roll; null leaves out nothing. */
    var withheld by mutableStateOf<Long?>(null)
    /** Counts the baseline saves, so the hold reads the saved baseline. */
    var baselineVersion by mutableStateOf(0)
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
    /**
     * Set during composition: the balance the hero shows now, or null for the real one.
     * State, so the balance card (a lazy list item) reads each change, also the release at the roll.
     */
    var hold by mutableStateOf<Long?>(null)

    /**
     * Every payment: the caption fades out and the +amount arrives (0); the
     * +amount rises into the balance and fades, the balance rolls with a
     * soft tick (550); the caption comes back (800). The first payment: the
     * +amount leaves at 650 and the balance turns green; success haptic (800);
     * caption (900); one shine (1000 to 2200); the green goes from 2000.
     */
    suspend fun run(arrival: Arrival, reduceMotion: Boolean, view: View) = coroutineScope {
        running = true
        withheld = arrival.total
        plus = arrival
        plusAlpha.snapTo(0f)
        plusRise.snapTo(if (reduceMotion) 0f else Motion.ARRIVE_RISE_DP)
        launch { captionAlpha.animateTo(0f, tween(200, easing = Motion.EaseInOut)) }
        launch { plusAlpha.animateTo(1f, if (reduceMotion) tween(200, easing = Motion.EaseInOut) else Motion.arrive()) }
        if (!reduceMotion) launch { plusRise.animateTo(0f, Motion.arrive()) }
        delay(if (arrival.big) 650L else 550L)
        launch { plusAlpha.animateTo(0f, tween(250, easing = Motion.EaseIn)) }
        if (!reduceMotion) launch { plusRise.animateTo(-Motion.ARRIVE_RISE_DP, tween(250, easing = Motion.EaseIn)) }
        withheld = null
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
    val reclaims = remember(transactions) { transactions.filter(isReclaim).mapTo(HashSet()) { it.hash } }
    val txs = remember(transactions) { transactions.map(ArrivalTx::of) }
    val baseline = remember(walletId, transactions, play.baselineVersion) { walletId?.let { store.baseline(it) } }
    val waiting = remember(txs, baseline, play.running) {
        if (baseline == null || play.running) emptyList()
        else txs.filter { MoneyArrivesRules.isNew(it, baseline, reclaims) }
    }
    val heroHold = remember(walletId, sessionId) { HeroHold() }
    // Goes up when a history's payments waited 3 s for the balance (the effect below): they count.
    var fallbacks by remember(walletId, sessionId) { mutableIntStateOf(0) }
    val lastShown = walletId?.let { store.lastBalance(it) }
    val hero = heroHold.hero(
        transactions, txs, balance, waiting,
        syncing = !synced && baseline != null, lastShown = lastShown, fallbacks = fallbacks
    )
    // Keyed on the waiting payments: a history published again with the same payments keeps the 3 s.
    LaunchedEffect(waiting.map { it.hash }, heroHold.allCounted) {
        if (heroHold.allCounted) return@LaunchedEffect
        delay(HeroHold.FALLBACK_MS)
        fallbacks++
    }
    play.hold = when (val out = play.withheld) {
        null -> hero.takeIf { it != balance }
        else -> balance - out
    }
    // The hero's value at a look, kept across launches: a catch-up sync holds it.
    LaunchedEffect(walletId, hero, synced, play.running) {
        if (walletId != null && synced && !play.running && play.withheld == null) store.saveBalance(walletId, hero)
    }

    val canLook = walletId != null && synced && idle && heroHold.allCounted &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val look = remember(walletId, transactions, canLook) {
        if (!canLook || walletId == null) null
        else MoneyArrivesRules.look(
            baseline = store.baseline(walletId),
            txs = txs,
            createdInApp = store.createdInApp(walletId),
            firstPaymentShown = store.firstPaymentShown(walletId),
            claimHashes = transactions.filter(isClaim).mapTo(HashSet()) { it.hash },
            reclaimHashes = reclaims
        )
    }
    LaunchedEffect(look) {
        val found = look ?: return@LaunchedEffect
        val id = walletId ?: return@LaunchedEffect
        val arrival = found.arrival
        if (arrival == null) {
            if (store.baseline(id) != found.baseline) {
                store.save(id, found.baseline, firstPaymentShown = false)
                play.baselineVersion++
            }
            return@LaunchedEffect
        }
        if (play.running || play.playedHashes == arrival.hashes) return@LaunchedEffect
        play.playedHashes = arrival.hashes
        // Set now, before the saved baseline ends the wait: the hero keeps the amount out.
        play.running = true
        play.withheld = arrival.total
        // Saved first: the moment plays once even when the app stops during it.
        store.save(id, found.baseline, firstPaymentShown = true)
        play.baselineVersion++
        scope.launch { play.run(arrival, reduceMotion, view) }
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

/**
 * TalkBack reads "Received <amount>" once per moment: a polite live region, as
 * in the address picker. It holds the words for 3 s, then is empty again, so
 * TalkBack does not stop on it later. It shows no text.
 */
@Composable
internal fun ArrivalAnnouncement(play: ArrivalPlay, words: (Arrival) -> String, modifier: Modifier = Modifier) {
    val currentWords by rememberUpdatedState(words)
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(play) {
        snapshotFlow { play.plus }.filterNotNull().distinctUntilChanged().collectLatest { arrival ->
            text = currentWords(arrival)
            delay(3_000L)
            text = null
        }
    }
    Box(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            text?.let { contentDescription = it }
        }
    )
}
