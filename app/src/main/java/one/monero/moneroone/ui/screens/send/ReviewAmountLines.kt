package one.monero.moneroone.ui.screens.send

import one.monero.moneroone.core.locale.tr
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
    /**
     * What TalkBack reads for a value shown fiat first: the fiat value, then the exact XMR, as on
     * the screen. Null when XMR is on top: those labels read the XMR alone.
     */
    val spokenFiatFirst: String?
        get() = if (isFiatFirst && secondary != null) "$primary, $secondary" else null

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

/** What TalkBack reads on the Send Review (iOS SendReviewFee). Labels only: nothing visible changes. */
internal object SendReviewSpoken {

    /** The fee row, in the order of the screen: in Fiat Mode the fiat value, then the exact XMR; else the XMR alone. */
    fun feeLabel(fee: Long, lines: ReviewAmountLines): String =
        lines.spokenFiatFirst?.let { tr("Network fee: %s", it) } ?: tr("Network fee: %s XMR", XmrFormat.format(fee))

    /** The fee row while the fee is not known yet. */
    fun feeLoadingLabel(): String = tr("Network fee: loading")

    /** The hint on Send once the fee is in: in Fiat Mode the fiat value, then the exact XMR; else the XMR alone. */
    fun sendHint(amount: Long, lines: ReviewAmountLines): String =
        lines.spokenFiatFirst?.let { tr("Double tap to send %s", it) } ?: tr("Double tap to send %s XMR", XmrFormat.format(amount))
}

/** What TalkBack reads on the Send with a Link Review (iOS PaymentLinkReviewStep). Labels only: nothing visible changes. */
internal object LinkReviewSpoken {

    /** A fee row, in the order of the screen: in Fiat Mode the fiat value, then the exact XMR; else the XMR alone. */
    fun valueLabel(title: String, value: Long, lines: ReviewAmountLines): String =
        "$title: ${lines.spokenFiatFirst ?: "${XmrFormat.format(value)} XMR"}"

    /** A fee row while its value loads, in the app's language. */
    fun loadingLabel(title: String): String = tr("%s: loading", title)

    /** The hint on Send once the link and its fees are in: in Fiat Mode the fiat value, then the exact XMR; else the XMR alone. */
    fun fundHint(amount: Long, lines: ReviewAmountLines): String =
        lines.spokenFiatFirst?.let { tr("Double tap to fund the link with %s", it) }
            ?: tr("Double tap to fund the link with %s XMR", XmrFormat.format(amount))
}
