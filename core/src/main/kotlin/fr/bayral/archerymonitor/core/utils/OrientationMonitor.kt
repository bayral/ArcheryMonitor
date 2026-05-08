package fr.bayral.archerymonitor.core.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2

/**
 * Monitors device orientation (roll) using accelerometer.
 */
@Singleton
class OrientationMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tilt = MutableStateFlow(0f)
    val tilt: StateFlow<Float> = _tilt.asStateFlow()

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Calculate roll (rotation around Z axis when phone is held upright or landscape)
        // In landscape, ay/ax relationship gives the tilt.
        var angle = Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat()

        // Adjust based on screen rotation
        val rotation = windowManager.defaultDisplay.rotation
        angle = when (rotation) {
            Surface.ROTATION_0 -> angle + 90f // Portrait
            Surface.ROTATION_90 -> angle // Landscape right
            Surface.ROTATION_180 -> angle - 90f // Portrait inverted
            Surface.ROTATION_270 -> angle + 180f // Landscape left
            else -> angle
        }

        // Normalize to -180 to 180
        if (angle > 180) angle -= 360f
        if (angle < -180) angle += 360f

        // We want the angle relative to the "horizon"
        // In Landscape right, 0 degrees is flat.
        
        _tilt.value = angle
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
