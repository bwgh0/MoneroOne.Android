package one.monero.moneroone.ui.screens.send

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** A fiat amount as the user typed it, and the XMR amount it made (iOS SendAmountStep.TypedFiat). */
internal data class TypedFiat(val fiat: String, val xmr: String) {
    companion object {
        /** Keeps it with the flow through a recreation. */
        val Saver: Saver<TypedFiat?, Any> = listSaver(
            save = { if (it == null) emptyList() else listOf(it.fiat, it.xmr) },
            restore = { if (it.size == 2) TypedFiat(it[0], it[1]) else null }
        )
    }
}

/** The unit the amount step starts in (iOS SendAmountStep.startsInFiat). */
internal object SendAmountStart {

    /** iOS PriceService.isPlausiblePrice: a fiat amount needs a sane price to convert. */
    fun isPlausiblePrice(price: Double?): Boolean =
        price != null && price.isFinite() && price >= MIN_PLAUSIBLE_PRICE && price <= MAX_PLAUSIBLE_PRICE

    /**
     * Fiat when Fiat Mode is on, like every amount in the app. XMR when there is no usable price
     * (a fiat amount needs one to convert), and when an amount is already filled in (a QR code, a
     * `monero:` link, a prefill): it stays as it came, with no rounding through fiat.
     */
    fun startsInFiat(fiatMode: Boolean, price: Double?, amount: String): Boolean =
        fiatMode && isPlausiblePrice(price) && amount.isEmpty()

    /**
     * The fiat amount to show again when the step comes back (the user went back to the recipient
     * and continued, or came back from Review): the one the user typed, while the amount is still
     * the XMR it made and a usable price exists. An amount filled in since (a QR code, a `monero:`
     * link) keeps XMR.
     */
    fun restoredFiat(typed: TypedFiat?, amount: String, price: Double?): String? =
        typed?.fiat?.takeIf { amount.isNotEmpty() && typed.xmr == amount && isPlausiblePrice(price) }

    private const val MIN_PLAUSIBLE_PRICE = 0.5
    private const val MAX_PLAUSIBLE_PRICE = 1_000_000.0
}
