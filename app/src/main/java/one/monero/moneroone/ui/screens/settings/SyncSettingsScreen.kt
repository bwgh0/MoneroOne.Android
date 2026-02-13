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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import one.monero.moneroone.core.service.WalletSyncService
import one.monero.moneroone.core.util.rememberNotificationPermission
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.SuccessGreen
import one.monero.moneroone.ui.theme.ErrorRed
import io.horizontalsystems.monerokit.util.RestoreHeight
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

    var backgroundSyncEnabled by remember {
        mutableStateOf(prefs.getBoolean("background_sync_enabled", false))
    }
    val (hasNotificationPermission, requestNotificationPermission) = rememberNotificationPermission()

    var showDatePicker by remember { mutableStateOf(false) }
    var restoreHeight by remember {
        val longVal = prefs.getLong("restore_height", 0L)
        val strVal = prefs.getString("restore_height_str", "0")?.toLongOrNull() ?: 0L
        mutableStateOf(if (longVal > 0L) longVal else strVal)
    }
    var restoreDateMillis by remember {
        mutableStateOf(prefs.getLong("restore_date_millis", 0L))
    }

    val dateFormatter = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
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
                    val progressPct = (progress * 100).toInt()
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MoneroOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$progressPct% complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Background Sync Section
        SectionLabel("BACKGROUND SYNC")

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MoneroOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync in Background",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Keep wallet synced when app is backgrounded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = backgroundSyncEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasNotificationPermission) {
                            requestNotificationPermission()
                        }
                        backgroundSyncEnabled = enabled
                        prefs.edit().putBoolean("background_sync_enabled", enabled).apply()
                        if (enabled) {
                            WalletSyncService.start(context)
                        } else {
                            WalletSyncService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MoneroOrange,
                        checkedTrackColor = MoneroOrange.copy(alpha = 0.3f)
                    )
                )
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
                    val displayText = if (restoreDateMillis > 0L) {
                        "${dateFormatter.format(Date(restoreDateMillis))} (Block $restoreHeight)"
                    } else if (restoreHeight > 0) {
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
            initialSelectedDateMillis = if (restoreDateMillis > 0L) {
                restoreDateMillis
            } else if (restoreHeight > 0) {
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
                            restoreDateMillis = dateMillis
                            prefs.edit()
                                .putLong("restore_height", newHeight)
                                .putLong("restore_date_millis", dateMillis)
                                .apply()
                            walletViewModel.setRestoreHeight(newHeight)
                            walletViewModel.resetSync()
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
                        restoreDateMillis = 0L
                        prefs.edit()
                            .putLong("restore_height", 0L)
                            .putLong("restore_date_millis", 0L)
                            .apply()
                        walletViewModel.setRestoreHeight(0L)
                        walletViewModel.resetSync()
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
        is SyncState.Syncing -> "Syncing ${((syncState.progress ?: 0.0) * 100).toInt()}%"
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

private fun dateToRestoreHeight(dateMillis: Long): Long {
    return RestoreHeight.getInstance().getHeight(Date(dateMillis))
}

/**
 * Height-to-date lookup table from RestoreHeight.java.
 * Used for reverse interpolation: given a height, find the approximate date.
 */
private val heightToDateTable: List<Pair<Long, Long>> by lazy {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    listOf(
        0L to sdf.parse("2014-04-18")!!.time,
        18844L to sdf.parse("2014-05-01")!!.time,
        65406L to sdf.parse("2014-06-01")!!.time,
        108882L to sdf.parse("2014-07-01")!!.time,
        153594L to sdf.parse("2014-08-01")!!.time,
        198072L to sdf.parse("2014-09-01")!!.time,
        241088L to sdf.parse("2014-10-01")!!.time,
        285305L to sdf.parse("2014-11-01")!!.time,
        328069L to sdf.parse("2014-12-01")!!.time,
        372369L to sdf.parse("2015-01-01")!!.time,
        416505L to sdf.parse("2015-02-01")!!.time,
        456631L to sdf.parse("2015-03-01")!!.time,
        501084L to sdf.parse("2015-04-01")!!.time,
        543973L to sdf.parse("2015-05-01")!!.time,
        588326L to sdf.parse("2015-06-01")!!.time,
        631187L to sdf.parse("2015-07-01")!!.time,
        675484L to sdf.parse("2015-08-01")!!.time,
        719725L to sdf.parse("2015-09-01")!!.time,
        762463L to sdf.parse("2015-10-01")!!.time,
        806528L to sdf.parse("2015-11-01")!!.time,
        849041L to sdf.parse("2015-12-01")!!.time,
        892866L to sdf.parse("2016-01-01")!!.time,
        936736L to sdf.parse("2016-02-01")!!.time,
        977691L to sdf.parse("2016-03-01")!!.time,
        1015848L to sdf.parse("2016-04-01")!!.time,
        1037417L to sdf.parse("2016-05-01")!!.time,
        1059651L to sdf.parse("2016-06-01")!!.time,
        1081269L to sdf.parse("2016-07-01")!!.time,
        1103630L to sdf.parse("2016-08-01")!!.time,
        1125983L to sdf.parse("2016-09-01")!!.time,
        1147617L to sdf.parse("2016-10-01")!!.time,
        1169779L to sdf.parse("2016-11-01")!!.time,
        1191402L to sdf.parse("2016-12-01")!!.time,
        1213861L to sdf.parse("2017-01-01")!!.time,
        1236197L to sdf.parse("2017-02-01")!!.time,
        1256358L to sdf.parse("2017-03-01")!!.time,
        1278622L to sdf.parse("2017-04-01")!!.time,
        1300239L to sdf.parse("2017-05-01")!!.time,
        1322564L to sdf.parse("2017-06-01")!!.time,
        1344225L to sdf.parse("2017-07-01")!!.time,
        1366664L to sdf.parse("2017-08-01")!!.time,
        1389113L to sdf.parse("2017-09-01")!!.time,
        1410738L to sdf.parse("2017-10-01")!!.time,
        1433039L to sdf.parse("2017-11-01")!!.time,
        1454639L to sdf.parse("2017-12-01")!!.time,
        1477201L to sdf.parse("2018-01-01")!!.time,
        1499599L to sdf.parse("2018-02-01")!!.time,
        1519796L to sdf.parse("2018-03-01")!!.time,
        1542067L to sdf.parse("2018-04-01")!!.time,
        1562861L to sdf.parse("2018-05-01")!!.time,
        1585135L to sdf.parse("2018-06-01")!!.time,
        1606715L to sdf.parse("2018-07-01")!!.time,
        1629017L to sdf.parse("2018-08-01")!!.time,
        1651347L to sdf.parse("2018-09-01")!!.time,
        1673031L to sdf.parse("2018-10-01")!!.time,
        1695128L to sdf.parse("2018-11-01")!!.time,
        1716687L to sdf.parse("2018-12-01")!!.time,
        1738923L to sdf.parse("2019-01-01")!!.time,
        1761435L to sdf.parse("2019-02-01")!!.time,
        1781681L to sdf.parse("2019-03-01")!!.time,
        1803081L to sdf.parse("2019-04-01")!!.time,
        1824671L to sdf.parse("2019-05-01")!!.time,
        1847005L to sdf.parse("2019-06-01")!!.time,
        1868590L to sdf.parse("2019-07-01")!!.time,
        1890878L to sdf.parse("2019-08-01")!!.time,
        1913201L to sdf.parse("2019-09-01")!!.time,
        1934732L to sdf.parse("2019-10-01")!!.time,
        1957051L to sdf.parse("2019-11-01")!!.time,
        1978433L to sdf.parse("2019-12-01")!!.time,
        2001315L to sdf.parse("2020-01-01")!!.time,
        2023656L to sdf.parse("2020-02-01")!!.time,
        2044552L to sdf.parse("2020-03-01")!!.time,
        2066806L to sdf.parse("2020-04-01")!!.time,
        2088411L to sdf.parse("2020-05-01")!!.time,
        2110702L to sdf.parse("2020-06-01")!!.time,
        2132318L to sdf.parse("2020-07-01")!!.time,
        2154590L to sdf.parse("2020-08-01")!!.time,
        2176790L to sdf.parse("2020-09-01")!!.time,
        2198370L to sdf.parse("2020-10-01")!!.time,
        2220670L to sdf.parse("2020-11-01")!!.time,
        2242241L to sdf.parse("2020-12-01")!!.time,
        2264584L to sdf.parse("2021-01-01")!!.time,
        2286892L to sdf.parse("2021-02-01")!!.time,
        2307079L to sdf.parse("2021-03-01")!!.time,
        2329385L to sdf.parse("2021-04-01")!!.time,
        2351004L to sdf.parse("2021-05-01")!!.time,
        2373306L to sdf.parse("2021-06-01")!!.time,
        2394882L to sdf.parse("2021-07-01")!!.time,
        2417162L to sdf.parse("2021-08-01")!!.time,
        2439490L to sdf.parse("2021-09-01")!!.time,
        2461020L to sdf.parse("2021-10-01")!!.time,
        2483377L to sdf.parse("2021-11-01")!!.time,
        2504932L to sdf.parse("2021-12-01")!!.time,
        2527316L to sdf.parse("2022-01-01")!!.time,
        2549605L to sdf.parse("2022-02-01")!!.time,
        2569711L to sdf.parse("2022-03-01")!!.time,
        2591995L to sdf.parse("2022-04-01")!!.time,
        2613603L to sdf.parse("2022-05-01")!!.time,
        2635840L to sdf.parse("2022-06-01")!!.time,
        2657395L to sdf.parse("2022-07-01")!!.time,
        2679705L to sdf.parse("2022-08-01")!!.time,
        2701991L to sdf.parse("2022-09-01")!!.time,
        2723607L to sdf.parse("2022-10-01")!!.time,
        2745899L to sdf.parse("2022-11-01")!!.time,
        2767427L to sdf.parse("2022-12-01")!!.time,
        2789763L to sdf.parse("2023-01-01")!!.time,
        2811996L to sdf.parse("2023-02-01")!!.time,
        2832118L to sdf.parse("2023-03-01")!!.time,
        2854365L to sdf.parse("2023-04-01")!!.time,
        2875972L to sdf.parse("2023-05-01")!!.time,
        2898270L to sdf.parse("2023-06-01")!!.time,
        2919840L to sdf.parse("2023-07-01")!!.time,
        2942140L to sdf.parse("2023-08-01")!!.time,
        2964430L to sdf.parse("2023-09-01")!!.time,
        2986010L to sdf.parse("2023-10-01")!!.time,
        3008300L to sdf.parse("2023-11-01")!!.time,
        3029880L to sdf.parse("2023-12-01")!!.time,
        3052170L to sdf.parse("2024-01-01")!!.time,
        3074470L to sdf.parse("2024-02-01")!!.time,
        3095330L to sdf.parse("2024-03-01")!!.time,
        3117620L to sdf.parse("2024-04-01")!!.time,
        3139200L to sdf.parse("2024-05-01")!!.time,
        3161490L to sdf.parse("2024-06-01")!!.time,
        3183070L to sdf.parse("2024-07-01")!!.time,
        3205360L to sdf.parse("2024-08-01")!!.time,
        3227660L to sdf.parse("2024-09-01")!!.time,
        3249240L to sdf.parse("2024-10-01")!!.time,
        3271530L to sdf.parse("2024-11-01")!!.time,
        3293110L to sdf.parse("2024-12-01")!!.time,
        3315400L to sdf.parse("2025-01-01")!!.time,
        3337700L to sdf.parse("2025-02-01")!!.time,
        3357830L to sdf.parse("2025-03-01")!!.time,
        3380130L to sdf.parse("2025-04-01")!!.time,
        3401700L to sdf.parse("2025-05-01")!!.time,
        3424000L to sdf.parse("2025-06-01")!!.time,
        3445580L to sdf.parse("2025-07-01")!!.time,
        3467870L to sdf.parse("2025-08-01")!!.time,
        3490175L to sdf.parse("2025-09-01")!!.time,
        3575753L to sdf.parse("2025-12-29")!!.time,
    )
}

/**
 * Converts a block height back to an estimated date using reverse lookup table interpolation.
 */
private fun restoreHeightToDate(height: Long): Date {
    if (height <= 0L) return Date(heightToDateTable.first().second)

    val table = heightToDateTable
    // If beyond the last known height, extrapolate at 120s/block from last entry
    if (height >= table.last().first) {
        val lastEntry = table.last()
        val extraMillis = (height - lastEntry.first) * 120L * 1000L
        return Date(lastEntry.second + extraMillis)
    }

    // Find bracketing entries and interpolate
    for (i in 0 until table.size - 1) {
        val (h0, t0) = table[i]
        val (h1, t1) = table[i + 1]
        if (height in h0..h1) {
            val fraction = (height - h0).toDouble() / (h1 - h0).toDouble()
            val interpolatedTime = t0 + (fraction * (t1 - t0)).toLong()
            return Date(interpolatedTime)
        }
    }

    return Date(table.last().second)
}
