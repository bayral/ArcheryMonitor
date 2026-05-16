package fr.bayral.archerymonitor.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import fr.bayral.archerymonitor.core.interfaces.AnalysisResult
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import fr.bayral.archerymonitor.core.renderer.FrameComposer

/**
 * Compose wrapper for the [FrameComposer] drawing logic.
 * This component handles the rendering of the skeletal overlay and AI analysis results
 * on top of the camera preview.
 */
@Composable
fun SkeletonOverlay(
    poseResult: PoseResult?,
    analysisResult: AnalysisResult?,
    transformationMatrix: Matrix,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false,
    calibrationText: String? = null,
    getReusableBitmap: ((Int, Int) -> Bitmap)? = null,
    onFrameCaptured: ((Bitmap) -> Unit)? = null
) {
    val frameComposer = remember { FrameComposer() }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawContext.canvas.nativeCanvas.let { canvas ->
            frameComposer.compose(
                canvas = canvas,
                poseResult = poseResult,
                analysisResult = analysisResult,
                transformationMatrix = transformationMatrix,
                isCalibrationMode = isCalibrationMode,
                calibrationText = calibrationText
            )
            
            // Capture for VisualCache using reusable bitmaps
            if (onFrameCaptured != null && getReusableBitmap != null && !isCalibrationMode) {
                val width = size.width.toInt().coerceAtLeast(1)
                val height = size.height.toInt().coerceAtLeast(1)
                
                val bitmap = getReusableBitmap(width, height)
                val captureCanvas = Canvas(bitmap)
                
                // Note: Clear bitmap before redraw to avoid ghosting
                captureCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                
                frameComposer.compose(captureCanvas, poseResult, analysisResult, transformationMatrix)
                onFrameCaptured(bitmap)
            }
        }
    }
}
