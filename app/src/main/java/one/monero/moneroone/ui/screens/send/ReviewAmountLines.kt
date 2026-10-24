package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.data.model.Currency
import one.monero.moneroone.data.util.MoneyFormat
import one.monero.moneroone.data.util.XmrFormat

/**
 * A Review amount in the order Fiat Mode picks (iOS ReviewAmountLines). Fiat Mode with a plausible
 * price: the fiat value on top and the exact XMR under it, like every amount in Fiat Mode (the
 * amount step too). Otherwise the XMR on top and "≈ fiat" under it. Both Reviews use it.
 */
internal data class ReviewAmountLines(
    val primary: String,
    /** Null with no price. */
    val secondary: String?,
    /** The fiat value is on top. */
    val isFiatFirst: Boolean
) {
    companion object {
        /** Fiat on top: Fiat Mode is on and the price is plausible (iOS PriceService.isPlausiblePrice). */
        fun fiatFirst(fiatMode: Boolean, price: Double?): Boolean =
            fiatMode && SendAmountStart.isPlausiblePrice(price)

        /** [xmr] is the exact amount without its unit; [fiat] the formatted fiat value, null with no price. */
        fun of(xmr: String, fiat: String?, fiatFirst: Boolean): ReviewAmountLines {
            val xmrText = "$xmr XMR"
            return if (fiatFirst && fiat != null) {
                ReviewAmountLines(primary = fiat, secondary = xmrText, isFiatFirst = true)
            } else {
                ReviewAmountLines(primary = xmrText, secondary = fiat?.let { "≈ $it" }, isFiatFirst = false)
            }
        }

        /** An amount in atomic units, at the current price, in the order the current Fiat Mode picks. */
        fun of(atomic: Long, price: Double?, currency: Currency, fiatMode: Boolean): ReviewAmountLines = of(
            xmr = XmrFormat.format(atomic),
            fiat = price?.let { MoneyFormat.format(atomic.toDouble() / ATOMIC_UNITS_PER_XMR * it, currency, fractionDigits = 2) },
            fiatFirst = fiatFirst(fiatMode, price)
        )

        /** A typical network fee, for the width of the fee row's placeholder (iOS ReviewValuePlaceholder.feeSample). */
        const val FEE_SAMPLE_ATOMIC = 31_520_000L

        private const val ATOMIC_UNITS_PER_XMR = 1_000_000_000_000.0
    }
}
