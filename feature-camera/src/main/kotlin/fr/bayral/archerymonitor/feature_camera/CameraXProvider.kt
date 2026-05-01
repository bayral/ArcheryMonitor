package fr.bayral.archerymonitor.feature_camera

import android.content.Context
import android.media.Image
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.bayral.archerymonitor.core.interfaces.ICameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

class CameraXProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val encoder: H264Encoder,
) : ICameraProvider {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @OptIn(ExperimentalGetImage::class)
    override fun startCapture(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        onResolutionChanged: (Int, Int) -> Unit,
        lowResAnalysis: (Image) -> Unit,
        useFrontCamera: Boolean
    ) {
        Log.d("CameraXProvider", "startCapture called. useFrontCamera=$useFrontCamera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                try {
                cameraProvider = cameraProviderFuture.get()

                // 1. UI Preview
                val preview = Preview.Builder()
                    .setTargetResolution(Size(720, 1280))
                    .build()
                preview.setSurfaceProvider(surfaceProvider)

                // 2. ImageAnalysis (Combined AI and Encoding)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build()

                var currentWidth = 0
                var currentHeight = 0
                var frameCount = 0

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val isPortrait = (rotation == 90) || (rotation == 270)
                    val targetW = if (isPortrait) 720 else 1280
                    val targetH = if (isPortrait) 1280 else 720

                    // Re-prepare encoder if dimensions change (e.g. rotation)
                    if (targetW != currentWidth || targetH != currentHeight) {
                        currentWidth = targetW
                        currentHeight = targetH
                        encoder.prepare(currentWidth, currentHeight)
                        encoder.start()
                        onResolutionChanged(currentWidth, currentHeight)
                    }

                    val image = imageProxy.image
                    if (image != null) {
                        frameCount++
                        if (frameCount % 100 == 0) {
                            Log.d("CameraXProvider", "Analyzer: $frameCount frames. Rot: $rotation")
                        }

                        val ts = SystemClock.elapsedRealtimeNanos() / 1000
                        lowResAnalysis(image)
                        encoder.encodeImage(image, ts, useFrontCamera, rotation)
                    }
                    imageProxy.close()
                }

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

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

        }, ContextCompat.getMainExecutor(context))
    }

    override fun stopCapture() {
        Log.d("CameraXProvider", "stopCapture called")
        cameraProvider?.unbindAll()
        encoder.stop()
    }
}
