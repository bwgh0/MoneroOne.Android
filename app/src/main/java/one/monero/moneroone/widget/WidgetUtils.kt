package one.monero.moneroone.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.content.ContextCompat
import one.monero.moneroone.R

object WidgetUtils {
    fun getCircularLogo(context: Context, sizeDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val drawable = ContextCompat.getDrawable(context, R.drawable.monero_logo) ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

        // Draw source bitmap
        val src = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val srcCanvas = Canvas(src)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(srcCanvas)

        // Circular mask
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, sizePx, sizePx)

        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)

        src.recycle()
        return output
    }
}
