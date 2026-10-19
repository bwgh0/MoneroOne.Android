package one.monero.moneroone.ui.screens.settings

import one.monero.moneroone.core.locale.tr
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.MoneroKit
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.alert.PriceAlertManager
import one.monero.moneroone.core.review.ReviewPromptGate
import one.monero.moneroone.core.review.openPlayListing
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.data.model.PriceAlert
import one.monero.moneroone.paymentlinks.PaymentLinkStore
import one.monero.moneroone.ui.components.DismissTextButton
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroSwitch
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SettingsBlue
import one.monero.moneroone.ui.theme.SettingsGray
import one.monero.moneroone.ui.theme.SettingsGreen
import one.monero.moneroone.ui.theme.SettingsIndigo
import one.monero.moneroone.ui.theme.SettingsPink

@Composable
fun SettingsScreen(
    walletViewModel: WalletViewModel,
    onBackupClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onThemeClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onLanguageClick: () -> Unit = {},
    onWidgetClick: () -> Unit = {},
    onPriceAlertsClick: () -> Unit = {},
    onSyncSettingsClick: () -> Unit,
    onResetSyncClick: () -> Unit,
    onRemoveAllWalletsClick: () -> Unit,
    onDonateClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE) }

    var showRemoveAllDialog by remember { mutableStateOf(false) }
    var showResetSyncDialog by remember { mutableStateOf(false) }

    var freshAddress by remember { mutableStateOf(walletViewModel.freshReceiveAddress) }
    val fiatMode by walletViewModel.fiatMode.collectAsState()
    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()
    val activeWallet by walletViewModel.activeWallet.collectAsState()
    val walletState by walletViewModel.walletState.collectAsState()
    val currencyCode = selectedCurrency.code.uppercase()
    val syncStatus = syncStatusText(walletState.syncState)

    // Settings leaves composition while a settings page shows, so these
    // reads are fresh when the rows show again.
    val appearance = ThemeOption.fromNightMode(
        prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    )
    val alerts = remember { PriceAlertManager(context).getAlerts() }

    fun openLink(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoneroTheme.colors.bgGrouped)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = tr("Settings"),
            style = MaterialTheme.typography.headlineLarge
        )

        // The header names the wallet these rows act on, as on iOS.
        val wallet = activeWallet
        SettingsSection(
            title = wallet?.let { "${it.emoji} ${it.name}" } ?: tr("Wallet"),
            titleDescription = wallet?.let { tr("Wallet: %s", it.name) }
        ) {
            SettingsItem(
                icon = Icons.Default.Key,
                title = tr("Backup Seed Phrase"),
                onClick = onBackupClick,
                iconColor = MoneroOrange,
                showDivider = false
            )
        }

        SettingsSection(
            title = tr("Privacy & Security"),
            footer = tr("After a payment arrives, Receive shows a new address. Old ones keep working.")
        ) {
            SettingsItem(
                icon = Icons.Default.Lock,
                title = tr("Security"),
                onClick = onSecurityClick,
                iconColor = SettingsBlue,
                showDivider = false
            )
            SettingsToggleItem(
                icon = Icons.Default.QrCode,
                title = tr("Fresh Receive Address"),
                checked = freshAddress,
                onCheckedChange = { freshAddress = it; walletViewModel.setFreshReceiveAddress(it) },
                iconColor = SettingsBlue
            )
        }

        SettingsSection(title = tr("Display")) {
            SettingsItem(
                icon = Icons.Default.Brush,
                title = tr("Appearance"),
                value = tr(appearance.rowValue),
                onClick = onThemeClick,
                iconColor = SettingsIndigo,
                showDivider = false
            )
            SettingsItem(
                icon = Icons.Default.Language,
                title = tr("Language"),
                value = selectedLanguageName(),
                onClick = onLanguageClick,
                iconColor = SettingsBlue
            )
            SettingsItem(
                icon = Icons.Default.Widgets,
                title = tr("Home Screen Widget"),
                onClick = onWidgetClick,
                iconColor = SettingsBlue
            )
        }

        SettingsSection(title = tr("Currency")) {
            SettingsItem(
                icon = Icons.Default.CurrencyExchange,
                title = tr("Currency"),
                value = currencyCode,
                onClick = onCurrencyClick,
                iconColor = SettingsGreen,
                showDivider = false,
                contentDescription = tr("Currency, %s", currencyCode)
            )
            SettingsToggleItem(
                icon = Icons.Default.Payments,
                title = tr("Show %s First", currencyCode),
                checked = fiatMode,
                onCheckedChange = walletViewModel::setFiatMode,
                iconColor = SettingsGreen
            )
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = tr("Price Alerts"),
                value = priceAlertsValue(alerts),
                onClick = onPriceAlertsClick,
                iconColor = SettingsPink,
                contentDescription = tr("Price Alerts, %s active", alerts.count { it.isEnabled })
            )
        }

        SettingsSection(title = tr("Sync")) {
            SettingsItem(
                icon = Icons.Default.Sync,
                title = tr("Sync Settings"),
                value = syncStatus,
                onClick = onSyncSettingsClick,
                iconColor = MoneroOrange,
                showDivider = false,
                contentDescription = tr("Sync Settings, status %s", syncStatus)
            )
        }

        SettingsSection(title = tr("About")) {
            // "1.0.9 (12)": versionName (versionCode), as iOS shows
            // CFBundleShortVersionString (CFBundleVersion).
            SettingsItem(
                icon = Icons.Default.Info,
                title = tr("Version"),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = null,
                iconColor = SettingsGray,
                showDivider = false,
                contentDescription = tr("Version %s, build %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            )
            // Links open the browser and show no chevron, as on iOS.
            SettingsItem(
                icon = Icons.Default.Language,
                title = tr("Website"),
                onClick = { openLink("https://monero.one") },
                iconColor = MoneroOrange,
                chevron = false
            )
            SettingsItem(
                icon = Icons.Default.PanTool,
                title = tr("Privacy Policy"),
                onClick = { openLink("https://monero.one/privacy") },
                iconColor = SettingsBlue,
                chevron = false
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = tr("Terms of Service"),
                onClick = { openLink("https://monero.one/terms") },
                iconColor = SettingsGray,
                chevron = false
            )
        }

        SettingsSection(title = tr("Help & Feedback")) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = tr("Contact Support"),
                onClick = {
                    val subject = "Monero One Android - Feedback"
                    val body = supportMailBody(
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                        Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL
                    )
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        // Some mail apps read the mailto query, others the extras.
                        data = Uri.parse("mailto:android_support@monero.one?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                },
                iconColor = SettingsBlue,
                showDivider = false,
                chevron = false
            )
        }

        SettingsSection(title = tr("Support the Developer")) {
            SettingsItem(
                icon = Icons.Default.Favorite,
                title = tr("Donate XMR"),
                onClick = onDonateClick,
                iconColor = SettingsPink,
                showDivider = false
            )
            // Opens the Play listing. A person who rates here never gets the
            // rating ask after a send (ReviewPromptGate).
            SettingsItem(
                icon = Icons.Default.Star,
                title = tr("Rate Monero One"),
                onClick = {
                    ReviewPromptGate(prefs).ratedFromSettings()
                    openPlayListing(context)
                },
                iconColor = MoneroOrange,
                chevron = false
            )
        }

        // Danger Zone: destructive rows keep their tile colors (Reset Sync is
        // brand, Remove is red) and show red titles, as on iOS.
        SettingsSection(title = tr("Danger Zone")) {
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = tr("Reset Sync Data"),
                onClick = { showResetSyncDialog = true },
                iconColor = MoneroOrange,
                isDestructive = true,
                showDivider = false,
                chevron = false
            )

            // One wallet is removed from the wallet switcher; this row wipes
            // them all, as on iOS.
            SettingsItem(
                icon = Icons.Default.Delete,
                title = tr("Remove All Wallets from Device"),
                onClick = { showRemoveAllDialog = true },
                iconColor = ErrorRed,
                isDestructive = true,
                chevron = false
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Remove-all confirmation dialog (iOS copy). The body leaves out iOS's
    // "or keys": Android restores wallets from seed phrases only.
    if (showRemoveAllDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAllDialog = false },
            title = {
                Text(
                    text = tr("Remove All Wallets from Device?"),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                // Remove All also wipes payment links: a sent link that may still hold money is lost with it.
                val linksLost = remember { PaymentLinkStore.get(context).hasUnsettledSentLinks() }
                Text(
                    text = tr("You can restore them only with their seed phrases.") +
                        if (linksLost) "\n" + tr("Unclaimed payment links will be lost.") else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAllWalletsClick()
                        showRemoveAllDialog = false
                    }
                ) {
                    Text(tr("Remove All"), color = ErrorRed)
                }
            },
            dismissButton = {
                DismissTextButton(onClick = { showRemoveAllDialog = false }) {
                    Text(tr("Cancel"))
                }
            }
        )
    }

    // Reset sync confirmation dialog. It names the wallet: the reset clears
    // only the active one. Android keeps the old cache file but reads keys only
    // from the new one, so past transaction keys become unavailable (iOS
    // deletes them, and says so).
    if (showResetSyncDialog) {
        AlertDialog(
            onDismissRequest = { showResetSyncDialog = false },
            title = {
                Text(
                    text = tr("Reset Sync Data?"),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = resetSyncMessage(activeWallet?.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetSyncClick()
                        showResetSyncDialog = false
                    }
                ) {
                    Text(tr("Reset"), color = ErrorRed)
                }
            },
            dismissButton = {
                DismissTextButton(onClick = { showResetSyncDialog = false }) {
                    Text(tr("Cancel"))
                }
            }
        )
    }

}

