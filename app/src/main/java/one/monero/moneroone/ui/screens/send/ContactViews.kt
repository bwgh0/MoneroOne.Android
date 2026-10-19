package one.monero.moneroone.ui.screens.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.contacts.ContactStore
import one.monero.moneroone.core.contacts.Contacts
import one.monero.moneroone.core.openalias.OpenAlias
import one.monero.moneroone.core.openalias.RecipientInput
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.moneroTextFieldColors
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.truncateMiddle
import one.monero.moneroone.core.locale.tr
import timber.log.Timber

/** What TalkBack reads for a contact: the name, then the OpenAlias name or the short address. */
internal fun contactSpoken(contact: Contact): String =
    "${contact.name}, ${if (contact.isOpenAlias) contact.destination else truncateMiddle(contact.destination)}"

/** The line under a contact's name: the OpenAlias name, or the address 8…8 in mono. */
@Composable
internal fun ContactDestinationText(contact: Contact) {
    if (contact.isOpenAlias) {
        Text(
            contact.destination,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(truncateMiddle(contact.destination), style = MonoCaption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The Contacts button at the end of Send's empty recipient field (iOS person.crop.circle). */
@Composable
internal fun ContactsFieldButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.AccountCircle, contentDescription = tr("Contacts"), tint = MoneroOrange, modifier = Modifier.size(24.dp))
    }
}

/**
 * The recipient field after a contact was picked, or an address or OpenAlias name that is a
 * contact's was typed, pasted or scanned: the monogram, the name and the destination, with the
 * clear button, as [OwnWalletRecipientCard] shows a wallet.
 */
@Composable
internal fun ContactRecipientCard(contact: Contact, onClear: () -> Unit) {
    val clearLabel = tr("Clear recipient")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = contactSpoken(contact)
                customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true })
            }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecipientMonogram(Contacts.initials(contact), 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ContactDestinationText(contact)
        }
        OpenAliasClearButton(onClear, label = clearLabel)
    }
}

