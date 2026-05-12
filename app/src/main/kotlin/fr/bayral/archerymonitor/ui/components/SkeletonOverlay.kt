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
 * High-contrast skeleton overlay that uses FrameComposer for rendering.
 * Supports capturing the final composed frame for WYSIWYG caching.
 */
@Composable
fun SkeletonOverlay(
    poseResult: PoseResult?,
    analysisResult: AnalysisResult?,
    transformationMatrix: Matrix,
    modifier: Modifier = Modifier,
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
                transformationMatrix = transformationMatrix
            )
            
            // Capture for VisualCache using reusable bitmaps
            if (onFrameCaptured != null && getReusableBitmap != null) {
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
