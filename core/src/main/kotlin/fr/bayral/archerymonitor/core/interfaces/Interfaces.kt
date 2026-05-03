package fr.bayral.archerymonitor.core.interfaces

import android.media.MediaCodec
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import androidx.lifecycle.LifecycleOwner

/**
 * Interface defining the camera capture and stream management operations.
 */
interface ICameraProvider {
    /**
     * Starts camera capture and binds use cases to the provided lifecycle.
     *
     * @param lifecycleOwner The lifecycle owner to bind CameraX use-cases.
     * @param surfaceProvider The provider for the live preview surface.
     * @param onResolutionChanged Callback invoked when the video resolution or rotation changes.
     * @param lowResAnalysis Callback invoked for each frame to perform AI analysis.
     * @param useFrontCamera Whether to use the front-facing camera.
     */
    fun startCapture(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider,
        onResolutionChanged: (width: Int, height: Int, rotation: Int) -> Unit = { _, _, _ -> },
        lowResAnalysis: (image: android.media.Image, timestamp: Long) -> Unit,
        useFrontCamera: Boolean
    )

    /**
     * Toggles the recording/encoding state.
     *
     * @param isRecording True to enable encoding, false to disable.
     */
    fun setRecording(isRecording: Boolean)

    /**
     * Prepares the internal recording state, ensuring components are ready.
     */
    fun prepareRecording()

    /**
     * Stops camera capture and unbinds all use cases.
     */
    fun stopCapture()
}

/**
 * Interface for managing a circular buffer of encoded video packets.
 */
interface IBufferManager {
    /**
     * Adds a new encoded packet to the circular buffer.
     *
     * @param data The encoded [ByteBuffer].
     * @param info The [MediaCodec.BufferInfo] associated with the packet.
     */
    fun addPacket(data: ByteBuffer, info: MediaCodec.BufferInfo)

    /**
     * Retrieves packets within a specific presentation timestamp range.
     *
     * @param startTimeUs Start of the range in microseconds.
     * @param endTimeUs End of the range in microseconds.
     * @return A list of [EncodedPacket] matching the range.
     */
    fun getPacketsForRange(startTimeUs: Long, endTimeUs: Long): List<EncodedPacket>

    /**
     * Returns the latest recorded presentation timestamp in microseconds.
     */
    fun getLatestTimestamp(): Long

    /**
     * Releases any resources held by the buffer manager.
     */
    fun release()
}

/**
 * Data class representing an encoded video packet stored in the buffer.
 *
 * @property data The encoded frame data.
 * @property info Metadata about the buffer (size, timestamp, flags).
 * @property isKeyFrame Whether this packet represents a H.264 keyframe (I-frame).
 */
data class EncodedPacket(
    val data: ByteBuffer,
    val info: MediaCodec.BufferInfo,
    val isKeyFrame: Boolean
)

/**
 * Interface for analyzing camera frames to detect human poses.
 */
interface IPoseAnalyzer {
    /**
     * Analyzes a single image frame for poses.
     *
     * @param image The raw camera [android.media.Image].
     * @param timestamp The high-precision system timestamp (us) of the frame.
     */
    fun analyze(image: android.media.Image, timestamp: Long)

    /**
     * [StateFlow] providing the latest detected pose result.
     */
    val poseResults: StateFlow<PoseResult?>
}

/**
 * Interface for the temporal synchronization engine between video and AI data.
 */
interface ISyncEngine {
    /**
     * Adds a pose result to the synchronization history.
     *
     * @param result The [PoseResult] to store.
     */
    fun addPoseResult(result: PoseResult)

    /**
     * Finds the pose result closest to the requested video playback timestamp.
     *
     * @param videoTimestamp The playback timestamp in microseconds.
     * @return The best matching [PoseResult], or null if no valid match exists.
     */
    fun getSyncPose(videoTimestamp: Long): PoseResult?

    /**
     * Clears the synchronization history.
     */
    fun clear()
}

/**
 * Data class representing the result of a pose analysis.
 *
 * @property landmarks Screen-space normalized landmarks.
 * @property worldLandmarks 3D world-space coordinates (MediaPipe specific).
 * @property timestamp The original frame timestamp (us).
 */
data class PoseResult(
    val landmarks: List<Landmark>,
    val worldLandmarks: List<Landmark>,
    val timestamp: Long
)

/**
 * Represents a single detected body joint.
 *
 * @property x Normalized X coordinate (0..1).
 * @property y Normalized Y coordinate (0..1).
 * @property z Z depth estimation.
 * @property visibility Confidence score for the joint visibility.
 * @property presence Confidence score for the joint presence in the frame.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
)

/**
 * Defines the primary states of the application.
 */
enum class AppState {
    /** No recording or replay active. */
    IDLE, 
    /** Replay is active and synchronized with AI. */
    RECORDING, 
    /** Buffering the initial delay before replay starts. */
    BUFFERING
}
