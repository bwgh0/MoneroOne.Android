package one.monero.moneroone.core.whatsnew

import one.monero.moneroone.core.wallet.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewGateTest {
    private val prefs = FakeSharedPreferences()
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_800_000_000_000L
    private val v11 = NotesVersion(1, 1)
    private val v12 = NotesVersion(1, 2)

    private fun entry(version: NotesVersion) = WhatsNewEntry(version, emptyList(), emptyList())
    private val notes = listOf(entry(v11), entry(v12))

    private fun gate(app: String, entries: List<WhatsNewEntry> = notes) =
        WhatsNewGate(prefs, NotesVersion.parse(app), entries)

    @Test
    fun `versions compare as numbers`() {
        assertTrue(NotesVersion(1, 10) > NotesVersion(1, 9))
        assertTrue(NotesVersion(2, 0) > NotesVersion(1, 10))
        assertEquals(NotesVersion(1, 1), NotesVersion.parse("1.1.0"))
        assertEquals(NotesVersion(1, 10), NotesVersion.parse("1.10.2-beta"))
        assertNull(NotesVersion.parse("one"))
    }

    @Test
    fun `the newest entry at or below the app version shows`() {
        assertEquals(v11, gate("1.1.0").entry?.version)
        assertEquals(v11, gate("1.1.3").entry?.version)
        assertEquals(v12, gate("1.4.0").entry?.version)
        assertNull(gate("1.0.9").entry)
        val tenth = listOf(entry(NotesVersion(1, 9)), entry(NotesVersion(1, 10)))
        assertEquals(NotesVersion(1, 10), gate("1.10.0", tenth).entry?.version)
    }

    @Test
    fun `a fresh install marks the newest entry seen and shows no banner`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = false)
        assertEquals("1.1", prefs.getString(WhatsNewGate.SEEN_KEY, null))
        assertFalse(gate.isPending)
        assertFalse(gate.bannerOnHome(start, otherBannerShowing = false))

        // The wallet the person creates next does not bring the banner back.
        gate.launched(hadWalletFiles = true)
        assertFalse(gate.bannerOnHome(start, otherBannerShowing = false))
    }

    @Test
    fun `an update with a wallet shows the banner until the X`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = true)
        assertTrue(gate.isPending)
        assertTrue(gate.bannerOnHome(start, otherBannerShowing = false))
        // A relaunch keeps it.
        gate("1.1.0").launched(hadWalletFiles = true)
        assertTrue(gate("1.1.0").bannerOnHome(start + day, otherBannerShowing = false))

        gate.markSeen()
        assertFalse(gate.isPending)
        assertFalse(gate("1.1.0").bannerOnHome(start + 2 * day, otherBannerShowing = false))
    }

    @Test
    fun `opening the sheet marks the entry seen`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = true)
        assertTrue(gate.bannerOnHome(start, otherBannerShowing = false))
        gate.markSeen()  // the banner or Settings > Version opened the sheet
        assertEquals("1.1", prefs.getString(WhatsNewGate.SEEN_KEY, null))
        assertFalse(gate.bannerOnHome(start, otherBannerShowing = false))
    }

    @Test
    fun `Settings opens the sheet with no banner and nothing breaks later`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = false)
        gate.markSeen()
        assertEquals("1.1", prefs.getString(WhatsNewGate.SEEN_KEY, null))
    }

    @Test
    fun `the banner goes by itself 30 days after it first showed`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = true)
        assertTrue(gate.bannerOnHome(start, otherBannerShowing = false))
        // A later home keeps the first date.
        assertTrue(gate.bannerOnHome(start + 10 * day, otherBannerShowing = false))
        assertTrue(gate.bannerOnHome(start + 30 * day - 1, otherBannerShowing = false))
        assertFalse(gate.bannerOnHome(start + 30 * day, otherBannerShowing = false))
        assertFalse(gate.isPending)
        assertEquals("1.1", prefs.getString(WhatsNewGate.SEEN_KEY, null))
    }

    @Test
    fun `another banner makes it wait and its clock does not start`() {
        val gate = gate("1.1.0")
        gate.launched(hadWalletFiles = true)
        assertFalse(gate.bannerOnHome(start, otherBannerShowing = true))
        assertFalse(prefs.contains(WhatsNewGate.SHOWN_AT_KEY))
        assertTrue(gate.isPending)

        // The offline banner goes 40 days later: the 30 days start now.
        assertTrue(gate.bannerOnHome(start + 40 * day, otherBannerShowing = false))
        assertTrue(gate.bannerOnHome(start + 69 * day, otherBannerShowing = false))
        assertFalse(gate.bannerOnHome(start + 70 * day, otherBannerShowing = false))
    }

    @Test
    fun `a later big update shows its own banner with a fresh clock`() {
        gate("1.1.0").apply {
            launched(hadWalletFiles = true)
            assertTrue(bannerOnHome(start, otherBannerShowing = false))
        }
        // Updated to 1.2 before anyone acted on the 1.1 banner, 20 days in.
        val later = gate("1.2.0")
        later.launched(hadWalletFiles = true)
        assertEquals(v12, later.entry?.version)
        assertTrue(later.bannerOnHome(start + 20 * day, otherBannerShowing = false))
        assertTrue(later.bannerOnHome(start + 49 * day, otherBannerShowing = false))
        assertFalse(later.bannerOnHome(start + 50 * day, otherBannerShowing = false))
        assertEquals("1.2", prefs.getString(WhatsNewGate.SEEN_KEY, null))
    }

    @Test
    fun `a seen 1_1 brings the 1_2 banner after the update`() {
        val first = gate("1.1.0")
        first.launched(hadWalletFiles = false)
        assertFalse(first.isPending)

        val later = gate("1.2.0")
        later.launched(hadWalletFiles = true)
        assertTrue(later.isPending)
        assertTrue(later.bannerOnHome(start, otherBannerShowing = false))
        later.markSeen()
        assertFalse(later.isPending)
    }

    @Test
    fun `an update with no wallet files marks the new entry seen`() {
        // 1.1 installed fresh, and no wallet made before the 1.2 update.
        gate("1.1.0").launched(hadWalletFiles = false)
        val later = gate("1.2.0")
        later.launched(hadWalletFiles = false)
        assertEquals("1.2", prefs.getString(WhatsNewGate.SEEN_KEY, null))
        assertFalse(later.isPending)

        // The first wallet, made after the update, brings no banner.
        gate("1.2.0").launched(hadWalletFiles = true)
        assertFalse(gate("1.2.0").bannerOnHome(start, otherBannerShowing = false))
    }

    @Test
    fun `an older app never lowers the seen version`() {
        gate("1.2.0").apply { launched(hadWalletFiles = false) }
        gate("1.1.0").markSeen()
        assertEquals("1.2", prefs.getString(WhatsNewGate.SEEN_KEY, null))
        assertFalse(gate("1.1.0").isPending)
    }

    @Test
    fun `an app below every entry shows nothing and stores nothing`() {
        val gate = gate("1.0.9")
        gate.launched(hadWalletFiles = false)
        assertFalse(gate.isPending)
        assertFalse(gate.bannerOnHome(start, otherBannerShowing = false))
        assertFalse(prefs.contains(WhatsNewGate.SEEN_KEY))
    }

    @Test
    fun `the shipped notes have a 1_1 entry with four highlights`() {
        val shipped = WhatsNewNotes.newest(NotesVersion(1, 1))
        assertEquals(v11, shipped?.version)
        assertEquals(4, shipped?.highlights?.size)
        assertEquals(7, shipped?.improvements?.size)
    }
}
