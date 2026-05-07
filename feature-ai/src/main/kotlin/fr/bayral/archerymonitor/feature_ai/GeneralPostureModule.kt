package fr.bayral.archerymonitor.feature_ai

import fr.bayral.archerymonitor.core.interfaces.*
import fr.bayral.archerymonitor.core.utils.PoseUtils
import kotlin.math.atan2
import kotlin.math.abs

/**
 * Advanced posture analysis module focusing on:
 * - Shoulder horizontal alignment.
 * - Arm alignment (Bow arm & Draw arm).
 * - Vertical body axis stability.
 *
 * This module dynamically adapts to the archer's laterality and orientation 
 * (facing or back to camera).
 */
class GeneralPostureModule : IPostureModule {
    override val labelResId: Int = fr.bayral.archerymonitor.resources.R.string.label_posture_general

    override val capabilities = ModuleCapabilities(
        supportedBowTypes = setOf(BowType.RECURVE, BowType.BAREBOW)
    )

    override fun analyze(pose: PoseResult, settings: ArcherySettings): AnalysisResult {
        val jointColors = mutableMapOf<Int, Long>()
        val segments = mutableListOf<AnalysisSegment>()

        // Ensure we have enough landmarks for basic analysis (up to elbows/hips)
        if (pose.landmarks.size <= 24) return AnalysisResult(jointColors, emptyList(), 0f)

        // 1. Determine effective laterality based on orientation
        // If back to camera, left/right are inverted in image space
        val facingCamera = PoseUtils.isFacingCamera(pose)
        val effectiveLaterality = if (facingCamera) {
            settings.laterality
        } else {
            if (settings.laterality == Laterality.LEFT_HANDED) Laterality.RIGHT_HANDED
            else Laterality.LEFT_HANDED
        }

        // 2. Define indices based on laterality (MediaPipe: Left=11/13/23, Right=12/14/24)
        val (bowArmShoulder, drawArmShoulder) = if (effectiveLaterality == Laterality.RIGHT_HANDED) 11 to 12 else 12 to 11
        val (bowArmElbow, drawArmElbow) = if (effectiveLaterality == Laterality.RIGHT_HANDED) 13 to 14 else 14 to 13

        val ls = pose.landmarks[bowArmShoulder]
        val rs = pose.landmarks[drawArmShoulder]
        val lh = pose.landmarks[23]
        val rh = pose.landmarks[24]

        // --- HORIZONTAL BAR (Shoulders alignment) ---
        val dy = rs.y - ls.y
        val dx = rs.x - ls.x
        val shoulderAngle = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI

        // Target: Shoulders horizontal (Green if < 5 degrees)
        val shoulderColor = if (abs(shoulderAngle) < 5.0) 0xFF00FF00L else 0xFFFF0000L

        segments.add(AnalysisSegment(
            startLandmarkIndex = bowArmShoulder,
            endLandmarkIndex = drawArmShoulder,
            color = shoulderColor
        ))

        // --- ARM ALIGNMENT (Shoulder to Elbow) ---
        var totalArmDeviance = 0.0
        val le = pose.landmarks[bowArmElbow]
        val re = pose.landmarks[drawArmElbow]

        // Analyze bow arm (Target: perfectly aligned with shoulders)
        val dyL = le.y - ls.y
        val dxL = le.x - ls.x
        val bowArmAngle = atan2(dyL.toDouble(), dxL.toDouble()) * 180 / Math.PI
        val targetBow = if (shoulderAngle > 0) shoulderAngle - 180.0 else shoulderAngle + 180.0
        val bowArmDeviance = abs(bowArmAngle - targetBow)

        // Analyze draw arm (Target: aligned with shoulder line)
        val dyR = re.y - rs.y
        val dxR = re.x - rs.x
        val drawArmAngle = atan2(dyR.toDouble(), dxR.toDouble()) * 180 / Math.PI
        val drawArmDeviance = abs(drawArmAngle - shoulderAngle)

        // Tolerance: Barebow requires slightly more flexibility (vertical height)
        val isBarebow = settings.bowType == BowType.BAREBOW
        val heightTolerance = if (isBarebow) 25.0 else 15.0

        val armColor = if (shoulderColor == 0xFF00FF00L && bowArmDeviance < heightTolerance && drawArmDeviance < 15.0) 0xFF00FF00L else 0xFFFF0000L

        segments.add(AnalysisSegment(startLandmarkIndex = bowArmShoulder, endLandmarkIndex = bowArmElbow, color = armColor))
        segments.add(AnalysisSegment(startLandmarkIndex = drawArmShoulder, endLandmarkIndex = drawArmElbow, color = armColor))

        jointColors[bowArmElbow] = armColor
        jointColors[drawArmElbow] = armColor
        totalArmDeviance = (bowArmDeviance + drawArmDeviance) / 2.0

        // --- VERTICAL AXIS (Body spine) ---
        val midShoulderX = (ls.x + rs.x) / 2f
        val midShoulderY = (ls.y + rs.y) / 2f
        val midHipX = (lh.x + rh.x) / 2f
        val midHipY = (lh.y + rh.y) / 2f

        val axisDeviance = abs(midShoulderX - midHipX) * 100 // Scale to 100 for easier thresholding
        val axisColor = if (axisDeviance < 5.0) 0xFF00FF00L else 0xFFFF0000L

        segments.add(AnalysisSegment(
            startCustom = Landmark(midShoulderX, midShoulderY, 0f, 1f, 1f),
            endCustom = Landmark(midHipX, midHipY, 0f, 1f, 1f),
            color = axisColor
        ))

        jointColors[bowArmShoulder] = shoulderColor
        jointColors[drawArmShoulder] = shoulderColor

        // --- SCORING (0.0 to 1.0) ---
        val sScore = (1.0 - (abs(shoulderAngle) / 15.0).coerceIn(0.0, 1.0)) * 0.4
        val aScore = (1.0 - (totalArmDeviance / 30.0).coerceIn(0.0, 1.0)) * 0.4
        val vScore = (1.0 - (axisDeviance / 10.0).coerceIn(0.0, 1.0)) * 0.2

        val finalScore = (sScore + aScore + vScore).toFloat()

        return AnalysisResult(jointColors, segments, finalScore)
    }
}
