package fr.bayral.archerymonitor.ui.components

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.PoseResult

/**
 * High-contrast skeleton overlay for outdoor archery posture review.
 *
 * This component draws the joints and bones detected by the AI using a neon yellow
 * center and black outlines to ensure maximum visibility in sunlight.
 *
 * @param poseResult The current synchronized pose to display.
 * @param analysisResult The current analysis result for colorization.
 * @param transformationMatrix Matrix used to map 0..1 AI coordinates to screen pixels.
 * @param modifier Modifier for the canvas layout.
 */
@Composable
fun SkeletonOverlay(
    poseResult: PoseResult?,
    analysisResult: AnalysisResult?,
    transformationMatrix: Matrix,
    modifier: Modifier = Modifier,
) {
    if (poseResult == null) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Map normalized landmarks to display pixels
        val mappedPoints = FloatArray(poseResult.landmarks.size * 2)
        poseResult.landmarks.forEachIndexed { index, landmark ->
            mappedPoints[index * 2] = landmark.x
            mappedPoints[(index * 2) + 1] = landmark.y
        }

        transformationMatrix.mapPoints(mappedPoints)

        /** Helper to retrieve pixel coordinates for a specific landmark index. */
        fun getOffset(index: Int): Offset {
            return Offset(mappedPoints[index * 2], mappedPoints[index * 2 + 1])
        }

        // Define body segment connections (MediaPipe index mapping)
        val connections = listOf(
            Pair(11, 12), // Shoulders
            Pair(11, 13), Pair(13, 15), // Left arm
            Pair(12, 14), Pair(14, 16), // Right arm
            Pair(11, 23), Pair(12, 24), // Torso
            Pair(23, 24), // Hips
            Pair(23, 25), Pair(25, 27), // Left leg
            Pair(24, 26), Pair(26, 28)  // Right leg
        )

        // Draw connections with double-layer (outline + neon center)
        connections.forEach { (start, end) ->
            if (start < poseResult.landmarks.size && end < poseResult.landmarks.size) {
                val p1 = getOffset(start)
                val p2 = getOffset(end)
                
                // If analysis provides a color for joints, use it, else default to Yellow
                val color = analysisResult?.jointColors?.get(start)?.let { Color(it) } ?: Color.Yellow

                drawLine(color = Color.Black, start = p1, end = p2, strokeWidth = 10f)
                drawLine(color = color, start = p1, end = p2, strokeWidth = 4f)
            }
        }

        // Draw individual joint circles
        poseResult.landmarks.forEachIndexed { index, _ ->
            if (index in 11..28) { // Focus on major body joints
                val center = getOffset(index)
                val color = analysisResult?.jointColors?.get(index)?.let { Color(it) } ?: Color.Yellow
                drawCircle(Color.Black, radius = 8f, center = center)
                drawCircle(color, radius = 4f, center = center)
            }
        }
    }
}