/** The Sync Settings row value, worded as on iOS: a status, or the percent while it syncs. */
internal fun syncStatusText(state: SyncState): String = when (state) {
    is SyncState.Synced -> tr("Synced")
    is SyncState.Syncing -> "${((state.progress ?: 0.0) * 100).toInt()}%"
    is SyncState.Connecting -> tr("Connecting")
    is SyncState.NotSynced ->
        if (state.error is MoneroKit.SyncError.NotStarted) tr("Idle") else tr("Error")
}

/** The Price Alerts row value, as on iOS: the count of active alerts once any alert exists. */
internal fun priceAlertsValue(alerts: List<PriceAlert>): String? =
    if (alerts.isEmpty()) null else alerts.count { it.isEnabled }.toString()

/** The Reset Sync Data message, naming the wallet when there is one. */
internal fun resetSyncMessage(walletName: String?): String =
    if (walletName != null) {
        tr("“%s” scans again from its restore height. Transaction keys for its past sends are no longer available.", walletName)
    } else {
        tr("This wallet scans again from its restore height. Transaction keys for its past sends are no longer available.")
    }

/** Space above a section header (tokens.json space.named.sectionAbove). */
private val SectionAbove = 24.dp

/** Section header to its card (tokens.json space.named.sectionBelow). */
private val SectionBelow = 12.dp

