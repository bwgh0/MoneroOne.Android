package one.monero.moneroone.core.wallet

import kotlinx.coroutines.flow.MutableStateFlow

/** How a create-wallet screen starts each time it enters composition. */
enum class CreateFlowStart {
    /** A new flow: issue its phrase. */
    ISSUE,

    /** The flow's phrase is still held (the Activity was recreated): show the same words. */
    RESUME,

    /** The flow showed a phrase that is gone: leave the screen, never show a new phrase. */
    RESTART
}

/**
 * The recovery phrase of a create-wallet flow, held in memory for the life of
 * that flow. A flow is its NavBackStackEntry id: the id stays the same when
 * Android recreates the Activity, while the lock screen covers the flow, and
 * after process death. The words never go into saved state, a Bundle or disk.
 *
 * [seed] is the ViewModel's pending seed. Other code may clear it (a wallet
 * switch, a full wipe); the flow then counts its phrase as gone.
 */
class CreateFlowSeed(private val seed: MutableStateFlow<PendingSeed?>) {

    /** The flow [seed] was issued to. Kept after the phrase is dropped, as a record of the issue. */
    private var flowId: String? = null

    /**
     * Issue the phrase for [flow]. A flow gets one phrase: asking again returns
     * the words it was shown, or null once they are gone. It never gets a second one.
     */
    fun issue(flow: String, generate: () -> PendingSeed): List<String>? {
        if (flow == flowId) return seed.value?.words
        val issued = generate()
        flowId = flow
        seed.value = issued
        return issued.words
    }

    /** True while [seed] holds the phrase issued to [flow]. */
    fun holds(flow: String): Boolean = flow == flowId && seed.value != null

    /**
     * How the create screen of [flow] starts. [savedIssued] is the screen's own
     * saved record that it showed a phrase; after process death it is the only record.
     */
    fun start(flow: String, savedIssued: Boolean): CreateFlowStart = when {
        holds(flow) -> CreateFlowStart.RESUME
        savedIssued || flow == flowId -> CreateFlowStart.RESTART
        else -> CreateFlowStart.ISSUE
    }

    /** [flow] ended for real (back, cancel, completion, the lock screen): drop its phrase. */
    fun discard(flow: String) {
        if (flow == flowId) seed.value = null
    }
}
