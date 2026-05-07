package fr.bayral.archerymonitor.ui.components

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import kotlin.math.abs

/**
 * High-contrast skeleton overlay for outdoor archery posture review.
 *
 * This component draws the joints and bones detected by the AI using a neon yellow
 * center and black outlines to ensure maximum visibility in sunlight. It now also
 * draws facial landmarks and an oval around the head.
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
    Canvas(modifier = modifier.fillMaxSize()) {
        if (poseResult == null) return@Canvas

        // Map normalized landmarks to display pixels
        val mappedPoints = FloatArray(poseResult.landmarks.size * 2)
        poseResult.landmarks.forEachIndexed { index, landmark ->
            mappedPoints[index * 2] = landmark.x
            mappedPoints[(index * 2) + 1] = landmark.y
        }

        transformationMatrix.mapPoints(mappedPoints)

        /** Helper to retrieve pixel coordinates for a specific landmark index. */
        fun getOffset(index: Int): Offset {
            // Ensure index is within bounds to prevent crashes if poseResult.landmarks is shorter than expected.
            if (index * 2 + 1 < mappedPoints.size) {
                return Offset(mappedPoints[index * 2], mappedPoints[index * 2 + 1])
            }
            // Return a default offset or handle error if index is out of bounds
            // For now, returning (0,0) which might draw points at the top-left if index is too high.
            // A more robust solution might involve checking poseResult.landmarks.size before calling getOffset.
            return Offset.Zero 
        }

        // Define body segment connections (MediaPipe index mapping)
        val connections = listOf(
            Pair(11, 12), // Shoulders
            Pair(11, 13), Pair(13, 15), // Left arm
            Pair(12, 14), Pair(14, 16), // Right arm
            Pair(11, 23), Pair(12, 24), // Torso
            Pair(23, 24), // Hips
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(27, 31), // Left leg & foot
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(28, 32)  // Right leg & foot
        )

        // Draw connections with double-layer (outline + neon center)
        connections.forEach { (start, end) ->
            // Check if both start and end landmarks are within the detected landmarks' range
            if (start < poseResult.landmarks.size && end < poseResult.landmarks.size) {
                val p1 = getOffset(start)
                val p2 = getOffset(end)
                
                // If analysis provides a color for joints, use it, else default to Yellow
                // Note: analysisResult?.jointColors might only have colors for specific joints.
                // We might need to fallback to a default color for connections if specific joint colors aren't found.
                val color = analysisResult?.jointColors?.get(start)?.let { Color(it) } ?: Color.Yellow

                drawLine(color = Color.Black, start = p1, end = p2, strokeWidth = 10f)
                drawLine(color = color, start = p1, end = p2, strokeWidth = 4f)
            }
        }

        // Draw facial landmarks and head oval logic (if landmarks available)
        // ... (existing code for head)

        // DRAW ANALYSIS SEGMENTS (New logic)
        // These are custom segments provided by the analysis module (e.g., body axis, arm alignment)
        analysisResult?.segments?.forEach { segment ->
            val p1: Offset
            val p2: Offset

            val startC = segment.startCustom
            val endC = segment.endCustom

            if (startC != null && endC != null) {
                // Map custom points (like mid-shoulder or mid-hip) using the transformation matrix
                val pts = floatArrayOf(
                    startC.x, startC.y,
                    endC.x, endC.y
                )
                transformationMatrix.mapPoints(pts)
                p1 = Offset(pts[0], pts[1])
                p2 = Offset(pts[2], pts[3])
            } else if (segment.startLandmarkIndex != -1 && segment.endLandmarkIndex != -1) {
                // Use existing mapped landmark points
                if (segment.startLandmarkIndex < poseResult.landmarks.size && 
                    segment.endLandmarkIndex < poseResult.landmarks.size) {
                    p1 = getOffset(segment.startLandmarkIndex)
                    p2 = getOffset(segment.endLandmarkIndex)
                } else return@forEach
            } else return@forEach

            val color = Color(segment.color)
            // Draw analysis segments with thicker lines to stand out from the base skeleton
            drawLine(color = Color.Black, start = p1, end = p2, strokeWidth = 14f)
            drawLine(color = color, start = p1, end = p2, strokeWidth = 8f)
        }

        // Draw individual joint circles for all detected landmarks
        poseResult.landmarks.forEachIndexed { index, _ ->
            val center = getOffset(index)
            // Use specific color if available from analysis, otherwise default to Yellow
            val jointColor = analysisResult?.jointColors?.get(index)?.let { Color(it) } ?: Color.Yellow
            
            // Draw black outline circle
            drawCircle(Color.Black, radius = 8f, center = center)
            // Draw colored inner circle
            drawCircle(jointColor, radius = 4f, center = center)
        }

        // Draw head oval if landmarks 0 (nose), 7 (left ear), and 8 (right ear) are available
        if (poseResult.landmarks.size > 8) { // Ensure we have landmarks up to index 8
            val leftEarOffset = getOffset(7)
            val rightEarOffset = getOffset(8)
            val noseOffset = getOffset(0)

            // Calculate head center and dimensions based on ears and nose
            // Center X is the midpoint between the ears.
            val headCenterX = (leftEarOffset.x + rightEarOffset.x) / 2f
            // Center Y is estimated as the midpoint between the nose's Y and the ears' average Y.
            // This places the oval vertically centered between the nose and the ears.
            val headCenterY = (noseOffset.y + (leftEarOffset.y + rightEarOffset.y) / 2f) / 2f

            // Head width is the distance between the ears.
            val headWidth = abs(rightEarOffset.x - leftEarOffset.x)
            // Head height is estimated as a ratio of the width. 1.2 is an approximation, can be tuned.
            val headHeight = headWidth * 1.2f 

            // Define oval bounds (top-left corner and size)
            val ovalLeft = headCenterX - headWidth / 2f
            val ovalTop = headCenterY - headHeight / 2f
            val ovalRight = headCenterX + headWidth / 2f
            val ovalBottom = headCenterY + headHeight / 2f

            // Define colors for the head oval. Using Gray for contrast.
            val ovalOutlineColor = Color.Gray
            val ovalFillColor = Color.Gray.copy(alpha = 0.3f) // Semi-transparent fill for better visibility

            // Draw the head oval outline
            drawOval(
                color = ovalOutlineColor,
                style = Stroke(width = 6f), // Outline thickness
                topLeft = Offset(ovalLeft, ovalTop),
                size = androidx.compose.ui.geometry.Size(headWidth, headHeight)
            )
            // Draw the head oval fill
            drawOval(
                color = ovalFillColor,
                topLeft = Offset(ovalLeft, ovalTop),
                size = androidx.compose.ui.geometry.Size(headWidth, headHeight)
            )
        }
    }
}