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
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .setDelegate(Delegate.GPU)

            val baseOptions = baseOptionsBuilder.build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    processResult(result, System.nanoTime() / 1000)
                }
                .setMinPoseDetectionConfidence(0.8f)
                .setMinPosePresenceConfidence(0.8f)
                .setMinTrackingConfidence(0.8f)
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
                    processResult(result, System.nanoTime() / 1000)
                }
                .setMinPoseDetectionConfidence(0.8f)
                .setMinPosePresenceConfidence(0.8f)
                .setMinTrackingConfidence(0.8f)
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
        // Use the same timestamp base as the encoder for syncing
        poseLandmarker?.detectAsync(mpImage, timestamp / 1000)
    }

    private fun Image.toBitmap(): Bitmap? {
        try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]

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

    private fun processResult(result: PoseLandmarkerResult, timestampUs: Long) {
        if (result.landmarks().isEmpty()) {
            _poseResults.value = null
            return
        }

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
