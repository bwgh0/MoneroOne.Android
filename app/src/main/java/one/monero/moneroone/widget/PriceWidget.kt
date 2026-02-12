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
import java.text.NumberFormat
import java.util.Locale

class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PriceUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PriceUpdateWorker.cancel(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidgetReceiver::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_price)

            // Circular logo
            views.setImageViewBitmap(R.id.price_logo, WidgetUtils.getCircularLogo(context, 36))

            val price = WidgetDataStore.getPrice(context)
            val change = WidgetDataStore.getChange24h(context)
            val symbol = WidgetDataStore.getCurrencySymbol(context)

            if (price > 0) {
                val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                views.setTextViewText(R.id.price_value, "$symbol${format.format(price)}")

                val sign = if (change >= 0) "+" else ""
                views.setTextViewText(R.id.price_change, " $sign${String.format("%.2f", change)}% ")
                val changeColor = if (change >= 0) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
                val badgeBg = if (change >= 0) R.drawable.widget_badge_green else R.drawable.widget_badge_red
                views.setTextColor(R.id.price_change, changeColor)
                views.setInt(R.id.price_change, "setBackgroundResource", badgeBg)
                views.setViewVisibility(R.id.price_change, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.price_value, "Open Monero One")
                views.setViewVisibility(R.id.price_change, View.GONE)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_price_root, pending)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
