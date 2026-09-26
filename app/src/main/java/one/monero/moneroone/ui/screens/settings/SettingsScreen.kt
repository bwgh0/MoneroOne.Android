package one.monero.moneroone.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.components.MoneroSwitch
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.widget.WalletWidget
import one.monero.moneroone.widget.WidgetDataStore
import androidx.compose.material.icons.filled.Widgets
import one.monero.moneroone.ui.theme.SettingsBlue
import one.monero.moneroone.ui.theme.SettingsGray
import one.monero.moneroone.ui.theme.SettingsGreen
import one.monero.moneroone.ui.theme.SettingsPink
import one.monero.moneroone.ui.theme.SettingsIndigo

@Composable
fun SettingsScreen(
    walletViewModel: WalletViewModel,
    onBackupClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onThemeClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onPriceAlertsClick: () -> Unit = {},
    onSyncSettingsClick: () -> Unit,
    onResetSyncClick: () -> Unit,
    onRemoveWalletClick: () -> Unit,
    onDonateClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", android.content.Context.MODE_PRIVATE) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetSyncDialog by remember { mutableStateOf(false) }

    val selectedCurrency by walletViewModel.selectedCurrency.collectAsState()

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
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Wallet Section
        SettingsSection(title = "Wallet") {
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Backup Seed Phrase",
                subtitle = "View your recovery phrase",
                onClick = onBackupClick,
                iconColor = MoneroOrange,
                showDivider = false
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Security",
                subtitle = "PIN and authentication settings",
                onClick = onSecurityClick,
                iconColor = SettingsBlue
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display Section
        SettingsSection(title = "Display") {
            SettingsItem(
                icon = Icons.Default.Brush,
                title = "Appearance",
                subtitle = "System default",
                onClick = onThemeClick,
                iconColor = SettingsIndigo,
                showDivider = false
            )

            SettingsItem(
                icon = Icons.Default.CurrencyExchange,
                title = "Currency",
                subtitle = selectedCurrency.code.uppercase(),
                onClick = onCurrencyClick,
                iconColor = SettingsGreen
            )

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Price Alerts",
                subtitle = "Get notified on price changes",
                onClick = onPriceAlertsClick,
                iconColor = SettingsPink
            )

            var walletWidgetEnabled by remember {
                mutableStateOf(WidgetDataStore.isWalletWidgetEnabled(context))
            }

            SettingsToggleItem(
                icon = Icons.Default.Widgets,
                title = "Balance & Transactions",
                subtitle = "Show wallet data on home screen",
                checked = walletWidgetEnabled,
                onCheckedChange = { enabled ->
                    walletWidgetEnabled = enabled
                    WidgetDataStore.setWalletWidgetEnabled(context, enabled)
                    WalletWidget.updateAll(context)
                },
                iconColor = SettingsBlue
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sync Section
        SettingsSection(title = "Sync") {
            SettingsItem(
                icon = Icons.Default.Sync,
                title = "Sync Settings",
                subtitle = "Configure blockchain sync",
                onClick = onSyncSettingsClick,
                iconColor = MoneroOrange,
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // About Section
        SettingsSection(title = "About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Build",
                subtitle = BuildConfig.VERSION_CODE.toString(),
                onClick = { },
                iconColor = SettingsGray,
                showDivider = false
            )

            SettingsItem(
                icon = Icons.Default.Language,
                title = "Website",
                subtitle = "Visit monero.one",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://monero.one"))
                    context.startActivity(intent)
                },
                iconColor = MoneroOrange
            )

        }

        Spacer(modifier = Modifier.height(20.dp))

        // Help & Feedback
        SettingsSection(title = "Help & Feedback") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Contact Support",
                subtitle = "android_support@monero.one",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:android_support@monero.one")
                        putExtra(Intent.EXTRA_SUBJECT, "MoneroOne Android - Feedback")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                },
                iconColor = SettingsBlue,
                showDivider = false
            )

        }

        Spacer(modifier = Modifier.height(20.dp))

        // Support Section
        SettingsSection(title = "Support the Developer") {
            SettingsItem(
                icon = Icons.Default.Favorite,
                title = "Donate XMR",
                subtitle = "Support development",
                onClick = onDonateClick,
                iconColor = SettingsPink,
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Danger Zone: destructive rows keep their tile colors (Reset Sync is
        // brand, Remove is red) and show red titles, as on iOS.
        SettingsSection(title = "Danger Zone") {
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = "Reset Sync Data",
                subtitle = "Resync wallet from scratch",
                onClick = { showResetSyncDialog = true },
                iconColor = MoneroOrange,
                isDestructive = true,
                showDivider = false
            )

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "Remove Wallet from Device",
                subtitle = "Permanently delete wallet from device",
                onClick = { showDeleteDialog = true },
                iconColor = ErrorRed,
                isDestructive = true
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Remove Wallet from Device?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This removes wallet data from this device only. " +
                        "Your wallet still exists on the blockchain and can be recovered with your seed phrase.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveWalletClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Remove", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset sync confirmation dialog
    if (showResetSyncDialog) {
        AlertDialog(
            onDismissRequest = { showResetSyncDialog = false },
            title = {
                Text(
                    text = "Reset Sync Data?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This will clear all sync progress and re-sync from the beginning. " +
                        "Your wallet and keys are not affected.",
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
                    Text("Reset", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSyncDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

/**
 * A settings group as on iOS: a title-case section header in the secondary
 * color, then one radius-16 card holding the rows, separated by inset hairlines.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        shadow = false
    ) {
        Column(content = content)
    }
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

/** Hairline between rows, inset to the title (16 + 28 tile + 12). */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MoneroOrange,
    isDestructive: Boolean = false,
    showDivider: Boolean = true
) {
    val titleColor = if (isDestructive) ErrorRed else MaterialTheme.colorScheme.onSurface

    if (showDivider) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, color = iconColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MoneroTheme.colors.labelTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconColor: Color = MoneroOrange,
    showDivider: Boolean = true
) {
    if (showDivider) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, color = iconColor)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MoneroSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
