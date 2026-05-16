package fr.bayral.archerymonitor.core.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.PoseResult

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
        transformationMatrix: Matrix,
        isCalibrationMode: Boolean = false,
        calibrationText: String? = null
    ) {
        if (isCalibrationMode) {
            drawCalibrationGuide(canvas, calibrationText)
            return
        }

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
                
                val color = analysisResult?.jointColors?.get(start)?.toInt() ?: Color.YELLOW

                linePaint.color = color
                canvas.drawLine(x1, y1, x2, y2, outlinePaint)
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        // Draw joints
        poseResult.landmarks.forEachIndexed { index, _ ->
            val (x, y) = getCoords(index)
            val jointColor = analysisResult?.jointColors?.get(index)?.toInt() ?: Color.YELLOW

            canvas.drawCircle(x, y, 8f, circleOutlinePaint)
            jointPaint.color = jointColor
            canvas.drawCircle(x, y, 4f, jointPaint)
        }
    }

    private fun drawCalibrationGuide(canvas: Canvas, text: String?) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        
        linePaint.color = Color.CYAN
        linePaint.alpha = 100
        linePaint.strokeWidth = 4f
        linePaint.style = Paint.Style.STROKE

        // 1. Vertical Center Line
        canvas.drawLine(w / 2, 0f, w / 2, h, linePaint)

        // 2. Draw Archer Silhouette (Stylized)
        val centerX = w / 2
        val centerY = h * 0.45f
        val headRadius = h * 0.05f
        val shoulderWidth = w * 0.25f
        val torsoHeight = h * 0.3f

        // Head
        canvas.drawCircle(centerX, centerY - headRadius * 1.5f, headRadius, linePaint)
        
        // Torso & Shoulders
        canvas.drawLine(centerX - shoulderWidth / 2, centerY, centerX + shoulderWidth / 2, centerY, linePaint)
        canvas.drawLine(centerX, centerY, centerX, centerY + torsoHeight, linePaint)
        
        // Arms
        canvas.drawLine(centerX - shoulderWidth / 2, centerY, centerX - shoulderWidth * 1.2f, centerY, linePaint)
        canvas.drawLine(centerX + shoulderWidth / 2, centerY, centerX + shoulderWidth * 0.8f, centerY - headRadius, linePaint)

        // 3. Shoulder target zones
        linePaint.alpha = 180
        linePaint.strokeWidth = 2f
        val boxSize = headRadius * 0.8f
        canvas.drawRect(centerX - shoulderWidth / 2 - boxSize, centerY - boxSize, centerX - shoulderWidth / 2 + boxSize, centerY + boxSize, linePaint)
        canvas.drawRect(centerX + shoulderWidth / 2 - boxSize, centerY - boxSize, centerX + shoulderWidth / 2 + boxSize, centerY + boxSize, linePaint)

        // 4. Ground/Feet guide
        canvas.drawLine(centerX - shoulderWidth, centerY + torsoHeight + h * 0.2f, centerX + shoulderWidth, centerY + torsoHeight + h * 0.2f, linePaint)

        // Text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            isAntiAlias = true
        }
        text?.let { 
            val textWidth = textPaint.measureText(it)
            canvas.drawText(it, (w - textWidth) / 2, 120f, textPaint) 
        }
    }
}
