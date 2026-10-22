package one.monero.moneroone.core.openalias

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.wallet.SendState

/**
 * The address last paid for each OpenAlias name (spec section 4.6). A pin
 * never gives the address: every Send flow does a fresh lookup and compares.
 * Key: the query name, so `donate@getmonero.org` and `donate.getmonero.org`
 * share one pin.
 */
class OpenAliasPinStore(
    private val prefs: SharedPreferences,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    enum class Network(val key: String) {
        MAINNET("pins_mainnet"),
        TESTNET("pins_testnet"),
    }

    @Serializable
    private data class Entry(
        val address: String,
        val pinnedAt: Long,
        /** The name as the user typed it ("donate@getmonero.org"). Older pins have none. */
        val display: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Entry.serializer())

    fun state(query: String, address: String, network: Network = Network.MAINNET): PinState {
        val entry = load(network)[query] ?: return PinState.None
        return if (entry.address == address) PinState.Same(entry.pinnedAt) else PinState.Changed(entry.pinnedAt)
    }

    /** Pins [address] for [query] now. Above the cap, the oldest pins go. */
    fun record(query: String, address: String, network: Network = Network.MAINNET, display: String? = null) {
        val pins = LinkedHashMap(load(network))
        pins.remove(query)
        pins[query] = Entry(address, nowSeconds(), display)
        while (pins.size > OpenAliasConfig.PIN_CAP) {
            val oldest = pins.entries.minByOrNull { it.value.pinnedAt }?.key ?: break
            pins.remove(oldest)
        }
        prefs.edit().putString(network.key, json.encodeToString(serializer, pins)).apply()
    }

    /** Remove All clears the pins of both networks. */
    fun clearAll() {
        prefs.edit().apply {
            Network.entries.forEach { remove(it.key) }
        }.apply()
    }

    fun count(network: Network = Network.MAINNET): Int = load(network).size

    /** Every pin: the query name and the address last paid for it. History names sends with it. */
    fun pinnedAddresses(network: Network = Network.MAINNET): Map<String, String> =
        load(network).mapValues { it.value.address }

    /** Every pin's name as the user typed it, by query name; an older pin falls back to the query name. */
    fun pinnedNames(network: Network = Network.MAINNET): Map<String, String> =
        load(network).mapValues { it.value.display ?: it.key }

    private fun load(network: Network): Map<String, Entry> {
        val raw = prefs.getString(network.key, null) ?: return emptyMap()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val PREFS_NAME = "openalias_pins"

        fun create(context: Context): OpenAliasPinStore =
            OpenAliasPinStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}

/**
 * The Send flow's pin rule: write a pin only when a send to the address that
 * a name resolved to succeeds (as on iOS). Returns true when it wrote one.
 */
fun OpenAliasPinStore.recordAfterSend(state: SendState, summary: OpenAliasSummary?, sentAddress: String): Boolean {
    if (state !is SendState.Success || summary == null || sentAddress.isEmpty()) return false
    if (summary.address != sentAddress) return false
    record(summary.name.query, sentAddress, display = summary.name.display)
    return true
}
