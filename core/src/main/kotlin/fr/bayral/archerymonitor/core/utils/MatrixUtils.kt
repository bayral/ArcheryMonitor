package fr.bayral.archerymonitor.core.utils

import android.graphics.Matrix
import android.graphics.RectF

object MatrixUtils {
    
    /**
     * Computes a matrix to map normalized (0..1) coordinates to view pixels.
     * 
     * @param viewWidth The width of the destination UI view.
     * @param viewHeight The height of the destination UI view.
     * @param rotationDegrees Sensor rotation (usually 90 or 270 for back camera).
     * @param isMirrored True if using front camera.
     */
    fun getTransformationMatrix(
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int,
        isMirrored: Boolean
    ): Matrix {
        val matrix = Matrix()
        
        // 1. Map normalized 0..1 to 1x1 square
        // (Actually, normalized coordinates are already in 0..1)
        
        // 2. Mirror if needed (around x=0.5)
        if (isMirrored) {
            matrix.postScale(-1f, 1f, 0.5f, 0.5f)
        }
        
        // 3. Rotate around center (0.5, 0.5)
        matrix.postRotate(rotationDegrees.toFloat(), 0.5f, 0.5f)
        
        // 4. Scale and Translate to fit/fill the view
        // For simplicity, we assume ContentScale.Fit logic here
        // In a real app, you'd calculate the scale to maintain aspect ratio
        matrix.postScale(viewWidth.toFloat(), viewHeight.toFloat())
        
        return matrix
    }
}
