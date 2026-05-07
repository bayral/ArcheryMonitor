package fr.bayral.archerymonitor.core.utils

import fr.bayral.archerymonitor.core.interfaces.PoseResult

/**
 * Utility class for common pose analysis calculations.
 */
object PoseUtils {

    /**
     * Checks if the archer is facing the camera by analyzing the presence/visibility
     * of key facial landmarks (Nose=0, LeftEye=1, RightEye=2).
     */
    fun isFacingCamera(pose: PoseResult): Boolean {
        // Face landmarks are indices 0, 1, 2, 3, 4, 5, 6
        val faceIndices = listOf(0, 1, 2, 3, 4, 5, 6)
        val faceLandmarks = pose.landmarks.filterIndexed { index, _ -> index in faceIndices }

        // Presence threshold (0..1)
        val presenceThreshold = 0.5f
        return faceLandmarks.any { it.presence > presenceThreshold }
    }
}
