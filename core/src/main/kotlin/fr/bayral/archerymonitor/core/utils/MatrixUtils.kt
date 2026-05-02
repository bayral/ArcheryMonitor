package fr.bayral.archerymonitor.core.utils

import android.graphics.Matrix

/**
 * Utility class for coordinate system transformations.
 */
object MatrixUtils {

    /**
     * Computes a transformation matrix to map normalized (0..1) AI coordinates from the camera sensor 
     * to the actual display view pixels on screen.
     *
     * This logic accurately replicates the "FILL_CENTER" scaling behavior used in the UI, 
     * accounting for sensor rotation, mirroring (selfie camera), and dynamic cropping.
     *
     * ## Transformation Order:
     * 1. **Normalization Pivot:** Centers the 0..1 space to -0.5..0.5 for rotation.
     * 2. **Rotation:** Rotates the sensor data to an upright orientation.
     * 3. **Mirroring:** Inverts the X-axis for front-facing cameras in UI space.
     * 4. **Scaling:** Applies the "Fill" factor (zoom) required to cover the entire viewport.
     * 5. **Translation:** Centers the resulting zoomed image, effectively cropping the edges.
     *
     * @param srcWidth The width of the raw sensor image (un-rotated).
     * @param srcHeight The height of the raw sensor image (un-rotated).
     * @param viewWidth The actual width of the UI component in pixels.
     * @param viewHeight The actual height of the UI component in pixels.
     * @param rotationDegrees Sensor rotation metadata from CameraX.
     * @param isMirrored True if the current camera is front-facing.
     * @return A [Matrix] ready to be used by [android.graphics.Canvas.concat].
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

        // 1. Move pivot to center
        matrix.postTranslate(-0.5f, -0.5f)

        // 2. Rotate FIRST to get the image upright
        matrix.postRotate(rotationDegrees.toFloat())

        // 3. Mirror AFTER rotation if needed (Front Camera)
        // This ensures a consistent horizontal flip in the final UI coordinate space.
        if (isMirrored) {
            matrix.postScale(-1f, 1f)
        }

        // 4. Map back to 0..1 space
        matrix.postTranslate(0.5f, 0.5f)

        // 5. Scale to Display Space (accounting for FILL_CENTER zoom)
        
        // Dimensions of the content AFTER rotation
        val isPortrait = (rotationDegrees == 90 || rotationDegrees == 270)
        val contentW = if (isPortrait) srcHeight else srcWidth
        val contentH = if (isPortrait) srcWidth else srcHeight

        // Calculate the scale used for FILL_CENTER (takes the larger ratio)
        val scale = maxOf(
            viewWidth.toFloat() / contentW,
            viewHeight.toFloat() / contentH
        )

        // Apply scale: from normalized to pixels, then the FILL zoom
        matrix.postScale(contentW.toFloat() * scale, contentH.toFloat() * scale)

        // 6. Centering (Offset calculation for cropping)
        val finalContentW = contentW * scale
        val finalContentH = contentH * scale
        val offsetX = (viewWidth - finalContentW) / 2f
        val offsetY = (viewHeight - finalContentH) / 2f
        
        matrix.postTranslate(offsetX, offsetY)

        return matrix
    }
}
