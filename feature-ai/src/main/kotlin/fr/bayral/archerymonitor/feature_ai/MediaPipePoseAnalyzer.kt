package fr.bayral.archerymonitor.feature_ai

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.bayral.archerymonitor.core.interfaces.IPoseAnalyzer
import fr.bayral.archerymonitor.core.interfaces.Landmark
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import fr.bayral.archerymonitor.core.sync.SyncEngineImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [IPoseAnalyzer] using Google's MediaPipe Pose Landmarker.
 *
 * This class handles the conversion of raw camera frames into AI-compatible formats,
 * triggers asynchronous pose detection, and broadcasts the results via a [StateFlow].
 *
 * ## Technical Constraints & Stabilizations:
 * - **Stride Handling:** Modern Android devices (like Google Pixel) often have YUV plane strides
 *   larger than the image width. This implementation manually copies pixels line-by-line in
 *   [toBitmap] to avoid image skewing.
 * - **Clock Synchronization:** To ensure AI skeletons are correctly anchored to delayed video frames,
 *   this class uses a unified timestamp (microseconds) shared with the video encoder.
 * - **Error Recovery:** Automatically falls back from GPU to CPU delegate if hardware acceleration
 *   fails during initialization.
 *
 * @property context The application context required by MediaPipe.
 * @property syncEngine The engine used to store and retrieve pose results based on timestamps.
 */
@Singleton
class MediaPipePoseAnalyzer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncEngine: fr.bayral.archerymonitor.core.interfaces.ISyncEngine,
) : IPoseAnalyzer, AutoCloseable {

    /** Internal reference to the MediaPipe PoseLandmarker instance. */
    private var poseLandmarker: PoseLandmarker? = null

    /** Backing property for [poseResults] flow. */
    private val _poseResults = MutableStateFlow<PoseResult?>(null)

    /** [StateFlow] emitting the latest detected pose result. */
    override val poseResults: StateFlow<PoseResult?> = _poseResults

    init {
        setupPoseLandmarker()
    }

    /**
     * Initializes the Pose Landmarker with GPU acceleration.
     * Falls back to [setupPoseLandmarkerCpu] on failure.
     */
    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .setDelegate(Delegate.GPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    // AI-Video Anchoring: use the frame's original timestampMs.
                    processResult(result, result.timestampMs())
                }
                .setMinPoseDetectionConfidence(0.6f) 
                .setMinPosePresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d("MediaPipePoseAnalyzer", "PoseLandmarker initialized with GPU")
        } catch (e: Exception) {
            Log.w("MediaPipePoseAnalyzer", "GPU initialization failed, falling back to CPU", e)
            setupPoseLandmarkerCpu()
        }
    }

    /**
     * Initializes the Pose Landmarker using CPU processing as a fallback.
     */
    private fun setupPoseLandmarkerCpu() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    processResult(result, result.timestampMs())
                }
                .setMinPoseDetectionConfidence(0.6f)
                .setMinPosePresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d("MediaPipePoseAnalyzer", "PoseLandmarker initialized with CPU")
        } catch (e: Exception) {
            Log.e("MediaPipePoseAnalyzer", "Failed to initialize PoseLandmarker with CPU", e)
        }
    }

    /**
     * Analyzes a camera frame for human poses.
     *
     * @param image The raw [Image] from CameraX (usually YUV_420_888).
     * @param timestamp The system clock timestamp in microseconds (us) to associate with this frame.
     */
    override fun analyze(image: Image, timestamp: Long) {
        try {
            // MediaPipe detectAsync expects milliseconds (ms).
            val bitmap = image.toBitmap() ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, timestamp / 1000)
        } catch (e: Exception) {
            Log.e("MediaPipePoseAnalyzer", "Analysis failed: ${e.message}")
        }
    }

    /**
     * Converts a YUV [Image] into a [Bitmap] while strictly respecting row strides.
     *
     * This method handles the potential gap between pixel data and row width in the YUV buffer,
     * ensuring that the resulting image isn't skewed or corrupted on devices like Pixel 7.
     *
     * @return A [Bitmap] containing the frame pixels, or null if conversion fails.
     */
    private fun Image.toBitmap(): Bitmap? {
        try {
            val width = width
            val height = height
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yStride = yPlane.rowStride
            val uvStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val nv21 = ByteArray(width * height * 3 / 2)
            var idY = 0
            var idUV = width * height

            // Copy Y plane handling potential padding (strides)
            for (y in 0 until height) {
                yBuffer.position(y * yStride)
                yBuffer.get(nv21, idY, width)
                idY += width
            }

            // Copy interleaved UV planes handling strides
            for (y in 0 until height / 2) {
                for (x in 0 until width / 2) {
                    val uvPos = y * uvStride + x * uvPixelStride
                    nv21[idUV++] = vBuffer.get(uvPos)
                    nv21[idUV++] = uBuffer.get(uvPos)
                }
            }

            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("MediaPipePoseAnalyzer", "Bitmap conversion failed", e)
            return null
        }
    }

    /**
     * Closes the MediaPipe instance and releases resources.
     */
    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    /**
     * Internal callback for MediaPipe results.
     *
     * Processes raw landmarks, converts them to [PoseResult], and feeds the [syncEngine].
     *
     * @param result The raw result from MediaPipe.
     * @param timestampMs The timestamp (ms) originally passed to [PoseLandmarker.detectAsync].
     */
    private fun processResult(result: PoseLandmarkerResult, timestampMs: Long) {
        if (result.landmarks().isEmpty()) {
            _poseResults.value = null
            return
        }

        // Convert back to microseconds (us) for high-precision SyncEngine matching.
        val timestampUs = timestampMs * 1000
        
        val poseResult = PoseResult(
            landmarks = result.landmarks()[0].map {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f), it.presence().orElse(0f))
            },
            worldLandmarks = result.worldLandmarks()[0].map {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f), it.presence().orElse(0f))
            },
            timestamp = timestampUs,
        )

        _poseResults.value = poseResult
        syncEngine.addPoseResult(poseResult)
    }
}
