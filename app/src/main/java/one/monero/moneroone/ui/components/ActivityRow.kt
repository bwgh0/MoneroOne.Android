package one.monero.moneroone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.monero.moneroone.ui.theme.MoneroOrange
import one.monero.moneroone.ui.theme.MoneroTheme
import one.monero.moneroone.ui.theme.SuccessGreen

/**
 * One Activity row (Recent Activity and All Transactions): the direction disc
 * (green ↙ in, orange ↗ out), the title over the date, the [amounts] and a
 * chevron, on a card. A payment link row adds the [linkBadge] to the disc; a
 * send between your wallets adds the [walletBadge]. A transaction row passes
 * its [confirmations]: the disc shows the confirmation ring from 0 to 9 (see
 * [ConfirmationRing]). TalkBack reads [spoken] for the whole row, then
 * "n of 10 confirmations" while the ring shows.
 */
@Composable
fun ActivityRow(
    incoming: Boolean,
    title: String,
    subtitle: String,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    linkBadge: Boolean = false,
    walletBadge: Boolean = false,
    /** A send to a contact: the person glyph in the badge. */
    personBadge: Boolean = false,
    confirmations: Long? = null,
    failed: Boolean = false,
    amounts: @Composable () -> Unit
) {
    val rowBackground = MaterialTheme.colorScheme.surfaceContainer
    val ring = rememberDiscRing(confirmations, failed)
    val label = if (ConfirmationRing.shows(confirmations, failed)) {
        spoken + ", " + ConfirmationRing.spoken(confirmations ?: 0L)
    } else spoken
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = rowBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = label }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionDisc(incoming = incoming, linkBadge = linkBadge, rowBackground = rowBackground, walletBadge = walletBadge, personBadge = personBadge, ring = ring)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            amounts()

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MoneroTheme.colors.labelTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * The 40 direction disc at 15% of its hue. With [linkBadge], an 18 badge sits
 * on its bottom-right corner (4 past the disc): the link glyph in the hue on
 * the hue's 15% tint, opaque over [rowBackground], cut out of the disc by a 2
 * ring of [rowBackground]. [walletBadge] is the same badge with the wallet glyph.
 * With a [ring], the confirmation ring sits on the disc's rim, under the badge;
 * at 10 the disc turns green and shows a check for a moment.
 */
@Composable
internal fun DirectionDisc(
    incoming: Boolean,
    linkBadge: Boolean,
    rowBackground: Color,
    walletBadge: Boolean = false,
    personBadge: Boolean = false,
    ring: DiscRingState? = null
) {
    val ownHue = if (incoming) SuccessGreen else MoneroOrange
    val green = SuccessGreen
    val hue = if (ring != null) lerp(ownHue, green, ring.discGreen.value) else ownHue
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(hue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = hue,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        if (ring != null) {
                            alpha = ring.arrowAlpha.value
                            scaleX = ring.arrowScale.value
                            scaleY = ring.arrowScale.value
                        }
                    }
                    .rotate(45f)
            )
            if (ring != null && ring.ringOn) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = hue,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            alpha = ring.checkAlpha.value
                            scaleX = ring.checkScale.value
                            scaleY = ring.checkScale.value
                        }
                )
            }
        }
        if (ring != null && ring.ringOn) DiscRing(ring)
        if (linkBadge || walletBadge || personBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // The 18 badge ends 4 past the disc; its 2 ring ends 6 past.
                    .offset(x = BadgeRing + BadgeOverhang, y = BadgeRing + BadgeOverhang)
                    .size(BadgeSize + BadgeRing * 2)
                    .clip(CircleShape)
                    .background(rowBackground)
                    .padding(BadgeRing)
                    .clip(CircleShape)
                    .background(ownHue.copy(alpha = 0.15f).compositeOver(rowBackground)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        personBadge -> Icons.Default.Person
                        walletBadge -> Icons.Default.AccountBalanceWallet
                        else -> Icons.Default.Link
                    },
                    contentDescription = null,
                    tint = ownHue,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

private val BadgeSize = 18.dp
private val BadgeRing = 2.dp
private val BadgeOverhang = 4.dp
