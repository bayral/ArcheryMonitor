package fr.bayral.archerymonitor.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import fr.bayral.archerymonitor.resources.R
import fr.bayral.archerymonitor.core.interfaces.AppState
import fr.bayral.archerymonitor.core.interfaces.IPostureModule
import fr.bayral.archerymonitor.core.utils.MatrixUtils
import fr.bayral.archerymonitor.ui.components.SkeletonOverlay
import fr.bayral.archerymonitor.ui.theme.ArcheryMonitorTheme

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    MainScreenContent(
        uiState = uiState,
        availableModules = viewModel.getAvailableModules(),
        onStartCapture = { lifecycleOwner, surfaceProvider ->
            viewModel.onStartCapture(lifecycleOwner, surfaceProvider)
        },
        onStopCapture = { viewModel.stopCapture() },
        onToggleRecording = { viewModel.toggleRecording() },
        onToggleAi = { viewModel.toggleAi() },
        onToggleCamera = { viewModel.toggleCamera() },
        onSetDelay = { viewModel.setDelay(it) },
        onSelectModule = { viewModel.selectModule(it) },
    ) {
        viewModel.startDelayedPlayback(it)
    }
}

@Composable
fun MainScreenContent(
    uiState: MainUiState,
    availableModules: List<IPostureModule>,
    onStartCapture: (androidx.lifecycle.LifecycleOwner, androidx.camera.core.Preview.SurfaceProvider) -> Unit,
    onStopCapture: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleAi: () -> Unit,
    onToggleCamera: () -> Unit,
    onSetDelay: (Float) -> Unit,
    onSelectModule: (IPostureModule?) -> Unit,
    onSurfaceCreated: (android.view.Surface) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }

    // CHALLENGE: Lifecycle and Surface abandonment.
    // Releasing CameraX and Decoders on PAUSE is mandatory to avoid "BufferQueue abandoned" errors.
    DisposableEffect(lifecycleOwner, surfaceProvider) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> onStopCapture()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> surfaceProvider?.let { onStartCapture(lifecycleOwner, it) }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.useFrontCamera, surfaceProvider, lifecycleOwner) {
        surfaceProvider?.let { onStartCapture(lifecycleOwner, it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Live Camera Preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.surfaceProvider.also { surfaceProvider = it }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Delayed Playback Surface
        if ((uiState.appState != AppState.IDLE) && (uiState.delaySeconds > 0)) {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // Determine orientation-adjusted dimensions
                val isPortrait = (uiState.videoRotation == 90 || uiState.videoRotation == 270)
                val contentW = if (isPortrait) uiState.videoHeight else uiState.videoWidth
                val contentH = if (isPortrait) uiState.videoWidth else uiState.videoHeight

                val modifier = if (boxSize != IntSize.Zero && contentW > 0 && contentH > 0) {
                    val scale = maxOf(
                        boxSize.width.toFloat() / contentW,
                        boxSize.height.toFloat() / contentH
                    )
                    Modifier.requiredSize(
                        width = with(density) { (contentW * scale).toDp() },
                        height = with(density) { (contentH * scale).toDp() }
                    )
                } else {
                    Modifier.fillMaxSize()
                }

                AndroidView(
                    factory = { context ->
                        android.view.TextureView(context).apply {
                            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                    onSurfaceCreated(android.view.Surface(st))
                                }
                                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = modifier
                )

                // 3. AI Overlay (Must match the boxSize which is the visible screen area)
                if (uiState.isAiEnabled && boxSize != IntSize.Zero && contentW > 0) {
                    val matrix = remember(uiState.videoWidth, uiState.videoHeight, uiState.videoRotation, uiState.useFrontCamera, boxSize) {
                        fr.bayral.archerymonitor.core.utils.MatrixUtils.getTransformationMatrix(
                            srcWidth = uiState.videoWidth,
                            srcHeight = uiState.videoHeight,
                            viewWidth = boxSize.width,
                            viewHeight = boxSize.height,
                            rotationDegrees = uiState.videoRotation,
                            isMirrored = uiState.useFrontCamera
                        )
                    }
                    SkeletonOverlay(
                        poseResult = uiState.currentPose,
                        analysisResult = uiState.analysisResult,
                        transformationMatrix = matrix,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Overlay Status + Analysis Module Selector
        Column(modifier = Modifier.fillMaxSize().padding(top = 80.dp, start = 32.dp)) {
            if (uiState.appState == AppState.BUFFERING) {
                Text(stringResource(R.string.status_buffering), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            } else if (uiState.appState == AppState.RECORDING) {
                Text(stringResource(R.string.status_recording), color = Color.Red, style = MaterialTheme.typography.headlineMedium)
            }

            // Analysis Module Selector
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text(
                        if (uiState.selectedModule == null) stringResource(R.string.label_none)
                        else if (uiState.selectedModule.labelResId == 0) "General"
                        else stringResource(uiState.selectedModule.labelResId)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.label_none), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSelectModule(null)
                            expanded = false
                        }
                    )
                    availableModules.forEach { module ->
                        DropdownMenuItem(
                            text = { Text(stringResource(module.labelResId), color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onSelectModule(module)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (uiState.appState == AppState.BUFFERING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
        }

        // 4. Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.label_delay, uiState.delaySeconds.toInt()),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = uiState.delaySeconds,
                onValueChange = { onSetDelay(it) },
                valueRange = 1f..30f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { onToggleRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.appState != AppState.IDLE) Color.Red else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (uiState.appState != AppState.IDLE) stringResource(R.string.btn_stop) else stringResource(R.string.btn_record))
                }
                Button(onClick = { onToggleAi() }) {
                    Text(if (uiState.isAiEnabled) stringResource(R.string.btn_ai_off) else stringResource(R.string.btn_ai_on))
                }
                Button(onClick = { onToggleCamera() }) {
                    Text(if (uiState.useFrontCamera) stringResource(R.string.btn_camera_back) else stringResource(R.string.btn_camera_front))
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2400px,dpi=440")
@Composable
fun MainScreenPreview() {
    ArcheryMonitorTheme {
        // Since we can't easily mock a Hilt ViewModel in a Preview,
        // we provide a UI-only preview of MainScreenContent
        // with a dummy viewModel instance if possible, or refactor.
        // For now, let's just use a stub.
        Text("Preview requires MainViewModel injection")
    }
}
