package fr.bayral.archerymonitor

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import fr.bayral.archerymonitor.core.interfaces.AppState
import fr.bayral.archerymonitor.ui.MainScreen
import fr.bayral.archerymonitor.ui.MainScreenContent
import fr.bayral.archerymonitor.ui.MainUiState
import fr.bayral.archerymonitor.ui.MainViewModel
import fr.bayral.archerymonitor.ui.theme.ArcheryMonitorTheme

/**
 * The primary entry point for the Archery Monitor application.
 *
 * This activity handles camera permission requests, sets up the Compose UI,
 * intercepts hardware key events for remote control, and manages
 * power/display features (screen on, auto-brightness).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    /** The main ViewModel managing app state and logic. */
    private val viewModel: MainViewModel by viewModels()

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            ArcheryMonitorTheme {
                // Keep screen on logic
                val uiState by viewModel.uiState.collectAsState()
                LaunchedEffect(uiState.appState) {
                    if (uiState.appState != AppState.IDLE) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                val context = LocalContext.current
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED,
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    hasCameraPermission = granted
                    if (!granted) {
                        Toast.makeText(context, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (hasCameraPermission) {
                    MainScreen(viewModel)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Veuillez autoriser la caméra pour utiliser l'application.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            val layoutParams = window.attributes

            // If ambient light is very high (outside, sun), force max brightness
            if (lux > 10000) { // Direct sunlight
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                window.attributes = layoutParams
            } else if (lux > 2000) { // Bright outdoor
                layoutParams.screenBrightness = 0.9f
                window.attributes = layoutParams
            } else {
                // Return to system default
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private var isVolumeUpLongPressed = false
    private var isVolumeDownLongPressed = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isLongPress == true) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    isVolumeUpLongPressed = true
                    viewModel.setDelay((viewModel.uiState.value.delaySeconds - 1f).coerceAtLeast(1f))
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    isVolumeDownLongPressed = true
                    viewModel.setDelay(viewModel.uiState.value.delaySeconds + 1f)
                    return true
                }
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (isVolumeUpLongPressed) {
                    isVolumeUpLongPressed = false
                } else {
                    viewModel.toggleRecording()
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (isVolumeDownLongPressed) {
                    isVolumeDownLongPressed = false
                } else {
                    viewModel.toggleAi()
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

/**
 * Preview for the [MainScreenContent] component.
 */
@Preview(showBackground = true, widthDp = 320, heightDp = 640, apiLevel = 35)
@Composable
fun MainPreview() {
    ArcheryMonitorTheme {
        // Preview content without full VM
        MainScreenContent(
            uiState = MainUiState(appState = AppState.IDLE, delaySeconds = 10f, isAiEnabled = true, currentPose = null, useFrontCamera = false),
            availableModules = emptyList(),
            onStartCapture = { _, _ -> },
            onStopCapture = {},
            onToggleRecording = {},
            onToggleAi = {},
            onToggleCamera = {},
            onSetDelay = {},
            onSelectModule = {},
            onSetLaterality = {},
            onSetBowType = {},
            onEnterReplay = {},
            onSurfaceCreated = {},
            viewModel = viewModel()
        )
    }
}
