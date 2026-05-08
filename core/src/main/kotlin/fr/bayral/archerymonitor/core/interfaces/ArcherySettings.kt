package fr.bayral.archerymonitor.core.interfaces

/**
 * Defines which hand the archer uses to draw the string.
 */
enum class Laterality {
    RIGHT_HANDED, // Bow in left hand, draws with right hand
    LEFT_HANDED   // Bow in right hand, draws with left hand
}

/**
 * Defines the type of bow being used, as postures vary significantly.
 */
enum class BowType {
    RECURVE,  // Classic Olympic bow with sight and stabilizers
    BAREBOW,  // Recurve without sight/stabilizers, uses string walking
    COMPOUND  // High-tech bow with pulleys and a release aid
}

/**
 * A container for user-specific archery configuration that affects analysis.
 */
data class ArcherySettings(
    val laterality: Laterality = Laterality.RIGHT_HANDED,
    val bowType: BowType = BowType.RECURVE,
    val deviceTilt: Float = 0f // Angle in degrees (roll)
)
