package one.monero.moneroone.ui.screens.onboarding

import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.RestoreDateRange
import one.monero.moneroone.ui.components.SecureScreen
import one.monero.moneroone.core.locale.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import one.monero.moneroone.core.util.SeedClipboard
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.core.wallet.seed.SeedFormat
import one.monero.moneroone.ui.components.AddWalletFlowEffect
import one.monero.moneroone.ui.components.MoneroTextField
import one.monero.moneroone.ui.components.OnNavEntryEnd
import one.monero.moneroone.ui.components.PrimaryButton
import one.monero.moneroone.ui.components.moneroTextFieldColors
import one.monero.moneroone.ui.theme.MoneroOrange
import io.horizontalsystems.monerokit.util.RestoreHeight
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RestoreWalletScreen(
    walletViewModel: WalletViewModel,
    flowId: String,
    onWalletRestored: () -> Unit,
    onBack: () -> Unit,
    isAddingWallet: Boolean = false
) {
    SecureScreen()
    // The words live in this entry's ViewModel only (never saved state); cleared when the flow ends.
    val seedEntry: SeedEntryViewModel = viewModel()
    val entry = seedEntry.entry
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var namingStep by remember { mutableStateOf(false) }
    // iOS: a birthday inside the last 30 days is confirmed before moving on.
    var confirmRecentDate by remember { mutableStateOf(false) }

    val walletState by walletViewModel.walletState.collectAsState()
    val scope = rememberCoroutineScope()

    // The add runs in the ViewModel. Its outcome finishes the flow, so a screen
    // that the Activity recreates during the restore still moves on.
    val addOutcomes by walletViewModel.addOutcomes.collectAsState()
    val walletRestored = addOutcomes[flowId] == true
    LaunchedEffect(walletRestored) {
        if (walletRestored) {
            seedEntry.wipe()
            onWalletRestored()
        }
    }
    DisposableEffect(Unit) {
        onDispose { entry.dropFocus() }
    }
    OnNavEntryEnd("restore-flow") { walletViewModel.clearAddOutcome(flowId) }
    val addsInFlight by walletViewModel.addsInFlight.collectAsState()

    // Suppress auto-lock while the add-wallet flow is open (iOS parity).
    if (isAddingWallet) AddWalletFlowEffect(walletViewModel)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val cellFocus = remember { List(SeedEntryState.CELL_COUNT) { FocusRequester() } }
    val scrollState = rememberScrollState()
    val statusRequester = remember { BringIntoViewRequester() }
    val applyEffect: (SeedEntryState.Effect) -> Unit = { effect ->
        // No toast: the clipboard just held the words that were pasted.
        if (effect.clearClipboard) SeedClipboard.clear(context)
        when (val focus = effect.focus) {
            SeedEntryState.Focus.Keep -> Unit
            SeedEntryState.Focus.Hide -> {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            is SeedEntryState.Focus.Cell -> runCatching { cellFocus[focus.index].requestFocus() }
        }
        when (effect.scroll) {
            // After a frame: a paste can change the number of cells first.
            SeedEntryState.Scroll.Status -> scope.launch {
                withFrameNanos { }
                statusRequester.bringIntoView()
            }
            SeedEntryState.Scroll.Top -> scope.launch { scrollState.animateScrollTo(0) }
            null -> Unit
        }
    }
    val isPolyseed = entry.format == SeedFormat.POLYSEED

    val dateFormatter = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Restore Wallet"), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { if (namingStep) namingStep = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        // Continue stays at the bottom, behind the keyboard while it is up:
        // the grid gets that space. The keyboard closes by itself after the
        // last word and once the phrase is valid, which shows Continue again.
        bottomBar = {
            if (!namingStep) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PrimaryButton(
                        onClick = {
                            val phrase = entry.validPhrase ?: return@PrimaryButton
                            focusManager.clearFocus()
                            seedEntry.accepted = phrase
                            // A date inside the last 30 days is the classic
                            // mistake: confirm it first, as iOS does. A Polyseed
                            // carries its own birthday.
                            val recent = phrase.format != SeedFormat.POLYSEED &&
                                selectedDate?.let { it > RestoreDateRange.todayMillis() - RecentDateWindowMillis } == true
                            // Naming happens at the end (iOS parity).
                            if (recent) confirmRecentDate = true else namingStep = true
                        },
                        enabled = !walletState.isInitializing && entry.validPhrase != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (walletState.isInitializing) {
                            Text(tr("Restoring..."))
                        } else {
                            Text(tr("Continue"))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) { padding ->
        // With the keyboard up, the seed step ends at its top: the focused
        // cell scrolls into view above the keyboard and the suggestion row.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (namingStep) Modifier else Modifier.consumeWindowInsets(padding).imePadding())
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // iOS: 16 on the seed step; the naming step keeps the
                    // margins it has on Create.
                    .padding(horizontal = if (namingStep) 24.dp else 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (namingStep) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NameWalletStep(
                        defaultName = walletViewModel.nextWalletName(),
                        buttonLabel = tr("Restore Wallet"),
                        // Also covers an add that a screen before an Activity recreation started.
                        isBusy = walletState.isInitializing || flowId in addsInFlight,
                        onDone = { name, emoji ->
                            val phrase = seedEntry.accepted ?: return@NameWalletStep
                            // A Polyseed's restore height comes from its own birthday (ViewModel).
                            val date = selectedDate.takeIf { phrase.format != SeedFormat.POLYSEED }
                            val restoreHeightValue = date?.let { dateToRestoreHeight(it) }?.toString()
                            if (walletState.isInitializing || walletRestored || flowId in addsInFlight) return@NameWalletStep
                            scope.launch {
                                walletViewModel.restoreWallet(
                                    seed = phrase.restoreWords,
                                    restoreHeight = restoreHeightValue,
                                    flowId = flowId,
                                    restoreDateMillis = date,
                                    name = name,
                                    emoji = emoji
                                )
                            }
                        }
                    )
                    if (walletState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = walletState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    return@Column
                }

                Text(
                    text = tr("Enter your seed phrase"),
                    style = MaterialTheme.typography.titleMedium, // iOS .headline
                    modifier = Modifier.semantics { heading() }
                )

                // iOS: heading, hint and word grid 24 apart.
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = tr("16 words (Polyseed), 24 words (BIP39), or 25 words (Legacy)"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Numbered word cells. The keyboard must not correct, capitalize or
                // learn the words: a learned word can come back as a suggestion.
                SeedPhraseEntry(entry = entry, requesters = cellFocus, statusRequester = statusRequester, onEffect = applyEffect)

                // A Polyseed carries its birthday (iOS skips the date step for it).
                if (!isPolyseed) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Restore height input
                    MoneroTextField(
                        value = selectedDate?.let { dateFormatter.format(Date(it)) } ?: "",
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        label = { Text(tr("Wallet Birthday (Optional)")) },
                        placeholder = { Text(tr("Select date when wallet was created")) },
                        supportingText = { Text(tr("Leave empty to scan from beginning (slower)")) },
                        // Disabled only so the whole field takes the click; it reads as enabled.
                        colors = moneroTextFieldColors(disabledTextColor = MaterialTheme.colorScheme.onSurface),
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = tr("Select date"),
                                    tint = MoneroOrange
                                )
                            }
                        },
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                }

                if (walletState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = walletState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            if (!namingStep && entry.focusedIndex != null && WindowInsets.isImeVisible) {
                SeedSuggestionBar(entry, applyEffect, Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate ?: RestoreDateRange.todayMillis(),
            yearRange = RestoreDateRange.years(),
            selectableDates = RestoreDateRange
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text(tr("OK"), color = MoneroOrange)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedDate = null
                        showDatePicker = false
                    }
                ) {
                    Text(tr("Clear"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    selectedDate?.takeIf { confirmRecentDate }?.let { date ->
        val shownDate = remember(date) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(date))
        }
        AlertDialog(
            onDismissRequest = { confirmRecentDate = false },
            title = { Text(tr("Restore from %s?", shownDate)) },
            text = { Text(tr("Only finds transactions after this date.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRecentDate = false
                    namingStep = true
                }) { Text(tr("Continue")) }
            },
            dismissButton = {
                DismissTextButton(onClick = {
                    confirmRecentDate = false
                    showDatePicker = true
                }) { Text(tr("Choose another date")) }
            }
        )
    }
}

/** How recent a birthday has to be for the confirmation (iOS: 30 days). */
private const val RecentDateWindowMillis = 30 * 86_400_000L

private fun dateToRestoreHeight(dateMillis: Long): Long {
    return RestoreHeight.getInstance().getHeight(Date(dateMillis))
}
