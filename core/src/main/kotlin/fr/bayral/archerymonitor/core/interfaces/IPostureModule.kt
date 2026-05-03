package fr.bayral.archerymonitor.core.interfaces
/**
 * Result of a posture analysis module.
 *
 * @property jointColors A map associating landmark indices with a specific color (ARGB Long).
 * @property score A quality score (0.0 to 1.0).
 */
data class AnalysisResult(
    val jointColors: Map<Int, Long>,
    val score: Float
)

/**
 * Interface for a modular archery posture analysis routine.
 */
interface IPostureModule {
    /** Localization resource ID for the module name. */
    val labelResId: Int

    /** Analyzes a [PoseResult] and returns a color mapping for the skeleton. */
    fun analyze(pose: PoseResult): AnalysisResult
}
