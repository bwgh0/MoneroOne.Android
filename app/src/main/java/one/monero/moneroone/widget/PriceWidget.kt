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
import android.os.Build
import android.os.Bundle
import android.util.SizeF
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

        private enum class Size { SMALL, MEDIUM, LARGE }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidgetReceiver::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val price = WidgetDataStore.getPrice(context)
            val change = WidgetDataStore.getChange24h(context)
            val symbol = WidgetDataStore.getCurrencySymbol(context)
            val chartPoints = WidgetDataStore.getChartPoints(context)
            val updatedAt = WidgetDataStore.getPriceUpdatedAt(context)

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: provide all three layouts; system picks based on actual size.
                // Cutoffs match iOS widget families: 2x2 small, 4x2 medium, 4x4 large.
                val small = buildView(context, Size.SMALL, price, change, symbol, chartPoints, updatedAt)
                val medium = buildView(context, Size.MEDIUM, price, change, symbol, chartPoints, updatedAt)
                val large = buildView(context, Size.LARGE, price, change, symbol, chartPoints, updatedAt)
                RemoteViews(
                    mapOf(
                        SizeF(110f, 110f) to small,
                        SizeF(240f, 110f) to medium,
                        SizeF(240f, 240f) to large
                    )
                )
            } else {
                val opts = manager.getAppWidgetOptions(widgetId)
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                val size = when {
                    w >= 240 && h >= 240 -> Size.LARGE
                    w >= 240 -> Size.MEDIUM
                    else -> Size.SMALL
                }
                buildView(context, size, price, change, symbol, chartPoints, updatedAt)
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun buildView(
            context: Context,
            size: Size,
            price: Float,
            changeApi: Float,
            symbol: String,
            chartPoints: List<Double>,
            updatedAt: Long
        ): RemoteViews {
            // Match the in-app chart screen, which displays a % computed from EMA-smoothed
            // points (span=10) — same algorithm as data/util/ChartAlgorithms.emaSmooth.
            val change = if (chartPoints.size > 1 && chartPoints.first() > 0) {
                val alpha = 2.0 / (10 + 1)
                var ema = chartPoints.first()
                for (i in 1 until chartPoints.size) {
                    ema = alpha * chartPoints[i] + (1 - alpha) * ema
                }
                val open = chartPoints.first()
                (((ema - open) / open) * 100).toFloat()
            } else {
                changeApi
            }
            val layoutId = when (size) {
                Size.LARGE -> R.layout.widget_price_large
                Size.MEDIUM -> R.layout.widget_price_medium
                Size.SMALL -> R.layout.widget_price
            }
            val views = RemoteViews(context.packageName, layoutId)

            val logoPx = when (size) {
                Size.SMALL -> 36
                Size.MEDIUM -> 24
                Size.LARGE -> 28
            }
            views.setImageViewBitmap(R.id.price_logo, WidgetUtils.getCircularLogo(context, logoPx))

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

            if (size != Size.SMALL && chartPoints.size > 1) {
                val bitmap = if (size == Size.LARGE) {
                    renderFullChart(chartPoints, symbol, 800, 600)
                } else {
                    renderSparkline(chartPoints, 400, 200)
                }
                views.setImageViewBitmap(R.id.price_chart, bitmap)

                if (size == Size.MEDIUM && price > 0) {
                    val hi = chartPoints.max()
                    val lo = chartPoints.min()
                    val compact = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                        maximumFractionDigits = 0
                    }
                    views.setTextViewText(
                        R.id.price_hilo,
                        "↑ $symbol${compact.format(hi)}   ↓ $symbol${compact.format(lo)}"
                    )
                    views.setViewVisibility(R.id.price_hilo, View.VISIBLE)
                }
            }

            if (size == Size.LARGE && updatedAt > 0) {
                val ago = formatRelative(System.currentTimeMillis() - updatedAt)
                views.setTextViewText(R.id.price_updated, "Updated $ago ago")
                views.setViewVisibility(R.id.price_updated, View.VISIBLE)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_price_root, pending)

            return views
        }

        private fun formatRelative(deltaMs: Long): String {
            val seconds = deltaMs / 1000
            return when {
                seconds < 60 -> "${seconds.coerceAtLeast(1)}s"
                seconds < 3600 -> "${seconds / 60}m"
                seconds < 86400 -> "${seconds / 3600}h"
                else -> "${seconds / 86400}d"
            }
        }

        private fun renderFullChart(points: List<Double>, symbol: String, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Reserve gutters for axis labels
            val rightGutter = 70f   // Y-axis price labels on right
            val bottomGutter = 30f  // X-axis time labels at bottom
            val topGutter = 18f     // headroom so the top Y-label isn't clipped
            val plotLeft = 0f
            val plotTop = topGutter
            val plotRight = width - rightGutter
            val plotBottom = height - bottomGutter
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            val min = points.min()
            val max = points.max()
            val range = if (max - min > 0) max - min else 1.0
            val padding = range * 0.05
            val yMin = min - padding
            val yMax = max + padding
            val yRange = yMax - yMin

            val yLabelPaint = Paint().apply {
                color = 0xFF8E8E93.toInt()
                textSize = 22f
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = Paint.Align.LEFT
            }
            val xLabelPaint = Paint().apply {
                color = 0xFF8E8E93.toInt()
                textSize = 22f
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }
            val gridPaint = Paint().apply {
                color = 0x408E8E93.toInt()
                strokeWidth = 1f
                isAntiAlias = true
            }

            // Y-axis: 3 labels (max, mid, min) with grid lines
            val yValues = listOf(yMax, (yMin + yMax) / 2, yMin)
            for (yv in yValues) {
                val y = (plotTop + (1.0 - (yv - yMin) / yRange) * plotH).toFloat()
                canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
                val label = "$symbol${yv.toInt()}"
                canvas.drawText(label, plotRight + 6f, y + yLabelPaint.textSize / 3f, yLabelPaint)
            }

            // X-axis: 4 inner time labels spanning 24h regardless of point count.
            val cal = java.util.Calendar.getInstance()
            val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val totalMinutes = 24 * 60
            val xLabelIndices = (1..4).map { (it * points.size) / 5 }
            for (idx in xLabelIndices) {
                val frac = idx.toDouble() / (points.size - 1)
                val x = (plotLeft + frac * plotW).toFloat()
                val minutesAgo = ((1.0 - frac) * totalMinutes).toInt()
                val pointMinutes = ((nowMinutes - minutesAgo) % totalMinutes + totalMinutes) % totalMinutes
                val hour = pointMinutes / 60
                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val ampm = if (hour < 12) "a" else "p"
                canvas.drawText("$displayHour$ampm", x, plotBottom + 22f, xLabelPaint)
            }

            // Sparkline (line + gradient fill)
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
                    0f, plotTop, 0f, plotBottom,
                    (ORANGE and 0x00FFFFFF) or 0x66000000,
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val linePath = Path()
            val fillPath = Path()
            val stepX = plotW / (points.size - 1)
            points.forEachIndexed { i, v ->
                val x = plotLeft + i * stepX
                val y = (plotTop + (1.0 - (v - yMin) / yRange) * plotH).toFloat()
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, plotBottom)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(plotRight, plotBottom)
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)

            return bitmap
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
