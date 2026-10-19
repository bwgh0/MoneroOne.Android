package one.monero.moneroone.core.wallet

import android.content.Context
import android.content.SharedPreferences
import io.horizontalsystems.monerokit.model.TransactionInfo

/** The parts of a transaction the Money arrives rules read. */
data class ArrivalTx(
    val hash: String,
    val amount: Long,
    val incoming: Boolean,
    val failed: Boolean,
    val confirmations: Long,
    val blockHeight: Long
) {
    companion object {
        fun of(tx: TransactionInfo) = ArrivalTx(
            hash = tx.hash,
            amount = tx.amount,
            incoming = tx.direction == TransactionInfo.Direction.Direction_In,
            failed = tx.isFailed,
            confirmations = tx.confirmations,
            blockHeight = tx.blockheight
        )
    }
}

/**
 * What a wallet has already shown: every incoming payment mined below
 * [height], and the payments in [recent] (a reorg can move one higher).
 */
data class ArrivalBaseline(val height: Long, val recent: List<String>)

/** One Money arrives moment: the [total] of the new payments, and whether it is the first-payment version. */
data class Arrival(val total: Long, val hashes: List<String>, val big: Boolean)

/** The baseline after a look, and the moment to play (null: none). */
data class ArrivalLook(val arrival: Arrival?, val baseline: ArrivalBaseline)

/**
 * Money arrives (moment 2) trigger rules, the same on iOS. A payment counts
 * at its first block (wallet2 adds it to the balance then). The app looks
 * while the wallet is synced and the Wallet tab shows; payments found while
 * the user was away play at the next look. A wallet with no baseline (its
 * first sync, a restore, an import or a Reset Sync) takes everything as seen,
 * with no play. All new payments in one look give one combined amount.
 */
object MoneyArrivesRules {
    const val RECENT_LIMIT = 50

    fun look(
        baseline: ArrivalBaseline?,
        txs: List<ArrivalTx>,
        createdInApp: Boolean,
        firstPaymentShown: Boolean,
        claimHashes: Set<String> = emptySet(),
        reclaimHashes: Set<String> = emptySet()
    ): ArrivalLook {
        // A baseline above the chain the wallet sees (a reset chain) resets like no baseline.
        if (baseline == null || baseline.height > (chainTop(txs) ?: Long.MAX_VALUE) + 1) {
            return ArrivalLook(null, baselineOf(txs))
        }
        val arrivals = txs.filter { isNew(it, baseline, reclaimHashes) }
        val hashes = arrivals.map { it.hash }
        val newSet = hashes.toSet()
        // The first payment: a wallet made in this app, nothing else in its history
        // (a payment still in the pool does not count), and no claimed payment link.
        val historyEmpty = txs.none { it.hash !in newSet && !(it.incoming && !it.failed && it.confirmations == 0L) }
        val big = createdInApp && !firstPaymentShown && historyEmpty && newSet.none { it in claimHashes }
        // A reclaim of our own link never plays, but it is seen.
        val next = ArrivalBaseline(
            height = maxOf(baseline.height, (txs.filter { it.isMinedIncoming() }.maxOfOrNull { it.blockHeight } ?: -1L) + 1),
            recent = (baseline.recent + hashes).distinct().takeLast(RECENT_LIMIT)
        )
        if (arrivals.isEmpty()) return ArrivalLook(null, next)
        return ArrivalLook(Arrival(arrivals.sumOf { it.amount }, hashes, big), next)
    }

    /** The top block the wallet sees, from its mined payments; null with none. */
    fun chainTop(txs: List<ArrivalTx>): Long? =
        txs.filter { it.confirmations >= 1L && it.blockHeight > 0L }.maxOfOrNull { it.blockHeight + it.confirmations - 1 }

    /** Everything mined is seen. */
    fun baselineOf(txs: List<ArrivalTx>): ArrivalBaseline {
        val mined = txs.filter { it.isMinedIncoming() }
        return ArrivalBaseline(
            height = (mined.maxOfOrNull { it.blockHeight } ?: -1L) + 1,
            recent = mined.sortedBy { it.blockHeight }.map { it.hash }.takeLast(RECENT_LIMIT)
        )
    }

    /** A payment the wallet has not shown yet: incoming, not failed, mined, above the [baseline]. */
    fun isNew(tx: ArrivalTx, baseline: ArrivalBaseline, reclaimHashes: Set<String> = emptySet()): Boolean =
        tx.isMinedIncoming() && tx.blockHeight >= baseline.height && tx.hash !in baseline.recent &&
            tx.hash !in reclaimHashes

    /** An incoming payment still in the pool: the balance rises when it is mined. */
    fun inPool(tx: ArrivalTx): Boolean = tx.incoming && !tx.failed && tx.confirmations == 0L

    private fun ArrivalTx.isMinedIncoming() = incoming && !failed && confirmations >= 1L
}

/**
 * The balance card's hero while new payments wait for their moment (iOS rules).
 * - During a sync, with a baseline: the last shown value, so a payment found in a
 *   catch-up sync does not tick the hero up and back down before its moment.
 * - Else the real balance minus the waiting payments: a send shows at once, and
 *   the moment's roll equals its +amount.
 * - With nothing waiting, while an incoming payment is in the pool, a rise holds
 *   at the balance of the last history update (a balance published first).
 *
 * The kit publishes each scan's history before its balance (MoneroKit.onRefreshed,
 * which stores the wallet in between), and the last history of a catch-up before
 * Synced. So a payment that a history shows counts once the balance moves (or moved
 * in the same frame as that history), and the moment waits for that. A payment the
 * balance has not counted 3 s after its history counts then ([FALLBACK_MS], one per
 * history). This gives the iOS results: one +amount per catch-up, and no tick up
 * and back at a new block.
 *
 * wallet2's balance takes in a pool payment, so the balance does not move when that
 * payment is mined. A payment the pool hold covered counts at once when it waits.
 */
