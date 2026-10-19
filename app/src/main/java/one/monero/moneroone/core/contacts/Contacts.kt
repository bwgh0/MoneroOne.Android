package one.monero.moneroone.core.contacts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import one.monero.moneroone.core.openalias.OpenAlias
import one.monero.moneroone.core.openalias.RecipientInput
import one.monero.moneroone.core.wallet.SecurePrefs
import timber.log.Timber
import java.util.UUID

/**
 * A person you send to: a name and one destination, an address or an OpenAlias name.
 * [kind] is text, so a later kind (a username) needs no migration.
 */
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val destination: String,
    val kind: String = KIND_ADDRESS,
    val createdAtMillis: Long = 0L
) {
    val isOpenAlias: Boolean get() = kind == KIND_OPEN_ALIAS

    companion object {
        const val KIND_ADDRESS = "address"
        const val KIND_OPEN_ALIAS = "openAlias"
    }
}

/**
 * The contacts of this phone: app-wide, never synced, no Contacts permission. They live in the
 * app's encrypted prefs ([SecurePrefs], key in the Android Keystore), which the app keeps out of
 * backups. Remove All wipes them.
 */
class ContactStore(private val prefs: SharedPreferences) {

    private val _contacts = MutableStateFlow(sorted(decode(prefs.getString(KEY_CONTACTS, null))))

    /** A to Z by name, any case. */
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    @Synchronized
    fun add(name: String, destination: String, kind: String, nowMillis: Long = System.currentTimeMillis()): Contact? {
        val contact = Contact(UUID.randomUUID().toString(), name.trim(), destination.trim(), kind, nowMillis)
        return if (save(_contacts.value + contact)) contact else null
    }

    @Synchronized
    fun update(contact: Contact): Boolean {
        if (_contacts.value.none { it.id == contact.id }) return false
        val clean = contact.copy(name = contact.name.trim(), destination = contact.destination.trim())
        return save(_contacts.value.map { if (it.id == contact.id) clean else it })
    }

    @Synchronized
    fun delete(id: String): Boolean = save(_contacts.value.filterNot { it.id == id })

    /** Remove All. */
    @Synchronized
    fun wipe() {
        try {
            prefs.edit().remove(KEY_CONTACTS).commit()
        } catch (e: Exception) {
            Timber.e(e, "Contacts: could not remove them")
        }
        _contacts.value = emptyList()
    }

    private fun save(next: List<Contact>): Boolean {
        val ok = try {
            prefs.edit().putString(KEY_CONTACTS, json.encodeToString(serializer, next)).commit()
        } catch (e: Exception) {
            Timber.e(e, "Contacts: could not save them")
            false
        }
        if (ok) _contacts.value = sorted(next)
        return ok
    }

    companion object {
        const val KEY_CONTACTS = "contacts.v1"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val serializer = ListSerializer(Contact.serializer())

        internal fun decode(encoded: String?): List<Contact> {
            if (encoded.isNullOrBlank()) return emptyList()
            return try {
                json.decodeFromString(serializer, encoded)
            } catch (e: Exception) {
                Timber.e("Contacts: the stored contacts are unreadable (${e.javaClass.simpleName})")
                emptyList()
            }
        }

        private fun sorted(list: List<Contact>) = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        @Volatile
        private var instance: ContactStore? = null

        fun get(context: Context): ContactStore =
            instance ?: synchronized(this) {
                instance ?: ContactStore(SecurePrefs.open(context)).also { instance = it }
            }
    }
}

/** Matching and display rules for contacts. */
object Contacts {

    /** Contacts whose name or destination contains [query], any case. Empty for an empty query. */
    fun matches(query: String, contacts: List<Contact>): List<Contact> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        return contacts.filter { it.name.contains(text, ignoreCase = true) || it.destination.contains(text, ignoreCase = true) }
    }

    /** The contact saved with [address], else null. */
    fun forAddress(address: String, contacts: List<Contact>): Contact? {
        val text = address.trim()
        if (text.isEmpty()) return null
        return contacts.firstOrNull { !it.isOpenAlias && it.destination == text }
    }

    /** The contact saved with the OpenAlias name [name], any case. */
    fun forOpenAlias(name: String, contacts: List<Contact>): Contact? {
        val text = name.trim()
        if (text.isEmpty()) return null
        return contacts.firstOrNull { it.isOpenAlias && it.destination.equals(text, ignoreCase = true) }
    }

    /** The contact that [input] (an address or an OpenAlias name in Send's field) is. */
    fun forInput(input: String, contacts: List<Contact>): Contact? =
        forAddress(input, contacts) ?: forOpenAlias(input, contacts)

    /**
     * The kind of [destination] when it is one Send accepts: an address that [isValidAddress]
     * accepts, or an OpenAlias name. Null for anything else.
     */
    fun kindOf(destination: String, isValidAddress: (String) -> Boolean): String? =
        when (val input = OpenAlias.classify(destination)) {
            is RecipientInput.Address -> if (isValidAddress(input.text)) Contact.KIND_ADDRESS else null
            is RecipientInput.Name -> Contact.KIND_OPEN_ALIAS
            else -> null
        }

    /** The query form of an OpenAlias contact's name, as the pins key it. */
    fun openAliasQuery(contact: Contact): String? =
        (OpenAlias.classify(contact.destination) as? RecipientInput.Name)?.name?.query

    /** Two letters for the monogram: the first letters of the first two words of the name. */
    fun initials(contact: Contact): String? = OpenAlias.initials(contact.name, contact.destination)
}
