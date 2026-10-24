package one.monero.moneroone.ui.screens.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.SeedValidation
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.core.wallet.WalletViewModel
import one.monero.moneroone.ui.screens.wallet.WalletRowCard
import one.monero.moneroone.ui.screens.wallet.WalletRowCheck

/**
 * Transfer (iOS Transfer sheet; a screen on Android, as Send is): From is the open wallet, with
 * the switcher's check and ring. To lists every other wallet in switcher order, as switcher rows
 * without the trailing slot. A tap on a To row opens Send's amount step for that wallet's saved
 * primary address. A wallet with no saved address is faded and says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
    onPick: (WalletInfo) -> Unit
) {
    val wallets by walletViewModel.wallets.collectAsState()
    val from by walletViewModel.activeWallet.collectAsState()
    val walletState by walletViewModel.walletState.collectAsState()
    val current = from
    val targets = remember(wallets, current?.id) { SendNames.transferTargets(wallets, current?.id) }

    // A wallet removed meanwhile can leave nothing to transfer to.
    LaunchedEffect(current == null || targets.isEmpty()) {
        if (current == null || targets.isEmpty()) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(tr("Transfer"), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        }
    ) { padding ->
        if (current == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel(tr("From"))
            val fromBalance = "${walletViewModel.formatXmr(walletState.balance.all)} XMR"
            WalletRowCard(
                emoji = current.emoji,
                name = current.name,
                address = current.cachedPrimaryAddress?.takeIf { SeedValidation.isPlausiblePrimaryAddress(it) },
                balanceText = fromBalance,
                isActive = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "${current.name}, $fromBalance"
                        selected = true
                    }
            ) {
                WalletRowCheck(isActive = true)
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel(tr("To"))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                targets.forEach { wallet ->
                    key(wallet.id) {
                        ToWalletRow(
                            wallet = wallet,
                            balanceText = "${walletViewModel.formatXmr(wallet.cachedBalance ?: 0L)} XMR",
                            onPick = { onPick(wallet) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** "From" and "To": a field label (subheadline, secondary), 8 above its rows; a heading for TalkBack. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/**
 * A To row: the switcher row with no trailing slot, since a tap goes on at once. With no saved
 * primary address (the wallet was not opened since the update) it fades, says so, and does nothing.
 */
@Composable
private fun ToWalletRow(wallet: WalletInfo, balanceText: String, onPick: () -> Unit) {
    val address = remember(wallet.cachedPrimaryAddress) {
        SendNames.transferAddress(wallet) { isValidMoneroAddress(it) }
    }
    val note = tr("Open this wallet once to send to it")
    WalletRowCard(
        emoji = wallet.emoji,
        name = wallet.name,
        address = address,
        balanceText = balanceText,
        onClick = if (address != null) onPick else null,
        contentAlpha = if (address != null) 1f else DimmedAlpha,
        note = if (address != null) null else note,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${wallet.name}, $balanceText"
                if (address != null) {
                    onClick { onPick(); true }
                } else {
                    stateDescription = note
                    disabled()
                }
            }
    )
}

/** A To row that cannot be picked, as the iOS disabled rows. */
private const val DimmedAlpha = 0.4f
