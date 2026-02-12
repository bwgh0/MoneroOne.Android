package one.monero.moneroone.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class PriceWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PriceWidget().onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        PriceWidget().onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        PriceWidget().onDisabled(context)
    }
}
