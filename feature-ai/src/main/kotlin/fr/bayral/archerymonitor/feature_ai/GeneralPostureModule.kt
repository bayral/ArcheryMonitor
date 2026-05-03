package fr.bayral.archerymonitor.feature_ai

import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.IPostureModule
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import kotlin.math.atan2
import kotlin.math.abs

/**
 * Basic posture analysis module focusing on:
 * - Shoulder horizontal alignment.
 * - Vertical body axis stability.
 */
class GeneralPostureModule : IPostureModule {
    // We cannot use R.string here because R is generated in the app module 
    // and not always accessible to feature-ai. 
    // For now, we return a hardcoded placeholder or 0.
    // NOTE: This module cannot access app's R.string. 
    // Using a placeholder; labels should ideally be handled by the UI layer or a shared res module.
    override val labelResId: Int = fr.bayral.archerymonitor.resources.R.string.label_posture_general
 

    override fun analyze(pose: PoseResult): AnalysisResult {
        val jointColors = mutableMapOf<Int, Long>()

        if (pose.landmarks.size <= 12) return AnalysisResult(jointColors, 0f)

        // Landmarks: 11 (Left Shoulder), 12 (Right Shoulder)
        val leftShoulder = pose.landmarks[11]
        val rightShoulder = pose.landmarks[12]

        // Calculate shoulder tilt angle
        val dy = rightShoulder.y - leftShoulder.y
        val dx = rightShoulder.x - leftShoulder.x
        val angle = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI

        // Target: Shoulders horizontal (angle near 0)
        // threshold 5 degrees
        // Green: 0xFF00FF00, Red: 0xFFFF0000
        val shoulderColor = if (abs(angle) < 5.0) 0xFF00FF00L else 0xFFFF0000L

        jointColors[11] = shoulderColor
        jointColors[12] = shoulderColor

        // Simple score: 1.0 if perfect, decreases with tilt
        val score = (1.0 - (abs(angle) / 45.0).coerceIn(0.0, 1.0)).toFloat()

        return AnalysisResult(jointColors, score)
    }
}
