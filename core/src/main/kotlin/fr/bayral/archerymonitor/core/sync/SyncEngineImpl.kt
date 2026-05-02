package fr.bayral.archerymonitor.core.sync

import fr.bayral.archerymonitor.core.interfaces.ISyncEngine
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SyncEngineImpl @Inject constructor() : ISyncEngine {

    private val poseHistory = TreeMap<Long, PoseResult>()

    fun addPoseResult(result: PoseResult) {
        synchronized(poseHistory) {
            poseHistory[result.timestamp] = result

            // Prune old entries (keep slightly more than 30s)
            val cutoff = result.timestamp - (MAX_HISTORY_MS * 1000)
            while (poseHistory.isNotEmpty() && poseHistory.firstKey() < cutoff) {
                poseHistory.pollFirstEntry()
            }
        }
    }

    override fun getSyncPose(videoTimestamp: Long): PoseResult? {
        synchronized(poseHistory) {
            if (poseHistory.isEmpty()) return null

            // Find the closest point in the past and future relative to the video frame
            val floor = poseHistory.floorEntry(videoTimestamp)
            val ceil = poseHistory.ceilingEntry(videoTimestamp)

            // Calculate exact distances
            val distFloor = floor?.let { abs(it.key - videoTimestamp) } ?: Long.MAX_VALUE
            val distCeil = ceil?.let { abs(it.key - videoTimestamp) } ?: Long.MAX_VALUE

            // Pick the absolute closest
            val closest = if (distFloor < distCeil) floor else ceil

            // VALIDATION: We only return the pose if it's within our precision window (500ms).
            // If the closest pose is too far (e.g. from the present while we want the past),
            // we return null to avoid "teleporting" skeletons.
            return if (closest != null && abs(closest.key - videoTimestamp) < MAX_JITTER_US) {
                closest.value
            } else {
                null
            }
        }
    }

    override fun clear() {
        synchronized(poseHistory) {
            poseHistory.clear()
        }
    }

    companion object {
        private const val MAX_HISTORY_MS = 35000L // Keep 35s of history
        private const val MAX_JITTER_US = 1_000_000L // 1 second max tolerance
    }
}
