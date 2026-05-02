package fr.bayral.archerymonitor.feature_camera

import android.content.Context
import android.media.Image
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.bayral.archerymonitor.core.interfaces.ICameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Android CameraX Implementation of [ICameraProvider].
 *
 * This class coordinates the camera lifecycle, UI preview, and image analysis pipeline.
 * It serves as the primary source of truth for video resolution and system timestamps.
 *
 * ## Technical Constraints & Stabilizations:
 * - **Unified Clock:** Uses [SystemClock.elapsedRealtimeNanos] as the single reference for
 *   both H.264 encoding and AI analysis, enabling perfect skeleton-to-frame anchoring.
 * - **Dynamic Re-Preparation:** Handles rotation and resolution changes on-the-fly,
 *   re-preparing the encoder only when necessary to avoid pipeline lag.
 * - **Thread Management:** Uses a dedicated [cameraExecutor] to prevent UI jank during
 *   heavy image analysis.
 *
 * @property context The application context.
 * @property encoder The H.264 encoder instance used for delayed replay buffering.
 */
class CameraXProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val encoder: H264Encoder,
) : ICameraProvider {

    /** Internal reference to the CameraX provider. */
    private var cameraProvider: ProcessCameraProvider? = null

    /** Dedicated executor for image analysis frames. */
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Tracks whether the user has active recording/replay enabled. */
    private var isRecording = false

    /** Current width of the rotated video frame. */
    private var currentWidth = 0

    /** Current height of the rotated video frame. */
    private var currentHeight = 0

    /** Current sensor rotation (0, 90, 180, 270). */
    private var currentRotation = -1

    /**
     * Toggles the active encoding state.
     * When disabled, the encoder is stopped to save battery.
     */
    override fun setRecording(isRecording: Boolean) {
        this.isRecording = isRecording
        if (!isRecording) {
            encoder.stop()
        }
    }

    /**
     * Manually prepares the encoder with current dimensions.
     * Used to ensure the [MediaCodec] is "armed" before the first frame arrives.
     */
    override fun prepareRecording() {
        if (currentWidth > 0 && currentHeight > 0) {
            encoder.prepare(currentWidth, currentHeight)
        }
    }

    /**
     * Starts the CameraX capture pipeline.
     *
     * @param lifecycleOwner The lifecycle owner (Activity/Fragment) to bind to.
     * @param surfaceProvider The provider for the live UI preview.
     * @param onResolutionChanged Callback triggered when dimensions or rotation change.
     * @param lowResAnalysis Callback for per-frame AI analysis.
     * @param useFrontCamera Whether to use the selfie or back camera.
     */
    @OptIn(ExperimentalGetImage::class)
    override fun startCapture(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onResolutionChanged: (width: Int, height: Int, rotation: Int) -> Unit,
        lowResAnalysis: (image: Image, timestamp: Long) -> Unit,
        useFrontCamera: Boolean
    ) {
        Log.d("CameraXProvider", "startCapture called. useFrontCamera=$useFrontCamera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                try {
                cameraProvider = cameraProviderFuture.get()

                // 1. UI Preview: Standard CameraX Preview use-case
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(720, 1280),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .build()
                preview.surfaceProvider = surfaceProvider

                // 2. ImageAnalysis: Combined AI and Encoding pipeline
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                    )
                    .build()

                var frameCount = 0

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val image = imageProxy.image
                    
                    if (image != null) {
                        val isPortrait = (rotation == 90) || (rotation == 270)
                        
                        // Dimensions AFTER rotation required by the H264 decoder and UI ratio.
                        val targetW = if (isPortrait) image.height else image.width
                        val targetH = if (isPortrait) image.width else image.height

                        // Re-prepare pipeline components if sensor metadata changes
                        if ((targetW != currentWidth) || (targetH != currentHeight) || (rotation != currentRotation)) {
                            currentWidth = targetW
                            currentHeight = targetH
                            currentRotation = rotation
                            encoder.prepare(currentWidth, currentHeight)
                            onResolutionChanged(image.width, image.height, rotation)
                        }
                        
                        frameCount++
                        // HARMONIZED CLOCK: Use the same reference for both AI and Replay buffer.
                        val ts = SystemClock.elapsedRealtimeNanos() / 1000
                        lowResAnalysis(image, ts)
                        
                        if (isRecording) {
                            if (!encoder.isEncoding) {
                                encoder.start()
                            }
                            encoder.encodeImage(image, ts, useFrontCamera, rotation)
                        }
                    }
                    imageProxy.close()
                }

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                // Bind all use cases to the LifecycleOwner
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d("CameraXProvider", "Camera bound successfully")

            } catch (exc: Exception) {
                Log.e("CameraXProvider", "Camera binding failed", exc)
            }

        },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Unbinds CameraX and stops the H.264 encoder.
     */
    override fun stopCapture() {
        Log.d("CameraXProvider", "stopCapture called")
        cameraProvider?.unbindAll()
        encoder.stop()
    }
}
