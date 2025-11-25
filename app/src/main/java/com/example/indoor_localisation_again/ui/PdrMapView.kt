package com.example.indoor_localisation_again.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.indoor_localisation_again.pdr.Point2D
import kotlin.math.min

class PdrMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentPosition: Point2D? = null
    private var ghostPath: List<Point2D> = emptyList()
    private var anchors: List<Point2D> = emptyList()
    private var lastAnchor: Point2D? = null
    private var currentAnchor: Point2D? = null
    private var tapListener: ((Point2D) -> Unit)? = null
    private var viewport: Bounds? = null

    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        alpha = 120
    }
    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA726")
        style = Paint.Style.FILL
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 32f
    }

    fun update(
        current: Point2D?,
        ghostPath: List<Point2D>,
        anchors: List<Point2D>,
        lastAnchor: Point2D?,
        currentAnchor: Point2D?
    ) {
        this.currentPosition = current
        this.ghostPath = ghostPath
        this.anchors = anchors
        this.lastAnchor = lastAnchor
        this.currentAnchor = currentAnchor
        if (current == null && ghostPath.isEmpty() && anchors.isEmpty()) {
            viewport = null
        }
        invalidate()
    }

    fun setOnMapTapListener(listener: ((Point2D) -> Unit)?) {
        tapListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val bounds = computeBounds()
        val scale = computeScale(bounds)
        val padding = PADDING

        drawGrid(canvas)

        // Draw anchors
        anchors.forEach { anchor ->
            val (sx, sy) = worldToScreen(anchor, bounds, scale, padding)
            canvas.drawCircle(sx, sy, 10f, anchorPaint)
        }

        // Draw anchor link (last to current)
        if (lastAnchor != null && currentAnchor != null) {
            val (sx1, sy1) = worldToScreen(lastAnchor!!, bounds, scale, padding)
            val (sx2, sy2) = worldToScreen(currentAnchor!!, bounds, scale, padding)
            canvas.drawLine(sx1, sy1, sx2, sy2, anchorPaint)
        }

        // Draw ghost path (short trail)
        if (ghostPath.size > 1) {
            val p = Path()
            ghostPath.forEachIndexed { index, point ->
                val (sx, sy) = worldToScreen(point, bounds, scale, padding)
                if (index == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
            }
            canvas.drawPath(p, ghostPaint)
        }

        // Draw current position
        currentPosition?.let { pos ->
            val (sx, sy) = worldToScreen(pos, bounds, scale, padding)
            canvas.drawCircle(sx, sy, 14f, currentPaint)
        }

        if (ghostPath.isEmpty() && anchors.isEmpty()) {
            canvas.drawText("No PDR data yet", padding, padding + 32f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event?.action == MotionEvent.ACTION_UP) {
            val bounds = computeBounds()
            val scale = computeScale(bounds)
            val world = screenToWorld(event.x, event.y, bounds, scale, PADDING)
            tapListener?.invoke(world)
        }
        return true
    }

    private fun computeBounds(): Bounds {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        ghostPath.forEach { xs.add(it.x); ys.add(it.y) }
        anchors.forEach { xs.add(it.x); ys.add(it.y) }
        currentPosition?.let { xs.add(it.x); ys.add(it.y) }
        lastAnchor?.let { xs.add(it.x); ys.add(it.y) }
        currentAnchor?.let { xs.add(it.x); ys.add(it.y) }

        val minX = xs.minOrNull() ?: -5.0
        val maxX = xs.maxOrNull() ?: 5.0
        val minY = ys.minOrNull() ?: -5.0
        val maxY = ys.maxOrNull() ?: 5.0

        fun padded(min: Double, max: Double): Pair<Double, Double> {
            var lo = min
            var hi = max
            val span = hi - lo
            if (span < MIN_EXTENT) {
                val mid = (lo + hi) / 2.0
                lo = mid - MIN_EXTENT / 2.0
                hi = mid + MIN_EXTENT / 2.0
            }
            return lo to hi
        }

        val (pMinX, pMaxX) = padded(minX, maxX)
        val (pMinY, pMaxY) = padded(minY, maxY)

        val currentViewport = viewport
        val updated = if (currentViewport == null) {
            Bounds(pMinX, pMaxX, pMinY, pMaxY)
        } else {
            Bounds(
                minOf(currentViewport.minX, pMinX),
                maxOf(currentViewport.maxX, pMaxX),
                minOf(currentViewport.minY, pMinY),
                maxOf(currentViewport.maxY, pMaxY)
            )
        }
        viewport = updated
        return updated
    }

    private fun computeScale(bounds: Bounds): Float {
        val worldWidth = (bounds.maxX - bounds.minX).takeIf { it > 0.001 } ?: 1.0
        val worldHeight = (bounds.maxY - bounds.minY).takeIf { it > 0.001 } ?: 1.0
        val usableWidth = width - PADDING * 2
        val usableHeight = height - PADDING * 2
        val scaleX = usableWidth / worldWidth
        val scaleY = usableHeight / worldHeight
        return min(scaleX, scaleY).toFloat()
    }

    private fun worldToScreen(
        point: Point2D,
        bounds: Bounds,
        scale: Float,
        padding: Float
    ): Pair<Float, Float> {
        val sx = ((point.x - bounds.minX) * scale + padding).toFloat()
        // invert Y to have up as positive
        val sy = (height - padding - (point.y - bounds.minY) * scale).toFloat()
        return sx to sy
    }

    private fun screenToWorld(
        sx: Float,
        sy: Float,
        bounds: Bounds,
        scale: Float,
        padding: Float
    ): Point2D {
        val x = (sx - padding) / scale + bounds.minX
        val y = ((height - padding) - sy) / scale + bounds.minY
        return Point2D(x, y)
    }

    private fun drawGrid(canvas: Canvas) {
        val step = 80f
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }



    private data class Bounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double
    )

    companion object {
        private const val PADDING = 32f
        private const val MIN_EXTENT = 10.0
    }
}
