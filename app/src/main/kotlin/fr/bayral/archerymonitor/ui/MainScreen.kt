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
                
                // Use requiredSize to force FILL_CENTER and bypass parent constraints
                val modifier = if (boxSize != IntSize.Zero && contentW > 0 && contentH > 0) {
                    val scale = Math.max(
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
                        SurfaceView(context).apply {
                            setZOrderMediaOverlay(true)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) = onSurfaceCreated(holder.surface)
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {}
                                override fun surfaceDestroyed(h: SurfaceHolder) {}
                            })
                        }
                    },
                    modifier = modifier
                )

                // 3. AI Overlay (Stays aligned with the Box, which is the visible screen)
                if (uiState.isAiEnabled && boxSize != IntSize.Zero && contentW > 0) {
                    val matrix = remember(uiState.videoWidth, uiState.videoHeight, uiState.videoRotation, uiState.useFrontCamera, boxSize) {
                        MatrixUtils.getTransformationMatrix(
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
                        transformationMatrix = matrix,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
            
        // Overlay Status
        Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp, start = 32.dp), contentAlignment = Alignment.TopStart) {
            if (uiState.appState == AppState.BUFFERING) {
                Text("● BUFFERING...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            } else if (uiState.appState == AppState.RECORDING) {
                Text("● RECORDING / REPLAY", color = Color.Red, style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (uiState.appState == AppState.BUFFERING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
        }

        // Controls
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
            Text(text = "Delay: ${uiState.delaySeconds.toInt()}s", color = Color.White, style = MaterialTheme.typography.bodyLarge)
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
                    Text(if (uiState.appState != AppState.IDLE) "STOP" else "RECORD")
                }
                Button(onClick = { onToggleAi() }) {
                    Text(if (uiState.isAiEnabled) "AI Off" else "AI On")
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
            uiState = MainUiState(appState = AppState.IDLE, delaySeconds = 10f, isAiEnabled = true, currentPose = null, useFrontCamera = false),
            onStartCapture = { _, _ -> }, onStopCapture = {}, onToggleRecording = {}, onToggleAi = {}, onToggleCamera = {}, onSetDelay = {}
        )
    }
}
