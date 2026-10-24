package one.monero.moneroone.ui.screens.send

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

    private const val MIN_PLAUSIBLE_PRICE = 0.5
    private const val MAX_PLAUSIBLE_PRICE = 1_000_000.0
}
