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
import java.text.SimpleDateFormat
import java.util.Date
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
            views.setImageViewBitmap(R.id.wallet_logo, WidgetUtils.getCircularLogo(context, 22))

            val balance = WidgetDataStore.getBalance(context)
            val price = WidgetDataStore.getPrice(context)
            val symbol = WidgetDataStore.getCurrencySymbol(context)

            // Balance
            val xmr = BigDecimal(balance).divide(BigDecimal(1_000_000_000_000L))
            val xmrText = xmr.setScale(4, RoundingMode.DOWN).toPlainString()
            views.setTextViewText(R.id.wallet_xmr, xmrText)

            // Fiat
            if (price > 0) {
                val fiatValue = xmr.multiply(BigDecimal(price.toDouble()))
                val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                views.setTextViewText(R.id.wallet_fiat, "$symbol${format.format(fiatValue)}")
                views.setViewVisibility(R.id.wallet_fiat, View.VISIBLE)
                views.setViewVisibility(R.id.wallet_fiat_dot, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.wallet_fiat, View.GONE)
                views.setViewVisibility(R.id.wallet_fiat_dot, View.GONE)
            }

            // Transactions
            val txData = WidgetDataStore.getTransactions(context)
            val transactions = parseTxData(txData)
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

            data class TxRow(
                val rowId: Int, val iconId: Int,
                val labelId: Int, val dateId: Int, val amountId: Int
            )
            val txRows = listOf(
                TxRow(R.id.tx_row_1, R.id.tx1_icon, R.id.tx1_label, R.id.tx1_date, R.id.tx1_amount),
                TxRow(R.id.tx_row_2, R.id.tx2_icon, R.id.tx2_label, R.id.tx2_date, R.id.tx2_amount),
                TxRow(R.id.tx_row_3, R.id.tx3_icon, R.id.tx3_label, R.id.tx3_date, R.id.tx3_amount),
            )

            if (transactions.isEmpty()) {
                views.setViewVisibility(R.id.wallet_divider, View.GONE)
                views.setViewVisibility(R.id.wallet_no_tx, View.VISIBLE)
                for (row in txRows) views.setViewVisibility(row.rowId, View.GONE)
            } else {
                views.setViewVisibility(R.id.wallet_divider, View.VISIBLE)
                views.setViewVisibility(R.id.wallet_no_tx, View.GONE)

                transactions.take(3).forEachIndexed { index, tx ->
                    val row = txRows[index]

                    val xmrAmount = BigDecimal(tx.amount)
                        .divide(BigDecimal(1_000_000_000_000L))
                        .setScale(4, RoundingMode.DOWN)
                        .toPlainString()
                    val sign = if (tx.isIncoming) "+" else "-"
                    val amountColor = if (tx.isIncoming) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
                    val label = if (tx.isIncoming) "Received" else "Sent"
                    val date = dateFormat.format(Date(tx.timestamp * 1000))

                    // Icon
                    val iconBg = if (tx.isIncoming) R.drawable.widget_tx_icon_green else R.drawable.widget_tx_icon_orange
                    val iconText = if (tx.isIncoming) "↓" else "↑"
                    val iconColor = if (tx.isIncoming) 0xFF34C759.toInt() else 0xFFFF9500.toInt()
                    views.setInt(row.iconId, "setBackgroundResource", iconBg)
                    views.setTextViewText(row.iconId, iconText)
                    views.setTextColor(row.iconId, iconColor)

                    views.setViewVisibility(row.rowId, View.VISIBLE)
                    views.setTextViewText(row.labelId, label)
                    views.setTextViewText(row.dateId, date)
                    views.setTextViewText(row.amountId, "$sign$xmrAmount")
                    views.setTextColor(row.amountId, amountColor)
                }

                // Hide unused rows
                for (i in transactions.size until 3) {
                    views.setViewVisibility(txRows[i].rowId, View.GONE)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_wallet_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun parseTxData(data: String): List<WidgetTransaction> {
            if (data.isBlank()) return emptyList()
            return data.split(";").take(3).mapNotNull { entry ->
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
