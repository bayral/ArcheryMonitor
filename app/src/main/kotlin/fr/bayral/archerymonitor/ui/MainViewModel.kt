package fr.bayral.archerymonitor.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.bayral.archerymonitor.core.interfaces.*
import fr.bayral.archerymonitor.core.utils.OrientationMonitor
import fr.bayral.archerymonitor.core.utils.PostureModuleFactory
import fr.bayral.archerymonitor.core.utils.SettingsManager
import fr.bayral.archerymonitor.core.renderer.VisualCache
import fr.bayral.archerymonitor.core.renderer.VideoExporter
import fr.bayral.archerymonitor.feature_camera.H264Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Data class for Badge definitions.
 */
data class Badge(val id: String, val labelResId: Int, val minScore: Float, val durationSeconds: Long)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val cameraProvider: ICameraProvider,
    private val poseAnalyzer: IPoseAnalyzer,
    private val syncEngine: ISyncEngine,
    private val settingsManager: SettingsManager,
    private val decoder: H264Decoder,
    private val moduleFactory: PostureModuleFactory,
    orientationMonitor: OrientationMonitor,
    private val visualCache: VisualCache,
    private val videoExporter: VideoExporter
) : ViewModel() {

    fun getAvailableModules(): List<IPostureModule> = moduleFactory.getCompatibleModules(_uiState.value.archerySettings)

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

    fun stopCapture() {
        cameraProvider.stopCapture()
        decoder.stop()
    }

    fun toggleRecording() {
        val newState = if (_uiState.value.appState != AppState.IDLE) {
            bufferingJob?.cancel()
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

    fun toggleCalibration() {
        _uiState.value = _uiState.value.copy(isCalibrationMode = !_uiState.value.isCalibrationMode)
    }

    fun setDelay(seconds: Float) {
        _uiState.value = _uiState.value.copy(delaySeconds = seconds)
        settingsManager.recordingDelay = seconds
        if (_uiState.value.appState == AppState.RECORDING) {
            currentSurface?.let { startDelayedPlayback(it) }
        }
    }

    fun selectModule(module: IPostureModule?) {
        _uiState.value = _uiState.value.copy(selectedModule = module, analysisResult = null)
        settingsManager.selectedModuleId = module?.let { it::class.java.simpleName }
    }

    fun setLaterality(laterality: Laterality) {
        val newSettings = _uiState.value.archerySettings.copy(laterality = laterality)
        settingsManager.laterality = laterality
        
        val compatibleModules = moduleFactory.getCompatibleModules(newSettings)
        val currentModule = _uiState.value.selectedModule
        val newSelected = if (compatibleModules.contains(currentModule)) currentModule else compatibleModules.firstOrNull()

        _uiState.value = _uiState.value.copy(
            archerySettings = newSettings,
            selectedModule = newSelected
        )
    }

    fun setBowType(bowType: BowType) {
        val newSettings = _uiState.value.archerySettings.copy(bowType = bowType)
        settingsManager.bowType = bowType

        val compatibleModules = moduleFactory.getCompatibleModules(newSettings)
        val currentModule = _uiState.value.selectedModule
        val newSelected = if (compatibleModules.contains(currentModule)) currentModule else compatibleModules.firstOrNull()

        _uiState.value = _uiState.value.copy(
            archerySettings = newSettings,
            selectedModule = newSelected
        )
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

    fun onDecoderStarted() {
        if (_uiState.value.appState == AppState.BUFFERING) {
            bufferingJob?.cancel()
            bufferingJob = viewModelScope.launch {
                val waitTime = (_uiState.value.delaySeconds * 1000).toLong().coerceAtLeast(500L)
                delay(waitTime)
                if (_uiState.value.appState == AppState.BUFFERING) {
                    _uiState.value = _uiState.value.copy(appState = AppState.RECORDING)
                }
            }
        }
    }

    fun enterReplayMode(): Boolean {
        if (visualCache.size > 0) {
            _uiState.update { it.copy(appState = AppState.REPLAY) }
            _replayIndex.value = (visualCache.size - 1).coerceAtLeast(0)
            return true
        }
        return false
    }

    fun exitReplayMode() {
        _uiState.update { it.copy(appState = AppState.RECORDING) }
        visualCache.clear()
    }

    fun exportReplay(outputFile: java.io.File, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until visualCache.size) {
                visualCache.getFrameAt(i)?.let { bitmaps.add(it) }
            }
            
            if (bitmaps.isEmpty()) {
                onComplete(false)
                return@launch
            }

            val success = videoExporter.export(
                bitmaps = bitmaps,
                outputFile = outputFile,
                width = bitmaps[0].width,
                height = bitmaps[0].height
            )
            onComplete(success)
        }
    }

    fun recordFrameToCache(bitmap: Bitmap) {
        if (_uiState.value.appState == AppState.RECORDING) {
            visualCache.addFrame(bitmap)
        }
    }

    fun getReusableBitmap(width: Int, height: Int): Bitmap {
        return visualCache.getReusableBitmap(width, height)
    }

    fun getCacheSize(): Int = visualCache.size

    fun setReplayIndex(index: Int) {
        _replayIndex.value = index.coerceIn(0, (visualCache.size - 1).coerceAtLeast(0))
    }

    private val _uiState = MutableStateFlow(
        MainUiState(
            delaySeconds = settingsManager.recordingDelay,
            useFrontCamera = settingsManager.useFrontCamera,
            archerySettings = ArcherySettings(
                laterality = settingsManager.laterality,
                bowType = settingsManager.bowType
            )
        ).let { state ->
            val compatible = moduleFactory.getCompatibleModules(state.archerySettings)
            state.copy(
                selectedModule = compatible.find { it::class.java.simpleName == settingsManager.selectedModuleId }
                    ?: compatible.firstOrNull()
            )
        }
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _replayIndex = MutableStateFlow(0)
    val replayIndex: StateFlow<Int> = _replayIndex.asStateFlow()

    val currentReplayFrame: StateFlow<Bitmap?> = _replayIndex
        .map { index -> visualCache.getFrameAt(index) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val badgeDefinitions = listOf(
        Badge("PERFECT_1S", fr.bayral.archerymonitor.resources.R.string.badge_perfect_1s, 0.95f, 1),
        Badge("SOLID_5S", fr.bayral.archerymonitor.resources.R.string.badge_solid_5s, 0.85f, 5),
        Badge("STATUE_15S", fr.bayral.archerymonitor.resources.R.string.badge_statue_15s, 0.80f, 15)
    )

    private val badgeIcons = mapOf(
        "PERFECT_1S" to "🎯",
        "SOLID_5S" to "🏅",
        "STATUE_15S" to "🗿"
    )

    private val _lastUnlockedBadge = MutableStateFlow<Pair<String, String>?>(null)
    val lastUnlockedBadge: StateFlow<Pair<String, String>?> = _lastUnlockedBadge.asStateFlow()

    fun getBadgeIcon(id: String) = badgeIcons[id] ?: "🏆"
    fun getBadgeDefinitions() = badgeDefinitions
    val unlockedBadges: Set<String> get() = settingsManager.unlockedBadges

    private val badgeProgress = mutableMapOf<String, Long>()

    init {
        orientationMonitor.start()

        orientationMonitor.tilt
            .onEach { tilt ->
                _uiState.value = _uiState.value.copy(
                    archerySettings = _uiState.value.archerySettings.copy(deviceTilt = tilt)
                )
            }
            .launchIn(viewModelScope)

        decoder.currentPlaybackTimestamp
            .onEach { pts ->
                if (_uiState.value.isAiEnabled && _uiState.value.appState == AppState.RECORDING) {
                    val syncedPose = syncEngine.getSyncPose(pts)
                    var analysisResult: AnalysisResult? = null
                    
                    if (syncedPose != null) {
                        analysisResult = _uiState.value.selectedModule?.analyze(
                            syncedPose,
                            _uiState.value.archerySettings
                        )
                        updateBadgeProgress(analysisResult?.score ?: 0f)
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

    private fun updateBadgeProgress(currentScore: Float) {
        val now = System.currentTimeMillis()
        badgeDefinitions.forEach { badge ->
            if (settingsManager.unlockedBadges.contains(badge.id)) return@forEach

            if (currentScore >= badge.minScore) {
                val startTime = badgeProgress[badge.id] ?: now
                badgeProgress[badge.id] = startTime
                
                val durationMs = now - startTime
                if (durationMs >= badge.durationSeconds * 1000) {
                    settingsManager.unlockBadge(badge.id)
                    _lastUnlockedBadge.value = badge.id to getBadgeIcon(badge.id)
                    viewModelScope.launch {
                        delay(3000)
                        _lastUnlockedBadge.value = null
                    }
                }
            } else {
                badgeProgress.remove(badge.id)
            }
        }
    }

    private var currentSurface: android.view.Surface? = null
    private var bufferingJob: Job? = null
    
    override fun onCleared() {
        super.onCleared()
        stopCapture()
        visualCache.clear()
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
    val videoRotation: Int = 0,
    val isCalibrationMode: Boolean = false,
    val archerySettings: ArcherySettings = ArcherySettings()
)
