package one.monero.moneroone.ui.screens.wallet

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The agree line under Claim: one link to the Username Terms in every app language. */
class UsernameTermsNoteTest {
    private val source = "By claiming, you confirm you are 18 or older and agree to the [Username Terms](https://monero.one/username-terms)."
    private val terms = "https://monero.one/username-terms"
    // The resource scripts/localization/import_ios.py makes for [source].
    private val resource = "l10n_by_claiming_you_confirm_you_are_18_or_older_and_agree_t_06caf4b4"

    @Test fun theEnglishLineLinksTheUsernameTerms() {
        val note = markdownLinks(source)
        assertEquals("By claiming, you confirm you are 18 or older and agree to the Username Terms.", note.text)
        val links = note.getLinkAnnotations(0, note.length)
        assertEquals(1, links.size)
        assertEquals(terms, (links[0].item as LinkAnnotation.Url).url)
        assertEquals("Username Terms", note.text.substring(links[0].start, links[0].end))
    }

    @Test fun textWithoutALinkStaysAsItIs() {
        val note = markdownLinks("Usernames are permanent. [Not a link] (here)")
        assertEquals("Usernames are permanent. [Not a link] (here)", note.text)
        assertTrue(note.getLinkAnnotations(0, note.length).isEmpty())
    }

    @Test fun everyLanguageKeepsOneLinkToTheUsernameTerms() {
        val folders = File("src/main/res").listFiles { f -> File(f, "strings_localized.xml").exists() }.orEmpty()
        assertEquals("English and 15 translations", 16, folders.size)
        for (folder in folders) {
            val value = stringValue(File(folder, "strings_localized.xml"))
            val note = markdownLinks(value)
            val links = note.getLinkAnnotations(0, note.length)
            assertEquals(folder.name, 1, links.size)
            assertEquals(folder.name, terms, (links[0].item as LinkAnnotation.Url).url)
            assertTrue(folder.name, links[0].end > links[0].start)
            assertFalse(folder.name, note.text.contains("](") || note.text.contains(terms))
        }
    }

    /** The resource's text as Android reads it: the quotes and backslash escapes removed. */
    private fun stringValue(file: File): String {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            if (node.attributes.getNamedItem("name")?.nodeValue == resource) {
                return node.textContent.removeSurrounding("\"").replace(Regex("""\\(.)""")) { m ->
                    if (m.groupValues[1] == "n") "\n" else m.groupValues[1]
                }
            }
        }
        throw AssertionError("$resource is missing in $file")
    }
}
