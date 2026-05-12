package fr.bayral.archerymonitor.core.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import kotlin.math.abs

/**
 * Service responsible for drawing the final visual composition of a frame.
 * This class isolates the drawing logic from the Compose UI component,
 * allowing it to be used in both the Live UI and for creating cached 'WYSIWYG' images.
 */
class FrameComposer {

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val outlinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.BLACK
        isAntiAlias = true
    }

    private val jointPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val circleOutlinePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        isAntiAlias = true
    }

    fun compose(
        canvas: Canvas,
        poseResult: PoseResult?,
        analysisResult: AnalysisResult?,
        transformationMatrix: Matrix
    ) {
        if (poseResult == null) return

        val mappedPoints = FloatArray(poseResult.landmarks.size * 2)
        poseResult.landmarks.forEachIndexed { index, landmark ->
            mappedPoints[index * 2] = landmark.x
            mappedPoints[(index * 2) + 1] = landmark.y
        }
        transformationMatrix.mapPoints(mappedPoints)

        fun getCoords(index: Int): Pair<Float, Float> {
            return Pair(mappedPoints[index * 2], mappedPoints[index * 2 + 1])
        }

        // Draw connections
        val connections = listOf(
            Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16),
            Pair(11, 23), Pair(12, 24), Pair(23, 24),
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(27, 31),
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(28, 32)
        )

        connections.forEach { (start, end) ->
            if (start < poseResult.landmarks.size && end < poseResult.landmarks.size) {
                val (x1, y1) = getCoords(start)
                val (x2, y2) = getCoords(end)
                
                val color = analysisResult?.jointColors?.get(start) ?: Color.YELLOW
                
                linePaint.color = color
                canvas.drawLine(x1, y1, x2, y2, outlinePaint)
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        // Draw joints
        poseResult.landmarks.forEachIndexed { index, _ ->
            val (x, y) = getCoords(index)
            val jointColor = analysisResult?.jointColors?.get(index) ?: Color.YELLOW
            
            canvas.drawCircle(x, y, 8f, circleOutlinePaint)
            jointPaint.color = jointColor
            canvas.drawCircle(x, y, 4f, jointPaint)
        }
    }
}
