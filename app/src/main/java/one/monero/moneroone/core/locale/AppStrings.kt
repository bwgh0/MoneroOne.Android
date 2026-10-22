package one.monero.moneroone.core.locale

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

object AppStrings {
    private var application: Context? = null

    fun initialize(context: Context) { application = context.applicationContext }

    /**
     * The context whose resources speak the app's language. From Android 13 the
     * system applies the per-app language to the app's own resources, and
     * `getContextForLanguage` returns the context unchanged after a binder call to
     * LocaleManager, which cost about 1 ms a string on every frame of a chart scrub.
     */
    private fun localized(): Context? {
        val context = application ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context else ContextCompat.getContextForLanguage(context)
    }

    fun text(source: String, vararg args: Any?): String {
        val context = localized()
        val translated = localizedStrings[source]?.let { context?.getString(it) } ?: source
        return formatTranslation(translated, args)
    }

    /** A resource of the app's language, for copy whose English text is already another message's key. */
    fun resource(@StringRes id: Int, fallback: String): String = localized()?.getString(id) ?: fallback

    fun quantity(source: String, count: Int): String {
        val context = localized()
        val translated = localizedPlurals[source]?.let { context?.resources?.getQuantityString(it, count) }
            ?: if (count == 1) englishSingular[source] ?: source else source
        return formatTranslation(translated, arrayOf(count))
    }
}

/** Localize UI copy only. Call at display time, never when persisting user data. */
fun tr(source: String, vararg args: Any?): String = AppStrings.text(source, *args)
fun trRes(@StringRes id: Int, fallback: String): String = AppStrings.resource(id, fallback)
fun pluralTr(source: String, count: Int): String = AppStrings.quantity(source, count)

/** One substitution pass: values containing percent signs are never reinterpreted. */
internal fun formatTranslation(template: String, args: Array<out Any?>): String {
    var next = 0
    return placeholder.replace(template) { match ->
        if (match.value == "%%") "%" else {
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: next++
            args.getOrNull(index)?.toString() ?: match.value
        }
    }
}

private val placeholder = Regex("%(?:(\\d+)\\$)?s|%%")

private val englishSingular = mapOf(
    "%s transactions" to "%s transaction",
    "%s pending transactions" to "%s pending transaction",
    "%s received transactions" to "%s received transaction",
    "%s sent transactions" to "%s sent transaction",
    "%s locations" to "%s location",
    "%s words" to "%s word",
    "%s payments" to "%s payment",
    "%s payment links" to "%s payment link",
    "%s link transactions" to "%s link transaction",
    "in %s open links" to "in %s open link",
    "%s links" to "%s link"
)
