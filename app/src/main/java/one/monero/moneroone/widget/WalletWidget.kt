package one.monero.moneroone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import one.monero.moneroone.MainActivity
import one.monero.moneroone.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

class WalletWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetReceiver::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val enabled = WidgetDataStore.isWalletWidgetEnabled(context)

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)

            if (!enabled) {
                val views = RemoteViews(context.packageName, R.layout.widget_wallet_disabled)
                views.setImageViewBitmap(R.id.wallet_disabled_logo, WidgetUtils.getCircularLogo(context, 32))
                views.setOnClickPendingIntent(R.id.widget_wallet_disabled_root, pending)
                manager.updateAppWidget(widgetId, views)
                return
            }

            val views = RemoteViews(context.packageName, R.layout.widget_wallet)
            views.setImageViewBitmap(R.id.wallet_logo, WidgetUtils.getCircularLogo(context, 28))

            val balance = WidgetDataStore.getBalance(context)

            // Balance (XMR only — match iOS, no fiat alongside)
            val xmr = BigDecimal(balance).divide(BigDecimal(1_000_000_000_000L))
            val xmrText = xmr.setScale(4, RoundingMode.HALF_UP).toPlainString()
            views.setTextViewText(R.id.wallet_xmr, xmrText)
            views.setViewVisibility(R.id.wallet_fiat, View.GONE)
            views.setViewVisibility(R.id.wallet_fiat_dot, View.GONE)

            // Sync status (matches iOS: synced=green, syncing/connecting=orange, offline=red)
            val status = WidgetDataStore.getSyncStatus(context)
            val (statusText, statusColor) = when (status) {
                "synced" -> "● Synced" to 0xFF34C759.toInt()
                "syncing" -> "● Syncing" to 0xFFFF9500.toInt()
                "connecting" -> "● Connecting" to 0xFFFF9500.toInt()
                else -> "● Offline" to 0xFFFF3B30.toInt()
            }
            views.setTextViewText(R.id.wallet_sync_status, statusText)
            views.setTextColor(R.id.wallet_sync_status, statusColor)

            // Transactions
            val txData = WidgetDataStore.getTransactions(context)
            val transactions = parseTxData(txData)

            data class TxRow(
                val rowId: Int, val iconId: Int,
                val labelId: Int, val dateId: Int, val amountId: Int
            )
            val txRows = listOf(
                TxRow(R.id.tx_row_1, R.id.tx1_icon, R.id.tx1_label, R.id.tx1_date, R.id.tx1_amount),
                TxRow(R.id.tx_row_2, R.id.tx2_icon, R.id.tx2_label, R.id.tx2_date, R.id.tx2_amount),
                TxRow(R.id.tx_row_3, R.id.tx3_icon, R.id.tx3_label, R.id.tx3_date, R.id.tx3_amount),
                TxRow(R.id.tx_row_4, R.id.tx4_icon, R.id.tx4_label, R.id.tx4_date, R.id.tx4_amount),
            )

            if (transactions.isEmpty()) {
                views.setViewVisibility(R.id.wallet_no_tx, View.VISIBLE)
                for (row in txRows) views.setViewVisibility(row.rowId, View.GONE)
            } else {
                views.setViewVisibility(R.id.wallet_no_tx, View.GONE)

                val now = System.currentTimeMillis()
                transactions.take(4).forEachIndexed { index, tx ->
                    val row = txRows[index]

                    val xmrAmount = BigDecimal(tx.amount)
                        .divide(BigDecimal(1_000_000_000_000L))
                        .setScale(4, RoundingMode.HALF_UP)
                        .toPlainString()
                    // iOS shows positive amount green for received, white for sent (no minus sign)
                    val amountText = xmrAmount
                    val amountColor = if (tx.isIncoming) 0xFF34C759.toInt() else 0xFFFFFFFF.toInt()
                    val label = if (tx.isIncoming) "Received" else "Sent"
                    val timeAgo = formatRelativeTime(now - tx.timestamp * 1000)

                    val iconBg = if (tx.isIncoming) R.drawable.widget_tx_icon_green else R.drawable.widget_tx_icon_orange
                    val iconRes = if (tx.isIncoming) R.drawable.ic_widget_arrow_down else R.drawable.ic_widget_arrow_up
                    val iconColor = if (tx.isIncoming) 0xFF34C759.toInt() else 0xFFFF9500.toInt()
                    views.setInt(row.iconId, "setBackgroundResource", iconBg)
                    views.setImageViewResource(row.iconId, iconRes)
                    views.setInt(row.iconId, "setColorFilter", iconColor)

                    views.setViewVisibility(row.rowId, View.VISIBLE)
                    views.setTextViewText(row.labelId, label)
                    views.setTextViewText(row.dateId, timeAgo)
                    views.setTextViewText(row.amountId, amountText)
                    views.setTextColor(row.amountId, amountColor)
                }

                for (i in transactions.size until 4) {
                    views.setViewVisibility(txRows[i].rowId, View.GONE)
                }
            }

            // Footer "Updated X ago"
            val updatedAt = WidgetDataStore.getBalanceUpdatedAt(context)
            if (updatedAt > 0) {
                val ago = formatRelativeTime(System.currentTimeMillis() - updatedAt)
                views.setTextViewText(R.id.wallet_updated, "Updated $ago ago")
            } else {
                views.setTextViewText(R.id.wallet_updated, "")
            }

            views.setOnClickPendingIntent(R.id.widget_wallet_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun formatRelativeTime(deltaMs: Long): String {
            val s = deltaMs / 1000
            return when {
                s < 60 -> "${s.coerceAtLeast(1)} sec"
                s < 3600 -> "${s / 60} min"
                s < 86400 -> {
                    val hours = s / 3600
                    if (hours < 24) "$hours hr" else "${hours / 24} days"
                }
                else -> {
                    val days = s / 86400
                    val remHours = (s % 86400) / 3600
                    if (remHours > 0) "$days days, $remHours hr" else "$days days"
                }
            }
        }

        private fun parseTxData(data: String): List<WidgetTransaction> {
            if (data.isBlank()) return emptyList()
            return data.split(";").take(4).mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    WidgetTransaction(
                        isIncoming = parts[0] == "in",
                        amount = parts[1].toLongOrNull() ?: 0L,
                        timestamp = parts[2].toLongOrNull() ?: 0L
                    )
                } else null
            }
        }
    }
}

data class WidgetTransaction(
    val isIncoming: Boolean,
    val amount: Long,
    val timestamp: Long
)
