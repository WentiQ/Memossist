package com.example.apptempleate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class GraphNode(
    val memory: MemoryItem,
    var x: Float,
    var y: Float,
    val radius: Float = 28f
)

class DagGraph2DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodes = mutableListOf<GraphNode>()
    private val edges = mutableListOf<DagEdge>()

    // Transform State (Pan & Scale)
    private var translateX = 0f
    private var translateY = 0f
    private var scaleFactor = 1.0f

    private var selectedNode: GraphNode? = null
    var onNodeSelectedListener: ((MemoryItem, List<DagEdge>) -> Unit)? = null

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        style = Paint.Style.FILL
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val edgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D4ED8")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val nodeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        style = Paint.Style.FILL
    }

    private val nodeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val nodeSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }

    private val nodeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val nodeSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4B5563")
        textSize = 22f
    }

    // Touch & Gesture Detectors
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY

            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.3f, min(scaleFactor, 4.0f))

            val scaleRatio = scaleFactor / oldScale
            translateX = focusX - (focusX - translateX) * scaleRatio
            translateY = focusY - (focusY - translateY) * scaleRatio

            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleSingleTap(e.x, e.y)
            return true
        }
    })

    init {
        setBackgroundColor(Color.parseColor("#F9FAFB"))
    }

    fun setData(memories: List<MemoryItem>, dagEdges: List<DagEdge>) {
        nodes.clear()
        edges.clear()

        // ONLY keep edges with strength > 0.0
        edges.addAll(dagEdges.filter { it.strength > 0.0 })

        if (memories.isEmpty()) {
            invalidate()
            return
        }

        // Random organic distribution starting from center (0,0) spreading outwards as experiences add
        val centerX = 0f
        val centerY = 0f

        memories.forEachIndexed { index, memory ->
            if (index == 0) {
                nodes.add(GraphNode(memory, centerX, centerY))
            } else {
                // Use a seeded Random based on memory.id to ensure consistent node positions across redraws
                val seedRandom = java.util.Random(memory.id.hashCode().toLong())
                
                // Golden angle (~137.5 degrees in radians) + organic random angle jitter
                val goldenAngle = 2.399963
                val angleJitter = (seedRandom.nextDouble() - 0.5) * 0.8
                val angle = index * goldenAngle + angleJitter

                // Distance expands outwards gradually from center with random distance jitter
                val baseDistance = 180f * Math.sqrt(index.toDouble()).toFloat()
                val distanceJitter = (seedRandom.nextFloat() - 0.5f) * 120f
                val radius = baseDistance + distanceJitter

                val nx = centerX + (radius * cos(angle)).toFloat()
                val ny = centerY + (radius * sin(angle)).toFloat()
                nodes.add(GraphNode(memory, nx, ny))
            }
        }

        resetView()
    }

    fun resetView() {
        translateX = width / 2f
        translateY = height / 2f
        scaleFactor = 1.0f
        selectedNode = null
        invalidate()
    }

    fun zoomIn() {
        scaleFactor = min(scaleFactor * 1.25f, 4.0f)
        invalidate()
    }

    fun zoomOut() {
        scaleFactor = max(scaleFactor / 1.25f, 0.3f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (translateX == 0f && translateY == 0f) {
            translateX = w / 2f
            translateY = h / 2f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    translateX += dx
                    translateY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }

        return true
    }

    private fun handleSingleTap(screenX: Float, screenY: Float) {
        // Convert screen coordinates to canvas 2D world coordinates
        val worldX = (screenX - translateX) / scaleFactor
        val worldY = (screenY - translateY) / scaleFactor

        var tappedNode: GraphNode? = null
        for (node in nodes) {
            val dist = Math.hypot((worldX - node.x).toDouble(), (worldY - node.y).toDouble()).toFloat()
            if (dist <= node.radius * 2.2f) {
                tappedNode = node
                break
            }
        }

        selectedNode = tappedNode
        invalidate()

        if (tappedNode != null) {
            val nodeEdges = edges.filter {
                it.experienceId1.equals(tappedNode.memory.id, ignoreCase = true) ||
                it.experienceId2.equals(tappedNode.memory.id, ignoreCase = true)
            }
            onNodeSelectedListener?.invoke(tappedNode.memory, nodeEdges)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // 1. Draw Infinite Background Plane Grid Dots
        drawBackgroundGrid(canvas)

        // 2. Draw Non-Zero Connecting Edges (Lines)
        drawEdges(canvas)

        // 3. Draw Tiny Dot Nodes (Experiences)
        drawNodes(canvas)

        canvas.restore()
    }

    private fun drawBackgroundGrid(canvas: Canvas) {
        val gridSize = 120f
        val viewWidthWorld = width / scaleFactor
        val viewHeightWorld = height / scaleFactor

        val startX = ((-translateX / scaleFactor) / gridSize).toInt() * gridSize - gridSize * 2
        val endX = startX + viewWidthWorld + gridSize * 4

        val startY = ((-translateY / scaleFactor) / gridSize).toInt() * gridSize - gridSize * 2
        val endY = startY + viewHeightWorld + gridSize * 4

        var x = startX
        while (x <= endX) {
            var y = startY
            while (y <= endY) {
                canvas.drawCircle(x, y, 3f / scaleFactor, gridDotPaint)
                y += gridSize
            }
            x += gridSize
        }
    }

    private fun drawEdges(canvas: Canvas) {
        val nodeMap = nodes.associateBy { it.memory.id }

        for (edge in edges) {
            val n1 = nodeMap[edge.experienceId1] ?: nodes.find { it.memory.id.equals(edge.experienceId1, ignoreCase = true) }
            val n2 = nodeMap[edge.experienceId2] ?: nodes.find { it.memory.id.equals(edge.experienceId2, ignoreCase = true) }

            if (n1 != null && n2 != null) {
                val isConnectedToSelected = selectedNode != null &&
                        (selectedNode!!.memory.id.equals(n1.memory.id, ignoreCase = true) ||
                         selectedNode!!.memory.id.equals(n2.memory.id, ignoreCase = true))

                // Line thickness & opacity scale with connection strength S_ij
                val alpha = if (isConnectedToSelected) 255 else (120 + (edge.strength * 135).coerceAtMost(135.0)).toInt()
                val thickness = if (isConnectedToSelected) 7f else (3f + (edge.strength * 6.0).toFloat())

                edgePaint.strokeWidth = thickness
                edgePaint.color = if (isConnectedToSelected) Color.parseColor("#10B981") else Color.parseColor("#2563EB")
                edgePaint.alpha = alpha

                canvas.drawLine(n1.x, n1.y, n2.x, n2.y, edgePaint)

                // Draw edge strength badge label in center of connection line
                val midX = (n1.x + n2.x) / 2f
                val midY = (n1.y + n2.y) / 2f
                val strengthText = String.format("S_ij=%.3f", edge.strength)
                edgeTextPaint.color = if (isConnectedToSelected) Color.parseColor("#047857") else Color.parseColor("#1D4ED8")
                canvas.drawText(strengthText, midX + 8f, midY - 8f, edgeTextPaint)
            }
        }
    }

    private fun drawNodes(canvas: Canvas) {
        for (node in nodes) {
            val isSelected = selectedNode != null && selectedNode!!.memory.id.equals(node.memory.id, ignoreCase = true)

            // Draw Node Circle Dot
            val radius = if (isSelected) node.radius * 1.3f else node.radius
            val paint = if (isSelected) nodeSelectedPaint else nodeBodyPaint

            canvas.drawCircle(node.x, node.y, radius, paint)
            canvas.drawCircle(node.x, node.y, radius + 4f, nodeRingPaint)

            // Draw Node Text Labels
            val titleSnippet = if (node.memory.title.length > 20) node.memory.title.take(20) + "..." else node.memory.title

            canvas.drawText(node.memory.id, node.x + radius + 10f, node.y - 6f, nodeTextPaint)
            canvas.drawText(titleSnippet, node.x + radius + 10f, node.y + 22f, nodeSubTextPaint)
        }
    }
}
