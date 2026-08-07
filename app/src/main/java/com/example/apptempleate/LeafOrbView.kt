package com.example.apptempleate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class LeafOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Leaf(
        var x: Float,
        var y: Float,
        var speedY: Float,
        var sinAmp: Float,
        var sinFreq: Float,
        var sinAngle: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var size: Float,
        var color: Int,
        var veinColor: Int,
        var alpha: Int
    )

    private val leaves = ArrayList<Leaf>()
    private val numLeaves = 20

    private val clipPath = Path()
    private val leafPath = Path()

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Palette of smooth organic yellow and green tones
    private val leafColors = intArrayOf(
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#66BB6A"), // Light Green
        Color.parseColor("#8BC34A"), // Lime Green
        Color.parseColor("#C0CA33"), // Yellow-Green
        Color.parseColor("#FBC02D"), // Vibrant Yellow
        Color.parseColor("#F57F17"), // Warm Amber Yellow
        Color.parseColor("#9E9D24"), // Olive Yellow
        Color.parseColor("#2E7D32")  // Deep Forest Green
    )

    private var initialized = false

    init {
        // Enforce Software rendering layer for pixel-perfect circular clipping
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initLeaves(w.toFloat(), h.toFloat())
    }

    private fun initLeaves(w: Float, h: Float) {
        if (w <= 0 || h <= 0) return
        leaves.clear()
        val radius = Math.min(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f

        for (i in 0 until numLeaves) {
            val leafSize = 24f + Random.nextFloat() * 22f
            val maxDist = radius - leafSize - 4f

            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val dist = Random.nextFloat() * maxDist
            val color = leafColors[Random.nextInt(leafColors.size)]

            leaves.add(
                Leaf(
                    x = cx + cos(angle) * dist,
                    y = cy + sin(angle) * dist,
                    speedY = -(0.7f + Random.nextFloat() * 1.3f),
                    sinAmp = 10f + Random.nextFloat() * 18f,
                    sinFreq = 0.02f + Random.nextFloat() * 0.03f,
                    sinAngle = Random.nextFloat() * 360f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 2.2f,
                    size = leafSize,
                    color = color,
                    veinColor = Color.argb(180, 255, 255, 255),
                    alpha = 210 + Random.nextInt(45)
                )
            )
        }

        // Light Ambient Center Glow for White Background
        glowPaint.shader = RadialGradient(
            cx, cy, radius * 0.88f,
            intArrayOf(
                Color.parseColor("#228BC34A"),
                Color.parseColor("#12FFD54F"),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        // Invisible Circular Boundary Path
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val radius = Math.min(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f

        if (!initialized) {
            initLeaves(w, h)
        }

        // 1. Draw Ambient Center Glow
        canvas.drawCircle(cx, cy, radius, glowPaint)

        // 2. Save Canvas & Clip Strictly to Invisible Circular Region
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. Update & Render Leaves Strictly Inside Invisible Circular Boundary
        for (leaf in leaves) {
            // Update leaf motion
            leaf.y += leaf.speedY
            leaf.sinAngle += leaf.sinFreq
            val offsetX = sin(leaf.sinAngle) * leaf.sinAmp
            var drawX = leaf.x + offsetX
            var drawY = leaf.y
            leaf.rotation += leaf.rotationSpeed

            // Exact radial distance calculation
            val distFromCenter = hypot((drawX - cx), (drawY - cy))
            val maxAllowedDist = radius - leaf.size / 2f

            // Respawn leaf inside lower region if it reaches boundary
            if (distFromCenter >= maxAllowedDist || drawY < cy - (radius - leaf.size)) {
                val spawnAngle = (0.2f + Random.nextFloat() * 0.6f) * Math.PI.toFloat()
                val spawnDist = Random.nextFloat() * (radius * 0.65f)
                leaf.x = cx + cos(spawnAngle) * spawnDist
                leaf.y = cy + abs(sin(spawnAngle)) * (radius * 0.7f)
                drawX = leaf.x
                drawY = leaf.y
                leaf.color = leafColors[Random.nextInt(leafColors.size)]
            }

            // Draw individual leaf inside boundary
            canvas.save()
            canvas.translate(drawX, drawY)
            canvas.rotate(leaf.rotation)

            // Leaf Geometry
            leafPath.reset()
            val halfS = leaf.size / 2f
            leafPath.moveTo(0f, -halfS)
            leafPath.cubicTo(halfS * 0.85f, -halfS * 0.4f, halfS * 0.85f, halfS * 0.5f, 0f, halfS)
            leafPath.cubicTo(-halfS * 0.85f, halfS * 0.5f, -halfS * 0.85f, -halfS * 0.4f, 0f, -halfS)
            leafPath.close()

            leafPaint.color = leaf.color
            leafPaint.alpha = leaf.alpha
            leafPaint.style = Paint.Style.FILL
            canvas.drawPath(leafPath, leafPaint)

            // Leaf vein line
            veinPaint.color = leaf.veinColor
            veinPaint.strokeWidth = 1.6f
            veinPaint.style = Paint.Style.STROKE
            canvas.drawLine(0f, -halfS * 0.7f, 0f, halfS * 0.7f, veinPaint)

            canvas.restore()
        }

        canvas.restore() // Restore canvas after clipped region

        // Continuous 60fps frame update loop
        postInvalidateOnAnimation()
    }
}
