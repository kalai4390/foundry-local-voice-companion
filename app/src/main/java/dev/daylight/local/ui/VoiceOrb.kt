package dev.daylight.local.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import dev.daylight.core.VoiceStage
import kotlin.math.sin

internal class VoiceOrb @JvmOverloads constructor(context: Context, attributes: AttributeSet? = null) : View(context, attributes) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var glow: RadialGradient? = null
    private var center: RadialGradient? = null
    private var stage = VoiceStage.IDLE
    private var level = 0f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun render(nextStage: VoiceStage, nextLevel: Float) {
        if (stage != nextStage || level != nextLevel) {
            val colorChanged = (stage == VoiceStage.LISTENING) != (nextStage == VoiceStage.LISTENING)
            stage = nextStage
            level = nextLevel
            if (colorChanged) prepareGradients()
            invalidate()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, previousWidth: Int, previousHeight: Int) {
        super.onSizeChanged(width, height, previousWidth, previousHeight)
        prepareGradients()
    }

    private fun prepareGradients() {
        val radius = minOf(width, height) * 0.25f
        if (radius <= 0f) return
        val accent = if (stage == VoiceStage.LISTENING) DaylightColors.mint else DaylightColors.lavender
        glow = RadialGradient(width / 2f, height / 2f, radius * 1.9f,
            intArrayOf(accent, DaylightColors.indigo, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0.12f, 0.62f, 1f), Shader.TileMode.CLAMP)
        center = RadialGradient(width / 2f - radius * 0.35f, height / 2f - radius * 0.35f, radius * 1.7f,
            intArrayOf(DaylightColors.text, accent, DaylightColors.indigo), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val base = minOf(width, height) * 0.25f
        val active = stage != VoiceStage.IDLE && ValueAnimator.areAnimatorsEnabled()
        val rhythm = if (active) (sin(SystemClock.uptimeMillis() / 600.0) * 0.06).toFloat() else 0f
        val voice = if (stage == VoiceStage.LISTENING) (level * 2f).coerceAtMost(0.22f) else 0f
        val radius = base * (1f + rhythm + voice)
        val accent = if (stage == VoiceStage.LISTENING) DaylightColors.mint else DaylightColors.lavender
        matrix.setScale(1f + rhythm + voice, 1f + rhythm + voice, centerX, centerY)
        glow?.setLocalMatrix(matrix)
        center?.setLocalMatrix(matrix)
        paint.shader = glow
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius * 1.9f, paint)
        paint.shader = center
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null
        paint.color = accent
        paint.alpha = 90
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.resources.displayMetrics.density
        canvas.drawCircle(centerX, centerY, radius * 1.3f, paint)
        paint.alpha = 255
        if (active && isShown) postInvalidateDelayed(48)
    }
}
