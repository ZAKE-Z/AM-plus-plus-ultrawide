package dev.amenhancer.module.hook.tabletmedia

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Pure glyph path for the Apple Music "more" (...) button.
 * Scaled down by an additional 20% (0.45x total) to match shuffle/repeat visual weight exactly.
 */
internal class PureMoreGlyph : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val save = canvas.save()
        // 0.45x total scale
        canvas.scale(0.45f, 0.45f, w / 2f, h / 2f)
        val r = h * 0.08f
        val cx = w / 2f
        canvas.drawCircle(cx, h * 0.28f, r, paint)
        canvas.drawCircle(cx, h * 0.50f, r, paint)
        canvas.drawCircle(cx, h * 0.72f, r, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Pure glyph path for the Apple Music "favorite" (star) button.
 * Scaled down by an additional 20% (0.45x total). Supports filled (favorited) and outlined states.
 */
internal class PureStarGlyph(var filled: Boolean = false) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val save = canvas.save()
        // 0.45x total scale
        canvas.scale(0.45f, 0.45f, w / 2f, h / 2f)
        path.reset()
        val scaleX = w / 28f
        val scaleY = h / 28f
        val points = floatArrayOf(
            14.075f, 5.8f,
            16.396f, 10.8f,
            21.461f, 11.5f,
            17.818f, 15.3f,
            18.925f, 20.6f,
            14.075f, 18.0f,
            9.225f, 20.6f,
            10.333f, 15.3f,
            6.689f, 11.5f,
            11.754f, 10.8f
        )
        path.moveTo(points[0] * scaleX, points[1] * scaleY)
        for (i in 2 until points.size step 2) {
            path.lineTo(points[i] * scaleX, points[i + 1] * scaleY)
        }
        path.close()
        paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = w * 0.08f
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
