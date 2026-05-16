package fr.bayral.archerymonitor.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.os.Build
import android.graphics.Canvas
import android.os.Environment
import android.view.TextureView
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bayral.archerymonitor.core.interfaces.AppState
import fr.bayral.archerymonitor.core.interfaces.BowType
import fr.bayral.archerymonitor.core.interfaces.IPostureModule
import fr.bayral.archerymonitor.core.interfaces.Laterality
import fr.bayral.archerymonitor.resources.R
import fr.bayral.archerymonitor.ui.components.SkeletonOverlay
import fr.bayral.archerymonitor.ui.theme.ArcheryMonitorTheme
import fr.bayral.archerymonitor.core.renderer.FrameComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val lastBadge by viewModel.lastUnlockedBadge.collectAsState()
    val replayFrame by viewModel.currentReplayFrame.collectAsState()
    val replayIndex by viewModel.replayIndex.collectAsState()
    var showTrophies by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportSuccessMsg = stringResource(R.string.export_success_downloads)
    val exportFailedMsg = stringResource(R.string.export_failed)
    val errorReplayEmptyMsg = stringResource(R.string.error_replay_empty)

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.appState == AppState.REPLAY) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                replayFrame?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Replay Frame",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Controls (Exit)
                IconButton(
                    onClick = { viewModel.exitReplayMode() },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                ) {
                    Text("🔙", style = MaterialTheme.typography.headlineSmall)
                }

                // Bottom Navigation & Export
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.large)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Slider(
                        value = replayIndex.toFloat(),
                        onValueChange = { viewModel.setReplayIndex(it.toInt()) },
                        valueRange = 0f..(viewModel.getCacheSize() - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { viewModel.setReplayIndex(replayIndex - 10) }) { Text("-10", textAlign = TextAlign.Center) }
                        FilledTonalButton(onClick = { viewModel.setReplayIndex(replayIndex - 1) }) { Text("-1", textAlign = TextAlign.Center) }
                        FilledTonalButton(onClick = { viewModel.setReplayIndex(replayIndex + 1) }) { Text("+1", textAlign = TextAlign.Center) }
                        FilledTonalButton(onClick = { viewModel.setReplayIndex(replayIndex + 10) }) { Text("+10", textAlign = TextAlign.Center) }
                    }

                    Button(
                        onClick = {
                            val tempFile = File(context.cacheDir, "temp_replay.mp4")
                            viewModel.exportReplay(tempFile) { success ->
                                scope.launch {
                                    val savedToPublic = saveVideoToPublicDownloads(context, tempFile)
                                    snackbarHostState.showSnackbar(
                                        if (success && savedToPublic) exportSuccessMsg
                                        else exportFailedMsg
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.btn_export), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
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
                onSetLaterality = { viewModel.setLaterality(it) },
                onSetBowType = { viewModel.setBowType(it) },
                onSurfaceCreated = { viewModel.startDelayedPlayback(it) },
                onEnterReplay = {
                    if (!viewModel.enterReplayMode()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(errorReplayEmptyMsg)
                        }
                    }
                },
                viewModel = viewModel
            )
        }

        // Floating Calibration Toggle (IDLE only)
        if (uiState.appState == AppState.IDLE) {
            FloatingActionButton(
                onClick = { viewModel.toggleCalibration() },
                containerColor = if (uiState.isCalibrationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (uiState.isCalibrationMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).size(48.dp)
            ) {
                Text(if (uiState.isCalibrationMode) "🎯" else "⚪", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp)
        )

        // 1. Score Display
        if (uiState.appState != AppState.REPLAY) {
            uiState.analysisResult?.let { result ->
                val scorePercent = (result.score * 100).toInt()
                val scoreColor = when {
                    result.score >= 0.85f -> Color.Green
                    result.score >= 0.60f -> Color.Yellow
                    else -> Color.Red
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 90.dp, end = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
                        Text(text = "$scorePercent%", color = scoreColor, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), textAlign = TextAlign.Center)
                    }
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
                        Text(text = "Shots: ${result.releaseCount}", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // 2. Badge Banner
        AnimatedVisibility(visible = lastBadge != null, modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
            lastBadge?.let { (badgeId, icon) ->
                val badgeName = viewModel.getBadgeDefinitions().find { it.id == badgeId }?.labelResId?.let { stringResource(it) } ?: badgeId
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge, shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(icon, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Column {
                            Text(stringResource(R.string.badge_unlocked_title), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            Text(badgeName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // 3. Trophy Dialog
        if (showTrophies) {
            AlertDialog(
                onDismissRequest = { showTrophies = false },
                title = { Text(stringResource(R.string.title_trophies), textAlign = TextAlign.Center) },
                text = {
                    val unlocked = viewModel.unlockedBadges
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        viewModel.getBadgeDefinitions().forEach { badge ->
                            val isUnlocked = unlocked.contains(badge.id)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = if (isUnlocked) viewModel.getBadgeIcon(badge.id) else "🔒", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(text = stringResource(badge.labelResId), style = MaterialTheme.typography.titleMedium, color = if (isUnlocked) Color.Unspecified else Color.Gray, textAlign = TextAlign.Start)
                                    Text(text = "${(badge.minScore * 100).toInt()}% / ${badge.durationSeconds}s", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Start)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showTrophies = false }) { Text("OK", textAlign = TextAlign.Center) } }
            )
        }

        IconButton(onClick = { showTrophies = true }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 16.dp)) {
            Text("🏆", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
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
    onSetLaterality: (Laterality) -> Unit,
    onSetBowType: (BowType) -> Unit,
    onEnterReplay: () -> Unit,
    onSurfaceCreated: (android.view.Surface) -> Unit = {},
    viewModel: MainViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    val frameComposer = remember { FrameComposer() }

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
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current

        // Camera Preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.surfaceProvider.also { surfaceProvider = it }
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
        )

        // Delayed Playback or Calibration Overlay
        if ((uiState.appState != AppState.IDLE && uiState.delaySeconds > 0) || uiState.isCalibrationMode) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                if (uiState.appState != AppState.IDLE) {
                    val isPortrait = (uiState.videoRotation == 90 || uiState.videoRotation == 270)
                    val contentW = if (isPortrait) uiState.videoHeight else uiState.videoWidth
                    val contentH = if (isPortrait) uiState.videoWidth else uiState.videoHeight

                    val modifier = if (boxSize != IntSize.Zero && contentW > 0 && contentH > 0) {
                        val scale = maxOf(boxSize.width.toFloat() / contentW, boxSize.height.toFloat() / contentH)
                        Modifier.requiredSize(width = with(density) { (contentW * scale).toDp() }, height = with(density) { (contentH * scale).toDp() })
                    } else {
                        Modifier.fillMaxSize()
                    }

                    AndroidView(
                        factory = { context ->
                            TextureView(context).apply {
                                textureViewRef = this
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) { onSurfaceCreated(android.view.Surface(st)) }
                                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean = true
                                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = modifier
                    )
                }

                if (boxSize != IntSize.Zero) {
                    val matrix = remember(uiState.videoWidth, uiState.videoHeight, uiState.videoRotation, uiState.useFrontCamera, boxSize) {
                        fr.bayral.archerymonitor.core.utils.MatrixUtils.getTransformationMatrix(
                            srcWidth = uiState.videoWidth, srcHeight = uiState.videoHeight,
                            viewWidth = boxSize.width, viewHeight = boxSize.height,
                            rotationDegrees = uiState.videoRotation, isMirrored = uiState.useFrontCamera
                        )
                    }

                    val pose = uiState.currentPose
                    val analysis = uiState.analysisResult

                    SkeletonOverlay(
                        poseResult = pose,
                        analysisResult = analysis,
                        transformationMatrix = matrix,
                        isCalibrationMode = uiState.isCalibrationMode,
                        calibrationText = stringResource(R.string.label_calibration_guide),
                        laterality = uiState.archerySettings.laterality,
                        modifier = Modifier.fillMaxSize()
                    )


                    // ROBUST CAPTURE LOGIC (TextureView based)
                    if (uiState.appState == AppState.RECORDING) {
                        val currentTextureView = textureViewRef
                        if (currentTextureView != null) {
                            SideEffect {
                                // Important: capture with correct aspect ratio
                                val isPortrait = (uiState.videoRotation == 90 || uiState.videoRotation == 270)
                                val videoW = if (isPortrait) uiState.videoHeight else uiState.videoWidth
                                val videoH = if (isPortrait) uiState.videoWidth else uiState.videoHeight

                                // We use native video resolution for cache to ensure perfect ratio
                                val bitmap = viewModel.getReusableBitmap(videoW, videoH)
                                try {
                                    currentTextureView.getBitmap(bitmap)

                                    if (uiState.isAiEnabled) {
                                        val canvas = Canvas(bitmap)
                                        // Since we capture at video resolution, we need a matrix
                                        // that maps normalized landmarks to this specific bitmap size
                                        val captureMatrix = fr.bayral.archerymonitor.core.utils.MatrixUtils.getTransformationMatrix(
                                            srcWidth = uiState.videoWidth, srcHeight = uiState.videoHeight,
                                            viewWidth = videoW, viewHeight = videoH,
                                            rotationDegrees = uiState.videoRotation, isMirrored = uiState.useFrontCamera
                                        )
                                        frameComposer.compose(canvas, pose, analysis, captureMatrix, false, null)
                                    }
                                    viewModel.recordFrameToCache(bitmap)
                                } catch (e: Exception) {
                                    android.util.Log.e("MainScreen", "Capture failed", e)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status Indicators
        Column(modifier = Modifier.fillMaxSize().padding(top = 80.dp, start = 32.dp)) {
            if (uiState.appState == AppState.BUFFERING) {
                Text(stringResource(R.string.status_buffering), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            } else if (uiState.appState == AppState.RECORDING) {
                Text(stringResource(R.string.status_recording), color = Color.Red, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }

            if (uiState.appState == AppState.IDLE) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) {
                        Text(
                            text = if (uiState.selectedModule == null) stringResource(R.string.label_none) else if (uiState.selectedModule.labelResId == 0) "General" else stringResource(uiState.selectedModule.labelResId),
                            textAlign = TextAlign.Center
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.label_none), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }, onClick = { onSelectModule(null); expanded = false })
                        availableModules.forEach { module ->
                            DropdownMenuItem(text = { Text(stringResource(module.labelResId), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }, onClick = { onSelectModule(module); expanded = false })
                        }
                    }
                }

                if (uiState.isAiEnabled) {
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var latExpanded by remember { mutableStateOf(false) }
                        Box {
                            FilledTonalButton(onClick = { latExpanded = true }) { Text(if (uiState.archerySettings.laterality == Laterality.RIGHT_HANDED) "R" else "L", textAlign = TextAlign.Center) }
                            DropdownMenu(expanded = latExpanded, onDismissRequest = { latExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.label_right_handed), textAlign = TextAlign.Center) }, onClick = { onSetLaterality(Laterality.RIGHT_HANDED); latExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.label_left_handed), textAlign = TextAlign.Center) }, onClick = { onSetLaterality(Laterality.LEFT_HANDED); latExpanded = false })
                            }
                        }

                        var bowExpanded by remember { mutableStateOf(false) }
                        Box {
                            FilledTonalButton(onClick = { bowExpanded = true }) { Text(uiState.archerySettings.bowType.name, textAlign = TextAlign.Center) }
                            DropdownMenu(expanded = bowExpanded, onDismissRequest = { bowExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.label_bow_recurve), textAlign = TextAlign.Center) }, onClick = { onSetBowType(BowType.RECURVE); bowExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.label_bow_barebow), textAlign = TextAlign.Center) }, onClick = { onSetBowType(BowType.BAREBOW); bowExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.label_bow_compound), textAlign = TextAlign.Center) }, onClick = { onSetBowType(BowType.COMPOUND); bowExpanded = false })
                            }
                        }
                    }
                }
            }
        }

        if (uiState.appState == AppState.BUFFERING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.appState == AppState.IDLE) {
                Text(text = stringResource(R.string.label_delay, uiState.delaySeconds.toInt()), color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Slider(value = uiState.delaySeconds, onValueChange = { onSetDelay(it) }, valueRange = 1f..30f, modifier = Modifier.fillMaxWidth())
            }

            // Primary Action Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { onToggleRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.appState != AppState.IDLE) Color.Red else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (uiState.appState != AppState.IDLE) stringResource(R.string.btn_stop) else stringResource(R.string.btn_record),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (uiState.appState == AppState.IDLE) {
                    Button(onClick = { onToggleAi() }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = if (uiState.isAiEnabled) stringResource(R.string.btn_ai_off) else stringResource(R.string.btn_ai_on),
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(onClick = { onToggleCamera() }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = if (uiState.useFrontCamera) stringResource(R.string.btn_camera_back) else stringResource(R.string.btn_camera_front),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Secondary Action Row (Replay)
            if (uiState.appState == AppState.RECORDING) {
                Button(
                    onClick = { onEnterReplay() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.btn_replay), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1080px,height=2400px,dpi=440", apiLevel = 36)
@Composable
fun MainScreenPreview() {
    ArcheryMonitorTheme {
        Text("Preview requires MainViewModel injection")
    }
}

/**
 * Saves a file to the public Downloads directory using MediaStore for better visibility and Android 10+ compliance.
 */
suspend fun saveVideoToPublicDownloads(context: Context, sourceFile: File): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists()) return@withContext false

    val fileName = "archery_replay_${System.currentTimeMillis()}.mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let { targetUri ->
        try {
            resolver.openOutputStream(targetUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Trigger a scan for the newly created file so it appears in Gallery immediately
            MediaScannerConnection.scanFile(
                context,
                arrayOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath + File.separator + fileName),
                arrayOf("video/mp4"),
                null
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "Failed to copy video to MediaStore", e)
            false
        }
    } ?: false
}
