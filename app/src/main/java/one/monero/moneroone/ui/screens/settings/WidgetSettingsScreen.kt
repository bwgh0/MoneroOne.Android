package one.monero.moneroone.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.ui.components.GlassCard
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.widget.WalletWidget
import one.monero.moneroone.widget.WidgetDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings > Home Screen Widget, as iOS WidgetSettingsView: one switch that
 * lets the wallet widget show the balance and transactions. The Price widget
 * needs no wallet data, so it works either way.
 */
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var walletWidgetEnabled by remember { mutableStateOf(WidgetDataStore.isWalletWidgetEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoneroTheme.colors.bgGrouped)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr("Back")
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tr("Widget"),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, shadow = false) {
            Column {
                SettingsToggleItem(
                    title = tr("Show Balance & Transactions"),
                    checked = walletWidgetEnabled,
                    onCheckedChange = { enabled ->
                        walletWidgetEnabled = enabled
                        WidgetDataStore.setWalletWidgetEnabled(context, enabled)
                        // Off Main: the redraw makes binder calls per widget.
                        val appContext = context.applicationContext
                        scope.launch(Dispatchers.IO) { WalletWidget.updateAll(appContext) }
                    },
                    showDivider = false
                )
            }
        }
        SettingsSectionFooter(tr("The Price widget works without this."))

        Spacer(modifier = Modifier.height(32.dp))
    }
}
