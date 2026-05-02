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
import fr.bayral.archerymonitor.core.interfaces.AppState
import fr.bayral.archerymonitor.core.utils.MatrixUtils
import fr.bayral.archerymonitor.ui.components.SkeletonOverlay
import fr.bayral.archerymonitor.ui.theme.ArcheryMonitorTheme

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    MainScreenContent(
        uiState = uiState,
        onStartCapture = { lifecycleOwner, surfaceProvider ->
            viewModel.onStartCapture(lifecycleOwner, surfaceProvider)
        },
        onStopCapture = { viewModel.stopCapture() },
        onToggleRecording = { viewModel.toggleRecording() },
        onToggleAi = { viewModel.toggleAi() },
        onToggleCamera = { viewModel.toggleCamera() },
        onSetDelay = { viewModel.setDelay(it) },
    ) {
        viewModel.startDelayedPlayback(it)
    }
}

@Composable
fun MainScreenContent(
    uiState: MainUiState,
    onStartCapture: (androidx.lifecycle.LifecycleOwner, androidx.camera.core.Preview.SurfaceProvider) -> Unit,
    onStopCapture: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleAi: () -> Unit,
    onToggleCamera: () -> Unit,
    onSetDelay: (Float) -> Unit,
    onSurfaceCreated: (android.view.Surface) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }
    
    // Add lifecycle observer to restart capture on RESUME if it was lost
    DisposableEffect(lifecycleOwner, surfaceProvider) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    onStopCapture()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    surfaceProvider?.let { onStartCapture(lifecycleOwner, it) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start capture when both camera preference and surface provider are ready
    LaunchedEffect(uiState.useFrontCamera, surfaceProvider, lifecycleOwner) {
        surfaceProvider?.let {
            onStartCapture(lifecycleOwner, it)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Camera lifecycle is handled by the CameraProvider bound to the LifecycleOwner
            // and the ViewModel onCleared().
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Preview (Always running in background)
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
        if ((uiState.appState == AppState.RECORDING) && (uiState.delaySeconds > 0)) {
            val ratio = if ((uiState.videoWidth > 0) && (uiState.videoHeight > 0)) {
                uiState.videoWidth.toFloat() / uiState.videoHeight.toFloat()
            } else {
                9f / 16f
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            setZOrderMediaOverlay(true)
                            holder.addCallback(
                                object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        onSurfaceCreated(holder.surface)
                                    }
                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {}
                                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 3. AI Overlay (Linked to playback Box for same scaling)
                if (uiState.isAiEnabled) {
                    val matrix = remember(uiState.videoWidth, uiState.videoHeight, uiState.useFrontCamera) {
                        MatrixUtils.getTransformationMatrix(
                            viewWidth = uiState.videoWidth,
                            viewHeight = uiState.videoHeight,
                            rotationDegrees = 90,
                            isMirrored = uiState.useFrontCamera
                        )
                    }
                    SkeletonOverlay(
                        poseResult = uiState.currentPose,
                        transformationMatrix = matrix,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // RED INDICATOR Overlay
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopStart) {
                Text("● RECORDING / REPLAY", color = Color.Red, style = MaterialTheme.typography.headlineMedium)
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
                text = "Delay: ${uiState.delaySeconds.toInt()}s",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = uiState.delaySeconds,
                onValueChange = { onSetDelay(it) },
                valueRange = 0f..30f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onToggleRecording() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.appState == AppState.RECORDING) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (uiState.appState == AppState.RECORDING) "STOP" else "RECORD")
                }
                Button(onClick = { onToggleAi() }) {
                    Text(if (uiState.isAiEnabled) "AI On" else "AI Off")
                }
                Button(onClick = { onToggleCamera() }) {
                    Text(if (uiState.useFrontCamera) "Back" else "Front")
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2400px,dpi=440")
@Composable
fun MainScreenPreview() {
    ArcheryMonitorTheme {
        MainScreenContent(
            uiState = MainUiState(
                appState = AppState.IDLE,
                delaySeconds = 10f,
                isAiEnabled = true,
                currentPose = null,
                useFrontCamera = false
            ),
            onStartCapture = { _, _ -> },
            onStopCapture = {},
            onToggleRecording = {},
            onToggleAi = {},
            onToggleCamera = {},
            onSetDelay = {}
        )
    }
}
