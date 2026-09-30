package dev.daylight.local.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class DaylightTrendView(context: Context, private val accent: Int) : View(context) {
    private val density = resources.displayMetrics.density
    private val fontDensity = resources.displayMetrics.scaledDensity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectangle = RectF()
    private val missingPattern = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
    private var entries: List<Float?> = emptyList()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        isSaveEnabled = false
        contentDescription = "No trend supplied."
    }

    fun bind(label: String, series: List<Float?>) {
        val description = if (series.isEmpty()) {
            "$label. No trend supplied."
        } else {
            val values = series.mapIndexed { index, value ->
                val entry = when {
                    value == null -> "missing record, not zero"
                    !value.isFinite() -> "unavailable value, not plotted"
                    value == 0f -> "zero"
                    else -> value.toString()
                }
                "Entry ${index + 1}: $entry"
            }.joinToString(". ")
            "$label. Supplied sequence, independently scaled with a zero baseline. $values."
        }
        if (contentDescription != description) contentDescription = description
        if (entries != series) {
            entries = series.toList()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((240 * density).roundToInt(), widthMeasureSpec),
            resolveSize((112 * density + 26 * fontDensity).roundToInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = null
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        paint.textSize = 12f * fontDensity
        paint.color = DaylightColors.secondary
        if (entries.isEmpty()) {
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("No trend supplied", width / 2f, height / 2f, paint)
            return
        }

        val plotLeft = paint.measureText("0") + 12f * density
        val plotRight = width - 4f * density
        val plotTop = 10f * density
        val plotBottom = height - 26f * fontDensity
        if (plotRight <= plotLeft || plotBottom <= plotTop) return
        val finiteEntries = entries.filterNotNull().filter { it.isFinite() }
        val lowerBound = min(0.0, finiteEntries.minOrNull()?.toDouble() ?: 0.0)
        val upperBound = max(0.0, finiteEntries.maxOrNull()?.toDouble() ?: 0.0)
            .let { bound -> if (bound == lowerBound) bound + 1.0 else bound }
        val span = upperBound - lowerBound
        fun position(value: Double): Float =
            plotTop + ((upperBound - value) / span * (plotBottom - plotTop)).toFloat()

        val baseline = position(0.0)
        paint.strokeWidth = density
        paint.color = DaylightColors.line
        for (gridIndex in 0..3) {
            val gridTop = plotTop + (plotBottom - plotTop) * gridIndex / 3f
            canvas.drawLine(plotLeft, gridTop, plotRight, gridTop, paint)
        }
        paint.color = DaylightColors.muted
        canvas.drawLine(plotLeft, baseline, plotRight, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("0", 0f, baseline + 4f * fontDensity, paint)

        val slotWidth = (plotRight - plotLeft) / entries.size
        val barWidth = min(22f * density, slotWidth * 0.56f)
        val gradient = LinearGradient(
            0f, plotTop, 0f, plotBottom,
            accent, Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Shader.TileMode.CLAMP
        )
        val labelWidth = paint.measureText(entries.size.toString()) + 12f * density
        val labelStride = max(1, ceil(labelWidth / slotWidth).toInt())
        for ((index, value) in entries.withIndex()) {
            val center = plotLeft + slotWidth * (index + 0.5f)
            paint.shader = null
            paint.pathEffect = null
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 1.5f * density
            when {
                value == null -> {
                    val markerTop = if (baseline - plotTop >= 20f * density) {
                        baseline - 18f * density
                    } else {
                        baseline + 3f * density
                    }
                    rectangle.set(center - barWidth / 2f, markerTop, center + barWidth / 2f, markerTop + 15f * density)
                    paint.style = Paint.Style.STROKE
                    paint.color = DaylightColors.secondary
                    paint.pathEffect = missingPattern
                    canvas.drawRoundRect(rectangle, min(4f * density, barWidth / 2f), 4f * density, paint)
                }
                !value.isFinite() -> {
                    paint.color = DaylightColors.amber
                    val markerTop = (baseline - 12f * density).coerceAtLeast(plotTop)
                    canvas.drawLine(center - barWidth / 2f, markerTop, center + barWidth / 2f, markerTop + 8f * density, paint)
                    canvas.drawLine(center + barWidth / 2f, markerTop, center - barWidth / 2f, markerTop + 8f * density, paint)
                }
                value == 0f -> {
                    paint.color = accent
                    paint.strokeWidth = 3f * density
                    canvas.drawLine(center - barWidth / 2f, baseline, center + barWidth / 2f, baseline, paint)
                }
                else -> {
                    val endpoint = position(value.toDouble())
                    rectangle.set(center - barWidth / 2f, min(endpoint, baseline), center + barWidth / 2f, max(endpoint, baseline))
                    paint.style = Paint.Style.FILL
                    paint.shader = gradient
                    val radius = min(5f * density, min(barWidth / 2f, abs(endpoint - baseline) / 2f))
                    canvas.drawRoundRect(rectangle, radius, radius, paint)
                }
            }
            paint.pathEffect = null
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = DaylightColors.secondary
            paint.textAlign = Paint.Align.CENTER
            val lastEntry = index == entries.lastIndex
            val nearLastLabel = entries.lastIndex - index < labelStride
            if (lastEntry || (index % labelStride == 0 && !nearLastLabel)) {
                canvas.drawText((index + 1).toString(), center, height - 5f * fontDensity, paint)
            }
        }
    }
}
