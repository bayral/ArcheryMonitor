package fr.bayral.archerymonitor.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.bayral.archerymonitor.core.interfaces.*
import fr.bayral.archerymonitor.core.utils.SettingsManager
import fr.bayral.archerymonitor.feature_camera.H264Decoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val cameraProvider: ICameraProvider,
    private val poseAnalyzer: IPoseAnalyzer,
    private val settingsManager: SettingsManager,
    private val decoder: H264Decoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            delaySeconds = settingsManager.recordingDelay,
            useFrontCamera = settingsManager.useFrontCamera,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var currentSurface: android.view.Surface? = null

    fun onStartCapture(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        cameraProvider.startCapture(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            onResolutionChanged = { w, h ->
                _uiState.value = _uiState.value.copy(videoWidth = w, videoHeight = h)
                // Restart decoder with new dimensions if already recording
                if (_uiState.value.appState == AppState.RECORDING) {
                    currentSurface?.let { startDelayedPlayback(it) }
                }
            },
            lowResAnalysis = { image ->
                poseAnalyzer.analyze(image, System.nanoTime() / 1000)
            },
            useFrontCamera = _uiState.value.useFrontCamera
        )
    }

    fun toggleRecording() {
        val newState = if (_uiState.value.appState == AppState.RECORDING) {
            decoder.stop()
            AppState.IDLE
        } else {
            AppState.RECORDING
        }
        _uiState.value = _uiState.value.copy(appState = newState)

        // If we just started recording, ensure decoder starts too
        if (newState == AppState.RECORDING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

    fun startDelayedPlayback(surface: android.view.Surface) {
        currentSurface = surface
        if (_uiState.value.appState == AppState.RECORDING) {
            decoder.start(surface, _uiState.value.videoWidth, _uiState.value.videoHeight, _uiState.value.delaySeconds)
        }
    }

    fun setDelay(seconds: Float) {
        _uiState.value = _uiState.value.copy(delaySeconds = seconds)
        settingsManager.recordingDelay = seconds
        if (_uiState.value.appState == AppState.RECORDING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

    fun toggleAi() {
        _uiState.value = _uiState.value.copy(isAiEnabled = !_uiState.value.isAiEnabled)
    }

    fun toggleCamera() {
        val newUseFront = !_uiState.value.useFrontCamera
        _uiState.value = _uiState.value.copy(useFrontCamera = newUseFront)
        settingsManager.useFrontCamera = newUseFront
        // Capture will restart via LaunchedEffect in MainScreen
    }

    fun stopCapture() {
        cameraProvider.stopCapture()
        decoder.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopCapture()
    }
}

data class MainUiState(
    val appState: AppState = AppState.IDLE,
    val delaySeconds: Float = 6f,
    val isAiEnabled: Boolean = true,
    val currentPose: PoseResult? = null,
    val useFrontCamera: Boolean = false,
    val videoWidth: Int = 720,
    val videoHeight: Int = 1280
)
