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

            // Prune old entries
            val cutoff = result.timestamp - (MAX_HISTORY_MS * 1000) // timestamps in us
            val iterator = poseHistory.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key < cutoff) {
                    iterator.remove()
                } else {
                    break // TreeMap is sorted, so we can stop
                }
            }
        }
    }

    override fun getSyncPose(videoTimestamp: Long): PoseResult? {
        synchronized(poseHistory) {
            if (poseHistory.isEmpty()) return null

            val floor = poseHistory.floorEntry(videoTimestamp)
            val ceil = poseHistory.ceilingEntry(videoTimestamp)

            // Find the closest entry
            val bestEntry = when {
                floor == null -> ceil
                ceil == null -> floor
                else -> if (videoTimestamp - floor.key < ceil.key - videoTimestamp) floor else ceil
            }

            // Relaxed jitter tolerance (500ms) to avoid blinking
            return if (abs(bestEntry.key - videoTimestamp) < MAX_JITTER_US) {
                bestEntry.value
            } else {
                null
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_MS = 31000L // Keep slightly more than 30s
        private const val MAX_JITTER_US = 500_000L // 500ms tolerance for sync
    }
}
