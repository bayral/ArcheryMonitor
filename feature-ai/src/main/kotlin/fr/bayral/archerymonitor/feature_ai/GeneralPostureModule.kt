package fr.bayral.archerymonitor.feature_ai

import fr.bayral.archerymonitor.core.interfaces.*
import fr.bayral.archerymonitor.core.utils.PoseUtils
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

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

    private var previousPose: PoseResult? = null
    private var smoothedScore = 0f
    private var stabilityFactor = INITIAL_STABILITY
    
    // Score history to capture pre-release posture (sliding window)
    private val scoreHistory = mutableListOf<Float>()

    // Internal state for release detection and freezing
    private var releaseCount = 0
    private var freezeScoreBase = 0f // Score before bonuses
    private var dynamismBonus = 0f
    private var followThroughBonus = 0f
    private var isFollowThroughActive = false
    private var followThroughEndTime = 0L
    private var initialBowElbowY = 0f

    private var freezeUntil = 0L

    private fun gaussianScore(error: Double, sigma: Double): Double {
        return exp(-(error.pow(2.0)) / (2.0 * sigma.pow(2.0)))
    }

    override fun analyze(pose: PoseResult, settings: ArcherySettings): AnalysisResult {
        val currentTime = System.currentTimeMillis()
        val jointColors = mutableMapOf<Int, Long>()
        val segments = mutableListOf<AnalysisSegment>()

        // Ensure we have enough landmarks for analysis
        if (pose.landmarks.size <= MIN_LANDMARKS_REQUIRED) {
            return AnalysisResult(jointColors, emptyList(), 0f, releaseCount)
        }

        // 0. Compensation for device tilt
        val tiltRad = Math.toRadians(settings.deviceTilt.toDouble())
        val cosT = Math.cos(tiltRad).toFloat()
        val sinT = Math.sin(tiltRad).toFloat()

        fun rotatePoint(x: Float, y: Float): Pair<Float, Float> {
            val dx = x - 0.5f
            val dy = y - 0.5f
            val rx = dx * cosT - dy * sinT + 0.5f
            val ry = dx * sinT + dy * cosT + 0.5f
            return rx to ry
        }

        fun rotateLandmark(l: Landmark): Landmark {
            val (rx, ry) = rotatePoint(l.x, l.y)
            return l.copy(x = rx, y = ry)
        }

        val rotatedLandmarks = pose.landmarks.map { rotateLandmark(it) }
        val compensatedPose = pose.copy(landmarks = rotatedLandmarks)

        // 1. Determine effective laterality based on camera orientation
        val facingCamera = PoseUtils.isFacingCamera(compensatedPose)
        val effectiveLaterality = if (facingCamera) {
            settings.laterality
        } else {
            if (settings.laterality == Laterality.LEFT_HANDED) Laterality.RIGHT_HANDED
            else Laterality.LEFT_HANDED
        }

        // 2. Map landmark indices based on laterality
        val (bowArmShoulder, drawArmShoulder) = if (effectiveLaterality == Laterality.RIGHT_HANDED) {
            L_SHOULDER to R_SHOULDER
        } else {
            R_SHOULDER to L_SHOULDER
        }
        
        val (bowArmElbow, drawArmElbow) = if (effectiveLaterality == Laterality.RIGHT_HANDED) {
            L_ELBOW to R_ELBOW
        } else {
            R_ELBOW to L_ELBOW
        }

        val ls = compensatedPose.landmarks[bowArmShoulder]
        val rs = compensatedPose.landmarks[drawArmShoulder]
        val lh = compensatedPose.landmarks[L_HIP]
        val rh = compensatedPose.landmarks[R_HIP]
        val le = compensatedPose.landmarks[bowArmElbow]
        val re = compensatedPose.landmarks[drawArmElbow]

        // --- RELEASE DETECTION & FOLLOW-THROUGH MONITORING ---
        var isReleaseEvent = false
        
        // 1. Check for new release
        previousPose?.let { prev ->
            val p1 = prev.landmarks[drawArmElbow]
            val p2 = compensatedPose.landmarks[drawArmElbow]
            
            // USE Pose timestamps for velocity calculation (pixels per ms)
            val timeDiff = (pose.timestamp - prev.timestamp).toDouble()
            val dist = Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0))
            
            // Normalize velocity to a standard frame rate (e.g. 33ms per frame)
            // If timeDiff is 0 (first frame or bug), velocity is 0
            val velocity = if (timeDiff > 0) (dist / timeDiff) * 33.0 else 0.0
            
            val isDrawn = abs(re.x - rs.x) > MIN_DRAW_DISTANCE
            
            if (velocity > RELEASE_VELOCITY_THRESHOLD && currentTime > freezeUntil && stabilityFactor > STABILITY_THRESHOLD_FOR_RELEASE && isDrawn) {
                releaseCount++
                isReleaseEvent = true
                
                // Dynamism Bonus
                val dynamismRange = DYNAMIC_RELEASE_VELOCITY_TARGET - RELEASE_VELOCITY_THRESHOLD
                dynamismBonus = (((velocity - RELEASE_VELOCITY_THRESHOLD) / dynamismRange).coerceIn(0.0, 1.0) * MAX_RELEASE_BONUS).toFloat()
                
                // Posture Base Score (Peak of last 200ms)
                freezeScoreBase = if (scoreHistory.isNotEmpty()) scoreHistory.maxOrNull() ?: smoothedScore else smoothedScore
                
                // Initialize Follow-Through
                isFollowThroughActive = true
                followThroughBonus = MAX_FOLLOW_THROUGH_BONUS
                followThroughEndTime = currentTime + FOLLOW_THROUGH_DURATION_MS
                initialBowElbowY = le.y
                
                freezeUntil = currentTime + FREEZE_DURATION_MS
            }
        }

        // 2. Monitor existing follow-through
        if (isFollowThroughActive) {
            if (currentTime < followThroughEndTime) {
                // If bow arm drops significantly, lose the bonus
                if (le.y > initialBowElbowY + FOLLOW_THROUGH_DROP_THRESHOLD) {
                    followThroughBonus = 0f
                    isFollowThroughActive = false // Locked for this shot
                }
            } else {
                isFollowThroughActive = false // Duration completed, bonus locked
            }
        }

        // --- STABILITY DETECTION ---
        val movement = previousPose?.let { prev ->
            val indices = STABILITY_KEY_POINTS
            indices.sumOf { i ->
                val p1 = prev.landmarks[i]
                val p2 = compensatedPose.landmarks[i]
                abs(p1.x - p2.x).toDouble() + abs(p1.y - p2.y).toDouble()
            } / indices.size
        } ?: STABILITY_INITIAL_MOVEMENT

        previousPose = compensatedPose

        // Smoothly update stability factor
        if (movement < STABILITY_MOVEMENT_THRESHOLD) {
            stabilityFactor = (stabilityFactor + STABILITY_GAIN).coerceAtMost(1.0f)
        } else {
            stabilityFactor = (stabilityFactor - STABILITY_DECAY).coerceAtLeast(MIN_STABILITY_FACTOR)
        }

        // --- HORIZONTAL BAR (Shoulders alignment) ---
        val dy = rs.y - ls.y
        val dx = rs.x - ls.x
        val shoulderAngle = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI
        val shoulderColor = if (abs(shoulderAngle) < SHOULDER_TOLERANCE_DEG) AnalysisColors.GREEN else AnalysisColors.RED
        
        segments.add(AnalysisSegment(startLandmarkIndex = bowArmShoulder, endLandmarkIndex = drawArmShoulder, color = shoulderColor))

        // --- ARM ALIGNMENT (Shoulder to Elbow) ---
        // le is already declared above for follow-through monitoring
        val dyL = le.y - ls.y
        val dxL = le.x - ls.x
        val bowArmAngle = atan2(dyL.toDouble(), dxL.toDouble()) * 180 / Math.PI
        
        // Target bow arm is 180 degrees opposite to shoulder line
        val targetBow = if (shoulderAngle > 0) shoulderAngle - 180.0 else shoulderAngle + 180.0
        val bowArmDeviance = abs(bowArmAngle - targetBow)

        val dyR = re.y - rs.y
        val dxR = re.x - rs.x
        val drawArmAngle = atan2(dyR.toDouble(), dxR.toDouble()) * 180 / Math.PI
        val drawArmDeviance = abs(drawArmAngle - shoulderAngle)

        // Tolerance adaptation based on bow type
        val heightTolerance = if (settings.bowType == BowType.BAREBOW) BAREBOW_TOLERANCE_DEG else RECURVE_TOLERANCE_DEG
        
        val armColor = if (shoulderColor == AnalysisColors.GREEN && 
                           bowArmDeviance < heightTolerance && 
                           drawArmDeviance < ARM_ALIGNMENT_TOLERANCE_DEG) {
            AnalysisColors.GREEN
        } else {
            AnalysisColors.RED
        }

        segments.add(AnalysisSegment(startLandmarkIndex = bowArmShoulder, endLandmarkIndex = bowArmElbow, color = armColor))
        segments.add(AnalysisSegment(startLandmarkIndex = drawArmShoulder, endLandmarkIndex = drawArmElbow, color = armColor))

        jointColors[bowArmElbow] = armColor
        jointColors[drawArmElbow] = armColor
        val totalArmDeviance = (bowArmDeviance + drawArmDeviance) / 2.0

        // --- VERTICAL AXIS (Body spine) ---
        val midShoulderX = (ls.x + rs.x) / 2f
        val midShoulderY = (ls.y + rs.y) / 2f
        val midHipX = (lh.x + rh.x) / 2f
        val midHipY = (lh.y + rh.y) / 2f
        
        val axisDeviance = abs(midShoulderX - midHipX) * 100 // Normalized to 100
        val axisColor = if (axisDeviance < VERTICAL_AXIS_TOLERANCE) AnalysisColors.GREEN else AnalysisColors.RED
        
        segments.add(AnalysisSegment(
            startCustom = Landmark(midShoulderX, midShoulderY, 0f, 1f, 1f),
            endCustom = Landmark(midHipX, midHipY, 0f, 1f, 1f),
            color = axisColor
        ))

        jointColors[bowArmShoulder] = shoulderColor
        jointColors[drawArmShoulder] = shoulderColor

        // --- SCORING (Weighted Gaussian) ---
        val sScore = gaussianScore(abs(shoulderAngle), SIGMA_SHOULDER) * WEIGHT_SHOULDER
        val aScore = gaussianScore(totalArmDeviance, SIGMA_ARMS) * WEIGHT_ARMS
        val vScore = gaussianScore(axisDeviance.toDouble(), SIGMA_VERTICAL_AXIS) * WEIGHT_VERTICAL_AXIS

        val rawScore = (sScore + aScore + vScore).toFloat()
        val currentScore = rawScore * stabilityFactor
        
        // Final smoothing (Exponential Moving Average)
        smoothedScore = if (smoothedScore == 0f) currentScore else (smoothedScore * (1f - SCORE_EMA_ALPHA) + currentScore * SCORE_EMA_ALPHA)

        // Update Score History (Sliding window of ~200ms)
        scoreHistory.add(smoothedScore)
        if (scoreHistory.size > SCORE_HISTORY_SIZE) scoreHistory.removeAt(0)

        // Handle Score Freeze during release
        val finalScore = if (currentTime < freezeUntil) {
            (freezeScoreBase + dynamismBonus + followThroughBonus).coerceAtMost(1.0f)
        } else {
            smoothedScore
        }

        return AnalysisResult(
            jointColors = jointColors,
            segments = segments,
            score = finalScore,
            releaseCount = releaseCount,
            isReleaseEvent = isReleaseEvent
        )
    }

    companion object {
        // Landmark Indices (MediaPipe Topology)
        private const val L_SHOULDER = 11
        private const val R_SHOULDER = 12
        private const val L_ELBOW = 13
        private const val R_ELBOW = 14
        private const val L_HIP = 23
        private const val R_HIP = 24
        private const val MIN_LANDMARKS_REQUIRED = 24

        // Detection & Stability Constants
        private const val FREEZE_DURATION_MS = 3000L
        private const val RELEASE_VELOCITY_THRESHOLD = 0.05 // Lowered to support static releases
        private const val MIN_DRAW_DISTANCE = 0.15 // Increased: elbow must be further back
        private const val STABILITY_THRESHOLD_FOR_RELEASE = 0.7f
        private const val DYNAMIC_RELEASE_VELOCITY_TARGET = 0.20 // Velocity for max bonus
        private const val MAX_RELEASE_BONUS = 0.10f // Up to 10% bonus for dynamic release
        private const val SCORE_HISTORY_SIZE = 5 // ~200ms window
        
        private const val FOLLOW_THROUGH_DURATION_MS = 1500L
        private const val FOLLOW_THROUGH_DROP_THRESHOLD = 0.05
        private const val MAX_FOLLOW_THROUGH_BONUS = 0.10f
        
        private val STABILITY_KEY_POINTS = listOf(11, 12, 13, 14, 23, 24)
        private const val INITIAL_STABILITY = 0.5f
        private const val MIN_STABILITY_FACTOR = 0.5f
        private const val STABILITY_MOVEMENT_THRESHOLD = 0.006
        private const val STABILITY_GAIN = 0.1f
        private const val STABILITY_DECAY = 0.15f
        private const val STABILITY_INITIAL_MOVEMENT = 0.05

        // Thresholds & Tolerances
        private const val SHOULDER_TOLERANCE_DEG = 5.0
        private const val ARM_ALIGNMENT_TOLERANCE_DEG = 15.0
        private const val RECURVE_TOLERANCE_DEG = 15.0
        private const val BAREBOW_TOLERANCE_DEG = 25.0
        private const val VERTICAL_AXIS_TOLERANCE = 5.0

        // Scoring Weights
        private const val WEIGHT_SHOULDER = 0.3
        private const val WEIGHT_ARMS = 0.5
        private const val WEIGHT_VERTICAL_AXIS = 0.2
        private const val SCORE_EMA_ALPHA = 0.2f

        // Gaussian Sigmas (Tightened for more expert scoring)
        private const val SIGMA_SHOULDER = 10.0
        private const val SIGMA_ARMS = 12.0
        private const val SIGMA_VERTICAL_AXIS = 10.0
    }
}
