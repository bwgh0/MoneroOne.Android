package one.monero.moneroone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Bundle
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

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        updateWidget(context, appWidgetManager, appWidgetId)
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
        private const val ORANGE = 0xFFFF7500.toInt()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidgetReceiver::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            // Check widget size to pick layout
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val isMediumOrLarger = minWidth > 180

            val layoutId = if (isMediumOrLarger) R.layout.widget_price_medium else R.layout.widget_price
            val views = RemoteViews(context.packageName, layoutId)

            // Logo
            views.setImageViewBitmap(R.id.price_logo, WidgetUtils.getCircularLogo(context, if (isMediumOrLarger) 24 else 36))

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

            // Render chart for medium/large widgets
            if (isMediumOrLarger) {
                val chartPoints = WidgetDataStore.getChartPoints(context)
                if (chartPoints.size > 1) {
                    val bitmap = renderSparkline(chartPoints, 400, 200)
                    views.setImageViewBitmap(R.id.price_chart, bitmap)
                }
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_price_root, pending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun renderSparkline(points: List<Double>, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val min = points.min()
            val max = points.max()
            val range = if (max - min > 0) max - min else 1.0
            val padding = range * 0.05

            val linePaint = Paint().apply {
                color = ORANGE
                strokeWidth = 4f
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val fillPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    (ORANGE and 0x00FFFFFF) or 0x66000000, // 40% alpha orange
                    0x00000000, // transparent
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val linePath = Path()
            val fillPath = Path()

            val stepX = width.toFloat() / (points.size - 1)

            points.forEachIndexed { i, value ->
                val x = i * stepX
                val y = height - ((value - min + padding) / (range + 2 * padding) * height).toFloat()

                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height.toFloat())
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            // Close fill path
            fillPath.lineTo(width.toFloat(), height.toFloat())
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)

            return bitmap
        }
    }
}
