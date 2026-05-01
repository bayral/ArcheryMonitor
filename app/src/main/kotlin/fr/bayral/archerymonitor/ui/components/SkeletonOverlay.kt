package fr.bayral.archerymonitor.ui.components

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import fr.bayral.archerymonitor.core.interfaces.Landmark
import fr.bayral.archerymonitor.core.interfaces.PoseResult

@Composable
fun SkeletonOverlay(
    poseResult: PoseResult?,
    transformationMatrix: Matrix,
    modifier: Modifier = Modifier
) {
    if (poseResult == null) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val mappedPoints = FloatArray(poseResult.landmarks.size * 2)
        poseResult.landmarks.forEachIndexed { index, landmark ->
            mappedPoints[index * 2] = landmark.x
            mappedPoints[index * 2 + 1] = landmark.y
        }

        transformationMatrix.mapPoints(mappedPoints)

        // Helper to get offset from landmark index
        fun getOffset(index: Int): Offset {
            return Offset(mappedPoints[index * 2], mappedPoints[index * 2 + 1])
        }

        // Define connections (Indices based on MediaPipe Pose Landmarker)
        val connections = listOf(
            Pair(11, 12), // Shoulders
            Pair(11, 13), Pair(13, 15), // Left arm
            Pair(12, 14), Pair(14, 16), // Right arm
            Pair(11, 23), Pair(12, 24), // Torso
            Pair(23, 24), // Hips
            Pair(23, 25), Pair(25, 27), // Left leg
            Pair(24, 26), Pair(26, 28)  // Right leg
        )

        connections.forEach { (start, end) ->
            if (start < poseResult.landmarks.size && end < poseResult.landmarks.size) {
                val p1 = getOffset(start)
                val p2 = getOffset(end)

                // High-visibility: Black outline
                drawLine(
                    color = Color.Black,
                    start = p1,
                    end = p2,
                    strokeWidth = 10f
                )
                // High-visibility: Neon Yellow center
                drawLine(
                    color = Color.Yellow,
                    start = p1,
                    end = p2,
                    strokeWidth = 4f
                )
            }
        }

        // Draw joints
        poseResult.landmarks.forEachIndexed { index, _ ->
            if (index in 11..28) { // Only major body joints
                val center = getOffset(index)
                drawCircle(Color.Black, radius = 8f, center = center)
                drawCircle(Color.Yellow, radius = 4f, center = center)
            }
        }
    }
}
