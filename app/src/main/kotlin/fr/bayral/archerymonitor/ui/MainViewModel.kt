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

@HiltViewModel
class MainViewModel @Inject constructor(
    private val cameraProvider: ICameraProvider,
    private val poseAnalyzer: IPoseAnalyzer,
    private val syncEngine: ISyncEngine,
    private val settingsManager: SettingsManager,
    private val decoder: H264Decoder,
    private val availableModules: @JvmSuppressWildcards List<IPostureModule>
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            delaySeconds = settingsManager.recordingDelay,
            useFrontCamera = settingsManager.useFrontCamera,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Collect decoder timestamp and sync pose
        decoder.currentPlaybackTimestamp
            .onEach { pts ->
                if (_uiState.value.isAiEnabled && _uiState.value.appState != AppState.IDLE) {
                    val syncedPose = syncEngine.getSyncPose(pts)
                    var analysisResult: AnalysisResult? = null
                    
                    if (syncedPose != null) {
                        analysisResult = _uiState.value.selectedModule?.analyze(
                            syncedPose,
                            ArcherySettings()
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        currentPose = syncedPose,
                        analysisResult = analysisResult
                    )
                } else if (!_uiState.value.isAiEnabled && _uiState.value.currentPose != null) {
                    _uiState.value = _uiState.value.copy(currentPose = null, analysisResult = null)
                }
            }
            .launchIn(viewModelScope)
    }

    private var currentSurface: android.view.Surface? = null

    fun selectModule(module: IPostureModule?) {
        _uiState.value = _uiState.value.copy(selectedModule = module, analysisResult = null)
    }

    fun getAvailableModules(): List<IPostureModule> = availableModules

    fun onStartCapture(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        if (_uiState.value.appState == AppState.RECORDING) {
            _uiState.value = _uiState.value.copy(
                appState = AppState.BUFFERING,
                currentPose = null,
                analysisResult = null
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
                if (_uiState.value.appState != AppState.IDLE) {
                    currentSurface?.let { startDelayedPlayback(it) }
                }
            },
            lowResAnalysis = { image, timestamp ->
                if (_uiState.value.isAiEnabled && _uiState.value.appState != AppState.IDLE) {
                    poseAnalyzer.analyze(image, timestamp)
                }
            },
            useFrontCamera = _uiState.value.useFrontCamera
        )

        if (_uiState.value.appState != AppState.IDLE) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

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
            currentPose = null,
            analysisResult = null
        )

        if (newState == AppState.BUFFERING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }
    
    fun onDecoderStarted() {
        if (_uiState.value.appState == AppState.BUFFERING) {
            viewModelScope.launch {
                val waitTime = (_uiState.value.delaySeconds * 1000).toLong().coerceAtLeast(500L)
                delay(waitTime)
                _uiState.value = _uiState.value.copy(appState = AppState.RECORDING)
            }
        }
    }
    fun startDelayedPlayback(surface: android.view.Surface) {
        currentSurface = surface
        if (_uiState.value.appState == AppState.RECORDING || _uiState.value.appState == AppState.BUFFERING) {
            val isPortrait = (_uiState.value.videoRotation == 90) || (_uiState.value.videoRotation == 270)
            val decodeW = if (isPortrait) _uiState.value.videoHeight else _uiState.value.videoWidth
            val decodeH = if (isPortrait) _uiState.value.videoWidth else _uiState.value.videoHeight

            decoder.start(surface, decodeW, decodeH, _uiState.value.delaySeconds) {
                onDecoderStarted()
            }
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
        val newAiEnabled = !_uiState.value.isAiEnabled
        if (newAiEnabled) {
            syncEngine.clear()
        }
        _uiState.value = _uiState.value.copy(
            isAiEnabled = newAiEnabled,
            currentPose = null,
            analysisResult = null
        )
    }

    fun toggleCamera() {
        val newUseFront = !_uiState.value.useFrontCamera
        _uiState.value = _uiState.value.copy(useFrontCamera = newUseFront)
        settingsManager.useFrontCamera = newUseFront
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
    val analysisResult: AnalysisResult? = null,
    val selectedModule: IPostureModule? = null,
    val useFrontCamera: Boolean = false,
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoRotation: Int = 0
)
