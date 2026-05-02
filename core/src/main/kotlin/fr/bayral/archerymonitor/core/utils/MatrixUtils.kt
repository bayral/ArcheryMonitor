package fr.bayral.archerymonitor.core.utils

import android.graphics.Matrix

object MatrixUtils {

    /**
     * Computes a matrix to map normalized (0..1) coordinates from the sensor image
     * to the actual display view pixels, accounting for rotation, mirroring, and 
     * FILL_CENTER cropping.
     */
    fun getTransformationMatrix(
        srcWidth: Int,
        srcHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        rotationDegrees: Int,
        isMirrored: Boolean,
    ): Matrix {
        val matrix = Matrix()

        // 1. Map 0..1 to -0.5..0.5 (to rotate/mirror around center)
        matrix.postTranslate(-0.5f, -0.5f)

        // 2. Mirror if needed (Front Camera)
        if (isMirrored) {
            matrix.postScale(-1f, 1f)
        }

        // 3. Rotate (Sensor rotation)
        matrix.postRotate(rotationDegrees.toFloat())

        // 4. Map back to 0..1
        matrix.postTranslate(0.5f, 0.5f)

        // 5. Scale to Display Space (accounting for FILL_CENTER zoom)
        
        // Dimensions of the content AFTER rotation
        val isPortrait = (rotationDegrees == 90 || rotationDegrees == 270)
        val contentW = if (isPortrait) srcHeight else srcWidth
        val contentH = if (isPortrait) srcWidth else srcHeight

        // Calculate the scale used for FILL_CENTER
        val scale = Math.max(
            viewWidth.toFloat() / contentW,
            viewHeight.toFloat() / contentH
        )

        // Scale to actual pixel size of the content
        matrix.postScale(contentW.toFloat(), contentH.toFloat())
        
        // Apply the FILL_CENTER zoom factor
        matrix.postScale(scale, scale)

        // 6. Centering (Offset calculation for cropping)
        val finalContentW = contentW * scale
        val finalContentH = contentH * scale
        val offsetX = (viewWidth - finalContentW) / 2f
        val offsetY = (viewHeight - finalContentH) / 2f
        
        matrix.postTranslate(offsetX, offsetY)

        return matrix
    }
}
