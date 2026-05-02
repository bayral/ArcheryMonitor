package fr.bayral.archerymonitor.core.interfaces

import android.media.MediaCodec
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

import androidx.lifecycle.LifecycleOwner

interface ICameraProvider {
    fun startCapture(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider,
        onResolutionChanged: (Int, Int) -> Unit = { _, _ -> },
        lowResAnalysis: (android.media.Image) -> Unit,
        useFrontCamera: Boolean
    )
    fun setRecording(isRecording: Boolean)
    fun prepareRecording()
    fun stopCapture()
}

interface IBufferManager {
    fun addPacket(data: ByteBuffer, info: MediaCodec.BufferInfo)
    fun getPacketsForRange(startTimeUs: Long, endTimeUs: Long): List<EncodedPacket>
    fun getLatestTimestamp(): Long
    fun release()
}

data class EncodedPacket(
    val data: ByteBuffer,
    val info: MediaCodec.BufferInfo,
    val isKeyFrame: Boolean
)

interface IPoseAnalyzer {
    fun analyze(image: android.media.Image, timestamp: Long)
    val poseResults: StateFlow<PoseResult?>
}

interface ISyncEngine {
    fun getSyncPose(videoTimestamp: Long): PoseResult?
}

data class PoseResult(
    val landmarks: List<Landmark>,
    val worldLandmarks: List<Landmark>,
    val timestamp: Long
)

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
)

enum class AppState {
    IDLE, RECORDING
}
