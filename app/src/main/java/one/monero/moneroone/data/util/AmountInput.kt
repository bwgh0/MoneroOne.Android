package one.monero.moneroone.data.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols

/** UI keyboards accept either decimal separator; payment requests always use '.'. */
object AmountInput {
    fun normalize(input: String, decimals: Int = 12): String? {
        val normalized = input.replace(',', '.')
        return normalized.takeIf { it.matches(Regex("[0-9]*(?:\\.[0-9]{0,$decimals})?")) }
    }

    /**
     * Pasted text as an amount, with '.' as the separator. Null unless the whole text, trimmed, is one
     * amount: digits with at most one decimal separator, the locale's or '.'. Digits are never taken out
     * of other text (an address, a link, "1.5 XMR").
     */
    fun pasted(text: String, decimalSeparator: Char = DecimalFormatSymbols.getInstance().decimalSeparator): String? {
        val trimmed = text.trim()
        val dotted = if (decimalSeparator == '.') trimmed else trimmed.replace(decimalSeparator, '.')
        return dotted.takeIf { it.any(Char::isDigit) && normalize(it) == it }
    }

    fun fiatToXmr(input: String, price: Double): String? {
        if (!price.isFinite() || price <= 0) return null
        val fiat = normalize(input)?.toBigDecimalOrNull() ?: return null
        if (fiat.signum() < 0) return null
        return fiat.divide(BigDecimal.valueOf(price), 12, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    }
}
