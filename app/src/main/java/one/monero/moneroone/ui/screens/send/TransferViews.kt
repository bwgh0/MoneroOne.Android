package one.monero.moneroone.ui.screens.send

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