class HeroHold {
    private var history: Any? = null
    private var historyBalance = 0L
    private var lastBalance: Long? = null
    /** Per waiting payment: the balance when its history came, or [COUNTED]. */
    private val before = HashMap<String, Long>()
    /** Pool payments that the balance already holds (the pool hold covered them). */
    private val pooled = HashSet<String>()
    private var fallbacks = 0

    /** True when the balance holds every waiting payment: the moment can play. */
    var allCounted = true
        private set

    /**
     * [history] is the list instance the screen shows (a new instance per update).
     * [lastShown] is the hero's last value from an earlier look, kept across launches.
     * [fallbacks] goes up when a history's payments waited [FALLBACK_MS]: they count.
     */
    fun hero(
        history: Any,
        txs: List<ArrivalTx>,
        balance: Long,
        waiting: List<ArrivalTx>,
        syncing: Boolean,
        lastShown: Long?,
        fallbacks: Int = 0
    ): Long {
        if (history !== this.history) {
            // The first history, or a balance that moved with it: the balance holds its payments.
            val movedWith = this.history == null || lastBalance != balance
            waiting.forEach {
                if (it.hash !in before) before[it.hash] = if (movedWith || it.hash in pooled) COUNTED else balance
            }
            before.keys.retainAll(waiting.mapTo(HashSet()) { it.hash })
            pooled.retainAll(txs.filter(MoneyArrivesRules::inPool).mapTo(HashSet()) { it.hash })
            this.history = history
            historyBalance = balance
        }
        lastBalance = balance
        if (fallbacks != this.fallbacks) {
            this.fallbacks = fallbacks
            before.replaceAll { _, _ -> COUNTED }
        }
        // Counted once the balance moves, and counted for good.
        before.replaceAll { _, b -> if (b != balance) COUNTED else b }
        val counted = waiting.filter { (before[it.hash] ?: COUNTED) == COUNTED }
        allCounted = counted.size == waiting.size
        if (syncing && lastShown != null) return lastShown
        if (waiting.isEmpty() && balance > historyBalance && txs.any(MoneyArrivesRules::inPool)) {
            txs.filter(MoneyArrivesRules::inPool).mapTo(pooled) { it.hash }
            return historyBalance
        }
        val shown = balance - counted.sumOf { it.amount }
        // A payment that the balance may hold but has not counted: the hero does not rise before its moment.
        return if (!allCounted && lastShown != null) minOf(shown, lastShown) else shown
    }

    companion object {
        /** How long a history's payments wait for the balance before they count anyway. */
        const val FALLBACK_MS = 3_000L
        private const val COUNTED = Long.MIN_VALUE
    }
}

/** The per-wallet Money arrives state: the baseline, "made in this app" and "first payment shown". */
class MoneyArrivesStore private constructor(private val prefs: SharedPreferences) {

    fun baseline(walletId: String): ArrivalBaseline? {
        if (!prefs.contains(heightKey(walletId))) return null
        val recent = prefs.getString(recentKey(walletId), "").orEmpty().split(',').filter { it.isNotEmpty() }
        return ArrivalBaseline(prefs.getLong(heightKey(walletId), 0L), recent)
    }

    fun save(walletId: String, baseline: ArrivalBaseline, firstPaymentShown: Boolean) {
        prefs.edit()
            .putLong(heightKey(walletId), baseline.height)
            .putString(recentKey(walletId), baseline.recent.joinToString(","))
            .apply { if (firstPaymentShown) putBoolean(firstKey(walletId), true) }
            .apply()
    }

    /** The balance the last look saw: the hero holds it until the moment rolls it. */
    fun lastBalance(walletId: String): Long? =
        if (prefs.contains(balanceKey(walletId))) prefs.getLong(balanceKey(walletId), 0L) else null

    fun saveBalance(walletId: String, balance: Long) {
        if (lastBalance(walletId) != balance) prefs.edit().putLong(balanceKey(walletId), balance).apply()
    }

    /** Reset Sync: the next synced look takes everything as seen, with no play. */
    fun resetBaseline(walletId: String) {
        prefs.edit().remove(heightKey(walletId)).remove(recentKey(walletId)).remove(balanceKey(walletId)).apply()
    }

    fun markCreatedInApp(walletId: String) {
        prefs.edit().putBoolean(createdKey(walletId), true).apply()
    }

    fun createdInApp(walletId: String): Boolean = prefs.getBoolean(createdKey(walletId), false)

    fun firstPaymentShown(walletId: String): Boolean = prefs.getBoolean(firstKey(walletId), false)

    fun forget(walletId: String) {
        prefs.edit()
            .remove(heightKey(walletId)).remove(recentKey(walletId)).remove(balanceKey(walletId))
            .remove(createdKey(walletId)).remove(firstKey(walletId))
            .apply()
    }

    private fun heightKey(id: String) = "height_$id"
    private fun recentKey(id: String) = "recent_$id"
    private fun createdKey(id: String) = "created_$id"
    private fun firstKey(id: String) = "first_shown_$id"
    private fun balanceKey(id: String) = "balance_$id"

    companion object {
        @Volatile private var instance: MoneyArrivesStore? = null

        fun get(context: Context): MoneyArrivesStore = instance ?: synchronized(this) {
            instance ?: MoneyArrivesStore(
                context.applicationContext.getSharedPreferences("money_arrives", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
