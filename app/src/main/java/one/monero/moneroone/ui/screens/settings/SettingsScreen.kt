package one.monero.moneroone.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.monero.moneroone.BuildConfig
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.widget.WalletWidget
import one.monero.moneroone.widget.WidgetDataStore
import androidx.compose.material.icons.filled.Widgets
import one.monero.moneroone.ui.theme.SettingsBlue
import one.monero.moneroone.ui.theme.SettingsGray
import one.monero.moneroone.ui.theme.SettingsGreen
import one.monero.moneroone.ui.theme.SettingsPink
import one.monero.moneroone.ui.theme.SettingsPurple

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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Wallet Section
        SettingsSection(title = "Wallet") {
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Backup Seed Phrase",
                subtitle = "View your recovery phrase",
                onClick = onBackupClick,
                iconColor = MoneroOrange
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
                iconColor = SettingsPurple
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
                iconColor = MoneroOrange
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
                iconColor = MoneroOrange
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
                iconColor = SettingsGray
            )

            SettingsItem(
                icon = Icons.Default.Language,
                title = "Website",
                subtitle = "Visit monero.one",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://monero.one"))
                    context.startActivity(intent)
                },
                iconColor = SettingsGreen
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
                iconColor = SettingsPink
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Danger Zone
        SettingsSection(title = "Danger Zone") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showResetSyncDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset Sync Data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Resync wallet from scratch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDeleteDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remove Wallet from Device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Permanently delete wallet from device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
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
                Button(
                    onClick = {
                        onRemoveWalletClick()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed
                    )
                ) {
                    Text("Remove")
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
                Button(
                    onClick = {
                        onResetSyncClick()
                        showResetSyncDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoneroOrange
                    )
                ) {
                    Text("Reset")
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MoneroOrange,
    isDestructive: Boolean = false
) {
    val actualIconColor = if (isDestructive) ErrorRed else iconColor
    val titleColor = if (isDestructive) ErrorRed else MaterialTheme.colorScheme.onSurface

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = actualIconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconColor: Color = MoneroOrange
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MoneroOrange,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
