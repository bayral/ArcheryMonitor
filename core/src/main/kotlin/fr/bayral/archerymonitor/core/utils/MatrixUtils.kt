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

        // 1. Map 0..1 to -0.5..0.5 to rotate around center
        matrix.postTranslate(-0.5f, -0.5f)

        // 2. Rotate FIRST to get the image upright
        matrix.postRotate(rotationDegrees.toFloat())

        // 3. Mirror AFTER rotation if needed (Front Camera)
        // This ensures a horizontal flip in the UI coordinate space
        if (isMirrored) {
            matrix.postScale(-1f, 1f)
        }

        // 4. Map back to 0..1
        matrix.postTranslate(0.5f, 0.5f)

        // 5. Scale and Crop (FILL_CENTER)
        
        // Dimensions after rotation
        val isPortrait = (rotationDegrees == 90 || rotationDegrees == 270)
        val contentW = if (isPortrait) srcHeight else srcWidth
        val contentH = if (isPortrait) srcWidth else srcHeight

        val scale = Math.max(
            viewWidth.toFloat() / contentW,
            viewHeight.toFloat() / contentH
        )

        matrix.postScale(contentW.toFloat() * scale, contentH.toFloat() * scale)

        // 6. Centering
        val offsetX = (viewWidth - (contentW * scale)) / 2f
        val offsetY = (viewHeight - (contentH * scale)) / 2f
        matrix.postTranslate(offsetX, offsetY)

        return matrix
    }
}
