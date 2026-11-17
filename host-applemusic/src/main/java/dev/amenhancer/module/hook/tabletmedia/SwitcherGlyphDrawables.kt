package dev.amenhancer.module.hook.tabletmedia

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Stacked rounded text lines standing in for Apple's lyrics symbol. */
internal class LyricsLinesDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val thickness = (h * 0.10f).coerceAtLeast(1f)
        fun line(top: Float, widthFraction: Float, start: Float) {
            rect.set(w * start, top, w * start + w * widthFraction, top + thickness)
            canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
        }
        line(h * 0.20f, 0.70f, 0.15f)
        line(h * 0.45f, 0.55f, 0.15f)
        line(h * 0.70f, 0.65f, 0.15f)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Row list standing in for Apple's playlist symbol. */
internal class QueueRowsDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val thickness = (h * 0.10f).coerceAtLeast(1f)
        fun row(top: Float) {
            rect.set(w * 0.22f, top, w * 0.86f, top + thickness)
            canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint)
            val dot = thickness * 1.1f
            rect.set(w * 0.06f, top + thickness / 2f - dot / 2f, w * 0.06f + dot, top + thickness / 2f + dot / 2f)
            canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint)
        }
        row(h * 0.20f)
        row(h * 0.45f)
        row(h * 0.70f)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
