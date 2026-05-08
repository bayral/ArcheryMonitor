package fr.bayral.archerymonitor.core.interfaces

/**
 * Metadata about what an analysis module can handle.
 */
data class ModuleCapabilities(
    val supportedBowTypes: Set<BowType> = BowType.values().toSet()
)

/**
 * Result of a posture analysis module.
 *
 * @property jointColors A map associating landmark indices with a specific color (ARGB Long).
 * @property segments List of custom line segments to draw (e.g., body axis, arm alignment).
 * @property score A quality score (0.0 to 1.0).
 */
data class AnalysisResult(
    val jointColors: Map<Int, Long>,
    val segments: List<AnalysisSegment> = emptyList(),
    val score: Float,
    val releaseCount: Int = 0,
    val isReleaseEvent: Boolean = false
)

/**
 * Custom line segment for skeleton drawing.
 */
data class AnalysisSegment(
    val startLandmarkIndex: Int = -1,
    val endLandmarkIndex: Int = -1,
    val startCustom: Landmark? = null,
    val endCustom: Landmark? = null,
    val color: Long
)

/**
 * Shared colors for skeleton analysis visualization.
 */
object AnalysisColors {
    const val GREEN = 0xFF00FF00L
    const val RED = 0xFFFF0000L
    const val YELLOW = 0xFFFFFF00L
    const val BLUE = 0xFF0000FFL
    const val ORANGE = 0xFFFFA500L
}

/**
 * Interface for a modular archery posture analysis routine.
 */
interface IPostureModule {
    /** Localization resource ID for the module name. */
    val labelResId: Int

    /** Defines which bow types this module is designed for. */
    val capabilities: ModuleCapabilities get() = ModuleCapabilities()

    /** Analyzes a [PoseResult] using the provided [ArcherySettings] and returns a color mapping for the skeleton. */
    fun analyze(pose: PoseResult, settings: ArcherySettings): AnalysisResult
}
