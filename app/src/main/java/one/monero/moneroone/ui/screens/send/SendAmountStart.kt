package one.monero.moneroone.ui.screens.send

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.util.Locale

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

    /**
     * A `monero:` link filled the flow (iOS SendAmountStep.linkKeepsFiat), with [linkAmount] the
     * amount it filled in ("" for none). A link with an amount shows that amount in XMR, as it
     * came (like any filled-in amount). A link with no amount clears the amount and leaves the unit
     * as it is: in fiat the step shows an empty fiat amount, as on a fresh Send. Either way the
     * fiat the user typed before is dropped, so a later return does not bring it back.
     */
    fun linkKeepsFiat(linkAmount: String): Boolean = linkAmount.isEmpty()

    /**
     * Whether the note "Amount pre-filled from payment link" (or "from QR code", the same note)
     * still shows after an edit on the amount step took the amount from [before] to [after]. It
     * shows only while the amount is still the one filled in: the first edit that changes it (a
     * key, delete, Paste) hides it; a key that changes nothing (the decimals are full) keeps it.
     * A unit swap is no edit: the amount stays as it is. Max always hides it, and a new link with
     * an amount shows it again.
     */
    fun prefillNoteShows(shown: Boolean, before: String, after: String): Boolean = shown && before == after

    /**
     * A fiat amount as the amount step shows it when it swaps from XMR (iOS formatFiatNumber): two
     * decimals at most, no trailing zeros, no thousands separator, as the user would type it.
     */
    fun fiatInput(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private const val MIN_PLAUSIBLE_PRICE = 0.5
    private const val MAX_PLAUSIBLE_PRICE = 1_000_000.0
}
