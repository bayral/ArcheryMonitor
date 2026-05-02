package fr.bayral.archerymonitor.feature_camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

class H264Decoder @Inject constructor(
    private val bufferManager: IBufferManager
) {
    private var mediaCodec: MediaCodec? = null
    private var decoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _currentPlaybackTimestamp = MutableStateFlow(0L)
    val currentPlaybackTimestamp: StateFlow<Long> = _currentPlaybackTimestamp.asStateFlow()

    fun start(surface: Surface, width: Int, height: Int, delaySeconds: Float) {
        stop()
        Log.d("H264Decoder", "STARTING DECODER V3 - Delay: ${delaySeconds}s")
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }

            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                codec.configure(format, surface, null, 0)
                codec.start()
                mediaCodec = codec
                Log.d("H264Decoder", "Decoder started: $width x $height")
            } catch (e: Exception) {
                codec.release()
                throw e
            }
        } catch (e: Exception) {
            Log.e("H264Decoder", "Failed to start decoder: ${e.message}")
            mediaCodec = null
            return
        }

        decoderJob = scope.launch {
            val info = MediaCodec.BufferInfo()
            var lastQueuedPts = -1L

            while (isActive) {
                val codec = mediaCodec ?: break

                val latestPts = bufferManager.getLatestTimestamp()
                if (latestPts == 0L) {
                    delay(100)
                    continue
                }

                val targetTimeUs = latestPts - (delaySeconds * 1_000_000).toLong()
                val packets = bufferManager.getPacketsForRange(targetTimeUs - 100_000, targetTimeUs + 100_000)

                if (packets.isNotEmpty()) {
                    val packet = packets
                        .filter { it.info.presentationTimeUs > lastQueuedPts }
                        .minByOrNull { abs(it.info.presentationTimeUs - targetTimeUs) }

                    if (packet != null) {
                        try {
                            val inputBufferIndex = codec.dequeueInputBuffer(5000)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                if (inputBuffer != null) {
                                    inputBuffer.clear()
                                    inputBuffer.put(packet.data)
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        packet.info.size,
                                        packet.info.presentationTimeUs,
                                        packet.info.flags
                                    )
                                    lastQueuedPts = packet.info.presentationTimeUs
                                    if (packet.isKeyFrame) {
                                        Log.d("H264Decoder", "Queued KEYFRAME at PTS: ${packet.info.presentationTimeUs}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("H264Decoder", "Queuing error: ${e.message}")
                        }
                    }
                }

                try {
                    var outputBufferIndex = codec.dequeueOutputBuffer(info, 5000)
                    while (outputBufferIndex >= 0) {
                        codec.releaseOutputBuffer(outputBufferIndex, true)
                        _currentPlaybackTimestamp.value = info.presentationTimeUs
                        Log.d("H264Decoder", "[REPLAY] PIXELS ENVOYÉS A L'ÉCRAN ! PTS: ${info.presentationTimeUs}")
                        outputBufferIndex = codec.dequeueOutputBuffer(info, 0)
                    }
                } catch (e: Exception) {
                    if (e !is IllegalStateException) {
                        Log.e("H264Decoder", "Dequeuing error: ${e.message}")
                    }
                }

                delay(10)
            }
        }
    }

    fun stop() {
        decoderJob?.cancel()
        decoderJob = null
        mediaCodec?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.e("H264Decoder", "Error stopping codec: ${e.message}")
            } finally {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.e("H264Decoder", "Error releasing codec: ${e.message}")
                }
            }
        }
        mediaCodec = null
        _currentPlaybackTimestamp.value = 0L
    }
}