/**
 * One contact as a card (android-4, ios-12): the gray monogram, the name and the destination.
 * [checked] marks the contact the field holds. One TalkBack stop, with the menu's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    actions: List<CustomAccessibilityAction> = emptyList()
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .clearAndSetSemantics {
                    contentDescription = contactSpoken(contact)
                    selected = checked
                    onClick { onClick(); true }
                    if (onLongClick != null) onLongClick { onLongClick(); true }
                    if (actions.isNotEmpty()) customActions = actions
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecipientMonogram(Contacts.initials(contact), 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ContactDestinationText(contact)
            }
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = MoneroOrange, modifier = Modifier.size(22.dp))
        }
    }
}

/** The one delete alert, from the menu, a swipe and Edit Contact. */
@Composable
internal fun DeleteContactAlert(onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Delete Contact?")) },
        text = { Text(tr("Your transactions stay in History.")) },
        confirmButton = { TextButton(onClick = onDelete) { Text(tr("Delete"), color = ErrorRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

private sealed interface ContactEdit {
    data class New(val prefill: String) : ContactEdit
    data class Edit(val contact: Contact) : ContactEdit
}

/**
 * The Contacts sheet (android-4): title, + (New Contact), Search, the contacts A to Z. A tap
 * picks a contact; touch and hold opens Edit, Copy and Delete; a swipe left deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsSheet(selectedId: String?, onPick: (Contact) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ContactStore.get(context) }
    val contacts by store.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ContactEdit?>(null) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val shown = remember(query, contacts) { if (query.isBlank()) contacts else Contacts.matches(query, contacts) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    tr("Contacts"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center).semantics { heading() }
                )
                IconButton(onClick = { editing = ContactEdit.New("") }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Add, contentDescription = tr("New Contact"))
                }
            }
            Spacer(Modifier.height(16.dp))
            MoneroTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Search"), maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = moneroTextFieldColors(containerColor = CellFill),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
            when {
                contacts.isEmpty() -> ContactsMessage(tr("No Contacts"), tr("Contacts you save show here."))
                shown.isEmpty() -> ContactsMessage(tr("No Results"), null)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.id }) { contact ->
                        SwipeToDeleteContact(onDelete = { deleting = contact }) {
                            Box {
                                val copyLabel = tr(if (contact.isOpenAlias) "Copy OpenAlias" else "Copy Address")
                                val editLabel = tr("Edit Contact")
                                val deleteLabel = tr("Delete Contact")
                                ContactRow(
                                    contact = contact,
                                    checked = contact.id == selectedId,
                                    onClick = { onPick(contact) },
                                    onLongClick = { menuFor = contact.id },
                                    actions = listOf(
                                        CustomAccessibilityAction(editLabel) { editing = ContactEdit.Edit(contact); true },
                                        CustomAccessibilityAction(copyLabel) { copyDestination(context, contact); true },
                                        CustomAccessibilityAction(deleteLabel) { deleting = contact; true }
                                    )
                                )
                                DropdownMenu(expanded = menuFor == contact.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text(editLabel) }, onClick = {
                                        menuFor = null
                                        editing = ContactEdit.Edit(contact)
                                    })
                                    DropdownMenuItem(text = { Text(copyLabel) }, onClick = {
                                        menuFor = null
                                        copyDestination(context, contact)
                                    })
                                    DropdownMenuItem(text = { Text(deleteLabel, color = ErrorRed) }, onClick = {
                                        menuFor = null
                                        deleting = contact
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val edit = editing) {
        is ContactEdit.New -> ContactEditorDialog(contact = null, prefill = edit.prefill, onDismiss = { editing = null })
        is ContactEdit.Edit -> ContactEditorDialog(contact = edit.contact, onDismiss = { editing = null })
        null -> Unit
    }
    deleting?.let { contact ->
        DeleteContactAlert(
            onDelete = {
                store.delete(contact.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** A swipe left asks to delete; the row springs back while the alert decides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteContact(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) ErrorRed else Color.Transparent,
                label = "contactSwipe"
            )
            Box(
                Modifier.fillMaxSize().background(color, RoundedCornerShape(20.dp)).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        }
    ) { content() }
}

@Composable
private fun ContactsMessage(title: String, detail: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyDestination(context: Context, contact: Contact) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(contact.name, contact.destination))
    Toast.makeText(context, tr("Copied to clipboard"), Toast.LENGTH_SHORT).show()
}

/** The display name of a contact the system picker returned. The pick grants the read; no permission. */
private fun pickedName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
    }
} catch (e: Exception) {
    Timber.e("Contacts: could not read the picked name (${e.javaClass.simpleName})")
    null
}

/**
 * New Contact (ios-14) and Edit Contact (ios-15), full screen: the monogram, Name with
 * "Choose from Contacts", and one "Address or OpenAlias" field, checked as Send's field is.
 * Edit ends with "Delete Contact" in red.
 */
@Composable
internal fun ContactEditorDialog(contact: Contact?, onDismiss: () -> Unit, prefill: String = "") {
    val context = LocalContext.current
    val store = remember { ContactStore.get(context) }
    var name by rememberSaveable { mutableStateOf(contact?.name.orEmpty()) }
    var destination by rememberSaveable { mutableStateOf(contact?.destination ?: prefill) }
    var deleting by remember { mutableStateOf(false) }
    val lookup = rememberOpenAliasLookup()
    LaunchedEffect(Unit) { if (destination.isNotBlank()) lookup.onInputChanged(destination, immediate = true) }
    val kind = remember(destination) { OpenAlias.classify(destination) }
    val resolved = (lookup.status as? LookupStatus.Resolved)?.takeIf { kind is RecipientInput.Name }
    val valid = when (kind) {
        is RecipientInput.Address -> isValidMoneroAddress(kind.text)
        is RecipientInput.Name -> resolved != null
        else -> false
    }
    val canSave = name.isNotBlank() && valid
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { pickedName(context, it) }?.let { name = it }
    }

    fun save() {
        val kindText = Contacts.kindOf(destination, ::isValidMoneroAddress) ?: return
        val ok = if (contact == null) store.add(name, destination, kindText) != null
        else store.update(contact.copy(name = name, destination = destination, kind = kindText))
        if (ok) onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MoneroTheme.colors.bgGrouped) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DismissTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text(tr("Cancel")) }
                    Text(
                        tr(if (contact == null) "New Contact" else "Edit Contact"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center).semantics { heading() }
                    )
                    TextButton(onClick = ::save, enabled = canSave, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(tr(if (contact == null) "Save" else "Done"), color = if (canSave) MoneroOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    RecipientMonogram(OpenAlias.initials(name.takeIf { it.isNotBlank() }, ""), 88.dp)
                    Spacer(Modifier.height(24.dp))
                    GlassCard(Modifier.fillMaxWidth(), shadow = false, cornerRadius = 16.dp) {
                        Column {
                            MoneroTextField(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                placeholder = { Text(tr("Name")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                colors = moneroTextFieldColors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("Name") }
                            )
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                tr("Choose from Contacts"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MoneroOrange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { picker.launch(null) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        tr("Address or OpenAlias"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
                    )
                    val fieldLabel = tr("Address or OpenAlias")
                    MoneroTextField(
                        value = destination,
                        onValueChange = {
                            destination = it
                            lookup.onInputChanged(it, immediate = false)
                        },
                        placeholder = { Text(fieldLabel, style = MonoCaption) },
                        textStyle = MonoCaption,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        trailingIcon = if (destination.isNotEmpty()) {
                            {
                                OpenAliasClearButton(onClear = {
                                    destination = ""
                                    lookup.onInputChanged("", immediate = true)
                                })
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldLabel }
                    )
                    if (destination.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth()) {
                            AddressCheckSlot(lookup.status)
                            OpenAliasStatusRow(status = lookup.status, onRetry = lookup::retry)
                            OpenAliasResolvedLines(resolved?.summary())
                        }
                    }
                    if (contact != null) {
                        Spacer(Modifier.height(32.dp))
                        GlassCard(Modifier.fillMaxWidth(), shadow = false, cornerRadius = 16.dp) {
                            Text(
                                tr("Delete Contact"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ErrorRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleting = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        if (deleting && contact != null) {
            DeleteContactAlert(
                onDelete = {
                    deleting = false
                    if (store.delete(contact.id)) onDismiss()
                },
                onDismiss = { deleting = false }
            )
        }
    }
}
