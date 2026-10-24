package one.monero.moneroone.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import one.monero.moneroone.core.wallet.SendNames
import one.monero.moneroone.core.wallet.TransferDestination
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.monero.moneroone.core.locale.tr
import one.monero.moneroone.core.wallet.WalletInfo
import one.monero.moneroone.ui.screens.wallet.EmojiCircle
import one.monero.moneroone.ui.theme.MonoCaption
import one.monero.moneroone.ui.theme.SystemFill
import one.monero.moneroone.ui.theme.truncateMiddle

/**
 * The address field after a typed, pasted or scanned address turned out to be the saved primary
 * address of one of your wallets: that wallet's emoji, name and address 8…8, with the clear
 * button, as [OpenAliasRecipientCard] shows a name. The "Valid address" row stays below.
 */
@Composable
internal fun OwnWalletRecipientCard(wallet: WalletInfo, onClear: () -> Unit) {
    val clearLabel = tr("Clear address")
    val address = wallet.cachedPrimaryAddress.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = wallet.name
                customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true })
            }
            // The clear button's 48 dp target ends at the edge, as in the name card.
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiCircle(emoji = wallet.emoji, size = 44.dp, color = SystemFill)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = wallet.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(truncateMiddle(address), style = MonoCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OpenAliasClearButton(onClear)
    }
}

/**
 * A Transfer's Review: one side of it ("From" or "To"), the wallet's emoji in the 40 disc of the
 * Recipient row, the label, the name and the address 8…8. One TalkBack element.
 */
@Composable
internal fun ReviewWalletRow(label: String, wallet: WalletInfo, address: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, ${wallet.name}" }
    ) {
        EmojiCircle(emoji = wallet.emoji, size = 40.dp, color = SystemFill)
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = wallet.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!address.isNullOrEmpty()) {
                Text(
                    text = truncateMiddle(address),
                    style = MonoCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A fiat amount with the currency's symbol, as the wallet switcher shows a balance in Fiat Mode. */
internal fun formatFiatAmount(amount: Double, symbol: String): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "$symbol${format.format(amount)}"
}

/**
 * Transfer's To card at the top of the amount step (iOS TransferPartiesCard with only the To row):
 * the wallet's emoji in a 40 disc, "To" over its name, 64 tall. With another wallet it opens a menu:
 * This Wallet first, then every other wallet in switcher order, with its balance or why it cannot
 * be chosen. With one wallet, a plain row fixed on This Wallet.
 */
@Composable
internal fun TransferToCard(
    wallets: List<WalletInfo>,
    current: WalletInfo?,
    shown: WalletInfo?,
    destinationOf: (WalletInfo) -> TransferDestination,
    balanceText: (Long) -> String,
    onChoose: (WalletInfo) -> Unit
) {
    val targets = remember(wallets, current?.id) { SendNames.transferTargets(wallets, current?.id) }
    val hasMenu = current != null && targets.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    val isThis = shown != null && shown.id == current?.id
    val shownName = when {
        shown == null -> tr("Choose a wallet")
        isThis -> tr("This Wallet")
        else -> shown.name
    }
    val toLabel = tr("To")
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MoneroTheme.colors.fill)
                .then(if (hasMenu) Modifier.clickable { menuOpen = true } else Modifier)
                .clearAndSetSemantics {
                    contentDescription = toLabel
                    stateDescription = shownName
                    if (hasMenu) {
                        role = Role.DropdownList
                        onClick { menuOpen = true; true }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shown != null) {
                EmojiCircle(emoji = shown.emoji, size = 40.dp, color = MoneroTheme.colors.fillElevated)
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MoneroTheme.colors.fillElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(toLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = shownName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (shown == null) MoneroOrange else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasMenu) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (hasMenu && current != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                TransferMenuItem(
                    emoji = current.emoji,
                    name = tr("This Wallet"),
                    subtitle = tr("Sends XMR back to itself"),
                    enabled = true,
                    onClick = { menuOpen = false; onChoose(current) }
                )
                HorizontalDivider(color = MoneroTheme.colors.separator)
                targets.forEach { wallet ->
                    val destination = destinationOf(wallet)
                    TransferMenuItem(
                        emoji = wallet.emoji,
                        name = wallet.name,
                        subtitle = when (destination) {
                            is TransferDestination.Ready -> balanceText(wallet.cachedBalance ?: 0L)
                            TransferDestination.NeedsOpen -> tr("Open this wallet once to send to it")
                            TransferDestination.NeedsRestore -> tr("Needs restore")
                        },
                        enabled = destination is TransferDestination.Ready,
                        onClick = { menuOpen = false; onChoose(wallet) }
                    )
                }
            }
        }
    }
}

/** One wallet in To's menu: its emoji, the name over a subtitle. A disabled one has a gray name. */
@Composable
private fun TransferMenuItem(emoji: String, name: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        enabled = enabled,
        leadingIcon = { Text(emoji, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {}) },
        text = {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MoneroTheme.colors.gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick
    )
}
