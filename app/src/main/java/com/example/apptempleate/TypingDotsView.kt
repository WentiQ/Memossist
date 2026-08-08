package com.example.apptempleate

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Custom View that renders an authentic WhatsApp-style 3-dot jumping typing animation.
 * 3 dots bounce up and down in a smooth sine wave with staggered delays.
 */
class TypingDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotCount = 3
    private var dotRadius = 0f
    private var dotSpacing = 0f
    private var bounceHeight = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5") // Brand Indigo Accent
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var animPhase = 0f

    init {
        val density = resources.displayMetrics.density
        dotRadius = 3.5f * density
        dotSpacing = 6.0f * density
        bounceHeight = 5.0f * density
    }

    fun setDotColor(colorHex: String) {
        paint.color = Color.parseColor(colorHex)
        invalidate()
    }

    fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                animPhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
        animPhase = 0f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = dotCount * (2 * dotRadius) + (dotCount - 1) * dotSpacing
        val startX = (width - totalWidth) / 2f + dotRadius
        val centerY = height / 2f

        for (i in 0 until dotCount) {
            // Stagger phase offset for dot 0, 1, 2 (0, 120deg, 240deg)
            val offset = animPhase - (i * Math.PI / 2.5).toFloat()
            // Sine wave calculation for vertical displacement (-bounceHeight to 0)
            val rawSin = sin(offset.toDouble()).toFloat()
            val normalizedBounce = (rawSin.coerceAtLeast(0f)) // Only bounce upward
            val dy = -normalizedBounce * bounceHeight

            // Dynamic alpha scaling (0.4 to 1.0)
            val alpha = (102 + (153 * (rawSin + 1f) / 2f)).toInt().coerceIn(40, 255)
            paint.alpha = alpha

            val cx = startX + i * (2 * dotRadius + dotSpacing)
            val cy = centerY + dy

            canvas.drawCircle(cx, cy, dotRadius, paint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredWidth = (dotCount * (2 * dotRadius) + (dotCount - 1) * dotSpacing + 16 * density).toInt()
        val desiredHeight = (2 * dotRadius + 2 * bounceHeight + 12 * density).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
