package one.monero.moneroone.ui.screens.settings

import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.ErrorRed
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen

enum class SyncMode(val title: String, val subtitle: String, val enabled: Boolean) {
    PRIVACY("Privacy Mode", "Full sync with remote node", true),
    LITE("Lite Mode", "Coming Soon", false)
}

@Composable
fun SyncSettingsScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onNodeSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    val walletState by walletViewModel.walletState.collectAsState()

    var selectedSyncMode by remember {
        val savedMode = prefs.getString("sync_mode", SyncMode.PRIVACY.name)
        mutableStateOf(SyncMode.entries.find { it.name == savedMode } ?: SyncMode.PRIVACY)
    }

    var showRestoreHeightDialog by remember { mutableStateOf(false) }
    var restoreHeight by remember {
        mutableStateOf(prefs.getString("restore_height", "0") ?: "0")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sync Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sync Status Section
        SectionLabel("STATUS")

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = getSyncStatusColor(walletState.syncState),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getSyncStatusText(walletState.syncState),
                            style = MaterialTheme.typography.bodySmall,
                            color = getSyncStatusColor(walletState.syncState)
                        )
                    }
                }

                // Progress bar for syncing
                val syncState = walletState.syncState
                if (syncState is SyncState.Syncing) {
                    val progress = syncState.progress ?: 0.0
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (progress.toFloat() / 100f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MoneroOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${progress.toInt()}% complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sync Mode Section
        SectionLabel("SYNC MODE")

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SyncMode.entries.forEach { mode ->
                SyncModeItem(
                    mode = mode,
                    isSelected = mode == selectedSyncMode,
                    onClick = {
                        if (mode.enabled) {
                            selectedSyncMode = mode
                            prefs.edit().putString("sync_mode", mode.name).apply()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Restore Height Section
        SectionLabel("RESTORE HEIGHT")

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showRestoreHeightDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MoneroOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restore Height",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Block $restoreHeight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoneroOrange
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

        Spacer(modifier = Modifier.height(20.dp))

        // Node Settings Section
        SectionLabel("NODE")

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNodeSettingsClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MoneroOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Node Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Manage remote nodes",
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

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Restore Height Dialog
    if (showRestoreHeightDialog) {
        RestoreHeightDialog(
            currentHeight = restoreHeight,
            onConfirm = { newHeight ->
                restoreHeight = newHeight
                prefs.edit().putString("restore_height", newHeight).apply()
                walletViewModel.setRestoreHeight(newHeight.toLongOrNull() ?: 0)
                showRestoreHeightDialog = false
            },
            onDismiss = { showRestoreHeightDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SyncModeItem(
    mode: SyncMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
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
                imageVector = if (mode == SyncMode.PRIVACY) Icons.Default.Lock else Icons.Default.Storage,
                contentDescription = null,
                tint = if (mode.enabled) {
                    if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (mode.enabled) {
                        if (isSelected) MoneroOrange else MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isSelected && mode.enabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MoneroOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RestoreHeightDialog(
    currentHeight: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var height by remember { mutableStateOf(currentHeight) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Height") },
        text = {
            Column {
                Text(
                    text = "Enter the block height from which to restore your wallet. A lower height means scanning more blocks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = {
                        if (it.isEmpty() || it.all { c -> c.isDigit() }) {
                            height = it
                            error = null
                        }
                    },
                    label = { Text("Block Height") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = ErrorRed) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MoneroOrange,
                        cursorColor = MoneroOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = height.toLongOrNull() ?: 0
                    if (h < 0) {
                        error = "Invalid height"
                    } else {
                        onConfirm(height.ifEmpty { "0" })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getSyncStatusText(syncState: SyncState): String {
    return when (syncState) {
        is SyncState.Synced -> "Synced"
        is SyncState.Syncing -> "Syncing ${(syncState.progress ?: 0.0).toInt()}%"
        is SyncState.NotSynced -> "Not synced"
        else -> "Connecting..."
    }
}

private fun getSyncStatusColor(syncState: SyncState): androidx.compose.ui.graphics.Color {
    return when (syncState) {
        is SyncState.Synced -> SuccessGreen
        is SyncState.Syncing -> MoneroOrange
        is SyncState.NotSynced -> ErrorRed
        else -> MoneroOrange
    }
}
