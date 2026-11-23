package com.runeterra.local

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.Choreographer
import kotlin.math.sin
import kotlin.math.cos

/**
 * Lightweight animated Runeterra-inspired background.
 * Slow aurora ribbons and drifting hextech motes; no bitmap or network assets.
 */
class RuneterraFlowDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val startedAt = SystemClock.uptimeMillis()
    private val particles = Array(34) { i ->
        floatArrayOf(
            ((i * 73) % 997) / 997f,
            ((i * 137) % 991) / 991f,
            0.7f + ((i * 17) % 13) / 10f,
            (i % 3).toFloat()
        )
    }
    private var scheduled = false
    private val frameCallback = Choreographer.FrameCallback {
        scheduled = false
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f

        paint.shader = LinearGradient(
            0f, 0f, w * 0.75f, h,
            intArrayOf(Color.rgb(5, 12, 25), Color.rgb(12, 28, 49), Color.rgb(5, 10, 22)),
            null, Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(b, paint)
        paint.shader = null

        // Slow moving teal and sapphire aurora ribbons.
        for (layer in 0..3) {
            val phase = t * (0.16f + layer * 0.035f) + layer * 1.7f
            path.reset()
            val y0 = h * (0.24f + layer * 0.17f)
            path.moveTo(0f, y0)
            path.cubicTo(
                w * 0.28f, y0 + sin(phase) * h * 0.12f,
                w * 0.62f, y0 + cos(phase * 0.8f) * h * 0.16f,
                w, y0 + sin(phase + 1.4f) * h * 0.12f
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = h * (0.055f - layer * 0.008f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = when (layer) {
                0 -> Color.argb(25, 41, 190, 190)
                1 -> Color.argb(22, 54, 125, 220)
                2 -> Color.argb(18, 210, 171, 91)
                else -> Color.argb(17, 45, 164, 177)
            }
            canvas.drawPath(path, paint)
            paint.strokeWidth = h * 0.004f
            paint.color = when (layer) {
                0 -> Color.argb(65, 66, 205, 190)
                1 -> Color.argb(52, 82, 143, 226)
                2 -> Color.argb(42, 220, 183, 105)
                else -> Color.argb(48, 62, 174, 188)
            }
            canvas.drawPath(path, paint)
        }

        // Drifting hextech motes with subtle pulse.
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val x = ((p[0] + t * (0.008f + p[2] * 0.002f)) % 1f) * w
            val y = ((p[1] + sin(t * 0.22f + p[0] * 8f) * 0.018f + 1f) % 1f) * h
            val pulse = 0.55f + 0.45f * sin(t * 1.15f + p[0] * 19f)
            val alpha = (18 + 35 * pulse).toInt().coerceIn(12, 55)
            paint.color = if (p[3] == 2f) Color.argb(alpha, 225, 190, 116)
                else Color.argb(alpha, 91, 210, 202)
            canvas.drawCircle(x, y, p[2] * 1.25f, paint)
        }

        if (!scheduled && isVisible) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated by Android Drawable API")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible && scheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            scheduled = false
        } else if (visible) {
            invalidateSelf()
        }
        return changed
    }

    override fun unscheduleSelf(what: Runnable) {
        if (what === frame) scheduled = false
        super.unscheduleSelf(what)
    }
}
