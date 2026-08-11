package com.example.apptempleate

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
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
    private var draggedNode: GraphNode? = null
    var onNodeSelectedListener: ((MemoryItem, List<DagEdge>) -> Unit)? = null

    // Scroller for Fling Inertia
    private val scroller = OverScroller(context)
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Animators for Smooth Zooming and Centering
    private var zoomAnimator: ValueAnimator? = null

    // Paints
    private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.app_card_border)
        style = Paint.Style.FILL
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_edge)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val edgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graph_edge_label)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val nodeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Keep nodes visibly distinct from the black graph canvas in dark mode.
        color = context.getColor(R.color.app_card_border)
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
        color = context.getColor(R.color.text_primary)
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val nodeSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = 22f
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY

            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.25f, min(scaleFactor, 5.0f))

            val scaleRatio = scaleFactor / oldScale
            translateX = focusX - (focusX - translateX) * scaleRatio
            translateY = focusY - (focusY - translateY) * scaleRatio

            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            handleSingleTap(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val worldX = (e.x - translateX) / scaleFactor
            val worldY = (e.y - translateY) / scaleFactor

            var tappedNode: GraphNode? = null
            for (node in nodes) {
                val dist = Math.hypot((worldX - node.x).toDouble(), (worldY - node.y).toDouble()).toFloat()
                if (dist <= node.radius * 2.5f) {
                    tappedNode = node
                    break
                }
            }

            if (tappedNode != null) {
                smoothAnimateTransform(
                    targetTx = width / 2f - tappedNode.x * 2.0f,
                    targetTy = height / 2f - tappedNode.y * 2.0f,
                    targetScale = 2.0f
                )
            } else {
                smoothAnimateTransform(
                    targetTx = width / 2f,
                    targetTy = height / 2f,
                    targetScale = 1.0f
                )
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (draggedNode != null || scaleDetector.isInProgress) return false

            scroller.forceFinished(true)
            scroller.fling(
                translateX.toInt(), translateY.toInt(),
                (velocityX * 0.75f).toInt(), (velocityY * 0.75f).toInt(),
                -10000, 10000, -10000, 10000
            )
            postInvalidateOnAnimation()
            return true
        }
    })

    init {
        setBackgroundColor(context.getColor(R.color.app_window_background))
    }

    fun setData(memories: List<MemoryItem>, dagEdges: List<DagEdge>) {
        nodes.clear()
        edges.clear()

        // Draw every stored relationship, including zero-strength edges. A zero
        // score is still a real graph relationship and must not disappear.
        edges.addAll(dagEdges.filter {
            it.experienceId1.isNotBlank() && it.experienceId2.isNotBlank() &&
                it.strength.isFinite()
        })

        if (memories.isEmpty()) {
            invalidate()
            return
        }

        val centerX = 0f
        val centerY = 0f

        memories.forEachIndexed { index, memory ->
            if (index == 0) {
                nodes.add(GraphNode(memory, centerX, centerY))
            } else {
                val seedRandom = java.util.Random(memory.id.hashCode().toLong())
                val goldenAngle = 2.399963
                val angleJitter = (seedRandom.nextDouble() - 0.5) * 0.8
                val angle = index * goldenAngle + angleJitter

                val baseDistance = 180f * Math.sqrt(index.toDouble()).toFloat()
                val distanceJitter = (seedRandom.nextFloat() - 0.5f) * 120f
                val radius = baseDistance + distanceJitter

                val nx = centerX + (radius * cos(angle)).toFloat()
                val ny = centerY + (radius * sin(angle)).toFloat()
                nodes.add(GraphNode(memory, nx, ny))
            }
        }

        // The view can still be unmeasured when data arrives from an Activity's
        // onCreate. Post the reset so graph coordinates use its real size.
        post { resetViewAnimated() }
    }

    fun resetView() {
        resetViewAnimated()
    }

    fun resetViewAnimated() {
        val targetTx = if (width > 0) width / 2f else 500f
        val targetTy = if (height > 0) height / 2f else 800f
        smoothAnimateTransform(targetTx, targetTy, 1.0f)
    }

    fun zoomIn() {
        val targetScale = min(scaleFactor * 1.35f, 5.0f)
        val scaleRatio = targetScale / scaleFactor
        val focusX = width / 2f
        val focusY = height / 2f
        val targetTx = focusX - (focusX - translateX) * scaleRatio
        val targetTy = focusY - (focusY - translateY) * scaleRatio
        smoothAnimateTransform(targetTx, targetTy, targetScale)
    }

    fun zoomOut() {
        val targetScale = max(scaleFactor / 1.35f, 0.25f)
        val scaleRatio = targetScale / scaleFactor
        val focusX = width / 2f
        val focusY = height / 2f
        val targetTx = focusX - (focusX - translateX) * scaleRatio
        val targetTy = focusY - (focusY - translateY) * scaleRatio
        smoothAnimateTransform(targetTx, targetTy, targetScale)
    }

    private fun smoothAnimateTransform(targetTx: Float, targetTy: Float, targetScale: Float) {
        zoomAnimator?.cancel()
        scroller.forceFinished(true)

        val startTx = translateX
        val startTy = translateY
        val startScale = scaleFactor

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                translateX = startTx + (targetTx - startTx) * fraction
                translateY = startTy + (targetTy - startTy) * fraction
                scaleFactor = startScale + (targetScale - startScale) * fraction
                invalidate()
            }
            start()
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            translateX = scroller.currX.toFloat()
            translateY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (translateX == 0f && translateY == 0f) {
            translateX = w / 2f
            translateY = h / 2f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                zoomAnimator?.cancel()

                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y

                val worldX = (event.x - translateX) / scaleFactor
                val worldY = (event.y - translateY) / scaleFactor

                draggedNode = null
                for (node in nodes) {
                    val dist = Math.hypot((worldX - node.x).toDouble(), (worldY - node.y).toDouble()).toFloat()
                    if (dist <= node.radius * 2.5f) {
                        draggedNode = node
                        selectedNode = node
                        invalidate()
                        break
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                activePointerId = event.getPointerId(index)
                lastTouchX = event.getX(index)
                lastTouchY = event.getY(index)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)

                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    if (!scaleDetector.isInProgress) {
                        if (draggedNode != null) {
                            draggedNode!!.x += dx / scaleFactor
                            draggedNode!!.y += dy / scaleFactor
                            invalidate()
                        } else {
                            translateX += dx
                            translateY += dy
                            invalidate()
                        }
                    }

                    lastTouchX = x
                    lastTouchY = y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(newPointerIndex)
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                draggedNode = null
            }
        }

        return true
    }

    private fun handleSingleTap(screenX: Float, screenY: Float) {
        val worldX = (screenX - translateX) / scaleFactor
        val worldY = (screenY - translateY) / scaleFactor

        var tappedNode: GraphNode? = null
        for (node in nodes) {
            val dist = Math.hypot((worldX - node.x).toDouble(), (worldY - node.y).toDouble()).toFloat()
            if (dist <= node.radius * 2.5f) {
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

        drawBackgroundGrid(canvas)
        drawEdges(canvas)
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
        val nodeMap = nodes.associateBy { canonicalId(it.memory.id) }

        for (edge in edges) {
            val n1 = nodeMap[canonicalId(edge.experienceId1)]
            val n2 = nodeMap[canonicalId(edge.experienceId2)]

            if (n1 != null && n2 != null) {
                val isConnectedToSelected = selectedNode != null &&
                        (selectedNode!!.memory.id.equals(n1.memory.id, ignoreCase = true) ||
                         selectedNode!!.memory.id.equals(n2.memory.id, ignoreCase = true))

                val alpha = 255
                val thickness = if (isConnectedToSelected) 10f else (6f + (edge.strength * 5.0).toFloat())

                edgePaint.strokeWidth = thickness
                edgePaint.color = context.getColor(
                    if (isConnectedToSelected) R.color.graph_edge_selected else R.color.graph_edge
                )
                edgePaint.alpha = alpha

                canvas.drawLine(n1.x, n1.y, n2.x, n2.y, edgePaint)

                val midX = (n1.x + n2.x) / 2f
                val midY = (n1.y + n2.y) / 2f
                val strengthText = String.format("S_ij=%.3f", edge.strength)
                edgeTextPaint.color = context.getColor(R.color.graph_edge_label)
                edgeTextPaint.alpha = 255
                canvas.drawText(strengthText, midX + 8f, midY - 8f, edgeTextPaint)
            }
        }
    }

    private fun canonicalId(id: String): String = id.trim().lowercase()

    private fun drawNodes(canvas: Canvas) {
        for (node in nodes) {
            val isSelected = selectedNode != null && selectedNode!!.memory.id.equals(node.memory.id, ignoreCase = true)

            val radius = if (isSelected) node.radius * 1.3f else node.radius
            val paint = if (isSelected) nodeSelectedPaint else nodeBodyPaint

            canvas.drawCircle(node.x, node.y, radius, paint)
            canvas.drawCircle(node.x, node.y, radius + 4f, nodeRingPaint)

            val titleSnippet = if (node.memory.title.length > 20) node.memory.title.take(20) + "..." else node.memory.title

            canvas.drawText(node.memory.id, node.x + radius + 10f, node.y - 6f, nodeTextPaint)
            canvas.drawText(titleSnippet, node.x + radius + 10f, node.y + 22f, nodeSubTextPaint)
        }
    }
}
