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
 *   [toOptimizedBitmap] to avoid image skewing.
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

    /** Buffer for pixels to avoid allocating IntArray every frame. */
    private var pixelBuffer: IntArray? = null

    /** Flag indicating if GPU acceleration is currently active. */
    private var isGpuEnabled = false

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
            isGpuEnabled = true
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
            isGpuEnabled = false
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
            val bitmap = image.toOptimizedBitmap() ?: return
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            synchronized(this) {
                poseLandmarker?.detectAsync(mpImage, timestamp / 1000)
            }
        } catch (e: Throwable) {
            Log.e("MediaPipePoseAnalyzer", "Analysis failed: ${e.message}")
            
            // Handle specific GPU errors that occur during execution but aren't caught in init.
            val errorMsg = e.toString().lowercase()
            val isGlError = errorMsg.contains("gl_") || 
                           errorMsg.contains("invalid enum") || 
                           errorMsg.contains("graph has errors")

            if (isGpuEnabled && isGlError) {
                Log.w("MediaPipePoseAnalyzer", "GPU error detected during execution, falling back to CPU...")
                fallbackToCpu()
            }
        }
    }

    /**
     * Switch to CPU delegate safely when GPU fails during runtime.
     */
    private fun fallbackToCpu() {
        synchronized(this) {
            if (!isGpuEnabled) return // Already falling back or on CPU

            isGpuEnabled = false
            try {
                poseLandmarker?.close()
            } catch (e: Exception) {
                Log.e("MediaPipePoseAnalyzer", "Error closing failing GPU landmarker", e)
            } finally {
                poseLandmarker = null
            }
            setupPoseLandmarkerCpu()
        }
    }

    /**
     * Optimized version of YUV to Bitmap conversion with integrated downscaling.
     * Avoids heavy JPEG compression/decompression and reduces AI workload by resizing 
     * the image to [TARGET_WIDTH].
     *
     * @return A [Bitmap] containing the resized frame pixels, or null if conversion fails.
     */
    private fun Image.toOptimizedBitmap(): Bitmap? {
        try {
            val srcW = width
            val srcH = height
            
            // Calculate target dimensions (maintaining aspect ratio)
            val targetW = TARGET_WIDTH
            val targetH = (srcH * TARGET_WIDTH) / srcW

            // Buffer for pixels can be reused safely as it's used only during conversion
            if (pixelBuffer == null || pixelBuffer?.size != targetW * targetH) {
                pixelBuffer = IntArray(targetW * targetH)
            }

            val pixels = pixelBuffer ?: return null
            
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Scaling factors
            val scaleX = srcW.toFloat() / targetW
            val scaleY = srcH.toFloat() / targetH

            for (y in 0 until targetH) {
                // Map target Y to source Y (Nearest Neighbor)
                val srcY = (y * scaleY).toInt().coerceIn(0, srcH - 1)
                val yOffset = srcY * yRowStride
                val uvYOffset = (srcY / 2) * uvRowStride

                for (x in 0 until targetW) {
                    // Map target X to source X (Nearest Neighbor)
                    val srcX = (x * scaleX).toInt().coerceIn(0, srcW - 1)
                    val uvXOffset = (srcX / 2) * uvPixelStride

                    val yVal = (yBuffer.get(yOffset + srcX).toInt() and 0xFF)
                    val uVal = (uBuffer.get(uvYOffset + uvXOffset).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(uvYOffset + uvXOffset).toInt() and 0xFF) - 128

                    var r = (yVal + 1.370705f * vVal).toInt()
                    var g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt()
                    var b = (yVal + 1.732446f * uVal).toInt()

                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)

                    pixels[y * targetW + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            return outBitmap
        } catch (e: Exception) {
            Log.e("MediaPipePoseAnalyzer", "Optimized Bitmap conversion failed", e)
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

    companion object {
        /** 
         * Target width for AI analysis. 
         * Lower resolution significantly improves performance while maintaining accuracy.
         */
        private const val TARGET_WIDTH = 640
    }
}
