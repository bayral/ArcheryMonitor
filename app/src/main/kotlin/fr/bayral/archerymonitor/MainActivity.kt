package fr.bayral.archerymonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.bayral.archerymonitor.core.interfaces.AppState
import fr.bayral.archerymonitor.ui.MainScreen
import fr.bayral.archerymonitor.ui.MainScreenContent
import fr.bayral.archerymonitor.ui.MainUiState
import fr.bayral.archerymonitor.ui.MainViewModel
import fr.bayral.archerymonitor.ui.theme.ArcheryMonitorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcheryMonitorTheme {
                val context = LocalContext.current
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        hasCameraPermission = granted
                        if (!granted) {
                            Toast.makeText(context, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            viewModel.toggleRecording()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun MainPreview() {
    ArcheryMonitorTheme {
        MainScreenContent(
            uiState = MainUiState(
                appState = AppState.IDLE,
                delaySeconds = 5f,
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
