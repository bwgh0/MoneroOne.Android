package one.monero.moneroone.core.whatsnew

import android.content.Context
import android.content.SharedPreferences
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.wallet.WalletStore
import java.io.File

/** A notes version, major.minor. Compares as numbers: 1.10 is above 1.9. */
data class NotesVersion(val major: Int, val minor: Int) : Comparable<NotesVersion> {
    override fun compareTo(other: NotesVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor })

    override fun toString(): String = "$major.$minor"

    companion object {
        /** "1.1", "1.1.0" or "1.10.2-beta" gives the major.minor; anything else gives null. */
        fun parse(text: String): NotesVersion? {
            val parts = text.trim().split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: return null
            return NotesVersion(major, minor)
        }
    }
}

/** The glyph on a highlight's tile. */
enum class WhatsNewGlyph { LINK, AT, KEY }

/** One highlight row: English source strings, translated with `tr` when shown. */
data class WhatsNewHighlight(val glyph: WhatsNewGlyph, val title: String, val text: String)

/** The notes of one big update. They ship in the app; nothing loads them from the network. */
data class WhatsNewEntry(
    val version: NotesVersion,
    val highlights: List<WhatsNewHighlight>,
    val improvements: List<String>
)

object WhatsNewNotes {
    /** Oldest first. Add an entry for each big update (major.minor). */
    val entries: List<WhatsNewEntry> = listOf(
        WhatsNewEntry(
            version = NotesVersion(1, 1),
            highlights = listOf(
                WhatsNewHighlight(
                    WhatsNewGlyph.LINK,
                    "Send XMR as a link",
                    "Whoever opens it claims the funds in Monero One. In Send, choose Link."
                ),
                WhatsNewHighlight(
                    WhatsNewGlyph.AT,
                    "Send to a name",
                    "Enter an OpenAlias name in place of an address, checked with DNSSEC."
                ),
                WhatsNewHighlight(
                    WhatsNewGlyph.KEY,
                    "New seed phrase entry",
                    "One box per word, paste a whole phrase at once, and a checksum check."
                )
            ),
            improvements = listOf(
                "Review shows the exact network fee you pay.",
                "Rounded QR codes.",
                "New wallets are created faster.",
                "Fixes for custom node logins.",
                "General stability improvements and bug fixes."
            )
        )
    )

    /** The newest entry at or below [app] (major.minor), or null when none is. */
    fun newest(app: NotesVersion?, entries: List<WhatsNewEntry> = this.entries): WhatsNewEntry? =
        app?.let { version -> entries.filter { it.version <= version }.maxByOrNull { it.version } }
}

/**
 * When the What's New banner shows on the wallet home (iOS WhatsNewGate). One
 * state per install, never per wallet:
 *
 * - `seen`: the newest notes version the person saw or never needs. The X, the
 *   sheet (from the banner or Settings > Version) and the 30-day limit set it.
 * - `shownAt` + `shownFor`: when the banner for that version first showed.
 *
 * [launched] runs at every launch: no wallet files mean nobody used the older
 * version (a fresh install, or one that never made a wallet), so the newest
 * entry counts as seen and no banner shows; wallet files mean an update, so an
 * unseen entry's banner waits for the home.
 */
class WhatsNewGate(
    private val prefs: SharedPreferences,
    appVersion: NotesVersion?,
    entries: List<WhatsNewEntry> = WhatsNewNotes.entries
) {
    /** The notes for this app version: the newest entry at or below it. */
    val entry: WhatsNewEntry? = WhatsNewNotes.newest(appVersion, entries)

    private val seen: NotesVersion? get() = prefs.getString(SEEN_KEY, null)?.let(NotesVersion::parse)

    /** The current entry is unseen, so the home shows the banner (when no other banner shows). */
    val isPending: Boolean
        get() {
            val current = entry ?: return false
            val seen = seen ?: return true
            return seen < current.version
        }

    /**
     * At each launch, before onboarding can create a wallet. No wallet files:
     * the newest entry counts as seen. Wallet files: nothing changes, and an
     * unseen entry's banner waits for the home.
     */
    fun launched(hadWalletFiles: Boolean) {
        if (hadWalletFiles) return
        entry?.let(::markSeen)
    }

    /**
     * The wallet home appeared, or its banners changed. True: show the banner.
     * The first time it shows, the date is stored; 30 days after that date the
     * entry counts as seen and the banner goes for good. While
     * [otherBannerShowing] (Offline) the banner waits and its clock does not start.
     */
    fun bannerOnHome(nowMs: Long, otherBannerShowing: Boolean): Boolean {
        val current = entry ?: return false
        if (!isPending) return false
        val shownAt = prefs.getLong(SHOWN_AT_KEY, -1L)
            .takeIf { it >= 0 && prefs.getString(SHOWN_FOR_KEY, null) == current.version.toString() }
        if (shownAt != null && nowMs - shownAt >= AUTO_HIDE_MS) {
            markSeen(current)
            return false
        }
        if (otherBannerShowing) return false
        if (shownAt == null) {
            prefs.edit()
                .putLong(SHOWN_AT_KEY, nowMs)
                .putString(SHOWN_FOR_KEY, current.version.toString())
                .apply()
        }
        return true
    }

    /** The X, or the sheet opened (from the banner or Settings > Version). */
    fun markSeen() {
        entry?.let(::markSeen)
    }

    private fun markSeen(current: WhatsNewEntry) {
        val stored = seen
        if (stored != null && stored >= current.version) return
        prefs.edit()
            .putString(SEEN_KEY, current.version.toString())
            .remove(SHOWN_AT_KEY)
            .remove(SHOWN_FOR_KEY)
            .apply()
    }

    companion object {
        const val SEEN_KEY = "whats_new_seen"
        const val SHOWN_AT_KEY = "whats_new_shown_at"
        const val SHOWN_FOR_KEY = "whats_new_shown_for"
        const val AUTO_HIDE_MS = 30L * 24 * 60 * 60 * 1000

        val appVersion: NotesVersion? get() = NotesVersion.parse(BuildConfig.VERSION_NAME)

        fun create(context: Context): WhatsNewGate = WhatsNewGate(
            context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE),
            appVersion
        )

        /**
         * Wallet files on disk: the kit's `files/wallets` folder, or the wallet list
         * in this app's preferences. Both go with an uninstall (no backup).
         */
        fun hasWalletFiles(context: Context): Boolean {
            val root = File(context.filesDir, "wallets")
            val files = root.walkTopDown().maxDepth(2).any { it.isFile }
            if (files) return true
            val prefs = context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE)
            return WalletStore(prefs).wallets().isNotEmpty() || WalletStore(prefs).hasUndecodableRows()
        }
    }
}
