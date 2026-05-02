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

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope

/**
 * Main ViewModel for the Archery Monitor application.
 *
 * This class orchestrates the interaction between the Camera pipeline, the AI analysis,
 * and the H.264 playback buffer. It manages the global [AppState] and ensures UI consistency.
 *
 * @property cameraProvider Source of camera frames and metadata.
 * @property poseAnalyzer Component for human pose detection.
 * @property syncEngine Engine for correlating AI results with video timestamps.
 * @property settingsManager Local storage for user preferences (delay, camera choice).
 * @property decoder Decoder for handling the delayed replay stream.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val cameraProvider: ICameraProvider,
    private val poseAnalyzer: IPoseAnalyzer,
    private val syncEngine: ISyncEngine,
    private val settingsManager: SettingsManager,
    private val decoder: H264Decoder,
) : ViewModel() {

    /** Internal UI state flow. */
    private val _uiState = MutableStateFlow(
        MainUiState(
            delaySeconds = settingsManager.recordingDelay,
            useFrontCamera = settingsManager.useFrontCamera,
        ),
    )

    /** Public read-only UI state exposed to the UI layer. */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        /**
         * AI-Video Synchronization Loop.
         * Listen to the decoder's presentation timestamp (PTS) and fetch the closest AI pose.
         * 
         * ## Technical Detail:
         * We only perform synchronization if AI is enabled and the app is not idle.
         * We clear the current pose if AI is disabled to avoid "frozen" skeletons.
         */
        decoder.currentPlaybackTimestamp
            .onEach { pts ->
                if (_uiState.value.isAiEnabled && _uiState.value.appState != AppState.IDLE) {
                    val syncedPose = syncEngine.getSyncPose(pts)
                    _uiState.value = _uiState.value.copy(currentPose = syncedPose)
                } else if (!_uiState.value.isAiEnabled && _uiState.value.currentPose != null) {
                    _uiState.value = _uiState.value.copy(currentPose = null)
                }
            }
            .launchIn(viewModelScope)
    }

    /** Tracks the Surface currently used for delayed playback. */
    private var currentSurface: android.view.Surface? = null

    /**
     * Initializes and starts the camera capture session.
     *
     * @param lifecycleOwner The lifecycle owner to bind CameraX use-cases to.
     * @param surfaceProvider The provider for the live camera preview.
     */
    fun onStartCapture(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        // Handle resumption from background: force buffering if previously recording
        if (_uiState.value.appState == AppState.RECORDING) {
            _uiState.value = _uiState.value.copy(
                appState = AppState.BUFFERING,
                currentPose = null
            )
            cameraProvider.prepareRecording()
            cameraProvider.setRecording(true)
        }

        cameraProvider.startCapture(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            onResolutionChanged = { w, h, rot ->
                _uiState.value = _uiState.value.copy(
                    videoWidth = w, 
                    videoHeight = h,
                    videoRotation = rot
                )
                // Re-arm decoder with new resolution if needed
                if (_uiState.value.appState != AppState.IDLE) {
                    currentSurface?.let { startDelayedPlayback(it) }
                }
            },
            lowResAnalysis = { image, timestamp ->
                // Optimize battery: only analyze if needed
                if (_uiState.value.isAiEnabled && _uiState.value.appState != AppState.IDLE) {
                    poseAnalyzer.analyze(image, timestamp)
                }
            },
            useFrontCamera = _uiState.value.useFrontCamera
        )

        // Ensure playback resumes correctly
        if (_uiState.value.appState != AppState.IDLE) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

    /**
     * Toggles the capture and replay state between IDLE and BUFFERING/RECORDING.
     */
    fun toggleRecording() {
        val newState = if (_uiState.value.appState != AppState.IDLE) {
            decoder.stop()
            cameraProvider.setRecording(false)
            AppState.IDLE
        } else {
            cameraProvider.prepareRecording()
            cameraProvider.setRecording(true)
            AppState.BUFFERING
        }
        _uiState.value = _uiState.value.copy(
            appState = newState,
            currentPose = null
        )

        if (newState == AppState.BUFFERING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }
    
    /**
     * Notification from the decoder that the first frame has been successfully output.
     * Triggers a transition from BUFFERING to RECORDING with a minimum display delay for UX.
     */
    fun onDecoderStarted() {
        if (_uiState.value.appState == AppState.BUFFERING) {
            viewModelScope.launch {
                // Ensure buffering UI is visible for a natural duration matching the delay
                val waitTime = (_uiState.value.delaySeconds * 1000).toLong().coerceAtLeast(500L)
                delay(waitTime)
                _uiState.value = _uiState.value.copy(appState = AppState.RECORDING)
            }
        }
    }

    /**
     * Starts the delayed video playback on the provided surface.
     * 
     * @param surface The target surface for the decoder.
     */
    fun startDelayedPlayback(surface: android.view.Surface) {
        currentSurface = surface
        if (_uiState.value.appState == AppState.RECORDING || _uiState.value.appState == AppState.BUFFERING) {
            // Apply rotation-aware dimensions to the decoder
            val isPortrait = (_uiState.value.videoRotation == 90) || (_uiState.value.videoRotation == 270)
            val decodeW = if (isPortrait) _uiState.value.videoHeight else _uiState.value.videoWidth
            val decodeH = if (isPortrait) _uiState.value.videoWidth else _uiState.value.videoHeight

            decoder.start(surface, decodeW, decodeH, _uiState.value.delaySeconds) {
                onDecoderStarted()
            }
        }
    }

    /**
     * Updates the replay delay duration.
     * 
     * @param seconds New delay in seconds.
     */
    fun setDelay(seconds: Float) {
        _uiState.value = _uiState.value.copy(delaySeconds = seconds)
        settingsManager.recordingDelay = seconds
        if (_uiState.value.appState == AppState.RECORDING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

    /**
     * Toggles AI Pose Detection on or off.
     */
    fun toggleAi() {
        val newAiEnabled = !_uiState.value.isAiEnabled
        if (newAiEnabled) {
            syncEngine.clear() // Prevent stale frames from flashing
        }
        _uiState.value = _uiState.value.copy(
            isAiEnabled = newAiEnabled,
            currentPose = null
        )
    }

    /**
     * Toggles between front and back camera sensors.
     */
    fun toggleCamera() {
        val newUseFront = !_uiState.value.useFrontCamera
        _uiState.value = _uiState.value.copy(useFrontCamera = newUseFront)
        settingsManager.useFrontCamera = newUseFront
    }

    /**
     * Releases all pipeline resources.
     */
    fun stopCapture() {
        cameraProvider.stopCapture()
        decoder.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopCapture()
    }
}

/**
 * UI State representation for the Main Screen.
 * 
 * @property appState Current application state (IDLE, BUFFERING, RECORDING).
 * @property delaySeconds User-configured replay delay.
 * @property isAiEnabled Whether pose analysis is active.
 * @property currentPose The AI result synchronized with the current replay frame.
 * @property useFrontCamera Whether the selfie camera is selected.
 * @property videoWidth Raw video width from sensor.
 * @property videoHeight Raw video height from sensor.
 * @property videoRotation Sensor rotation relative to natural orientation.
 */
data class MainUiState(
    val appState: AppState = AppState.IDLE,
    val delaySeconds: Float = 6f,
    val isAiEnabled: Boolean = true,
    val currentPose: PoseResult? = null,
    val useFrontCamera: Boolean = false,
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoRotation: Int = 0
)
