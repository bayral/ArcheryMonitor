package fr.bayral.archerymonitor.core.interfaces

/**
 * Result of a posture analysis module.
 *
 * @property jointColors A map associating landmark indices with a specific color (ARGB Long).
 * @property score A quality score (0.0 to 1.0).
 * @property segments List of custom line segments to draw.
 */
data class AnalysisResult(
    val jointColors: Map<Int, Long>,
    val score: Float,
    val segments: List<AnalysisSegment> = emptyList()
)

/**
 * Custom line segment for skeleton drawing.
 */
data class AnalysisSegment(
    val startLandmarkIndex: Int = -1,
    val endLandmarkIndex: Int = -1,
    val startCustom: Point? = null,
    val endCustom: Point? = null,
    val color: Long
)

/**
 * 2D point for custom segments.
 */
data class Point(val x: Float, val y: Float)

/**
 * Interface for a modular archery posture analysis routine.
 */
interface IPostureModule {
    /** Localization resource ID for the module name. */
    val labelResId: Int

    /** Analyzes a [PoseResult] using the provided [ArcherySettings] and returns a color mapping for the skeleton. */
    fun analyze(pose: PoseResult, settings: ArcherySettings): AnalysisResult
}
