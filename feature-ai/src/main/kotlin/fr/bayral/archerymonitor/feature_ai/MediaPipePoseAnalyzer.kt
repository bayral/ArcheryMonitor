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
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipePoseAnalyzer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncEngine: fr.bayral.archerymonitor.core.interfaces.ISyncEngine,
) : IPoseAnalyzer, AutoCloseable {

    private var poseLandmarker: PoseLandmarker? = null
    private val _poseResults = MutableStateFlow<PoseResult?>(null)
    override val poseResults: StateFlow<PoseResult?> = _poseResults

    init {
        setupPoseLandmarker()
    }

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
                    processResult(result, result.timestampMs())
                }
                .setMinPoseDetectionConfidence(0.6f) // Slightly more permissive for stability
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

    override fun analyze(image: Image, timestamp: Long) {
        val bitmap = image.toBitmap() ?: return
        val mpImage = BitmapImageBuilder(bitmap).build()
        poseLandmarker?.detectAsync(mpImage, timestamp / 1000)
    }

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

            // MANDATORY for Pixel: Copy Y plane handling strides
            for (y in 0 until height) {
                yBuffer.position(y * yStride)
                yBuffer.get(nv21, idY, width)
                idY += width
            }

            // MANDATORY for Pixel: Copy UV planes handling strides
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

    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    private fun processResult(result: PoseLandmarkerResult, timestampMs: Long) {
        if (result.landmarks().isEmpty()) {
            _poseResults.value = null
            return
        }

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
        (syncEngine as? SyncEngineImpl)?.addPoseResult(poseResult)
    }
}
