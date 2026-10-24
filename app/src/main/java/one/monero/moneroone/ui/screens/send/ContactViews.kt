package one.monero.moneroone.ui.screens.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.contacts.Contact
import one.monero.moneroone.core.contacts.ContactStore
import one.monero.moneroone.core.contacts.Contacts
import one.monero.moneroone.core.openalias.OpenAlias
import one.monero.moneroone.core.openalias.RecipientInput
import one.monero.moneroone.ui.components.CapsuleShape
import one.monero.moneroone.ui.components.CellFill
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.moneroTextFieldColors
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.truncateMiddle
import one.monero.moneroone.ui.screens.wallet.SwipeToDeleteRow
import one.monero.moneroone.ui.screens.wallet.WalletRowCheck
import one.monero.moneroone.ui.screens.wallet.rememberRowSwipeState
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
    actions: List<CustomAccessibilityAction> = emptyList(),
    /** The card fill: the surface on Send, fillElevated on the Contacts sheet (android-4). */
    color: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    // The switcher's capsule row without the ring (iOS ContactRowButton).
    GlassCard(modifier = modifier.fillMaxWidth(), color = color, shape = CapsuleShape) {
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
            // The switcher's green check; TalkBack hears the row as selected.
            if (checked) WalletRowCheck(isActive = true)
        }
    }
}

/**
 * A send to a contact's Review: the monogram in the 40 disc of the Recipient row, "Recipient", the
 * name and the address 8…8, as [ReviewWalletRow] shows a wallet. One TalkBack element.
 */
@Composable
internal fun ReviewContactRow(contact: Contact, address: String) {
    val label = tr("Recipient")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, ${contact.name}, ${truncateMiddle(address)}" }
    ) {
        RecipientMonogram(Contacts.initials(contact), 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (address.isNotEmpty()) Text(truncateMiddle(address), style = MonoCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        dismissButton = { DismissTextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
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
    // Saved, so a theme or font change keeps New and Edit Contact open.
    var editingNew by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleting = contacts.firstOrNull { it.id == deletingId }
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
                // iOS: Cancel at the leading edge, as in the New Contact bar.
                DismissTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text(tr("Cancel")) }
                Text(
                    tr("Contacts"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center).semantics { heading() }
                )
                // iOS: a glass circle with a plus in the label color; fillElevated, as the sheet is the surface.
                GlassButton(
                    onClick = { editingNew = true },
                    containerColor = MoneroTheme.colors.fillElevated,
                    // 16 from the edge, over the search field's end (the bar pads 8).
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = tr("New Contact"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (contacts.isNotEmpty()) MoneroTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Search"), maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = moneroTextFieldColors(containerColor = CellFill),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            if (contacts.isNotEmpty()) Spacer(Modifier.height(16.dp))
            when {
                contacts.isEmpty() -> ContactsEmptyCard()
                shown.isEmpty() -> ContactsMessage(tr("No Results"), null)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.id }) { contact ->
                        val swipe = rememberRowSwipeState()
                        val deleteLabel = tr("Delete Contact")
                        SwipeToDeleteRow(swipe = swipe, deleteLabel = deleteLabel, onDelete = { deletingId = contact.id }) {
                            Box {
                                val copyLabel = tr(if (contact.isOpenAlias) "Copy OpenAlias" else "Copy Address")
                                val editLabel = tr("Edit Contact")
                                ContactRow(
                                    contact = contact,
                                    checked = contact.id == selectedId,
                                    color = MoneroTheme.colors.fillElevated,
                                    modifier = Modifier.offset { swipe.offset() },
                                    // A tap on an open row closes it and never picks.
                                    onClick = { if (swipe.isOpen) swipe.close() else onPick(contact) },
                                    onLongClick = { menuFor = contact.id },
                                    actions = listOf(
                                        CustomAccessibilityAction(editLabel) { editingId = contact.id; true },
                                        CustomAccessibilityAction(copyLabel) { copyDestination(context, contact); true },
                                        CustomAccessibilityAction(deleteLabel) { deletingId = contact.id; true }
                                    )
                                )
                                DropdownMenu(expanded = menuFor == contact.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text(editLabel) }, onClick = {
                                        menuFor = null
                                        editingId = contact.id
                                    })
                                    DropdownMenuItem(text = { Text(copyLabel) }, onClick = {
                                        menuFor = null
                                        copyDestination(context, contact)
                                    })
                                    DropdownMenuItem(text = { Text(deleteLabel, color = ErrorRed) }, onClick = {
                                        menuFor = null
                                        deletingId = contact.id
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingNew) ContactEditorSheet(contact = null, onDismiss = { editingNew = false })
    contacts.firstOrNull { it.id == editingId }?.let { contact ->
        ContactEditorSheet(contact = contact, onDismiss = { editingId = null })
    }
    deleting?.let { contact ->
        DeleteContactAlert(
            onDelete = {
                store.delete(contact.id)
                deletingId = null
            },
            onDismiss = { deletingId = null }
        )
    }
}

/** No contacts yet (ios-10): the empty-state card, with the person glyph, on the cell fill. */
@Composable
private fun ContactsEmptyCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shadow = false,
        color = CellFill
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(tr("No Contacts"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Contacts you save show here."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactEditorSheet(contact: Contact?, onDismiss: () -> Unit, prefill: String = "") {
    val context = LocalContext.current
    val store = remember { ContactStore.get(context) }
    var name by rememberSaveable { mutableStateOf(contact?.name.orEmpty()) }
    var destination by rememberSaveable { mutableStateOf(contact?.destination ?: prefill) }
    var deleting by rememberSaveable { mutableStateOf(false) }
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

    // A sheet over the Contacts sheet, as iOS presents it, so the list keeps its place.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
            val cardColor = CellFill
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
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
                    GlassCard(Modifier.fillMaxWidth(), shadow = false, cornerRadius = 16.dp, color = cardColor) {
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
                        textStyle = MonoCaption,
                        // The whole address shows, over several lines (ios-14, ios-15).
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        colors = moneroTextFieldColors(containerColor = cardColor),
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
                        GlassCard(Modifier.fillMaxWidth(), shadow = false, cornerRadius = 16.dp, color = cardColor) {
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
