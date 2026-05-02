package fr.bayral.archerymonitor.core.sync

import fr.bayral.archerymonitor.core.interfaces.ISyncEngine
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Synchronization Engine for correlating AI Pose Results and Video Frames.
 *
 * This component solves the challenge of asynchronous AI analysis by buffering pose results 
 * in a time-sorted map and retrieving the closest match for a given video playback timestamp.
 *
 * ## Technical Constraints & Stabilizations:
 * - **O(log N) Matching:** Uses [TreeMap] to efficiently find the closest floor/ceiling 
 *   temporal matches for any video frame presentation timestamp (PTS).
 * - **Gapping Control:** Enforces a 1-second threshold ([MAX_JITTER_US]) to prevent displaying
 *   out-of-sync or present-time poses during delayed replay.
 * - **History Management:** Automatically prunes entries older than 35s to prevent memory leaks 
 *   while supporting the maximum 30s user delay.
 */
@Singleton
class SyncEngineImpl @Inject constructor() : ISyncEngine {

    /** History of pose results indexed by their presentation timestamp in microseconds (us). */
    private val poseHistory = TreeMap<Long, PoseResult>()

    /**
     * Records a new pose result into the history and prunes old data.
     *
     * @param result The pose result containing landmarks and high-precision timestamp.
     */
    fun addPoseResult(result: PoseResult) {
        synchronized(poseHistory) {
            poseHistory[result.timestamp] = result

            // Prune old entries to keep memory usage low while supporting max delay
            val cutoff = result.timestamp - (MAX_HISTORY_MS * 1000)
            while (poseHistory.isNotEmpty() && poseHistory.firstKey() < cutoff) {
                poseHistory.pollFirstEntry()
            }
        }
    }

    /**
     * Retrieves the AI pose result most closely matching the provided video timestamp.
     *
     * @param videoTimestamp Presentation timestamp (us) of the video frame currently on screen.
     * @return The closest matching [PoseResult], or null if no result exists within [MAX_JITTER_US].
     */
    override fun getSyncPose(videoTimestamp: Long): PoseResult? {
        synchronized(poseHistory) {
            if (poseHistory.isEmpty()) return null

            // Find candidates just before (floor) and after (ceil) the requested timestamp
            val floor = poseHistory.floorEntry(videoTimestamp)
            val ceil = poseHistory.ceilingEntry(videoTimestamp)

            // Pick the absolute closest result
            val distFloor = floor?.let { abs(it.key - videoTimestamp) } ?: Long.MAX_VALUE
            val distCeil = ceil?.let { abs(it.key - videoTimestamp) } ?: Long.MAX_VALUE
            val closest = if (distFloor < distCeil) floor else ceil

            // VALIDATION: Only return results within the precision window to avoid UI glitches.
            return if (closest != null && abs(closest.key - videoTimestamp) < MAX_JITTER_US) {
                closest.value
            } else {
                null
            }
        }
    }

    /**
     * Clears all stored pose results from the history.
     * Essential when toggling AI or restarting capture to avoid "ghost" skeletons.
     */
    override fun clear() {
        synchronized(poseHistory) {
            poseHistory.clear()
        }
    }

    companion object {
        /** Maximum history duration (ms) to keep in memory. Covers max delay + margin. */
        private const val MAX_HISTORY_MS = 35000L 
        
        /** Maximum allowed temporal distance (us) between a video frame and an AI pose. */
        private const val MAX_JITTER_US = 1_000_000L 
    }
}
