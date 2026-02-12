package one.monero.moneroone.ui.screens.settings

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.horizontalsystems.monerokit.SyncState
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.ErrorRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onNodeSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("monero_wallet", Context.MODE_PRIVATE) }
    val walletState by walletViewModel.walletState.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var restoreHeight by remember {
        mutableStateOf(prefs.getLong("restore_height", 0L))
    }

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

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

        // Restore Height Section
        SectionLabel("WALLET BIRTHDAY")

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDatePicker = true }
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
                        text = "Restore Date",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val displayText = if (restoreHeight > 0) {
                        val estimatedDate = restoreHeightToDate(restoreHeight)
                        "${dateFormatter.format(estimatedDate)} (Block $restoreHeight)"
                    } else {
                        "From beginning (full scan)"
                    }
                    Text(
                        text = displayText,
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

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (restoreHeight > 0) {
                restoreHeightToDate(restoreHeight).time
            } else {
                System.currentTimeMillis()
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            val newHeight = dateToRestoreHeight(dateMillis)
                            restoreHeight = newHeight
                            prefs.edit().putLong("restore_height", newHeight).apply()
                            walletViewModel.setRestoreHeight(newHeight)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = MoneroOrange)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Clear / scan from beginning
                        restoreHeight = 0L
                        prefs.edit().putLong("restore_height", 0L).apply()
                        walletViewModel.setRestoreHeight(0L)
                        showDatePicker = false
                    }
                ) {
                    Text("Scan All")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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

/**
 * Converts a date (in milliseconds) to an estimated Monero block height.
 * Monero mainnet started on April 18, 2014 (block 0).
 * Average block time is ~2 minutes (120 seconds).
 */
private fun dateToRestoreHeight(dateMillis: Long): Long {
    // Monero mainnet genesis: April 18, 2014 00:00:00 UTC
    val genesisTimestamp = 1397782800000L

    if (dateMillis <= genesisTimestamp) {
        return 0L
    }

    val secondsSinceGenesis = (dateMillis - genesisTimestamp) / 1000
    val estimatedHeight = secondsSinceGenesis / 120

    // Subtract some blocks as safety margin (1 day = ~720 blocks)
    return maxOf(0L, estimatedHeight - 720)
}

/**
 * Converts a block height back to an estimated date.
 */
private fun restoreHeightToDate(height: Long): Date {
    val genesisTimestamp = 1397782800000L
    val estimatedMillis = genesisTimestamp + (height * 120 * 1000)
    return Date(estimatedMillis)
}