/** A one-line row: the 28dp tile plus 12dp above and below, as the rows were. */
private val RowHeight = 52.dp

/**
 * A settings section header as on iOS: title case, the headline role in the
 * secondary label color, inset to the row text, 24 above and 12 to its card
 * (tokens.json space.named). Every settings page uses it, so the rhythm lives
 * here and the pages add no spacers of their own. [trailing] (an Add button)
 * is centered on the title line and adds no height, so the gaps hold.
 * TalkBack reads [contentDescription] in place of the title when set.
 */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SectionAbove, bottom = SectionBelow)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    }
                )
        )
        if (trailing != null) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.wrapContentHeight(unbounded = true)) { trailing() }
            }
        }
    }
}

/**
 * A section footer as on iOS: footnote text in the secondary color, inset to
 * the row text, under the card.
 */
@Composable
internal fun SettingsSectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

/**
 * A settings group as on iOS: a [SettingsSectionHeader], then one radius-16
 * card holding the rows, separated by inset hairlines, then an optional
 * [footer].
 */
@Composable
internal fun SettingsSection(
    title: String,
    titleDescription: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsSectionHeader(title, contentDescription = titleDescription)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        shadow = false
    ) {
        Column(content = content)
    }
    if (footer != null) SettingsSectionFooter(footer)
}

/** The 28dp settings tile: solid tile color, radius 6, white glyph (tokens.json settingsTile). */
@Composable
fun SettingsIcon(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Hairline between rows, inset to the title (16 + 28 tile + 12, or 16 with no tile). */
@Composable
private fun RowDivider(inset: Dp = 56.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * A one-line row as on iOS: tile, title, then the [value] in the secondary
 * color. The value keeps its width, so a long title wraps instead. A row that
 * opens a page shows a chevron; one that acts or opens a link does not. With
 * no [onClick] the row only shows its value.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    iconColor: Color = MoneroOrange,
    value: String? = null,
    isDestructive: Boolean = false,
    showDivider: Boolean = true,
    chevron: Boolean = onClick != null,
    // TalkBack reads this in place of the title and value when set.
    contentDescription: String? = null
) {
    val titleColor = if (isDestructive) ErrorRed else MaterialTheme.colorScheme.onSurface

    if (showDivider) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                when {
                    contentDescription == null -> Modifier
                    // A row that only shows a value is one TalkBack stop.
                    onClick == null -> Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                    else -> Modifier.semantics { this.contentDescription = contentDescription }
                }
            )
            .heightIn(min = RowHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, color = iconColor)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
        if (chevron) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MoneroTheme.colors.labelTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A one-line toggle row. The whole row is the switch, so TalkBack reads the
 * title with the state, as an iOS Toggle does. [icon] is optional: the Widget
 * page row has none, as on iOS.
 */
@Composable
internal fun SettingsToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = MoneroOrange,
    showDivider: Boolean = true
) {
    if (showDivider) RowDivider(inset = if (icon != null) 56.dp else 16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = RowHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            SettingsIcon(icon = icon, color = iconColor)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // The row handles the tap and speaks the state.
        MoneroSwitch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The Contact Support mail body: room to write, then the app and device facts
 * that support asks for. No wallet, node or address data.
 */
internal fun supportMailBody(
    versionName: String, versionCode: Int, androidRelease: String, apiLevel: Int,
    manufacturer: String, model: String
): String = listOf(
    "", "", "----------",
    "Monero One $versionName ($versionCode)",
    "Android $androidRelease (API $apiLevel)",
    "$manufacturer $model"
).joinToString("\n")
