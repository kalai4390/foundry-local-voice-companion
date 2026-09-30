package dev.daylight.local.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

internal enum class DaylightGlyph { SUN, TODAY, TALK, EVIDENCE, PRIVACY, TREND }

internal class DaylightIcon(
    private val glyph: DaylightGlyph,
    private val ink: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rectangle = RectF()
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.color = ink
        paint.alpha = drawableAlpha
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        path.reset()
        when (glyph) {
            DaylightGlyph.SUN -> {
                rectangle.set(6f, 8f, 18f, 20f)
                canvas.drawArc(rectangle, 180f, 180f, false, paint)
                canvas.drawLine(3f, 15f, 21f, 15f, paint)
                canvas.drawLine(7f, 19f, 17f, 19f, paint)
                canvas.drawLine(12f, 2f, 12f, 4f, paint)
                canvas.drawLine(3.5f, 6f, 5.2f, 7.7f, paint)
                canvas.drawLine(20.5f, 6f, 18.8f, 7.7f, paint)
            }
            DaylightGlyph.TODAY -> {
                rectangle.set(3f, 5f, 21f, 21f)
                canvas.drawRoundRect(rectangle, 4f, 4f, paint)
                canvas.drawLine(3f, 10f, 21f, 10f, paint)
                canvas.drawLine(8f, 3f, 8f, 7f, paint)
                canvas.drawLine(16f, 3f, 16f, 7f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(8f, 15f, 1f, paint)
                canvas.drawCircle(12f, 15f, 1f, paint)
                canvas.drawCircle(16f, 15f, 1f, paint)
            }
            DaylightGlyph.TALK -> {
                path.moveTo(7f, 4f)
                path.lineTo(17f, 4f)
                path.quadTo(21f, 4f, 21f, 8f)
                path.lineTo(21f, 13f)
                path.quadTo(21f, 17f, 17f, 17f)
                path.lineTo(10f, 17f)
                path.lineTo(5f, 21f)
                path.lineTo(5f, 17f)
                path.quadTo(3f, 16f, 3f, 13f)
                path.lineTo(3f, 8f)
                path.quadTo(3f, 4f, 7f, 4f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(8f, 9f, 16f, 9f, paint)
                canvas.drawLine(8f, 12f, 13f, 12f, paint)
            }
            DaylightGlyph.EVIDENCE -> {
                rectangle.set(5f, 3f, 19f, 22f)
                canvas.drawRoundRect(rectangle, 3f, 3f, paint)
                canvas.drawLine(9f, 8f, 15f, 8f, paint)
                canvas.drawLine(9f, 12f, 15f, 12f, paint)
                canvas.drawLine(9f, 16f, 13f, 16f, paint)
            }
            DaylightGlyph.PRIVACY -> {
                path.moveTo(12f, 2f)
                path.lineTo(21f, 6f)
                path.lineTo(20f, 13f)
                path.quadTo(19f, 18f, 12f, 22f)
                path.quadTo(5f, 18f, 4f, 13f)
                path.lineTo(3f, 6f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(8f, 12f, 11f, 15f, paint)
                canvas.drawLine(11f, 15f, 16f, 9f, paint)
            }
            DaylightGlyph.TREND -> {
                canvas.drawLine(4f, 21f, 20f, 21f, paint)
                canvas.drawLine(6f, 16f, 6f, 18f, paint)
                canvas.drawLine(12f, 10f, 12f, 18f, paint)
                canvas.drawLine(18f, 4f, 18f, 18f, paint)
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
