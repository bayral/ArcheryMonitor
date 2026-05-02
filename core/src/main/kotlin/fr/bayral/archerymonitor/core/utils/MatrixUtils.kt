package fr.bayral.archerymonitor.core.utils

import android.graphics.Matrix

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
        isMirrored: Boolean,
    ): Matrix {
        val matrix = Matrix()

        // 1. Mirroring
        if (isMirrored) {
            matrix.postScale(-1f, 1f, 0.5f, 0.5f)
        }

        // 2. Rotation
        matrix.postRotate(rotationDegrees.toFloat(), 0.5f, 0.5f)

        // 3. Aspect Ratio Scaling (Fill Center)
        // Camera sensor (1280x720) vs Viewport (e.g., 1080x2400)
        // To achieve FILL_CENTER, we need to scale to fit then crop or stretch
        // MediaPipe results are 0..1 normalized.
        
        val scaleX = viewWidth.toFloat()
        val scaleY = viewHeight.toFloat()
        
        matrix.postScale(scaleX, scaleY)

        return matrix
    }
}
